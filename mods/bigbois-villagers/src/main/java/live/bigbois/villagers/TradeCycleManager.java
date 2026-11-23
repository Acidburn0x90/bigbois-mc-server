package live.bigbois.villagers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import live.bigbois.villagers.mixin.VillagerInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cross-play villager trade cycling, enchanted-book filtering, and price range inspection.
 */
public final class TradeCycleManager {
    private static final double SEARCH_RADIUS = 6.0;
    private static final int MAX_ROLLS = 10_000;
    private static final long COOLDOWN_TICKS = 10L;

    private static final Map<UUID, Long> LAST_ACTION_TICK = new HashMap<>();

    private static final Map<String, String> ENCHANT_ALIASES = Map.ofEntries(
        Map.entry("mend", "mending"),
        Map.entry("eff", "efficiency"),
        Map.entry("unbr", "unbreaking"),
        Map.entry("prot", "protection"),
        Map.entry("fprot", "fire_protection"),
        Map.entry("fire_prot", "fire_protection"),
        Map.entry("pprot", "projectile_protection"),
        Map.entry("proj_prot", "projectile_protection"),
        Map.entry("bprot", "blast_protection"),
        Map.entry("blast_prot", "blast_protection"),
        Map.entry("ff", "feather_falling"),
        Map.entry("feather", "feather_falling"),
        Map.entry("resp", "respiration"),
        Map.entry("aqua", "aqua_affinity"),
        Map.entry("depth", "depth_strider"),
        Map.entry("frost", "frost_walker"),
        Map.entry("sharp", "sharpness"),
        Map.entry("kb", "knockback"),
        Map.entry("knock", "knockback"),
        Map.entry("fa", "fire_aspect"),
        Map.entry("fire", "fire_aspect"),
        Map.entry("loot", "looting"),
        Map.entry("sweep", "sweeping_edge"),
        Map.entry("silk", "silk_touch"),
        Map.entry("fort", "fortune"),
        Map.entry("pow", "power"),
        Map.entry("pun", "punch"),
        Map.entry("inf", "infinity"),
        Map.entry("luck", "luck_of_the_sea"),
        Map.entry("imp", "impaling"),
        Map.entry("chan", "channeling"),
        Map.entry("rip", "riptide"),
        Map.entry("loy", "loyalty"),
        Map.entry("multi", "multishot"),
        Map.entry("quick", "quick_charge"),
        Map.entry("pierce", "piercing")
    );

    private TradeCycleManager() {}

    /** Registers the player-accessible {@code /cycle} command tree. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("cycle")
                .executes(ctx -> sendUsage(ctx.getSource()))
                .then(Commands.literal("list").executes(TradeCycleManager::handleList))
                .then(Commands.literal("reroll").executes(TradeCycleManager::handleReroll))
                .then(
                    Commands.literal("range")
                        .then(
                            Commands.argument("enchant", StringArgumentType.word())
                                .suggests(TradeCycleManager::suggestEnchantments)
                                .executes(ctx -> handleRange(ctx, null))
                                .then(
                                    Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> handleRange(ctx, IntegerArgumentType.getInteger(ctx, "level")))
                                )
                        )
                )
                .then(
                    Commands.literal("book")
                        .then(
                            Commands.argument("enchant", StringArgumentType.word())
                                .suggests(TradeCycleManager::suggestEnchantments)
                                .executes(ctx -> handleBook(ctx, null, 64))
                                .then(
                                    Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> handleBook(ctx, IntegerArgumentType.getInteger(ctx, "level"), 64))
                                        .then(
                                            Commands.argument("max_price", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> handleBook(
                                                    ctx,
                                                    IntegerArgumentType.getInteger(ctx, "level"),
                                                    IntegerArgumentType.getInteger(ctx, "max_price")
                                                ))
                                        )
                                )
                        )
                )
        );
    }

    /** Handles Sneak + Right-Click with an empty hand on an unlocked villager to reroll once. */
    public static InteractionResult onUseEntity(
        Player player,
        Level level,
        InteractionHand hand,
        Entity entity,
        EntityHitResult hitResult
    ) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !(entity instanceof Villager villager)) {
            return InteractionResult.PASS;
        }
        if (villager.isBaby()
            || villager.getVillagerData().profession().is(VillagerProfession.NONE)
            || villager.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
            return InteractionResult.PASS;
        }
        if (isCooldownActive(sp)) {
            return InteractionResult.SUCCESS;
        }
        if (isLocked(villager)) {
            sp.sendSystemMessage(Component.literal("[Cycle] Villager trades are locked (already traded).").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (villager.isSleeping()) {
            sp.sendSystemMessage(Component.literal("[Cycle] Villager is asleep.").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        performSingleReroll(sp, (ServerLevel) level, villager);
        return InteractionResult.SUCCESS;
    }

    /** Handles Sneak + Right-Click on a sign near an unlocked novice Librarian to auto-cycle. */
    public static InteractionResult onUseBlock(
        Player player,
        Level level,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!sp.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(hitResult.getBlockPos());
        if (!(be instanceof SignBlockEntity sign)) {
            return InteractionResult.PASS;
        }

        List<String> lines = readNonEmptySignLines(sign);
        if (lines.isEmpty()) {
            return InteractionResult.PASS;
        }

        SignQuery query = parseSignQuery(lines);
        if (query == null) {
            return InteractionResult.PASS;
        }

        Optional<Holder.Reference<Enchantment>> holderOpt = resolveEnchantment(level.registryAccess(), query.enchantRaw());
        if (holderOpt.isEmpty() || !holderOpt.get().is(EnchantmentTags.TRADEABLE)) {
            return InteractionResult.PASS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 signCenter = Vec3.atCenterOf(hitResult.getBlockPos());
        Villager librarian = findTargetVillager(serverLevel, signCenter, null, true);
        if (librarian == null) {
            return InteractionResult.PASS;
        }
        if (isCooldownActive(sp)) {
            return InteractionResult.SUCCESS;
        }

        Holder.Reference<Enchantment> holder = holderOpt.get();
        int levelReq = query.level() != null ? query.level() : holder.value().getMaxLevel();
        cycleLibrarian(sp, serverLevel, librarian, holder, levelReq, query.maxPrice());
        return InteractionResult.SUCCESS;
    }

    private static int sendUsage(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.literal(
                "[Cycle] Commands: /cycle book <enchant> [level] [max_price] | /cycle range <enchant> [level] | /cycle list | /cycle reroll | Or Sneak+Right-Click a villager (empty hand) or lectern sign."
            ).withStyle(ChatFormatting.AQUA),
            false
        );
        return 1;
    }

    private static int handleList(CommandContext<CommandSourceStack> ctx) {
        List<Holder.Reference<Enchantment>> tradeable = getTradeableEnchantments(ctx.getSource().registryAccess());
        MutableComponent msg = Component.literal("[Cycle] Tradeable enchantments (" + tradeable.size() + "): ")
            .withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < tradeable.size(); i++) {
            Holder.Reference<Enchantment> ref = tradeable.get(i);
            int maxLvl = ref.value().getMaxLevel();
            int[] range = getPriceRange(ref, maxLvl);
            if (i > 0) {
                msg.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            String id = ref.key().identifier().getPath();
            String suffix = maxLvl > 1 ? (" (1-" + maxLvl + ", max " + range[0] + "-" + range[1] + "e)") : (" (" + range[0] + "-" + range[1] + "e)");
            msg.append(Component.literal(id + suffix).withStyle(ChatFormatting.WHITE));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int handleRange(CommandContext<CommandSourceStack> ctx, Integer requestedLevel) {
        String raw = StringArgumentType.getString(ctx, "enchant");
        Optional<Holder.Reference<Enchantment>> holderOpt = resolveEnchantment(ctx.getSource().registryAccess(), raw);
        if (holderOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] Unknown or ambiguous enchantment: " + raw + ". Use /cycle list."));
            return 0;
        }
        Holder.Reference<Enchantment> holder = holderOpt.get();
        if (!holder.is(EnchantmentTags.TRADEABLE)) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] Not offered by Librarians: " + holder.key().identifier().getPath()));
            return 0;
        }

        int maxLvl = holder.value().getMaxLevel();
        boolean doubled = holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE);
        String name = holder.key().identifier().getPath();
        if (requestedLevel != null) {
            if (requestedLevel < 1 || requestedLevel > maxLvl) {
                ctx.getSource().sendFailure(Component.literal("[Cycle] Valid level for " + name + " is 1-" + maxLvl + "."));
                return 0;
            }
            int[] range = getPriceRange(holder, requestedLevel);
            String note = doubled ? " (treasure 2x)" : "";
            ctx.getSource().sendSuccess(
                () -> Component.literal("[Cycle] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Enchantment.getFullname(holder, requestedLevel).copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": " + range[0] + "-" + range[1] + " emeralds" + note).withStyle(ChatFormatting.GREEN)),
                false
            );
            return 1;
        }

        MutableComponent msg = Component.literal("[Cycle] " + name + (doubled ? " (treasure 2x)" : "") + " price ranges: ")
            .withStyle(ChatFormatting.AQUA);
        for (int lvl = 1; lvl <= maxLvl; lvl++) {
            int[] range = getPriceRange(holder, lvl);
            if (lvl > 1) {
                msg.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            }
            msg.append(Component.literal("Lvl " + lvl + ": " + range[0] + "-" + range[1] + "e").withStyle(ChatFormatting.GREEN));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int handleReroll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (isCooldownActive(sp)) {
            return 0;
        }
        ServerLevel level = sp.level();
        Villager villager = findTargetVillager(level, sp.getEyePosition(), sp.getLookAngle(), false);
        if (villager == null) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] No unlocked novice villager found within 6 blocks."));
            return 0;
        }
        performSingleReroll(sp, level, villager);
        return 1;
    }

    private static int handleBook(CommandContext<CommandSourceStack> ctx, Integer requestedLevel, int maxPrice) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        String raw = StringArgumentType.getString(ctx, "enchant");
        Optional<Holder.Reference<Enchantment>> holderOpt = resolveEnchantment(ctx.getSource().registryAccess(), raw);
        if (holderOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] Unknown or ambiguous enchantment: " + raw + ". Use /cycle list."));
            return 0;
        }
        Holder.Reference<Enchantment> holder = holderOpt.get();
        if (!holder.is(EnchantmentTags.TRADEABLE)) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] Librarians do not sell " + holder.key().identifier().getPath() + "."));
            return 0;
        }
        if (isCooldownActive(sp)) {
            return 0;
        }

        ServerLevel level = sp.level();
        Villager librarian = findTargetVillager(level, sp.getEyePosition(), sp.getLookAngle(), true);
        if (librarian == null) {
            ctx.getSource().sendFailure(Component.literal("[Cycle] No unlocked novice Librarian found within 6 blocks."));
            return 0;
        }

        int targetLvl = requestedLevel != null ? requestedLevel : holder.value().getMaxLevel();
        return cycleLibrarian(sp, level, librarian, holder, targetLvl, maxPrice) ? 1 : 0;
    }

    private static boolean cycleLibrarian(
        ServerPlayer sp,
        ServerLevel level,
        Villager librarian,
        Holder.Reference<Enchantment> target,
        int targetLvl,
        int maxPrice
    ) {
        int maxLvl = target.value().getMaxLevel();
        String name = target.key().identifier().getPath();
        if (targetLvl < 1 || targetLvl > maxLvl) {
            sp.sendSystemMessage(Component.literal("[Cycle] Valid level for " + name + " is 1-" + maxLvl + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        int[] range = getPriceRange(target, targetLvl);
        if (maxPrice < range[0]) {
            sp.sendSystemMessage(
                Component.literal(
                    "[Cycle] Max price " + maxPrice + " is below minimum (" + range[0] + "-" + range[1] + " emeralds)."
                ).withStyle(ChatFormatting.RED)
            );
            return false;
        }
        if (isLocked(librarian) || librarian.isSleeping()) {
            sp.sendSystemMessage(Component.literal("[Cycle] Target Librarian is locked or asleep.").withStyle(ChatFormatting.RED));
            return false;
        }

        for (int roll = 1; roll <= MAX_ROLLS; roll++) {
            rerollVillagerOffers(level, librarian);
            for (MerchantOffer offer : librarian.getOffers()) {
                ItemStack result = offer.getResult();
                if (!result.is(Items.ENCHANTED_BOOK)) {
                    continue;
                }
                for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(result).entrySet()) {
                    if (entry.getKey().is(target.key()) && entry.getIntValue() >= targetLvl) {
                        int price = offer.getBaseCostA().getCount();
                        if (price <= maxPrice) {
                            syncOpenMerchantMenu(librarian);
                            playFeedback(level, librarian, true);
                            int finalRoll = roll;
                            sp.sendSystemMessage(
                                Component.literal("[Cycle] Found ")
                                    .withStyle(ChatFormatting.GREEN)
                                    .append(Enchantment.getFullname(target, entry.getIntValue()).copy().withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(" for " + price + " emeralds (range " + range[0] + "-" + range[1] + ") in " + finalRoll + " rolls.").withStyle(ChatFormatting.GREEN))
                            );
                            return true;
                        }
                    }
                }
            }
        }

        syncOpenMerchantMenu(librarian);
        playFeedback(level, librarian, false);
        sp.sendSystemMessage(
            Component.literal(
                "[Cycle] No match in " + MAX_ROLLS + " rolls (valid price range: " + range[0] + "-" + range[1] + " emeralds). Try again or raise max price."
            ).withStyle(ChatFormatting.RED)
        );
        return false;
    }

    private static void performSingleReroll(ServerPlayer sp, ServerLevel level, Villager villager) {
        rerollVillagerOffers(level, villager);
        syncOpenMerchantMenu(villager);
        playFeedback(level, villager, true);

        MutableComponent msg = Component.literal("[Cycle] New trades: ").withStyle(ChatFormatting.AQUA);
        MerchantOffers offers = villager.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (i > 0) {
                msg.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            }
            MerchantOffer offer = offers.get(i);
            ItemStack result = offer.getResult();
            if (result.is(Items.ENCHANTED_BOOK)) {
                var enchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
                boolean first = true;
                for (var entry : enchants.entrySet()) {
                    if (!first) {
                        msg.append(Component.literal("+").withStyle(ChatFormatting.GRAY));
                    }
                    first = false;
                    int[] range = getPriceRange(entry.getKey(), entry.getIntValue());
                    msg.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).copy().withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (" + offer.getBaseCostA().getCount() + "e, range " + range[0] + "-" + range[1] + "e)").withStyle(ChatFormatting.WHITE));
                }
            } else {
                String costText = offer.getBaseCostA().getCount() + " " + offer.getBaseCostA().getHoverName().getString();
                String resText = result.getCount() + " " + result.getHoverName().getString();
                msg.append(Component.literal(costText + " -> " + resText).withStyle(ChatFormatting.WHITE));
            }
        }
        sp.sendSystemMessage(msg);
    }

    private static void rerollVillagerOffers(ServerLevel level, Villager villager) {
        villager.setOffers(new MerchantOffers());
        ((VillagerInvoker) (Object) villager).bigbois$invokeUpdateTrades(level);
    }

    private static void syncOpenMerchantMenu(Villager villager) {
        if (villager.getTradingPlayer() instanceof ServerPlayer trader && trader.containerMenu instanceof MerchantMenu menu) {
            MerchantOffers offers = villager.getOffers();
            menu.setOffers(offers);
            trader.sendMerchantOffers(
                menu.containerId,
                offers,
                villager.getVillagerData().level(),
                villager.getVillagerXp(),
                villager.showProgressBar(),
                villager.canRestock()
            );
        }
    }

    private static void playFeedback(ServerLevel level, Villager villager, boolean success) {
        if (success) {
            level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.5, villager.getZ(), 8, 0.35, 0.35, 0.35, 0.05);
        } else {
            level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.5, villager.getZ(), 6, 0.35, 0.35, 0.35, 0.05);
        }
    }

    private static Villager findTargetVillager(ServerLevel level, Vec3 origin, Vec3 lookDir, boolean librarianOnly) {
        AABB box = AABB.ofSize(origin, SEARCH_RADIUS * 2.0, SEARCH_RADIUS * 2.0, SEARCH_RADIUS * 2.0);
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, box, villager -> {
            if (villager.isBaby() || villager.isSleeping() || isLocked(villager)) {
                return false;
            }
            if (villager.position().subtract(origin).lengthSqr() > SEARCH_RADIUS * SEARCH_RADIUS) {
                return false;
            }
            var prof = villager.getVillagerData().profession();
            if (librarianOnly) {
                return prof.is(VillagerProfession.LIBRARIAN);
            }
            return !prof.is(VillagerProfession.NONE) && !prof.is(VillagerProfession.NITWIT);
        });

        Villager best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Villager villager : candidates) {
            Vec3 delta = villager.getEyePosition().subtract(origin);
            double dist = Math.max(0.1, delta.length());
            double score = lookDir != null ? (delta.normalize().dot(lookDir) * 4.0 - dist * 0.25) : -dist;
            if (score > bestScore) {
                bestScore = score;
                best = villager;
            }
        }
        return best;
    }

    private static boolean isLocked(Villager villager) {
        return villager.getVillagerXp() > 0 || villager.getVillagerData().level() > 1;
    }

    private static boolean isCooldownActive(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Long last = LAST_ACTION_TICK.get(sp.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return true;
        }
        LAST_ACTION_TICK.put(sp.getUUID(), now);
        return false;
    }

    /** Computes the vanilla 26.3 emerald price range {@code [min, max]} for an enchantment level. */
    public static int[] getPriceRange(Holder<Enchantment> holder, int level) {
        int min = 5 + (level - 1) * 3;
        int max = 19 + (level - 1) * 13;
        if (holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
            min *= 2;
            max *= 2;
        }
        return new int[]{Math.min(min, 64), Math.min(max, 64)};
    }

    private static List<Holder.Reference<Enchantment>> getTradeableEnchantments(RegistryAccess access) {
        Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        return registry.listElements()
            .filter(ref -> ref.is(EnchantmentTags.TRADEABLE))
            .sorted(Comparator.comparing(ref -> ref.key().identifier().getPath()))
            .toList();
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(
        CommandContext<CommandSourceStack> ctx,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            getTradeableEnchantments(ctx.getSource().registryAccess()).stream().map(ref -> ref.key().identifier().getPath()),
            builder
        );
    }

    private static Optional<Holder.Reference<Enchantment>> resolveEnchantment(RegistryAccess access, String rawInput) {
        String key = rawInput.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        key = ENCHANT_ALIASES.getOrDefault(key, key);

        Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        Identifier id = Identifier.withDefaultNamespace(key);
        Optional<Holder.Reference<Enchantment>> exact = registry.get(id);
        if (exact.isPresent()) {
            return exact;
        }

        String finalKey = key;
        List<Holder.Reference<Enchantment>> prefixMatches = getTradeableEnchantments(access).stream()
            .filter(ref -> ref.key().identifier().getPath().startsWith(finalKey))
            .toList();
        if (prefixMatches.size() == 1) {
            return Optional.of(prefixMatches.get(0));
        }

        List<Holder.Reference<Enchantment>> containsMatches = getTradeableEnchantments(access).stream()
            .filter(ref -> ref.key().identifier().getPath().contains(finalKey))
            .toList();
        return containsMatches.size() == 1 ? Optional.of(containsMatches.get(0)) : Optional.empty();
    }

    private static List<String> readNonEmptySignLines(SignBlockEntity sign) {
        for (SignTextSlot slot : new SignTextSlot[]{SignTextSlot.FRONT, SignTextSlot.BACK}) {
            List<String> out = new ArrayList<>();
            for (Component line : sign.getText(slot).getMessages(false)) {
                String text = line.getString().trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return List.of();
    }

    private static SignQuery parseSignQuery(List<String> lines) {
        String[] line0 = lines.get(0).trim().split("\\s+");
        if (line0.length == 0 || line0[0].isEmpty()) {
            return null;
        }
        String enchantRaw = line0[0];
        Integer level = null;
        if (line0.length >= 2) {
            level = parseLevelToken(line0[1]);
        }

        int maxPrice = 64;
        if (line0.length >= 3) {
            Integer inlineMax = parsePositiveInt(line0[2]);
            if (inlineMax != null) {
                maxPrice = Math.min(64, inlineMax);
            }
        }
        if (lines.size() >= 2) {
            String cleaned = lines.get(1).trim().toLowerCase(Locale.ROOT)
                .replaceAll("^(max|<=|:|\\s)+", "")
                .replaceAll("(emeralds?|e|\\s)+$", "");
            Integer parsed = parsePositiveInt(cleaned);
            if (parsed != null) {
                maxPrice = Math.min(64, parsed);
            }
        }
        return new SignQuery(enchantRaw, level, maxPrice);
    }

    private static Integer parsePositiveInt(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            int val = Integer.parseInt(text);
            return val >= 1 ? val : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseLevelToken(String token) {
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> {
                try {
                    yield Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private record SignQuery(String enchantRaw, Integer level, int maxPrice) {}
}

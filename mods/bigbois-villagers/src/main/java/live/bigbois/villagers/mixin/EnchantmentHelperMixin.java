package live.bigbois.villagers.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows Mending (REPAIR_WITH_XP) to pick damaged enchanted items anywhere in a player's inventory.
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    @Inject(method = "getRandomItemWith", at = @At("HEAD"), cancellable = true)
    private static void bigbois$mendingFromInventory(
        DataComponentType<?> component,
        LivingEntity entity,
        Predicate<ItemStack> filter,
        CallbackInfoReturnable<Optional<EnchantedItemInUse>> cir
    ) {
        if (component != EnchantmentEffectComponents.REPAIR_WITH_XP || !(entity instanceof ServerPlayer player)) {
            return;
        }

        List<EnchantedItemInUse> candidates = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && seen.add(stack) && filter.test(stack) && EnchantmentHelper.has(stack, component)) {
                candidates.add(new EnchantedItemInUse(stack, slot, player));
            }
        }

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && seen.add(stack) && filter.test(stack) && EnchantmentHelper.has(stack, component)) {
                candidates.add(new EnchantedItemInUse(stack, EquipmentSlot.MAINHAND, player, ignored -> {}));
            }
        }

        cir.setReturnValue(Util.getRandomSafe(candidates, player.getRandom()));
    }
}

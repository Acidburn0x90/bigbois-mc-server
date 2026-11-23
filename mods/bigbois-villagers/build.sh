#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="/var/opt/minecraft/crafty/crafty-4/servers/8b9dd022-ae6d-42b9-b0ea-87f33310c950"
MOD_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

FAPI_JAR="$(ls -1 "$SERVER_DIR"/mods/fabric-api-*.jar | head -n 1)"
mkdir -p "$WORK_DIR/fapi_jij" "$WORK_DIR/classes"

python3 - "$FAPI_JAR" "$WORK_DIR/fapi_jij" <<'PY'
import os, sys, zipfile
fapi_jar, out_dir = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(fapi_jar) as z:
    for name in z.namelist():
        if name.startswith("META-INF/jars/") and name.endswith(".jar"):
            out = os.path.join(out_dir, os.path.basename(name))
            with open(out, "wb") as fh:
                fh.write(z.read(name))
PY

CP_JARS=("$SERVER_DIR/versions/26.3/server-26.3.jar" "$FAPI_JAR")
while IFS= read -r jar; do
    CP_JARS+=("$jar")
done < <(find "$SERVER_DIR/libraries" "$WORK_DIR/fapi_jij" -name '*.jar' | sort)
CLASSPATH="$(IFS=:; echo "${CP_JARS[*]}")"

mapfile -t JAVA_SRCS < <(find "$MOD_DIR/src/main/java" -name '*.java' | sort)
javac --release 25 -Xlint:all -cp "$CLASSPATH" -d "$WORK_DIR/classes" "${JAVA_SRCS[@]}"

cp -r "$MOD_DIR/src/main/resources/"* "$WORK_DIR/classes/"
mkdir -p "$MOD_DIR/build"
jar --create --file "$MOD_DIR/build/bigbois-villagers-1.0.0.jar" -C "$WORK_DIR/classes" .
echo "Built $MOD_DIR/build/bigbois-villagers-1.0.0.jar"

# Build the demo and put it on the phone (source tools/phone.sh first for $ADB).
# usage: tools/dev.sh [gradle args]
set -e
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
./gradlew assembleDebug -Pdemo --console=plain -q "$@" 2>&1 | grep -E "^e:|error" || true
"${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}" install -r app/build/outputs/apk/debug/app-debug.apk | tail -1

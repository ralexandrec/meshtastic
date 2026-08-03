#!/bin/bash
# -*- coding: utf-8 -*-

# Script to initialize two Wear OS emulators and connect them to the same Mock Server,
# simulating an offline peer-to-peer LoRa chat between two smartwatches.
#
# Usage:
#   ./scripts/launch_two_emulators.sh [--locale pt|en]
#
# Options:
#   --locale pt   Configure emulators in Portuguese-Brazil (default)
#   --locale en   Configure emulators in English (US)

set -o pipefail

# Console colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'
CLEAR='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# shellcheck source=lib/emulator_utils.sh
source "$SCRIPT_DIR/lib/emulator_utils.sh"

DEFAULT_LOCALE_SHORT="pt"
LOCALE_SHORT="$DEFAULT_LOCALE_SHORT"

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')][INFO]${CLEAR} $1"
}

warn() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')][WARN]${CLEAR} $1"
}

error() {
    echo -e "${RED}[$(date +'%H:%M:%S')][ERROR]${CLEAR} $1"
    exit 1
}

usage() {
    echo "Usage: $0 [--locale pt|en]"
    echo "  --locale pt   Portuguese-Brazil (default)"
    echo "  --locale en   English (US)"
    exit 0
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --locale)
                if [ -z "${2:-}" ]; then
                    error "Missing value for --locale. Use pt or en."
                fi
                LOCALE_SHORT="$2"
                shift 2
                ;;
            --locale=*)
                LOCALE_SHORT="${1#*=}"
                shift
                ;;
            -h|--help)
                usage
                ;;
            *)
                error "Unknown argument: $1 (use --help)"
                ;;
        esac
    done
}

detect_android_environment() {
    if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
        if [ -x "/usr/libexec/java_home" ]; then
            export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
        fi
        if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
            if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
                export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
            fi
        fi
    fi
    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
    EMULATOR="$ANDROID_HOME/emulator/emulator"
    ADB="$ANDROID_HOME/platform-tools/adb"

    log "Setting up Android SDK environment..."
    if [ ! -d "$ANDROID_HOME" ]; then
        error "Android SDK not found at $ANDROID_HOME."
    fi
    if [ ! -x "$EMULATOR" ]; then
        error "Emulator executable not found at $EMULATOR."
    fi
    if [ ! -x "$ADB" ]; then
        error "adb executable not found at $ADB."
    fi
}

find_avd() {
    local avd_list
    avd_list=$("$EMULATOR" -list-avds)
    if [ -z "$avd_list" ]; then
        error "No emulator (AVD) installed. Please create the Wear OS emulator 'wear_test_watch' before continuing."
    fi
    AVD_NAME=$(echo "$avd_list" | head -n 1)
}

cleanup_old_processes() {
    log "Cleaning up previous QEMU/Mock processes..."
    kill -9 $(pgrep -f qemu) 2>/dev/null || true
    kill -9 $(pgrep -f emulator) 2>/dev/null || true
    kill -9 $(lsof -t -i:4403) 2>/dev/null || true
}

start_mock_server() {
    log "Starting mesh_mock on port 4403..."
    cd "$PROJECT_ROOT/mesh_mock" || error "Folder mesh_mock not found."
    source .venv/bin/activate || error "Virtual environment .venv not found."
    python -u mock_server.py --no-ble --no-echo --port 4403 > mock_server_multi.log 2>&1 &
    MOCK_PID=$!
    cd "$PROJECT_ROOT"
    sleep 1
}

start_emulator() {
    local port="$1"
    local label="$2"
    log "Starting ${label} (Port ${port}) with locale $LOCALE_TAG..."
    "$EMULATOR" -avd "$AVD_NAME" -port "$port" -read-only -memory 1024 -feature -Wifi \
        -netdelay none -netspeed full -gpu host -feature -Vulkan \
        $LOCALE_PROP > /dev/null 2>&1 &
    if [ "$port" = "5554" ]; then
        EMU_PID_1=$!
    else
        EMU_PID_2=$!
    fi
}

wait_for_watch_boot() {
    local serial="$1"
    local label="$2"
    log "Waiting for full boot of $label ($serial)..."
    "$ADB" -s "$serial" wait-for-device
    local boot=""
    while [ "$boot" != "1" ]; do
        boot=$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        sleep 2
    done
    "$ADB" -s "$serial" shell setprop debug.hwui.renderer opengl
    log "Applying locale $LOCALE_TAG on $label..."
    configure_emulator_locale "$ADB" "$serial" "$LOCALE_TAG"
    log "${GREEN}$label is online (locale: $LOCALE_TAG)!${CLEAR}"
}

install_and_launch() {
    local serial="$1"
    local label="$2"
    log "Installing application on $label..."
    "$ADB" -s "$serial" install -r "$APK_PATH"
    "$ADB" -s "$serial" install -r "$PROJECT_ROOT/sayboard.apk" 2>/dev/null || true
    
    log "Applying locale $LOCALE_TAG on $label (app + keyboard)..."
    configure_emulator_locale "$ADB" "$serial" "$LOCALE_TAG"

    "$ADB" -s "$serial" shell input keyevent KEYCODE_WAKEUP
    "$ADB" -s "$serial" shell wm dismiss-keyguard
    "$ADB" -s "$serial" shell am start -n com.example.meshtasticwear/.ui.MainActivity
}

main() {
    parse_args "$@"
    LOCALE_TAG=$(resolve_locale_tag "$LOCALE_SHORT") || error "Invalid locale: $LOCALE_SHORT"
    LOCALE_PROP=$(emulator_locale_prop "$LOCALE_TAG")

    detect_android_environment
    find_avd
    cleanup_old_processes
    start_mock_server

    start_emulator 5554 "Watch 1"
    log "Waiting 10 seconds before starting the second watch to avoid temporary file conflicts..."
    sleep 10
    start_emulator 5556 "Watch 2"

    log "Building the debug APK of the Wear OS app..."
    cd "$PROJECT_ROOT/wear"
    ./gradlew assembleDebug || error "Error compiling the APK."
    cd "$PROJECT_ROOT"
    APK_PATH="$PROJECT_ROOT/wear/wear/build/outputs/apk/debug/wear-debug.apk"

    wait_for_watch_boot "emulator-5554" "Watch 1"
    wait_for_watch_boot "emulator-5556" "Watch 2"

    log "Waiting 5 seconds for emulator network stack to stabilize..."
    sleep 5

    install_and_launch "emulator-5554" "Watch 1"
    install_and_launch "emulator-5556" "Watch 2"

    echo -e "\n${GREEN}========================================================================${CLEAR}"
    echo -e "${GREEN}  SUCCESS: Two simulated watches started and connected to mesh_mock!   ${CLEAR}"
    echo -e "${GREEN}========================================================================${CLEAR}"
    echo -e "Locale: ${GREEN}$LOCALE_TAG${CLEAR} (override with --locale en)"
    echo -e "How to test:"
    echo -e " 1. Use the physical PTT (STEM_1 / STEM_2) or digital button to speak on one of the watches."
    echo -e " 2. The voice will be transcribed locally and transmitted via mock server to the other watch."
    echo -e " 3. The other watch will display the message on the screen and read the text via TTS automatically!"
    echo -e "========================================================================\n"
}

main "$@"

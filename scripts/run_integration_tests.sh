#!/bin/bash
# -*- coding: utf-8 -*-

# Automation script for Cucumber integration tests (Wear OS + mesh_mock)

set -o pipefail

# Console colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color
CLEAR='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# shellcheck source=lib/emulator_utils.sh
source "$SCRIPT_DIR/lib/emulator_utils.sh"

DEFAULT_LOCALE_SHORT="pt"
TEST_LOCALE_SHORT="$DEFAULT_LOCALE_SHORT"
SHUTDOWN_EMULATOR=true

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
    echo "Usage: $0 [--locale pt|en] [--no-shutdown]"
    echo "  --locale pt|en    Set emulator and app locale for the integration test run. Default: pt"
    echo "  --no-shutdown     Keep emulator and mock server running after tests finish."
    exit 0
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --locale)
                if [ -z "${2:-}" ]; then
                    error "Missing value for --locale. Use pt or en."
                fi
                TEST_LOCALE_SHORT="$2"
                shift 2
                ;;
            --locale=*)
                TEST_LOCALE_SHORT="${1#*=}"
                shift
                ;;
            --no-shutdown)
                SHUTDOWN_EMULATOR=false
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
        error "Android SDK not found at $ANDROID_HOME. Set \$ANDROID_HOME variable."
    fi
    if [ ! -x "$EMULATOR" ]; then
        error "Emulator executable not found at $EMULATOR."
    fi
    if [ ! -x "$ADB" ]; then
        error "adb executable not found at $ADB."
    fi
}

find_avd() {
    log "Checking available virtual devices (AVDs)..."
    AVDS=$("$EMULATOR" -list-avds)

    if [ -z "$AVDS" ]; then
        error "No emulator (AVD) installed. Create a Wear OS emulator in Android Studio before running tests."
    fi

    AVD_NAME=$(echo "$AVDS" | head -n 1)
    log "Using emulator: ${GREEN}$AVD_NAME${CLEAR}"
}

main() {
    parse_args "$@"
    detect_android_environment
    find_avd

    log "Starting emulator in the background..."
    "$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full -gpu host -partition-size 2048 -feature -Vulkan > /dev/null 2>&1 &
    EMULATOR_PID=$!

    log "Waiting for adb connection..."
    "$ADB" wait-for-device

    log "Waiting for full boot of the Wear OS operating system..."
    BOOT_COMPLETED=""
    while [ "$BOOT_COMPLETED" != "1" ]; do
        BOOT_COMPLETED=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        if [ "$BOOT_COMPLETED" != "1" ]; then
            sleep 2
        fi
    done
    log "${GREEN}Wear OS Emulator is online and ready!${CLEAR}"
    # Force OpenGL renderer to avoid Vulkan crash on macOS host
    "$ADB" shell setprop debug.hwui.renderer opengl

    TEST_LOCALE=$(resolve_locale_tag "$TEST_LOCALE_SHORT")
    DEVICE_SERIAL=$("$ADB" devices | awk '/emulator/{print $1; exit}')
    log "Applying test locale $TEST_LOCALE on $DEVICE_SERIAL..."
    configure_emulator_locale "$ADB" "$DEVICE_SERIAL" "$TEST_LOCALE"
    sleep 2

    log "Cleaning up previous mesh_mock processes..."
    kill -9 $(lsof -t -i:4403) 2>/dev/null || true
    log "Starting mesh_mock in the background on port 4403..."
    cd "$PROJECT_ROOT/mesh_mock" || error "Folder mesh_mock not found."
    source .venv/bin/activate || error "Virtual environment .venv not found. Run setup first."

    python -u mock_server.py --no-ble --port 4403 > mock_server_integration.log 2>&1 &
    MOCK_PID=$!
    cd "$PROJECT_ROOT"

    sleep 2
    if ! ps -p $MOCK_PID > /dev/null; then
        error "Failed to start mesh_mock. Check logs in mesh_mock/mock_server_integration.log."
    fi
    log "${GREEN}Simulator mesh_mock running under PID: $MOCK_PID${CLEAR}"

    log "Starting execution of Cucumber tests on Wear OS..."
    cd "$PROJECT_ROOT/wear" || error "Folder wear not found."

    ./gradlew clean installDebug connectedDebugAndroidTest
    TEST_EXIT_CODE=$?
    cd "$PROJECT_ROOT"

    log "Finishing and releasing resources..."
    if [ "$SHUTDOWN_EMULATOR" = "true" ]; then
        if ps -p $MOCK_PID > /dev/null; then
            log "Terminating simulator mesh_mock (PID $MOCK_PID)..."
            kill $MOCK_PID
        fi
    else
        warn "Warning: Simulator mesh_mock kept running in background (PID $MOCK_PID)."
    fi

    if [ "$SHUTDOWN_EMULATOR" = "true" ]; then
        log "Shutting down Android emulator..."
        "$ADB" emu kill > /dev/null 2>&1 || kill $EMULATOR_PID
    else
        warn "Warning: Emulator kept active (--no-shutdown)."
    fi

    if [ $TEST_EXIT_CODE -eq 0 ]; then
        echo -e "\n${GREEN}============================================================${CLEAR}"
        echo -e "${GREEN}  SUCCESS: All BDD scenarios passed integrated with the Mock!${CLEAR}"
        echo -e "${GREEN}============================================================${CLEAR}"
        exit 0
    else
        echo -e "\n${RED}============================================================${CLEAR}"
        echo -e "${RED}  FAILURE: One or more Cucumber test scenarios failed.     ${CLEAR}"
        echo -e "${RED}  Please consult the Gradle build logs.                      ${CLEAR}"
        echo -e "${RED}============================================================${CLEAR}"
        exit 1
    fi
}

main "$@"

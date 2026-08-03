#!/bin/bash
# Shared emulator helpers (locale configuration, tag resolution).
# Sourced by launch and test scripts — do not execute directly.

# resolve_locale_tag <short_code>
# Maps a short locale code to a full BCP-47 tag used by Android.
#   pt -> pt-BR (default for Brazilian developers)
#   en -> en-US
resolve_locale_tag() {
    local lower_code
    lower_code=$(echo "$1" | tr '[:upper:]' '[:lower:]')
    case "$lower_code" in
        pt|pt-br) echo "pt-BR" ;;
        en|en-us) echo "en-US" ;;
        *)
            echo "Unsupported locale: $1 (use 'pt' or 'en')" >&2
            return 1
            ;;
    esac
}

# configure_emulator_locale <adb_path> <device_serial> <locale_tag>
# Applies a system locale on a running emulator via adb.
configure_emulator_locale() {
    local adb_path="$1"
    local device_serial="$2"
    local locale_tag="$3"

    local lang="${locale_tag%-*}"
    local country="${locale_tag#*-}"

    "$adb_path" -s "$device_serial" shell "cmd locale set $locale_tag" 2>/dev/null || true
    "$adb_path" -s "$device_serial" shell "settings put system system_locales $locale_tag" 2>/dev/null || true
    "$adb_path" -s "$device_serial" shell "setprop persist.sys.language $lang" 2>/dev/null || true
    "$adb_path" -s "$device_serial" shell "setprop persist.sys.country $country" 2>/dev/null || true
    "$adb_path" -s "$device_serial" shell "setprop persist.sys.locale $locale_tag" 2>/dev/null || true
    "$adb_path" -s "$device_serial" shell "am broadcast -a android.intent.action.LOCALE_CHANGED" 2>/dev/null || true
}

# emulator_locale_prop <locale_tag>
# Returns the -prop argument for the emulator command line.
emulator_locale_prop() {
    echo "-prop persist.sys.locale=$1"
}

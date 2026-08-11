#!/bin/bash
# generate_store_screenshots.sh - captures Play Store listing screenshots
#
# Requires a debug build installed on the target device/emulator:
#   ./gradlew installDebug
# Debug builds export QuothPrefs (see app/src/debug/AndroidManifest.xml) so
# the settings screen can be launched directly via `am start`, and are
# debuggable so `run-as` can write SharedPreferences directly - both needed
# because the available emulators are Play Store-certified images (no root,
# so `cmd dreams start-dreaming` and root-based exported-activity bypasses
# aren't available here).
#
# Usage: ./generate_store_screenshots.sh [device]  (default: emulator-5554)

set -u

PACKAGE="im.skn.daydreamerquoth"

# Resolve adb without hardcoding any particular machine's username/path:
# prefer whatever's already on PATH, then the standard SDK env vars, then a
# $HOME-relative guess at the default macOS install location.
if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
elif [ -n "${ANDROID_HOME:-}" ]; then
    ADB="$ANDROID_HOME/platform-tools/adb"
elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
else
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi

if [ ! -x "$ADB" ]; then
    echo "ERROR: couldn't find a usable adb (checked PATH, \$ANDROID_HOME, \$ANDROID_SDK_ROOT, and \$HOME/Library/Android/sdk)." >&2
    echo "       Install the Android SDK platform-tools, or set ANDROID_HOME to your SDK location." >&2
    exit 1
fi

DEVICE=${1:-emulator-5554}

OUTROOT="store_screenshots"
RAWDIR="$OUTROOT/raw"
FINALDIR="$OUTROOT/final"
REMOTE_PREFS_DIR="/data/data/$PACKAGE/shared_prefs"
REMOTE_PREFS_PATH="$REMOTE_PREFS_DIR/${PACKAGE}_preferences.xml"
TMP_PREFS="/tmp/qtd_prefs_$$.xml"

mkdir -p "$RAWDIR" "$FINALDIR"

adb() { "$ADB" -s "$DEVICE" "$@"; }

echo "Device: $DEVICE"

echo "Setting DayDreamer as the default screensaver..."
adb shell settings put secure screensaver_components "$PACKAGE/.DayDreamerQuoth" >/dev/null
adb shell settings put secure screensaver_enabled 1 >/dev/null

# Writes a full canonical SharedPreferences file with the given values and
# force-stops the app, so the next attach re-reads it from scratch - prefs
# are only read once per DreamService attach (see
# DayDreamerQuoth.onAttachedToWindow / parseTimingPreference).
write_prefs() {
    local show_time=$1 show_date=$2 show_batt_pct=$3 show_batt_status=$4
    local text_size=$5 font_family=$6
    # Defaults to "fixed" (not "smart"/"hybrid") specifically so the quote
    # never auto-advances mid-capture: Adaptive's delay can be as short as
    # 5s for a short quote, which is well under the ~8s dream_* capture
    # wait and was caught in the act once - a screenshot landed mid
    # cross-fade with two quotes' text visibly overlapping.
    local delay_mode=${7:-fixed}

    cat > "$TMP_PREFS" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="PREF_DELAY_MODE">$delay_mode</string>
    <int name="PREF_DELAY_SECONDS" value="60" />
    <string name="PREF_READING_SPEED">200</string>
    <string name="PREF_TEXT_SIZE">$text_size</string>
    <string name="PREF_FONT_FAMILY">$font_family</string>
    <boolean name="PREF_SHOW_TIME" value="$show_time" />
    <boolean name="PREF_SHOW_DATE" value="$show_date" />
    <boolean name="PREF_SHOW_BATTERY_PCT" value="$show_batt_pct" />
    <boolean name="PREF_SHOW_BATTERY_STATUS" value="$show_batt_status" />
</map>
EOF

    adb push "$TMP_PREFS" /data/local/tmp/qtd_prefs.xml >/dev/null
    # Must be passed as a single argv element to `adb shell` - splitting this
    # across multiple args (even individually quoted) loses the nested sh -c
    # quoting when adb rejoins them for the device, silently breaking mkdir/cat
    # (verified empirically: mkdir -p landed with zero arguments).
    local remote_cmd="run-as $PACKAGE sh -c 'mkdir -p $REMOTE_PREFS_DIR && cat /data/local/tmp/qtd_prefs.xml > $REMOTE_PREFS_PATH'"
    adb shell "$remote_cmd"
    adb shell am force-stop "$PACKAGE"
}

# Fakes a charging battery state so the dream's activation trigger condition
# is satisfied (Somnambulator alone is often not enough on an emulator,
# since mIsCharging defaults false - see CLAUDE.md's "Operational tooling"
# section for the same gotcha as the soak-test scripts).
set_battery() {
    local plug_key=$1 level=$2
    adb shell dumpsys battery set ac 0 >/dev/null
    adb shell dumpsys battery set usb 0 >/dev/null
    adb shell dumpsys battery set "$plug_key" 1 >/dev/null
    adb shell dumpsys battery set status 2 >/dev/null   # BATTERY_STATUS_CHARGING
    adb shell dumpsys battery set level "$level" >/dev/null
}

reset_battery() {
    adb shell dumpsys battery reset >/dev/null
}

# Taps the center of the first UI node with the given resource-id, by
# dumping the current UI hierarchy via uiautomator and parsing its bounds.
# Used instead of hardcoded coordinates since bounds depend on screen size.
tap_resource_id() {
    local id=$1
    local dump="/tmp/qtd_dump_$$.xml"
    adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null
    adb pull /sdcard/window_dump.xml "$dump" >/dev/null 2>&1

    local bounds
    bounds=$(grep -o "resource-id=\"$id\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$dump" \
        | grep -o 'bounds="[^"]*"' | head -1 | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/')
    rm -f "$dump"

    if [ -z "$bounds" ]; then
        echo "  WARNING: could not find resource-id $id in the UI dump" >&2
        return 1
    fi

    read -r x1 y1 x2 y2 <<< "$bounds"
    adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

# Same idea as tap_resource_id, but matches on a node's exact visible text -
# used for tapping a specific preference row by its title (e.g. "Text font"),
# since every row in the list shares the same android:id/title resource-id.
tap_text() {
    local target=$1
    local dump="/tmp/qtd_dump_$$.xml"
    adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null
    adb pull /sdcard/window_dump.xml "$dump" >/dev/null 2>&1

    local bounds
    bounds=$(grep -o "text=\"$target\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$dump" \
        | grep -o 'bounds="[^"]*"' | head -1 | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/')
    rm -f "$dump"

    if [ -z "$bounds" ]; then
        echo "  WARNING: could not find text \"$target\" in the UI dump" >&2
        return 1
    fi

    read -r x1 y1 x2 y2 <<< "$bounds"
    adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

# Somnambulator (the usual soak-test trigger, see soak_cycle.sh) turned out
# not to be enough here: this Screen saver's "When to start" is "While
# docked and charging", and docked state can't be faked from adb (DOCK_EVENT
# is a protected broadcast - see CLAUDE.md's "Operational tooling" section).
# The reliable, condition-independent way to actually start the dream is the
# same "Preview" button CLAUDE.md documents for manual soak testing -
# verified here via `dumpsys dreams` showing mCurrentDream populated only
# after tapping it, never after a plain Somnambulator `am start`.
capture_dream() {
    local name=$1
    echo "Capturing dream look: $name"
    adb shell am start -a android.settings.DREAM_SETTINGS >/dev/null
    sleep 2
    tap_resource_id "com.android.settings:id/dream_preview_button"
    sleep 8   # let the async quote load finish and the first cross-fade settle
    adb exec-out screencap -p > "$RAWDIR/$name.png"
    adb shell input keyevent KEYCODE_HOME
    sleep 1
}

capture_settings() {
    local name=$1
    echo "Capturing settings screen: $name"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.QuothPrefs" >/dev/null
    sleep 3
    adb exec-out screencap -p > "$RAWDIR/$name.png"
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    adb shell input keyevent KEYCODE_BACK
}

# Opens the settings screen, taps a preference row by its title to open its
# ListPreference choice dialog (AlertDialog with a radio-button list), and
# screenshots it - shows off the actual available choices, not just whatever
# single value happens to be selected.
capture_choice_dialog() {
    local pref_title=$1
    local name=$2
    echo "Capturing choice dialog: $name"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.QuothPrefs" >/dev/null
    sleep 3
    tap_text "$pref_title"
    sleep 1
    adb exec-out screencap -p > "$RAWDIR/$name.png"
    adb shell input keyevent KEYCODE_BACK   # dismiss the dialog
    sleep 1
    adb shell input keyevent KEYCODE_BACK   # exit the settings activity
    sleep 1
}

# Crops a raw capture only as much as Play Store's 2:1 max aspect-ratio cap
# actually requires (both available emulators natively exceed it: Pixel 9
# Pro XL is 1344x2992 = 2.226:1, Medium Phone is 1080x2400 = 2.222:1).
# Deliberately does NOT resize down to Play's merely-recommended 1080x1920 -
# neither emulator's native resolution violates Play's min/max pixel bounds
# (320-3840 per side), only the ratio does, so the output keeps the device's
# real native resolution and pixel detail, showing as much of the actual
# screen as the ratio cap allows rather than the smallest "safe" crop.
# Target ratio is 1.99, not 2.00, as a small margin against any rounding at
# the exact edge of the spec. $2 selects which edge absorbs the crop:
#   top    (default) - dream screenshots: content is centered with
#            time/date/battery pinned to the bottom, so the safe edge to
#            trim is the top (empty background for any short-ish quote).
#   bottom - the settings screen: content starts at the top (title, then
#            every preference row in order) with genuine blank space below
#            the last row, so trimming the bottom instead keeps every row -
#            including "Delay between quotes"/"Delay length" - in frame.
postprocess() {
    local name=$1
    local crop_from=${2:-top}
    local raw="$RAWDIR/$name.png"
    local final="$FINALDIR/$name.png"

    local w h
    w=$(sips -g pixelWidth "$raw" | awk '/pixelWidth/{print $2}')
    h=$(sips -g pixelHeight "$raw" | awk '/pixelHeight/{print $2}')

    local max_h=$(( w * 199 / 100 ))

    if [ "$h" -gt "$max_h" ]; then
        local offset
        if [ "$crop_from" = "bottom" ]; then
            # 1, not 0: an offset of exactly 0 is indistinguishable from "no
            # offset given" to sips and silently falls back to a symmetric
            # center crop instead (verified empirically) - 1px trims
            # virtually nothing off the top, pushing nearly all the excess
            # off the bottom instead.
            offset=1
        else
            # -1: an offset landing exactly flush with the bottom edge
            # silently no-ops sips's crop (verified empirically); backing
            # off by 1px avoids the edge case and is visually negligible.
            offset=$((h - max_h - 1))
        fi
        sips --cropOffset "$offset" 0 -c "$max_h" "$w" "$raw" --out "$final" >/dev/null
    else
        cp "$raw" "$final"
    fi
    echo "  -> $final ($(sips -g pixelWidth -g pixelHeight "$final" | tr '\n' ' '))"
}

# --- Look 1: full display, everything on ---
write_prefs true true true true 1 Santana
set_battery ac 80
capture_dream dream_full
reset_battery

# --- Look 2: minimal, everything off ---
write_prefs false false false false 1 Santana
set_battery ac 80
capture_dream dream_minimal
reset_battery

# --- Look 3: charging close-up (distinct plug type + level from the baseline) ---
write_prefs true true true true 1 Santana
set_battery usb 45
capture_dream dream_charging
reset_battery

# --- Look 4: alternate font (small Typewriter) ---
write_prefs true true true true 1 Typewriter
set_battery ac 80
capture_dream dream_typewriter_font
reset_battery

# --- Look 5: date only ---
write_prefs false true false false 1 Santana
set_battery ac 80
capture_dream dream_date_only
reset_battery

# --- Look 6: settings screen (Hybrid mode so BOTH the Delay length slider
# and Reading speed rows are visible - Adaptive alone would hide the slider) ---
write_prefs true true true true 1 Santana hybrid
capture_settings settings_screen

# --- Look 7: font choices dialog ---
write_prefs true true true true 1 Santana smart
capture_choice_dialog "Text font" settings_font_choices

# --- Look 8: timing mode choices dialog (smart, so "Adaptive Timing" shows
# selected - a more illustrative default than "Fixed" for this screenshot) ---
write_prefs true true true true 1 Santana smart
capture_choice_dialog "Delay between quotes" settings_timing_choices

echo ""
echo "Post-processing to 1080x1920..."
for f in "$RAWDIR"/*.png; do
    name="$(basename "$f" .png)"
    if [ "$name" = "settings_screen" ]; then
        postprocess "$name" bottom
    else
        postprocess "$name" top
    fi
done

rm -f "$TMP_PREFS"

echo ""
echo "Done. Final screenshots in $FINALDIR/"

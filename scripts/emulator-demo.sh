#!/usr/bin/env bash
# Installs the APK on a running emulator/device, clicks through the kiosk
# flow, and saves screenshots plus a screen recording.
#
#   scripts/emulator-demo.sh app/build/outputs/apk/debug/app-debug.apk demo-output
set -euo pipefail

APK=${1:?usage: emulator-demo.sh <apk> [output-dir]}
OUT=${2:-demo-output}
PKG=com.formbricks.kiosk
SCRIPTS=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

# A mock of the Formbricks Feedback Records API on the host. The emulator reaches the
# host as 10.0.2.2. It validates every record and logs the accepted ones.
API_KEY=test-api-key
DATASET=cmdemodataset0000000000001
python3 "$SCRIPTS/mock-formbricks.py" "$OUT/feedback-records.jsonl" 8787 "$API_KEY" "$DATASET" > "$OUT/mock-formbricks.log" 2>&1 &
MOCK_PID=$!
trap 'kill $MOCK_PID 2>/dev/null || true' EXIT

debug_dump() {
  echo "---- uiautomator dump ----"
  adb shell uiautomator dump /sdcard/ui.xml || true
  adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -o 'text="[^"]*"\|content-desc="[^"]*"\|class="[^"]*"\|bounds="[^"]*"' | paste -sd' ' | fold -w 300 | head -60 || true
  echo "---- top activity ----"
  adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" || true
  echo "---- crash log ----"
  adb logcat -d -b crash | tail -60 || true
  adb logcat -d | grep -E "AndroidRuntime|FATAL|formbricks" | tail -40 || true
}

shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "saved $OUT/$1.png"
}

# Prints "x y" for the centre of the first UI node whose attribute matches.
# Usage: center_of content-desc "Very happy"   |   center_of text "Unlock"
center_of() {
  local attr=$1 value=$2 xml bounds
  for _ in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    xml=$(adb exec-out cat /sdcard/ui.xml 2>/dev/null || true)
    bounds=$(printf '%s' "$xml" | tr '>' '\n' | grep -F "$attr=\"$value\"" | head -1 |
      grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' || true)
    if [ -n "$bounds" ]; then
      echo "$bounds" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' |
        awk '{ printf "%d %d\n", ($1+$3)/2, ($2+$4)/2 }'
      return 0
    fi
    sleep 1
  done
  echo "could not find $attr=\"$value\" on screen" >&2
  debug_dump >&2
  return 1
}

tap() {
  local xy
  xy=$(center_of "$1" "$2")
  echo "tap $1=\"$2\" at $xy"
  adb shell input tap $xy
}

adb install -r "$APK"
# Skip Android's one-time "Viewing full screen" hint that covers the app.
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell screenrecord --time-limit 90 /sdcard/demo.mp4 &
sleep 1

# Debug builds accept the Formbricks connection as launch extras.
adb shell "am start -W -n $PKG/.MainActivity \
  --es fb_host http://10.0.2.2:8787 \
  --es fb_api_key $API_KEY \
  --es fb_dataset_id $DATASET \
  --es fb_location 'Hamburg Store Exit' \
  --es fb_kiosk_id hamburg-exit-01"
sleep 4
# Dismiss the full-screen hint if it still showed up.
if xy=$(center_of text "Got it" 2>/dev/null); then adb shell input tap $xy; sleep 1; fi
shot 01-survey

tap content-desc "Very happy"
sleep 2
shot 02-follow-up

tap text "Friendly staff"
sleep 1
shot 03-thank-you

# Back on the survey after the thank-you screen; add a few more answers.
sleep 4
for mood in "Happy" "Unhappy" "Very happy"; do
  tap content-desc "$mood"
  sleep 1
  tap text "Skip"
  sleep 4
done

# Five quick taps in the hidden admin corner, then the default PIN.
xy=$(center_of content-desc "Admin corner")
for _ in 1 2 3 4 5; do adb shell input tap $xy; done
sleep 2
shot 04-pin
adb shell input text 1234
tap text "Unlock"
sleep 3
shot 05-admin

# Scroll to the Formbricks connection, test it, and push everything queued.
adb shell input swipe 1280 1500 1280 300 600
sleep 1
adb shell input swipe 1280 1500 1280 700 600
sleep 1
tap text "Test connection"
sleep 3
tap text "Sync now"
sleep 6
shot 06-admin-formbricks

adb shell pkill -INT screenrecord || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/demo.mp4" || echo "no screen recording available"
ls -la "$OUT"

echo "---- records received by the mock Formbricks API ----"
cat "$OUT/mock-formbricks.log"
# 4 answers, one with a follow-up reason: 4 rating records + 1 categorical record.
python3 - "$OUT/feedback-records.jsonl" <<'PY'
import json, sys
records = [json.loads(l) for l in open(sys.argv[1]) if l.strip()]
ratings = [r for r in records if r["field_id"] == "satisfaction" and r["field_type"] == "rating"]
reasons = [r for r in records if r["field_id"] == "reason" and r["field_type"] == "categorical"]
assert len(ratings) == 4, f"expected 4 rating records, got {len(ratings)}"
assert sorted(r["value_number"] for r in ratings) == [2, 3, 4, 4], ratings
assert [r["value_text"] for r in reasons] == ["Friendly staff"], reasons
assert reasons[0]["submission_id"] in {r["submission_id"] for r in ratings}, "reason not grouped with its rating"
assert all(r["source_type"] == "kiosk" and r["source_name"] == "Hamburg Store Exit" for r in records)
print(f"OK: {len(records)} feedback records match the expected answers")
PY

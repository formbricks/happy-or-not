# Formbricks Kiosk

An open-source smiley-face feedback terminal for Android tablets. Put a tablet
on a stand at a till, exit, reception desk or restroom, ask one question, and
collect one-tap answers on a four-point scale.

<!-- Screenshots: see the `emulator-demo` artifact of the latest Android workflow run. -->

## Features

- **One-tap answers.** Four large drawn faces, from very happy to very unhappy.
- **Optional follow-up.** After a tap, ask "What was the main reason?" with up to
  nine configurable answers. It times out automatically if nobody answers.
- **Spam protection.** Taps are ignored while the follow-up and thank-you
  screens are showing.
- **Works offline.** Every answer is stored on the device in SQLite first.
- **Formbricks sync (optional).** Queued answers are sent to a Formbricks
  survey (Cloud or self-hosted) through the public Client API. No API key is
  stored on the device.
- **Built-in results.** Today, all-time and 7-day breakdowns with a
  "% positive" score, plus a CSV export.
- **Kiosk mode.** Fullscreen, keeps the screen on, ignores the back gesture,
  and can pin itself to the screen (Android screen pinning / lock task).

## Using it

| Action | How |
| --- | --- |
| Open the admin screen | Tap the **top-left corner five times** within 3 seconds, then enter the PIN (default `1234`). |
| Change the question, thank-you text or follow-up answers | Admin → **Survey** → **Save settings** |
| Export responses | Admin → **Export CSV** (opens the Android share sheet) |
| Lock the tablet to the app | Admin → **Pin app to screen** |

Change the default PIN before deploying.

### Sending responses to Formbricks

1. In Formbricks, create a **link survey** with:
   - a **Rating** question with a range of **4**. The app sends `4` for very happy down to `1` for very unhappy.
   - optionally, a **Single-select** question whose choices match the follow-up answers configured in the app.
2. In the app's admin screen, enter the Formbricks URL, the environment ID, the
   survey ID and the question IDs, then save.

Responses are sent about 20 seconds after they're given, so that the follow-up
answer goes in the same response. Unsent responses stay queued and are retried
every 30 seconds. Admin → **Sync now** sends everything immediately.

Request sent per response:

```http
POST {formbricksUrl}/api/v1/client/{environmentId}/responses
Content-Type: application/json

{ "surveyId": "…", "finished": true, "data": { "<ratingQuestionId>": 4, "<followUpQuestionId>": "Friendly staff" } }
```

## Building

Requirements: JDK 17 and the Android SDK (API 35). Android Studio provides both.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install it on a connected device or a running emulator and click through
the flow, saving screenshots and a recording to `demo-output/`:

```bash
scripts/emulator-demo.sh app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration and online demo

`.github/workflows/android.yml` runs on every push and does three things:

1. **build**: builds the debug APK and uploads it as the `formbricks-kiosk-apk` artifact.
2. **emulator-demo**: boots an Android 14 tablet emulator on the GitHub runner,
   runs `scripts/emulator-demo.sh`, and uploads screenshots and an MP4 as the
   `emulator-demo` artifact.
3. **appetize** (optional): uploads the APK to [Appetize.io](https://appetize.io)
   so anyone can use the app in a browser. To turn it on, add an
   `APPETIZE_API_TOKEN` repository secret. To keep updating the same link
   instead of creating a new app each time, add an `APPETIZE_PUBLIC_KEY`
   repository variable. The link appears in the workflow run summary.

## Project layout

```
app/src/main/java/com/formbricks/kiosk/
  MainActivity.kt          fullscreen, keep-screen-on activity
  KioskViewModel.kt        survey flow, timers, admin unlock, sync loop
  data/Settings.kt         operator settings (SharedPreferences)
  data/ResponseDb.kt       local response store, stats, CSV
  data/FormbricksClient.kt Formbricks Client API call
  ui/Faces.kt              the drawn smiley faces and buttons
  ui/KioskApp.kt           survey, follow-up, thank-you and PIN screens
  ui/AdminScreen.kt        results, settings and actions
```

## License

MIT. See [LICENSE](LICENSE).

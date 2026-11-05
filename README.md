# Formbricks Kiosk

An open-source, one-tap feedback terminal for Android tablets. Put a tablet on a stand at a till,
exit, reception desk or restroom, ask one question, and collect answers on a four-point smiley
scale. Every answer flows straight into a [Formbricks](https://formbricks.com) Feedback Dataset, so
it appears in your Formbricks dashboards next to all your other feedback.

| Survey | Follow-up |
| --- | --- |
| ![Survey screen](docs/screenshots/01-survey.png) | ![Follow-up question](docs/screenshots/02-follow-up.png) |
| **Thank you** | **Admin: results** |
| ![Thank-you screen](docs/screenshots/03-thank-you.png) | ![Admin results](docs/screenshots/05-admin.png) |
| **Admin: PIN** | **Admin: Formbricks connection** |
| ![PIN prompt](docs/screenshots/04-pin.png) | ![Formbricks connection settings](docs/screenshots/06-admin-formbricks.png) |

The screenshots come from the CI run on an Android 14 tablet emulator (see [CI](#continuous-integration-and-online-demo)).

## Features

- **One-tap answers.** Four large drawn faces, from very happy to very unhappy.
- **Optional follow-up.** After a tap, ask "What was the main reason?" with up to nine answers you
  choose. It times out after 12 seconds if nobody answers.
- **Spam protection.** Taps are ignored while the follow-up and thank-you screens are showing.
- **Built into Formbricks.** Answers become Formbricks feedback records in the dataset you choose,
  tagged with the kiosk's location, ready for dashboards, filters and AI topics.
- **Works offline.** Every answer is saved on the tablet first and sent when the connection is back.
- **On-device results.** Today, all-time and 7-day breakdowns with a "% positive" score, plus a CSV
  export, even without Formbricks.
- **Kiosk mode.** Fullscreen, keeps the screen on, ignores the back gesture, and can pin itself to
  the screen.

## Send feedback to Formbricks

The kiosk writes into a **Feedback Dataset** through the Formbricks
[Feedback Records API](https://formbricks.com/docs/api-v3-reference/introduction). You need four
things: your Formbricks URL, a dataset, a workspace that can access it, and an API key. Setup takes
about five minutes and needs an organization **Owner** or **Manager**. Your organization needs the
**Unify Feedback** feature.

### 1. Create a dataset, or pick an existing one

A dataset is the container your kiosk feedback lands in. Decide where kiosk feedback belongs:

- **Next to your other customer feedback** (surveys, reviews, support tickets): use your existing
  dataset. Kiosk answers are marked with `source_type = kiosk`, so you can always filter them apart.
- **On its own** (for example, a separate "In-store feedback" program): create a new dataset.

To create one, open **Settings → Organization → Datasets** and click **Create dataset**.

### 2. Give a workspace access to the dataset

API keys are granted per workspace, so the dataset needs a workspace the key can act on.

1. In **Settings → Organization → Datasets**, click **Manage** on the dataset.
2. Add the workspace whose dashboards should show the kiosk feedback.

A workspace can access only one dataset. Use a dataset shared by **one** workspace if you want the
kiosk's records to be editable or deletable through the API later.

### 3. Create an API key for the kiosks

1. Open **API Keys** from the organization dropdown in the breadcrumbs.
2. Create a key, add the workspace from step 2 and give it **Write** access. Write is enough to
   create records; don't use Manage.
3. Copy the key. It's shown only once.

Use one key for all kiosks, kept just for them. The key is stored on the tablet, so if a tablet is
lost, delete the key and set a new one on the other kiosks.

### 4. Find the dataset ID

In **Settings → Organization → Datasets**, click **Manage** on the dataset. The **Dataset ID** is
shown at the bottom of the dialog; copy it. It looks like `cmabc123def456ghi789jkl0`.

On older Formbricks versions that don't show it there, ask an AI assistant connected to the
Formbricks MCP server to run `list_feedback_datasets` for your workspace, or ask your Formbricks
contact.

### 5. Connect the kiosk

1. On the tablet, tap the **top-left corner five times** and enter the PIN (default `1234`).
2. Under **Formbricks connection**, fill in:

   | Field | Value |
   | --- | --- |
   | Formbricks URL | `https://app.formbricks.com`, or your self-hosted URL |
   | API key | The key from step 3 |
   | Dataset ID | The ID from step 4 |
   | Location name | Where this tablet stands, e.g. `Hamburg store, exit`. Shows up as the source name in Formbricks. |
   | Kiosk ID | Pre-filled with a random ID. Change it to something readable like `hamburg-exit-01` if you like. |

3. Tap **Save settings**, then **Test connection**. It should say *Connected: this kiosk can write
   to the dataset.*
4. Tap **Sync now** to send any answers collected so far.

From then on, answers are sent automatically about 20 seconds after they're given (so the
follow-up answer travels with the rating), and unsent answers are retried every 30 seconds.

### 6. See the feedback in Formbricks

In the workspace, open **Analyze → Feedback Data**. Each answer arrives as a submission of one or
two feedback records:

| `field_id` | `field_type` | Value | Question text (`field_label`) |
| --- | --- | --- | --- |
| `satisfaction` | `rating` | `value_number`: 4 = very happy, 3 = happy, 2 = unhappy, 1 = very unhappy | Your kiosk question |
| `reason` | `categorical` | `value_text`: the follow-up answer picked (only if one was picked) | Your follow-up question |

Every record also carries:

| Member | Value |
| --- | --- |
| `source_type` | `kiosk` |
| `source_id` | The kiosk ID |
| `source_name` | The location name |
| `submission_id` | `<kiosk ID>-<answer number>`: links the rating and the reason of one answer |
| `collected_at` | When the face was tapped, not when it was sent |
| `metadata` | `kiosk_id`, `location`, `mood` (e.g. "Very happy"), `scale_max` (4), `app_version` |

Suggested dashboard charts:

- **Average satisfaction over time**: `field_id = satisfaction`, average of the value, by day.
- **Kiosks compared**: the same chart, split by `source_name`.
- **Why people are unhappy**: `field_id = reason`, count by value, for submissions with a
  satisfaction of 1 or 2.

### Troubleshooting

The admin screen shows the result of the last sync and how many answers are waiting.

| Message | Fix |
| --- | --- |
| *The API key is not valid.* | Re-enter the key, or create a new one (step 3). |
| *The API key has no access to this dataset.* | Check the dataset ID, and that the key has Write access to a workspace that's assigned to the dataset (steps 2–4). |
| *This Formbricks instance doesn't offer the Feedback Records API.* | Check the URL. Self-hosted instances need Unify Feedback and the Feedback Records API gateway enabled. |
| *Can't reach Formbricks* | The tablet is offline. Answers stay queued and are sent once it's back online. |

Nothing is lost while a setting is wrong: answers stay queued on the tablet until a sync succeeds.

## Using the kiosk

| Action | How |
| --- | --- |
| Open the admin screen | Tap the **top-left corner five times** within 3 seconds, then enter the PIN (default `1234`). |
| Change the question, thank-you text or follow-up answers | Admin → **Survey** → **Save settings** |
| Export answers stored on the tablet | Admin → **Export CSV** (opens the Android share sheet) |
| Lock the tablet to the app | Admin → **Pin app to screen** |

Change the default PIN before you put a tablet out.

Changing the question text only affects new answers: each record keeps the question it was
answered under.

## Building

Requirements: JDK 17 and the Android SDK (API 35). Android Studio provides both.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install it on a connected device or a running emulator, click through the whole flow, and check
what reaches a mock of the Formbricks API (screenshots, a recording and the received records land in
`demo-output/`):

```bash
scripts/emulator-demo.sh app/build/outputs/apk/debug/app-debug.apk
```

Debug builds also accept the Formbricks connection as launch extras, which is handy during
development:

```bash
adb shell "am start -n com.formbricks.kiosk/.MainActivity --es fb_host https://app.formbricks.com --es fb_api_key <key> --es fb_dataset_id <datasetId> --es fb_location 'Test bench'"
```

## Continuous integration and online demo

`.github/workflows/android.yml` runs on every push:

1. **build**: builds the debug APK (artifact `formbricks-kiosk-apk`).
2. **emulator-demo**: boots an Android 14 tablet emulator, starts a mock of the Formbricks Feedback
   Records API (`scripts/mock-formbricks.py`), clicks through the kiosk, syncs, and fails the build
   unless the expected feedback records arrive. Screenshots, a screen recording and the received
   records are uploaded as the `emulator-demo` artifact and published to the `ci-demo` branch.
3. **appetize** (optional): uploads the APK to [Appetize.io](https://appetize.io) so anyone can use
   the app in a browser. Add an `APPETIZE_API_TOKEN` repository secret to turn it on, and an
   `APPETIZE_PUBLIC_KEY` repository variable to keep updating the same link. The link appears in the
   workflow run summary.

## Project layout

```
app/src/main/java/com/formbricks/kiosk/
  MainActivity.kt          fullscreen, keep-screen-on activity
  KioskViewModel.kt        survey flow, timers, admin unlock, sync loop
  data/Settings.kt         operator settings (SharedPreferences)
  data/ResponseDb.kt       local answer store, stats, CSV
  data/FormbricksClient.kt Formbricks Feedback Records API client
  ui/Faces.kt              the drawn smiley faces and buttons
  ui/KioskApp.kt           survey, follow-up, thank-you and PIN screens
  ui/AdminScreen.kt        results, Formbricks connection, settings and actions
scripts/
  emulator-demo.sh         drives the app on an emulator and checks the records sent
  mock-formbricks.py       mock of the Feedback Records API for CI
```

## License

MIT. See [LICENSE](LICENSE).

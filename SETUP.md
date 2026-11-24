# Pull GHOSTGRID into Android Studio and fly it

Six steps, about 15 minutes the first time.

## 1. Fetch the branch

The app lives on branch `arena/0a7a4efc-dronesystem`.

**If you already have the repo cloned** (your `dronesystem` folder):

```bash
cd dronesystem
git fetch origin
git checkout arena/0a7a4efc-dronesystem
git pull origin arena/0a7a4efc-dronesystem
```

**If you do not have it yet:**

```bash
git clone https://github.com/thabofromrichfield/dronesystem.git
cd dronesystem
git checkout arena/0a7a4efc-dronesystem
```

## 2. Open the right folder in Android Studio

`File → Open…` and pick the **repository root**, the folder that contains
`settings.gradle`. Not `app/`, and not `dji-reference/sample-code/`.

Android Studio reads `settings.gradle`, sees `include ':app'`, and ignores
`dji-reference/` entirely.

**Set the Gradle JDK to 11 before syncing.** The repo pins Gradle 6.7.1, which
runs on Java 8–15 only. Android Studio ships JBR 17 or 21 and the sync will fail
with `Unsupported class file major version` unless you point it at an 11:
`File → Settings → Build Tools → Gradle → Gradle JDK → 11`.

## 3. Let Gradle sync

First sync downloads the Gradle 6.7.1 wrapper, DJI SDK 4.18 and the AndroidX
libraries. Give it 5–10 minutes on a cold cache.

Toolchain, pinned on purpose:

| Piece | Version |
| --- | --- |
| Android Gradle Plugin | 4.2.2 |
| Gradle | 6.7.1 |
| Kotlin | 1.5.10 |
| compileSdk / targetSdk | 33 / 33 |
| minSdk | 23 |
| DJI Mobile SDK | 4.18 |

These are the versions DJI 4.18 is proven against. Do not let the IDE's
"upgrade Gradle" prompt run — the SDK ships native libraries and Kotlin metadata
from this era and will not build cleanly on newer toolchains.

Android Studio Hedgehog or older is the comfortable match. On newer versions,
if it insists on a newer AGP, add this to `gradle.properties`:

```properties
android.suppressUnsupportedCompileSdk=33
```

## 3b. Run it on an emulator (interface only)

The `sim` flavour links no DJI SDK at all, so it builds for `x86_64` and runs
unemulated on a normal AVD. This is the fast way to see and tune the interface.

1. Create the AVD: `Tools → Device Manager → Create Virtual Device`
   - Phone profile, anything with a landscape-friendly size
   - System image: **API 33, x86_64**, no Google Play needed
2. Pick the build variant: `Build → Select Build Variant…` → `app` → **simDebug**
3. Press Run

Or from the terminal:

```bash
./gradlew installSimDebug
adb shell am start -n drone.test.sim/drone.test.ui.BootActivity
```

What the sim build does and does not do:

| | sim build | full build |
| --- | --- | --- |
| HUD, tapes, reticle, console | yes | yes |
| Synthetic telemetry | yes | no |
| Live camera feed | no — surface stays black | yes |
| Virtual stick / flight commands | refused and logged | yes |
| DJI App Key needed | no | yes |
| Runs on x86_64 emulator | yes | no, ARM only |

Its `applicationId` is `drone.test.sim`, so it installs alongside the real app
without replacing it.

---

## 4. Get a DJI App Key

1. Go to https://account.dji.com/register?appId=71#/developer
2. Create an app. **Package name must be exactly** `drone.test`
3. Copy the App Key.

Paste it into `app/src/main/res/values/strings.xml`:

```xml
<string name="dji_api_key">YOUR_KEY_GOES_HERE</string>
```

Until this is done, the boot screen shows `APP KEY : MISSING — see README` and
registration fails. SIM FEED still works, so you can play with the interface
without a key.

## 5. Build and install

Real flight build, on a phone:

```bash
./gradlew assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

Interface build, on an emulator — see step 3b:

```bash
./gradlew installSimDebug
```

`./gradlew assembleDebug` builds both. The full APK is ARM only and will not
install on an x86_64 AVD.

## 6. Fly

1. Phone powered on, aircraft and controller powered on.
2. Plug the phone into the controller's USB port with an OTG cable.
3. Launch GHOSTGRID. Grant every permission it asks for — location is mandatory,
   the SDK will not register without it.
4. Boot screen shows `LINK : LINKED` and your aircraft model.
5. `ENTER GRID`.
6. `LIMITS` → set RTH altitude and max radius first.
7. `STICK` → read the dialog → `ENGAGE`.
8. Fly. `HOVER` neutralises both sticks instantly.

### Rehearse without flying

- **SIM** button starts DJI's onboard simulator. Real aircraft, real SDK, no
  props spinning.
- **ENTER GRID — SIM FEED** on the boot screen renders the whole HUD from
  synthetic data. No aircraft, no App Key needed. It is watermarked
  `SIMULATION` across the glass and refuses every flight command.

## Sanity-check the source without building

`tools/verify_structure.py` runs the structural checks in about a second and
needs no JDK:

```bash
python3 tools/verify_structure.py
```

It confirms the flavour boundary (nothing outside `src/full` imports `dji.*`),
that both backends implement all 28 `UavBackend` methods, that every `R.*`
reference and layout class resolves, and that the manifest classes exist. It is
not a compiler — run it to catch a broken edit fast, then still build.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `SDK registration failed: App key does not exist` | App Key wrong, or registered against a different package name |
| `SDK registration failed` with no aircraft | Phone has no internet on first launch — DJI validates the key online |
| Boot screen stuck on `REGISTERING` | Permissions denied. Re-launch and grant location |
| `LINK : STANDBY` forever | USB/OTG cable not passing data. Try another cable |
| Video black, telemetry alive | Primary video feed not up yet. Power the aircraft fully, not just the controller |
| Push forward, aircraft rolls | `SWAP_PITCH_ROLL` in `VirtualStickController.SWAP_PITCH_ROLL` is wrong for your airframe. Flip it |
| `Unsupported class file major version` | Gradle JDK is not 11. `Settings → Build Tools → Gradle → Gradle JDK` |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | You put the `full` APK on an x86_64 emulator. Use `installSimDebug` instead |
| Build fails on `Could not find com.dji:dji-sdk:4.18` | Stale Gradle cache. `./gradlew --stop` then `File → Invalidate Caches` |
| Build fails on `unresolved reference: UavBackendFactory` | You selected a build variant that has no source set. Pick `fullDebug` or `simDebug` |
| `More than one file was found with OS independent path 'META-INF/rxjava.properties'` | Should not happen — the `packagingOptions` excludes are in `app/build.gradle`. Check they were not removed |

## Where things live

| I want to change… | File |
| --- | --- |
| Colours | `src/main/res/values/colors.xml` **and** `ui/widget/Palette.kt` — keep both in sync |
| HUD drawing | `ui/widget/HudOverlay.kt` |
| Stick feel and speed ceilings | `ui/widget/CyberJoystick.kt`, `VirtualStickController.kt` (in `src/full`) |
| What telemetry is read | `src/full/core/TelemetrySource.kt`, `src/main/core/Telemetry.kt` |
| What commands exist | `src/full/core/FlightOps.kt`, behind `src/main/core/UavBackend.kt` |
| Buttons and layout | `src/main/res/layout/activity_control.xml` |
| App name, labels | `src/main/res/values/strings.xml` |

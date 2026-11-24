# From repo to flying

For Mavic 2 / Air 2 / Mini 1 / Phantom 4 / Inspire 2 / Matrice on Mobile SDK
V4 4.18. About 30 minutes the first time, most of it Gradle downloads.

---

## 1. Get the DJI App Key

This is the only thing that blocks the build. Everything else is mechanical.

1. https://account.dji.com/register?appId=71#/developer
2. Create an app. **Package name must be exactly** `drone.test`
3. Copy the App Key into `app/src/main/res/values/strings.xml` line 11:

```xml
<string name="dji_api_key">a1b2c3d4e5f6...</string>
```

Skip this and `./gradlew assembleFullDebug` now stops immediately and tells you,
instead of failing at runtime after a 15-minute sync.

## 2. Hardware

| Item | Note |
| --- | --- |
| Android phone | 6.0 or newer, `armeabi-v7a` or `arm64-v8a`. No emulator |
| USB OTG cable | the short one that came with the controller |
| Controller | powered on before the phone connects |
| Aircraft | powered on, props clear |

## 3. Build and install

```bash
cd ~/Downloads/dronesystem
git pull origin arena/0a7a4efc-dronesystem
./gradlew assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

First build 10–20 minutes. Later builds under a minute.

## 4. Two different ways to fly — pick one

This is the distinction that matters most.

**Fly with the controller sticks.** Do **not** press `STICK`. The controller
flies the aircraft; GHOSTGRID is the head-up glass, the camera and the gimbal.
Telemetry, video, capture, record, RTH altitude and limits all work.

**Fly from the phone screen.** Press `STICK` → `ENGAGE`. The phone takes flight
authority and the controller sticks stop responding. Release both sticks to
hover. Press `STICK` again to hand authority back.

Engaging virtual stick is not additive. One or the other has the sticks.

## 5. First launch

1. Plug the phone into the controller. The app should open by itself
2. Grant every permission. Location is not optional — the SDK refuses to
   register without it
3. Boot screen should read `LINK : LINKED` and your aircraft model
4. `ENTER GRID`
5. **`LIMITS` first** — set RTH altitude and max radius before you take off

## 6. The one setting you must check on the ground

`app/src/full/java/drone/test/core/VirtualStickController.kt`:

```kotlin
private const val SWAP_PITCH_ROLL = true
```

DJI 4.18's `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
two arguments swapped relative to their names. DJI says so in a comment in their
own sample. `true` matches the sample.

**Hover at 2 m, push the right stick forward.** Aircraft pitches forward: leave
it. Aircraft rolls sideways: set it to `false`, rebuild, reinstall.

Do this over grass, with `MAX RADIUS 100 m` set, and nobody downwind.

## 7. Pre-flight, in order

1. `LIMITS` → RTH altitude above the tallest obstacle nearby
2. `LIMITS` → max radius to something you can actually see
3. Satellites in the footer at 8 or more
4. Battery above 50%
5. Warning strip clear — no `HOME POINT NOT SET`, no `COMPASS ERROR`
6. Take off, hover at 2 m, do the stick check above
7. Only then fly the mission

## 8. Controls

| Button | Effect |
| --- | --- |
| `STICK` | toggle phone flight authority |
| `TAKEOFF` | auto climb to about 1.2 m and hover |
| `HOVER` | neutralise both sticks immediately |
| `LAND` | descend. DJI pauses near the ground on some airframes |
| `CONFIRM LAND` | confirm the descent. Motors stop |
| `ABORT LAND` | cancel the landing |
| `RTH` | fly to the recorded home point and land |
| `CAPTURE` / `RECORD` | camera |
| `GIM ±` / `GIM 0` / `HORIZON` / `DOWN 45` | gimbal jog and absolute angles |
| `LIMITS` | RTH altitude, max height, max radius |
| `LOG` | mission console — every command and every SDK result, timestamped |
| `SIM` | start DJI's onboard simulator. Real aircraft, props do not spin |

Speed ceilings live in `VirtualStickController.kt`: 6 m/s horizontal, 3 m/s
vertical, 60 °/s yaw. Lower them before your first flight if you want.

## 9. Rehearse without flying

`SIM` starts the DJI onboard simulator. Real SDK, real aircraft connection,
real telemetry — motors stay off. Set limits, engage virtual stick, fly the
circuit. Do this before the real thing.

## 10. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `App key does not exist` | key wrong, or registered against a package that is not `drone.test` |
| Stuck on `REGISTERING` | phone has no internet. The key is validated online on first launch |
| `LINK : STANDBY` forever | OTG cable is charge-only. Try another cable |
| App does not open on plug-in | open it manually. Auto-launch depends on the USB accessory filter |
| Permissions denied | uninstall, reinstall, grant everything including location |
| Video black, telemetry alive | aircraft not fully powered, or the primary feed is not up yet |
| Push forward, aircraft rolls | `SWAP_PITCH_ROLL` is wrong for your airframe |
| Controller sticks do nothing | virtual stick is engaged. Press `STICK` to release |
| Mavic 2 Pro/Zoom video looks distorted | `library-anti-distortion` is excluded in `app/build.gradle` to shrink the APK. Remove the `exclude module:` line and rebuild |
| `Unsupported class file major version` | Gradle JDK is not 11 |
| `Could not find com.dji:dji-sdk:4.18` | `./gradlew --stop`, then `File → Invalidate Caches` |

## 11. Before you trust it

This build has never been flown. It compiles against the DJI SDK, every call was
checked against DJI's bundled API reference, and the logic mirrors DJI's own
sample — but nobody has put it in the air.

First session: simulator, then a 2 m hover over grass, then short low flights.
`RTH` is always available, and the aircraft's own failsafe takes over if the
phone stops sending stick frames.

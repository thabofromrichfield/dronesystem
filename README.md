# GHOSTGRID — DJI drone control interface

A cybersecurity-styled tactical control app for DJI aircraft, built on **DJI
Mobile SDK V4 (4.18)**. Fly from the phone screen or from the physical
controller; the phone is the head-up glass either way.

```
┌──────────────────────────────────────────────────────────────┐
│  ▌LINK AIR  MATRICE 300 RTK  BAT 87%  SAT 16▐        [LIMITS] │
│                    2 8 5   N                                │
│                   ╔═════╗                                     │
│      GS   ┌───┐   ║ 285 ║   ┌───┐   ALT                      │
│      M/S  │ 12│   ╚═════╝   │ 63│   M                        │
│           └───┘      ┼      └───┘                             │
│           ───────╲   │   ╱───────                             │
│                   ╲  ┼  ╱                                    │
│      ┌───────────────┴──────────────┐                        │
│      │  GPS   T+04:12   GIM -32     │                        │
│      └──────────────────────────────┘                        │
│   ( THR/YAW )    [STICK][TAKEOFF][HOVER][LAND][RTH]  ( PITCH/ROLL )
└──────────────────────────────────────────────────────────────┘
```

## What it does

| System | Detail |
| --- | --- |
| Live FPV | Primary camera feed decoded through `DJICodecManager` onto a `SurfaceView` |
| Head-up glass | Artificial horizon with pitch ladder, heading tape, speed and altitude tapes, vertical-speed bar, reticle |
| Virtual stick | Mode 2 (American hand 2). Two touch sticks, 10 Hz command stream, auto-recentre on release |
| Flight commands | Takeoff, hover, land, confirm land, abort land, return to home — each behind a confirmation dialog |
| Gimbal | Speed jog up/down, stop, absolute 0° and −45° |
| Camera | Single capture, record toggle |
| Telemetry | Altitude, ground speed, vertical speed, distance from home, lat/lon, heading, pitch/roll, gimbal pitch, satellites, battery % / V / °C, flight timer, flight mode, RC switch |
| Warnings | Failsafe armed, battery low/serious, home point not set, GPS weak, above 400 m |
| Mission console | Timestamped log of every command sent and every SDK result |
| Flight limits | RTH altitude 40/60 m, max height 60/120 m, max radius 100/300 m |
| Simulator | Starts the DJI onboard simulator at your current position so you can rehearse with props still |
| SIM feed | Synthetic telemetry that renders the whole HUD with no aircraft attached. Cannot send commands |

## Supported aircraft

DJI Mobile SDK V4 4.18 talks to these and nothing newer. Taken from the bundled
API reference, not from memory:

| Family | Models |
| --- | --- |
| Mavic | Pro, Air, Air 2, 2 Pro, 2 Zoom, 2 Enterprise / Dual / Advanced, Mini (1) |
| Phantom | 3 Standard / Advanced / Professional, 4, 4 Advanced, 4 Pro, 4 Pro V2 |
| Inspire | 1, 1 Pro, 1 Raw, 2 |
| Matrice | 100, 200 / V2, 210 / V2, 210 RTK / V2, 300 RTK, 600, 600 Pro |

**Not supported:** Mini 2 and later, Air 3, Mavic 3 family, Avata, FPV, Neo.
Those need Mobile SDK **v5**, which is a different SDK with a different API —
this project would not build against it unchanged.

## Two build flavours

| | `full` | `sim` |
| --- | --- | --- |
| DJI Mobile SDK 4.18 | linked | **not linked at all** |
| What it does | flies a real aircraft | renders the interface on synthetic data |
| ABI | `armeabi-v7a`, `arm64-v8a` | `x86_64`, `x86` |
| Runs on an emulator | no | **yes, unemulated** |
| Application ID | `drone.test` | `drone.test.sim` |
| Build | `./gradlew assembleFullDebug` | `./gradlew installSimDebug` |

The split is a source-set boundary, not a runtime flag. `src/main` holds the
interface and is DJI-free by construction; `src/full` holds every class that
imports `dji.*`; `src/sim` supplies a stub backend with no SDK on the classpath.
Nothing in `ui/` imports `dji.*`, and a check in the build notes enforces it.

## Running it

| Where | Command | What you get |
| --- | --- | --- |
| Browser, no toolchain | `python3 -m http.server 8080 --directory preview` | visual preview of the whole interface |
| Android emulator | `./gradlew installSimDebug` | the real app, synthetic telemetry, x86_64 |
| Physical phone + aircraft | `./gradlew assembleFullDebug` | the real app, real flight |

**The real app is the `full` build.** `assembleFullDebug` produces an APK that
registers with DJI, decodes the live camera feed and flies the aircraft. It needs
a physical phone and a DJI App Key registered against package `drone.test`.
Step-by-step from repo to first flight: [FIRST-FLIGHT.md](FIRST-FLIGHT.md).

The `sim` build and `preview/index.html` exist so the interface can be developed
without hardware. Neither can command an aircraft. VS Code and emulator setup in
[VSCODE.md](VSCODE.md); Android Studio path in [SETUP.md](SETUP.md).

## Getting an APK without installing the toolchain

[`.github/workflows/android.yml`](.github/workflows/android.yml) builds both
flavours on every push. No local JDK, Android SDK or Gradle needed.

Push, then open **Actions** → the newest run → **Artifacts** at the bottom.

| Artifact | Size | Use |
| --- | --- | --- |
| `ghostgrid-sim-debug` | ~3 MB | emulator, interface only |
| `ghostgrid-full-debug-NO-APP-KEY` | ~73 MB | installs and launches, **will not connect** |

That 70 MB difference is DJI's ARM native libraries. The full APK is named the
way it is because it is built with the placeholder key: it registers with DJI,
gets rejected, and never reaches an aircraft.

**To get a full APK that actually flies**, add your key as a repository secret:

1. Settings → Secrets and variables → Actions → **New repository secret**
2. Name it exactly `DJI_API_KEY`, value = your App Key
3. Push anything, or re-run the workflow

The `full` job then builds `ghostgrid-full-debug` with the key baked in. The key
is injected at build time and is never committed. Your App Key must be
registered against package `drone.test` — see [FIRST-FLIGHT.md](FIRST-FLIGHT.md).

Until the secret exists, the `full` job skips and says so, and the
`full_compile_check` job still compiles `src/full` against dji-sdk 4.18 so the
flight code is never left uncompiled.

## Repository layout

Two halves, deliberately separate:

- **`app/`** — GHOSTGRID. My code. Package `drone.test`.
- **`dji-reference/`** — DJI's unmodified sample app and API docs. Read-only, **not compiled**.

Full tree in [PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md).
Import and run instructions in [SETUP.md](SETUP.md).

## Safety

These are real aircraft controls, so the risky paths are deliberate:

1. Every command that moves the aircraft goes through a confirmation dialog.
2. Leaving the screen while airborne asks first, and says the aircraft keeps flying.
3. Leaving the screen disengages virtual stick before tearing down the stream, so
   authority returns to the physical controller instead of leaving DJI waiting on
   an input that is no longer coming.
4. Virtual stick streams at 10 Hz; if the stream stops, the aircraft's own
   failsafe takes over.
5. Speed ceilings live in `VirtualStickController.kt` (in `src/full`): 6 m/s horizontal,
   3 m/s vertical, 60 °/s yaw.

## Before the first real flight

`app/src/full/java/drone/test/core/VirtualStickController.kt` has one constant that must be checked on the
ground: `SWAP_PITCH_ROLL`.

DJI 4.18's `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
two arguments swapped relative to their names — DJI's own sample says so in a
code comment. The flag defaults to `true` to match that sample.

**Hover at 2 m and push the right stick forward.** If the aircraft rolls sideways
instead of pitching forward, set the flag to `false`.

## Requirements

- Android 6.0 (API 23) or newer, `armeabi-v7a` or `arm64-v8a`. No x86 emulator.
- A DJI aircraft supported by Mobile SDK V4 4.18 and its remote controller.
- A DJI developer App Key registered against package `drone.test`.
- Internet on first launch — the App Key is validated against DJI's servers.

## License

GHOSTGRID source is MIT. `dji-reference/` and the DJI Mobile SDK remain under
DJI's own terms.

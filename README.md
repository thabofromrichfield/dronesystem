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

## Repository layout

Two halves, deliberately separate:

- **`app/`** — GHOSTGRID. My code. Package `com.thabo.ghostgrid`.
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
5. Speed ceilings live in `core/VirtualStickController.kt`: 6 m/s horizontal,
   3 m/s vertical, 60 °/s yaw.

## Before the first real flight

`core/VirtualStickController.kt` has one constant that must be checked on the
ground: `SWAP_PITCH_ROLL`.

DJI 4.18's `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
two arguments swapped relative to their names — DJI's own sample says so in a
code comment. The flag defaults to `true` to match that sample.

**Hover at 2 m and push the right stick forward.** If the aircraft rolls sideways
instead of pitching forward, set the flag to `false`.

## Requirements

- Android 6.0 (API 23) or newer, `armeabi-v7a` or `arm64-v8a`. No x86 emulator.
- A DJI aircraft supported by Mobile SDK V4 4.18 and its remote controller.
- A DJI developer App Key registered against package `com.thabo.ghostgrid`.
- Internet on first launch — the App Key is validated against DJI's servers.

## License

GHOSTGRID source is MIT. `dji-reference/` and the DJI Mobile SDK remain under
DJI's own terms.

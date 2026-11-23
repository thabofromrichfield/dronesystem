# GHOSTGRID — project structure

Two clearly separated halves. Mine on top, DJI's underneath.

```
/
├── app/                      ← MY PROJECT. Everything I write lives here.
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/           DJI health-code tables
│       ├── java/drone/test/
│       │   ├── GhostGridApp.kt
│       │   ├── core/         SDK-facing code, no Android views
│       │   │   ├── ProductBus.kt           registerApp + connect/disconnect
│       │   │   ├── Telemetry.kt            one immutable HUD frame
│       │   │   ├── TelemetryStore.kt       latest frame + main-thread push
│       │   │   ├── TelemetrySource.kt      DJI state callbacks → Telemetry
│       │   │   ├── FlightOps.kt            every command that leaves the phone
│       │   │   ├── VirtualStickController.kt  10 Hz stick stream
│       │   │   ├── DemoTelemetry.kt        synthetic frames, SDK never touched
│       │   │   ├── SdkPermissions.kt
│       │   │   └── LogBus.kt               rolling mission console
│       │   ├── ui/
│       │   │   ├── BootActivity.kt         link screen
│       │   │   ├── ControlActivity.kt      the control surface
│       │   │   └── widget/
│       │   │       ├── FpvSurface.kt       DJICodecManager video sink
│       │   │       ├── HudOverlay.kt       artificial horizon, tapes, reticle
│       │   │       ├── CyberJoystick.kt    touch stick, auto-recentres
│       │   │       ├── ConsoleView.kt      scrolling log
│       │   │       └── Palette.kt          canvas colours
│       │   └── util/Fmt.kt
│       └── res/
├── dji-reference/            ← DJI's sample code and docs. READ ONLY, not built.
├── build.gradle  settings.gradle  gradle.properties  gradlew  gradle/
├── skills/  AGENTS.md        ← agent skills, not app code
└── README.md  SETUP.md  PROJECT-STRUCTURE.md
```

## Layer rule

`ui/` never imports `dji.*`. All SDK traffic goes through `core/FlightOps`,
`core/TelemetrySource` and `core/ProductBus`. That keeps the interface testable
with `DemoTelemetry` and no aircraft attached.

## The one dangerous constant

`core/VirtualStickController.kt` → `SWAP_PITCH_ROLL`.

DJI 4.18's `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
two arguments swapped relative to their names; DJI's own sample says so in a code
comment. The flag defaults to `true` to match the sample. **Before the first real
flight, hover at 2 m and push the right stick forward.** If the aircraft rolls
sideways instead of pitching forward, set it to `false`.

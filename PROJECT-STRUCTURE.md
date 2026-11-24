# GHOSTGRID — project structure

Three clearly separated parts: my interface, my two backend flavours, and DJI's reference code.

```
/
├── app/                      ← MY PROJECT. Everything I write lives here.
│   ├── build.gradle            flavours: full (ARM, real SDK) / sim (x86_64, no SDK)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/             SHARED — interface only, imports no dji.*
│       │   ├── AndroidManifest.xml
│       │   ├── assets/       DJI health-code tables
│       │   ├── java/drone/test/
│       │   │   ├── GhostGridApp.kt
│       │   │   ├── core/
│       │   │   │   ├── UavBackend.kt        the seam: interface + UavLinkState
│       │   │   │   ├── Backend.kt           resolves the flavour's factory
│       │   │   │   ├── Telemetry.kt         one immutable HUD frame
│       │   │   │   ├── TelemetryStore.kt    latest frame + main-thread push
│       │   │   │   ├── DemoTelemetry.kt     synthetic frames
│       │   │   │   ├── SdkPermissions.kt
│       │   │   │   └── LogBus.kt            rolling mission console
│       │   │   ├── ui/
│       │   │   │   ├── BootActivity.kt      link screen
│       │   │   │   ├── ControlActivity.kt   the control surface
│       │   │   │   └── widget/  HudOverlay, CyberJoystick, ConsoleView, Palette
│       │   │   └── util/Fmt.kt
│       │   └── res/
│       ├── full/java/drone/test/   ← every file that imports dji.*
│       │   ├── core/  RealBackend, ProductBus, TelemetrySource, FlightOps,
│       │   │          VirtualStickController
│       │   └── ui/widget/FpvSurface.kt      DJICodecManager video sink
│       └── sim/java/drone/test/    ← no SDK on the classpath at all
│           ├── core/SimBackend.kt           refuses every flight command
│           └── ui/widget/FpvSurface.kt      black surface, same class name
├── dji-reference/            ← DJI's sample code and docs. READ ONLY, not built.
├── build.gradle  settings.gradle  gradle.properties  gradlew  gradle/
├── skills/  AGENTS.md        ← agent skills, not app code
└── README.md  SETUP.md  PROJECT-STRUCTURE.md
```

## Layer rule

`ui/` never imports `dji.*`. All SDK traffic goes through `Backend.uav`, which
resolves to `RealBackend` in the `full` flavour and `SimBackend` in `sim`. That
is what makes the emulator build possible: the DJI SDK has no x86 native
libraries, so a single-variant project could never load on an AVD.

`FpvSurface` is the one class declared twice, once per flavour, under the same
fully-qualified name. That is deliberate: the shared layout inflates it and the
shared activity drives it, so neither needs to know which build is running.

## The one dangerous constant

`app/src/full/java/drone/test/core/VirtualStickController.kt` → `SWAP_PITCH_ROLL`.

DJI 4.18's `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
two arguments swapped relative to their names; DJI's own sample says so in a code
comment. The flag defaults to `true` to match the sample. **Before the first real
flight, hover at 2 m and push the right stick forward.** If the aircraft rolls
sideways instead of pitching forward, set it to `false`.

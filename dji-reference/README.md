# dji-reference/ — vendor sample code, READ ONLY

This folder is **not my project**. It is the unmodified DJI Mobile SDK V4 sample
that GHOSTGRID was built from. Nothing in here is compiled: `settings.gradle`
only includes `:app`.

| Path | What it is |
| --- | --- |
| `sample-code/` | DJI's official Android SDK V4 sample app (`com.dji.sdk.sample`), untouched |
| `docs/` | DJI's generated HTML API reference + SDK API diff docs |
| `hms.json`, `hms_match_sdkerror.json` | DJI health-management code tables, copied into `app/src/main/assets/` |
| `README-dji-sdk-sample.md` | DJI's original top-level README |

## Why it is kept

It is the only trustworthy source of exact V4 4.18 method signatures. Every DJI
call in `app/` was checked against it before use — for example
`sendVirtualStickFlightControlData(new FlightControlData(roll, pitch, yaw, throttle), ...)`
in `sample-code/app/src/main/java/com/dji/sdk/sample/demo/flightcontroller/VirtualStickView.java`
carries the note that DJI swapped the first two constructor arguments.

## Rule

Do not edit files in here. Copy out of it, then edit the copy in `app/`.

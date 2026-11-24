# VS Code + Android emulator, no Android Studio

Everything Android Studio's GUI does, from a terminal and VS Code.

Two hard facts first:

- The **`full` build cannot run on an emulator.** DJI SDK 4.18 ships ARM native
  libraries only. `installSimDebug` is the emulator path.
- **Gradle JDK must be 11.** The repo pins Gradle 6.7.1, which runs on Java 8–15.
  A JDK 17 or 21 default gives `Unsupported class file major version` and stops
  the sync before anything else happens.

---

## 1. Install a JDK 11

Ubuntu / Debian:

```bash
sudo apt update
sudo apt install -y openjdk-11-jdk
/usr/lib/jvm/java-11-openjdk-amd64/bin/java -version
```

Fedora: `sudo dnf install java-11-openjdk-devel`
Arch: `sudo pacman -S jdk11-openjdk`
macOS: `brew install --cask temurin@11`

`.vscode/settings.json` already points at `/usr/lib/jvm/java-11-openjdk-amd64`.
Change that path if your JDK lives elsewhere.

## 2. Install the Android command-line tools

Download **Command line tools only** from https://developer.android.com/studio#command-line-tools-only
then, on Linux:

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest
```

Put the tools on your PATH — add to `~/.bashrc` or `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Then `source ~/.bashrc` and check:

```bash
sdkmanager --version
```

## 3. Install the emulator packages

About 5 minutes and roughly 3 GB.

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.2" \
           "emulator" "system-images;android-33;google_apis;x86_64"
```

## 4. Check hardware acceleration

On Linux this decides whether the emulator is usable or painfully slow:

```bash
ls -l /dev/kvm
egrep -c '(vmx|svm)' /proc/cpuinfo
```

| Result | Meaning |
| --- | --- |
| `/dev/kvm` exists, count > 0 | KVM ready, emulator runs at near-native speed |
| `/dev/kvm` missing, count > 0 | CPU supports it, KVM not enabled — enable VT-x/AMD-V in BIOS |
| count = 0 | No virtualisation. Add `-accel off` and expect QEMU: minutes per screen |

If `/dev/kvm` exists but you get permission denied:

```bash
sudo usermod -aG kvm $USER
# log out and back in
```

## 5. Create and start the AVD

```bash
avdmanager create avd -n ghostgrid_sim \
  -k "system-images;android-33;google_apis;x86_64" -d pixel_5 --force

emulator -avd ghostgrid_sim -gpu host
```

No KVM:

```bash
emulator -avd ghostgrid_sim -gpu swiftshader_indirect -accel off
```

VS Code has tasks for both: `Terminal → Run Task…` → `emulator: create AVD (API 33 x86_64)` then `emulator: start`.

## 6. Build and install

```bash
cd ~/Downloads/dronesystem
git pull origin arena/0a7a4efc-dronesystem
./gradlew installSimDebug
adb shell am start -n drone.test.sim/drone.test.ui.BootActivity
```

Or `Terminal → Run Task…` → `install: simDebug onto emulator`.

First build is 10–20 minutes. Later builds are under a minute.

## 7. VS Code extensions

`.vscode/extensions.json` recommends these; VS Code prompts on open.

| Extension | What it gives you |
| --- | --- |
| `vscjava.vscode-gradle` | Gradle task tree in the sidebar |
| `vscjava.vscode-java-pack` | Java language server, debugging |
| `fwcd.kotlin` | Kotlin syntax and completion |
| `adelphes.android-dev-ext` | Deploy the APK and attach a debugger — `launch.json` is already wired for it |
| `redhat.vscode-xml` | Layout and manifest editing |

## 8. Logs

VS Code has no Logcat panel. Use a terminal:

```bash
adb logcat | grep -iE 'ghostgrid|drone.test|dji'
```

Or `Terminal → Run Task…` → `logcat: GHOSTGRID only`.

---

## What VS Code does not replace

| Android Studio | VS Code equivalent |
| --- | --- |
| AVD Manager GUI | `avdmanager` CLI |
| SDK Manager GUI | `sdkmanager` CLI |
| Layout Designer | none — edit `activity_control.xml` by hand |
| Logcat panel | `adb logcat` in a terminal |
| APK Analyzer | none |

You write Kotlin and XML by hand either way. The Designer is the only real loss,
and this project's layout is a single `FrameLayout` with fixed-position children.

---

## Seeing the interface without any of this

`preview/index.html` is a browser port of the HUD — horizon, heading and
altitude tapes, reticle, joysticks, mission console, all of it. It links no DJI
SDK and sends nothing anywhere.

```bash
python3 -m http.server 8080 --directory preview --bind 0.0.0.0
# open http://localhost:8080
```

Or just open the file directly in a browser. It is a visual reference, not the
app: no video feed, and every flight command is refused and logged.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Unsupported class file major version` | Gradle JDK is not 11 |
| `sdkmanager: command not found` | `cmdline-tools/latest/bin` is not on PATH |
| `x86 emulation currently requires hardware acceleration` | no KVM. Add `-accel off`, or enable VT-x in BIOS |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | you installed the `full` APK. Use `installSimDebug` |
| `error: package android does not exist` | `platforms;android-33` not installed, or `ANDROID_HOME` unset |
| `Could not find com.dji:dji-sdk:4.18` | only affects `full`. `./gradlew --stop` then retry |
| Emulator boots to a black screen | `-gpu host` unsupported. Try `-gpu swiftshader_indirect` |
| App installs but shows nothing | wrong activity. `adb shell am start -n drone.test.sim/drone.test.ui.BootActivity` |

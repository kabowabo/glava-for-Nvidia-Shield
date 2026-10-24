# GLava for NVIDIA Shield / Android TV

A port of GLava's `bars`, `radial` and `wave` visualizers to Android TV. Play music in
Spotify (or anything else) on the Shield, then open **GLava**. You can also set it as
the screensaver so it appears on its own when the Shield goes idle.

Audio comes from the global output mix (`android.media.audiofx.Visualizer`, session 0)
and runs through ports of GLava's own transforms (`window → fft → gravity → avg`, plus
`smooth_audio`, in `Dsp.kt`). The shaders in `app/src/main/assets/shaders` are GLES 3.0
ports of `shaders/glava/*`.

## Remote controls

| Button | Visualizer | Settings menu |
|---|---|---|
| ◀ ▶ | switch visualizer | change value |
| ▲ ▼ | amplify | select option |
| OK / MENU | open settings | (on *Reset*) reset to defaults |
| BACK | exit | close menu |

The settings save automatically and the screensaver uses them too:

- **All visualizers:** visualizer type, color theme (GLava default, Ocean, Crimson,
  Spotify, Sunset, Purple haze, Ice, Neon, Mono, Rainbow), background, amplify,
  smoothing, fall speed (gravity), averaging frames
- **Bars:** bar width, gap, outline, mirrored or low-to-high layout, top or bottom
- **Radial:** circle radius, number of bars, bar width, rotation speed
- **Wave:** line thickness

## Build & install (Windows)

Requires JDK 17 and the Android SDK (`platforms;android-34`, `build-tools;34.0.0`).
`local.properties` points to the SDK.

```sh
cd android
./gradlew assembleDebug
adb connect <shield-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.glava.shield android.permission.RECORD_AUDIO
```

## Use as the screensaver

Settings → Device Preferences → Screen saver → **GLava Visualizer**, or:

```sh
adb shell settings put secure screensaver_components com.glava.shield/.VisualizerDream
```

## Troubleshooting

`adb logcat -s GlavaShield` logs the capture every ~5 s. If `peak=0.000` while music is
playing, the firmware isn't sharing the output mix with the Visualizer API.

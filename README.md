# TouchMoveCompose

Android app: drag on the screen and isometric **cubes** spawn and fly. Built with **Jetpack Compose** (no SurfaceView, no game engine). Drawing is a vsync loop on the UI thread.

**Look:** Hatsune Miku palette — teal cubes, night/mint background, pink HUD.

[Detailed overview](docs/OVERVIEW.md) covers every class, the frame loop, telemetry, and performance budget.

<img width="1280" height="720" alt="TouchMoveKotlin 2" src="https://github.com/user-attachments/assets/a6f06dc0-8693-4806-ba9d-f9230c41768c" />

## Features

- Tap and drag to spawn cubes; they start moving as soon as they exist
- New press clears the board, then starts a new stroke
- Live HUD: FPS, frame time, jank, CPU, heap, native, PSS, thermal
- Quality scaling aimed at **55+ FPS** (hold spawn / skip tiny cubes when the UI thread is overloaded)
- Batched `drawVertices` + `drawLines` (two native draws per frame, not one path per face)
- Edge-to-edge Material 3 chrome (`minSdk` 24, `targetSdk` 37)

<img width="540" height="1200" alt="Screenshot_20260907_114137" src="https://github.com/user-attachments/assets/61cf5976-01b9-432c-99c4-a8264dc1bcd7" />
<img width="540" height="1200" alt="Screenshot_20260907_114049" src="https://github.com/user-attachments/assets/fae792c1-6d5a-4c08-938e-12cc169dacff" />
<img width="540" height="1200" alt="Screenshot_20260907_114045" src="https://github.com/user-attachments/assets/bc546521-9345-4da9-a817-cafa2962f564" />
<img width="540" height="1200" alt="Screenshot_20260907_114039" src="https://github.com/user-attachments/assets/fb6775ca-40f1-4058-a2ff-354785c117f8" />
<img width="540" height="1200" alt="Screenshot_20260907_114032" src="https://github.com/user-attachments/assets/106c231b-3e05-4fda-b957-daba6912adf9" />

## Requirements

- Android Studio with JDK 11
- Device or emulator, API 24+

## Run

```bat
gradlew.bat installDebug
```

```bash
./gradlew installDebug
```

Open the app, wait out the short intro scale, tap, and drag.

## Repo layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/.../TouchMoveScreen.kt` | UI, gestures, vsync loop |
| `app/src/main/java/.../TouchMoveEngine.kt` | Spawn, motion, HUD |
| `app/src/main/java/.../CubeGraphics.kt` | Isometric mesh batch |
| `app/src/main/java/.../PerformanceTelemetry.kt` | FPS / CPU / memory |
| `app/src/main/java/.../PlotPoints.kt` | Pixel flight paths |
| `docs/OVERVIEW.md` | Full design notes |

This is a 2D isometric canvas, not Filament/OpenGL 3D.

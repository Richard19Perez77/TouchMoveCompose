# TouchMoveCompose

Android app: drag on the screen and isometric **cubes** spawn and fly. Built with **Jetpack Compose** (no SurfaceView, no game engine). Drawing is a vsync loop on the UI thread.

**Look:** Hatsune Miku palette — teal cubes, night/mint background, pink HUD.

[Detailed overview](docs/OVERVIEW.md) covers every class, the frame loop, telemetry, and performance budget.

https://github.com/user-attachments/assets/f2926a40-db79-45a7-a3f7-dabf70c7a666

## Features

- Tap and drag to spawn cubes; they start moving as soon as they exist
- New press clears the board, then starts a new stroke
- Live HUD: FPS, frame time, jank, CPU, heap, native, PSS, thermal
- Quality scaling aimed at **55+ FPS** (hold spawn / skip tiny cubes when the UI thread is overloaded)
- Batched `drawVertices` + `drawLines` (two native draws per frame, not one path per face)
- Edge-to-edge Material 3 chrome (`minSdk` 24, `targetSdk` 37)

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

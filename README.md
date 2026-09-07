# TouchMoveCompose

Android app: drag on the screen and isometric **cubes** spawn and fly. Built with **Jetpack Compose** (no SurfaceView, no game engine). Drawing is a vsync loop on the UI thread.

**Look:** Hatsune Miku palette — teal cubes, night/mint background, pink HUD.

[Detailed overview](docs/OVERVIEW.md) covers every class, the frame loop, telemetry, and performance budget.

<p align="center">
  <img src="https://github.com/user-attachments/assets/a6f06dc0-8693-4806-ba9d-f9230c41768c" alt="TouchMoveKotlin 2" width="220"/>
</p>

## Features

- Tap and drag to spawn cubes; they start moving as soon as they exist
- New press clears the board, then starts a new stroke
- Live HUD: FPS, frame time, jank, CPU, heap, native, PSS, thermal
- Quality scaling aimed at **55+ FPS** (hold spawn / skip tiny cubes when the UI thread is overloaded)
- Batched `drawVertices` + `drawLines` (two native draws per frame, not one path per face)
- Edge-to-edge Material 3 chrome (`minSdk` 24, `targetSdk` 37)

## Screenshots

<table>
  <tr>
    <td width="33%"><img src="https://github.com/user-attachments/assets/61cf5976-01b9-432c-99c4-a8264dc1bcd7" alt="Screenshot 114137" width="220"/></td>
    <td width="33%"><img src="https://github.com/user-attachments/assets/fae792c1-6d5a-4c08-938e-12cc169dacff" alt="Screenshot 114049" width="220"/></td>
    <td width="33%"><img src="https://github.com/user-attachments/assets/bc546521-9345-4da9-a817-cafa2962f564" alt="Screenshot 114045" width="220"/></td>
  </tr>
  <tr>
    <td width="33%"><img src="https://github.com/user-attachments/assets/fb6775ca-40f1-4058-a2ff-354785c117f8" alt="Screenshot 114039" width="220"/></td>
    <td width="33%"><img src="https://github.com/user-attachments/assets/106c231b-3e05-4fda-b957-daba6912adf9" alt="Screenshot 114032" width="220"/></td>
    <td width="33%"></td>
  </tr>
</table>

## Requirements

- Android Studio with JDK 17
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

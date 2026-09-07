# TouchMoveCompose

TouchMoveCompose is a Jetpack Compose Android app that turns a finger drag into a field of isometric cubes. Each touch sample spawns a cube that immediately flies toward a random on-screen destination. The canvas is a custom vsync loop (not a game engine): physics and drawing run on the UI thread from `withFrameNanos`.

The look is a Hatsune Miku palette: teal cubes (primary), dark mint / night background (secondary), and ribbon-pink HUD text (accent).

**Platform:** `minSdk` 24, `targetSdk` / `compileSdk` 37, Java 11, Compose Material 3, edge-to-edge window.

---

## What you see and do

1. Launch: a 1.5s scale-in (t² accelerate curve, same feel as the old XML interpolator).
2. Idle overlay: “Tap Blue Screen” (string not renamed) in accent pink on the secondary background.
3. First tap starts the simulation; dragging along the same gesture already spawns cubes.
4. Later presses **clear the board**, then spawn and fly cubes from the new stroke.
5. Finger-up **stops spawning**; cubes already in flight keep moving.
6. Top bar **Restart?** resets simulation, telemetry holds, and the idle overlay.
7. Leaving the app (not `RESUMED`) pauses the loop and shows “Message to User”.

Cubes start moving **on create**, not on lift. One cube is requested per physics tick while the finger is down (so spawn rate is vsync, not every pointer event).

---

## Screen layout

| Region | Role |
| --- | --- |
| Material 3 `TopAppBar` | Title “Touch Move Compose”, restart action. Insets come from `Scaffold` so content sits below the status bar. |
| Full-bleed `Canvas` | Background, cubes, then telemetry HUD on top. |
| Center overlay | Shown when not running (intro wait, pause, restart). |
| HUD strip (top of canvas) | Translucent secondary scrim; accent text. Always drawn last so cubes never cover it. |

HUD lines:

- Cube count, how many were actually drawn this frame, draw count, touch samples
- FPS (0.5s average), last frame ms, jank count, display refresh Hz
- Process CPU as **% of all cores**, UI-thread CPU as **% of one core**
- Java heap used/max, native heap
- PSS (whole-process RAM), thermal status, budget label (`Budget ok` / `Hold spawn` / `LOD`)

---

## Miku theme

Defined in `ui/theme`. Dynamic (wallpaper) color is **off** so the Miku tokens always win.

| Token | Color | Used for |
| --- | --- | --- |
| Primary | `#39C5BB` teal | Cube faces (lighter top, darker side/edges derived from primary) |
| Secondary | `#102826` night (dark) / `#D4FFFB` mint (light) | Canvas background, HUD scrim |
| Tertiary / accent | `#FF5AAD` pink | HUD, overlay, telemetry text |

`TouchMoveScreen` pushes `MaterialTheme.colorScheme` into the engine each frame via `applyPalette`. App bar / buttons follow the same scheme. Canvas HUD uses native `Paint`, not Compose `Text`.

---

## Architecture

Single `app` module. No XML layouts, no `SurfaceView`. Compose owns the window; a small engine owns cubes and drawing.

```
MainActivity
  └─ TouchMoveComposeTheme
       └─ TouchMoveScreen
            ├─ withFrameNanos loop
            ├─ PerformanceTelemetry.onFrame
            ├─ TouchMoveEngine.updatePhysics
            └─ Canvas → TouchMoveEngine.draw → CubeBatch
```

### `MainActivity.kt`

`ComponentActivity`. `enableEdgeToEdge()` for API &lt; 35; target 37 is already edge-to-edge. `setContent` wraps `TouchMoveScreen` in the Miku theme.

### `TouchMoveScreen.kt`

UI shell:

- Intro `Animatable` scale 0 → 1 over 1500ms
- `LocalLifecycleOwner`: if not resumed, pause engine and stop the frame loop
- Otherwise an infinite `withFrameNanos`: sample telemetry, tick physics if running, bump `frameNanos` so `Canvas` redraws even when other state is unchanged
- `pointerInput` + `awaitEachGesture`: first down either resumes from overlay (and still drags) or `onDown` (clear + spawn); `drag` → `onMove`; end → `onUp`
- Gestures ignored until intro finishes
- `onSizeChanged` reports canvas pixels to the engine

### `TouchMoveEngine.kt`

Simulation. Pointer handlers **only set flags**. Each vsync:

1. If `clearCubes`: wipe list, `telemetry.onSceneCleared()` (unlocks spawn hold / LOD)
2. If `createCube` and not `holdSpawn`: spawn one cube at the last pointer position
3. Step **every** cube one pixel along its path

Spawn size: random fraction (`CUBE_RATIO` 0.2) of the shortest distance to a screen edge, so cubes stay on-canvas. Each `MovingCube` gets a `PlotPoints` polyline from its origin to a random destination.

Draw: fill background → in-place sort (painter’s algorithm) → `CubeBatch` → HUD scrim + text. HUD text size is 40px.

### `CubeGraphics.kt` (`CubeBatch`)

Isometric cube as three quads (top / right / front) plus nine edges. **Not** a 3D scene: depth is a 2D offset.

All cubes for a frame are packed into reused `FloatArray` / `IntArray` buffers, then:

- one `Canvas.drawVertices` (filled triangles, per-vertex colors)
- one `Canvas.drawLines` (edges)

That is why hundreds of cubes stay near 60fps: two native draws per frame instead of six Compose `Path`s per cube. Hardware canvas needs a 1×1 white `BitmapShader` or vertex colors are ignored.

### `PlotPoints.kt`

Pixel-step path from A to B (cardinal + diagonal). Diagonals step the long axis and interpolate the short one — **not** Bresenham — so the original flight feel is preserved. Allocated once per cube at spawn; each frame only advances an index.

### `PerformanceTelemetry.kt`

Sampled from the same vsync timestamps:

| Metric | How | Cadence |
| --- | --- | --- |
| FPS | Frames / wall time | 500ms window |
| Frame ms | Delta between `withFrameNanos` | Every frame |
| Jank | Frame longer than 1.5× display budget | Every frame |
| Refresh Hz | `Display.refreshRate` | Start / reset |
| Process CPU | `Process.getElapsedCpuTime()` / wall / **core count** | 500ms |
| UI CPU | `Debug.threadCpuTimeNanos()` / wall (one core) | 500ms |
| Java heap | `Runtime` used vs max | 500ms |
| Native heap | `Debug.getNativeHeapAllocatedSize()` | 500ms |
| PSS | `Debug.getMemoryInfo` (can hitch) | 2s |
| Thermal | `PowerManager.currentThermalStatus` (API 29+) | 500ms |

**CPU reading:** 100% process CPU means the **whole chip** is busy, not one core. FPS usually drops when **UI** CPU nears 100% (the thread that draws), not when process CPU is modest on an 8-core phone.

### Frame budget (keep ~55 FPS)

Target floor: 55 FPS.

- FPS **below 55** → `Hold spawn` (no new cubes) and `LOD` (skip cubes smaller than 18px)
- FPS **at/above 55** → spawn and draw all sizes
- A new press **clears** hold/LOD immediately so a 60Hz phone cannot get stuck (an older resume-at-62Hz hysteresis never released on 60Hz panels; Restart was the only way out)

This is still a Compose `Canvas` on the UI thread. Unity/Filament stay high FPS with GPU instancing and a render thread; we batch instead. Past a few thousand cubes the UI thread will still miss vsync.

### Theme files

- `Color.kt` — Miku teal / mint / ink / night / accent pink (plus leftover board-game tokens unused by this screen)
- `Theme.kt` — light + dark Material 3 schemes; `dynamicColor` default false
- `Type.kt` — Material `bodyLarge`; HUD is native Paint, not this type scale

### Tests

Default template unit + instrumentation tests only (`ExampleUnitTest`, `ExampleInstrumentedTest`). No engine tests yet.

---

## Typical frame (while dragging)

1. Vsync → `telemetry.onFrame(nanos)`
2. `updatePhysics`: maybe clear, maybe spawn, always step cubes
3. Compose invalidates `Canvas`
4. `draw`: background, batched cubes, HUD
5. Repeat

Pointer `onMove` only sets `createCube` + last XY; the engine applies at most **one spawn per frame**.

---

## Build and run

Android Studio (this machine is Windows) or Gradle:

```bat
gradlew.bat assembleDebug
gradlew.bat installDebug
```

Unix:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

---

## What this project is for

A small reference for:

- Compose pointer gestures and a vsync game loop without SurfaceView
- Immediate-mode canvas vs batched `drawVertices`
- Live FPS / CPU / memory HUD
- Simple quality scaling (hold spawn / skip small cubes) across phones
- Edge-to-edge + `Scaffold` insets on targetSdk 37

It is **not** a 3D engine. Cubes are shaded isometric 2D. A Filament/OpenGL path would be the next step if you need many thousands of cubes at 120Hz.

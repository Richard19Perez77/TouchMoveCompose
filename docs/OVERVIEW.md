# TouchMoveCompose — detailed overview

Companion to the short GitHub [README](../README.md). This document is the full map of the app as it exists in source: UI, simulation, drawing, telemetry, theme, and limits.

## 1. What the app is

TouchMoveCompose is a single-activity Android app. A finger drag samples points on a Compose `Canvas`. Each physics tick can spawn one isometric cube at the latest point. Cubes immediately walk a precomputed pixel path toward a random destination.

It started as a port of a `SurfaceView` + XML interpolator demo (squares, hairline stroke, date HUD). The current app keeps the same tick/flag style and `PlotPoints` flight, but:

- squares → shaded isometric cubes
- path draws → one batched mesh per frame
- blue/white/red → Miku primary / secondary / accent
- date HUD → live performance telemetry
- move-on-finger-up → move-on-create
- spawn/clear/disperse as mutually exclusive if-else → clear, then spawn, then always step

There is **no** XML content layout, **no** `SurfaceView`, **no** GLSurfaceView, **no** Filament. Compose owns the window. A small engine owns cube state. Native Android `Canvas` draws the mesh.

**IDs:** application `com.rick.touchmovecompose`, version `1.0` (`versionCode` 1).  
**SDK:** `minSdk` 24, `targetSdk` 37, `compileSdk` 37, Java 17.  
**UI:** Jetpack Compose, Material 3, edge-to-edge.

---

## 2. Source tree

```
app/src/main/
  AndroidManifest.xml          launcher activity, e2e-friendly theme
  java/com/rick/touchmovecompose/
    MainActivity.kt            setContent + enableEdgeToEdge
    TouchMoveScreen.kt         scaffold, intro, gestures, vsync
    TouchMoveEngine.kt         flags, spawn, step, HUD
    CubeGraphics.kt            CubeBatch mesh
    PlotPoints.kt              octant pixel paths
    PerformanceTelemetry.kt    FPS / CPU / memory / thermal / budget
    ui/theme/
      Color.kt                 Miku swatches
      Theme.kt                 light/dark Material schemes
      Type.kt                  Material bodyLarge (HUD is Paint)
  res/values/
    strings.xml                title, restart, tap overlay, pause overlay
    themes.xml                 Theme.Material.Light.NoActionBar
```

Tests are still the Android Studio templates (`ExampleUnitTest`, `ExampleInstrumentedTest`). There are no engine or telemetry tests.

---

## 3. Process and window

`MainActivity` is the only activity (`MAIN` / `LAUNCHER`). `enableEdgeToEdge()` makes system bars transparent on API 24–34; API 35+ already draws edge-to-edge when targeting 35+.

`Scaffold` consumes `innerPadding` so the top app bar sits below the status bar and the canvas sits above the nav bar. The window still draws into the bar regions. Manifest `windowSoftInputMode=adjustResize` is leftover from the template (no IME on this screen).

Theme XML parent is `android:Theme.Material.Light.NoActionBar`. Compose `TouchMoveComposeTheme` is what you actually see.

---

## 4. Screen (`TouchMoveScreen`)

### Chrome

- `TopAppBar` title: “Touch Move Compose”
- Action: “Restart?” → `engine.restart` + `telemetry.reset()`
- Overlay `Text` 28sp, accent (`tertiary`), centered, only when `overlayVisible`

### Intro

`Animatable` scale 0 → 1 in 1500ms with `t²` easing (same curve as the old XML accelerate interpolator). Pointer gestures are ignored until that finishes (`introFinished`).

The whole canvas+overlay `Box` is scaled, including the HUD, during intro.

### Palette hook

`SideEffect` copies Material tokens into the engine every composition:

| Scheme token | Engine use |
| --- | --- |
| `secondary` | canvas background + HUD scrim (88% alpha) |
| `primary` | cube fill (faces shaded from it) |
| `tertiary` | HUD `Paint` color and overlay text |

Wallpaper/dynamic color is **disabled** (`dynamicColor = false`) so Miku tokens always apply.

### Vsync loop

`LaunchedEffect(isResumed, engine)`:

- If lifecycle is not at least `RESUMED`: `pauseFromLifecycle("Message to User")` and return (loop stops).
- Else: forever `withFrameNanos`:
  1. `telemetry.onFrame(nanos)` (always, even when paused overlay is showing — after resume; while not resumed the effect has exited)
  2. If `engine.isRunning`, `updatePhysics(telemetry)`
  3. `frameNanos = nanos` so `Canvas` redraws when nothing else changed

While the overlay is up, `isRunning` is false, so physics does not tick, but after the first tap it does. Telemetry still runs whenever the activity is resumed.

### Gestures (`pointerInput` + `awaitEachGesture`)

| Situation | Down | Move | Up |
| --- | --- | --- | --- |
| Intro not finished | ignored | — | — |
| Overlay / not running | `resumeFromTap()` (sets `touchingScreen`) then same drag as a stroke | `onMove` | `onUp` |
| Running | `onDown` (clear + spawn + reset draw/touch counters) | `onMove` | `onUp` |

`onMove` only stores XY and sets `createCube`. The engine spawns **at most one cube per vsync**, not one per pointer event.

First stroke after idle does **not** clear (board is already empty). Later strokes clear, then spawn.

`onUp` stops spawning (`touchingScreen = false`). Cubes already in the list keep stepping.

---

## 5. Engine (`TouchMoveEngine`)

Pointer code never mutates the cube list. It sets flags; `updatePhysics` consumes them on the UI thread.

### Flags

| Flag | Set by | Meaning |
| --- | --- | --- |
| `createCube` | `onDown`, `onMove` | spawn once this tick |
| `clearCubes` | `onDown` | wipe list this tick |
| `touchingScreen` | `onDown` / `resumeFromTap` true; `onUp` false | `spawnCube` no-ops if false |

### Physics order (same tick)

1. If `clearCubes`: `cubes.clear()`, `telemetry.onSceneCleared()` (drops Hold spawn / LOD so a 60Hz phone can draw again immediately)
2. If `createCube` and not `telemetry.holdSpawn`: `spawnCube()`
3. For every cube: `updatePoint()` (one pixel along its path)

### Spawn size

Half-extent `buffer` is `random * CUBE_RATIO (0.2) * min(distance to left, right, top, bottom)`. Cube is axis-aligned in 2D (`left/top/right/bottom`); drawn size is `min(width, height)`. Near edges, cubes are tiny; near center they can be large.

If `screenW/H` is still 0 (first frames before `onSizeChanged`), destinations and clamps are wrong — size is set as soon as the canvas lays out.

### `MovingCube`

On construct: `PlotPoints.plotLine(origin, random point in screen)`. Each step copies the next `Point` into `left/top` and preserves width/height. Stops at end of array. Points with `x <= 0` or `y <= 0` are skipped (legacy guard).

### Draw order

1. Fill `screenColor`
2. `cubes.sortBy { top + left }` (in-place painter’s algorithm)
3. `CubeBatch.begin()`; add cubes with `size >= minSize` (`1` or `LOD_MIN_SIZE` 18px)
4. `cubeBatch.draw(nativeCanvas)`
5. HUD scrim full width × five lines, then native `drawText` (size **40px**, accent)

HUD is drawn last so cubes cannot cover it.

### Counters

- `drawCounter`: increments every `draw`, including overlay frames; reset on `onDown` and `restart`
- `touchCounter`: increments on `recordTouch` and `onUp`; reset on `onDown` and `restart`
- HUD `Cubes: N (M drawn)`: `N` is live list size; `M` is how many passed LOD and entered the batch

`restart` also clears flags, cubes, overlay message “Tap To Start”, `isRunning = false`. It does not reset telemetry (the button in the screen does that separately).

---

## 6. Cube mesh (`CubeBatch`)

Not 3D. Front face is the 2D square. Depth is `size * 0.45`, offset by isometric `dx = depth * 0.72`, `dy = depth * 0.42` (up-right). Three quads: top, right, front. Nine visible edges as line segments.

Per cube in the CPU buffers:

- 3 quads × 2 triangles × 3 vertices = **18 vertices** = **36 floats**
- **18** packed ARGB colors (constant per face)
- **9** lines × 4 floats = **36** line floats

Buffers start at `36 * 64` floats and double when full. `begin()` only resets write cursors (no realloc).

`draw()`:

- `Canvas.drawVertices(TRIANGLES, …)` with per-vertex colors
- `Canvas.drawLines(…)` stroke 1.5px

Fill `Paint` uses a 1×1 white `BitmapShader`. Hardware-accelerated Android Canvas **ignores vertex colors** without a shader; colors then multiply with white.

`setPrimaryColor` derives:

- front = primary
- top = 40% toward white
- right = 28% toward black
- edge = 48% toward black

**Why FPS used to cliff at ~400 cubes:** six Compose `Path` allocations + draws per cube on the UI thread (~2400 submissions). Game engines instance one mesh. This batch is the 2D equivalent: two submissions per frame. Past several thousand cubes the UI thread still misses vsync; Filament/OpenGL would be the next step.

---

## 7. Paths (`PlotPoints`)

Shared helper (one instance on the engine). `plotLine(A, B)` fills `pointArr` with integer steps.

Octants: up, down, left, right, and four diagonals. Cardinal: one axis fixed. Diagonal: if `|dx| == |dy|`, lockstep; else walk the longer axis one pixel at a time and interpolate the shorter with a float accumulator + `Math.round`. That is **not** Bresenham (no error term), which is deliberate so motion matches the original demo.

Cost is **at spawn** (large arrays for long flights), not per frame. Stepping is an index increment.

The helper is not thread-safe; only the UI vsync uses it.

---

## 8. Telemetry (`PerformanceTelemetry`)

Constructed with `Context` (display + `PowerManager`). `onFrame` is called every vsync while resumed.

### Cadence

| Item | When |
| --- | --- |
| `frameMs`, jank | every frame |
| FPS, process CPU, UI CPU, heap, native, thermal, budget flags | every **500ms** |
| PSS (`Debug.getMemoryInfo`) | every **2s** (can hitch the UI thread) |

FPS is `framesInWindow * 1000 / windowMs`, not `1000 / lastFrameMs`. First 500ms FPS reads `0` until the window closes.

Jank: `dtNs > 1.5 * (1e9 / refreshHz)`. Refresh comes from `Display.refreshRate` (API 30+ `context.display`, else deprecated default display), fallback 60.

### CPU math

- **Process %** = `Δ Process.getElapsedCpuTime() / Δ wall / cores * 100`  
  100% ≈ all cores busy. Can exceed 100% slightly due to accounting. Older HUD treated one core as 100%, so values like 200% on 8 cores looked like “over 100%” while FPS was still fine.
- **UI %** = `Δ Debug.threadCpuTimeNanos()` / wall * 100  
  One core (the thread that runs composition + our draw). This is the number that predicts FPS drop.

### Memory

- Heap used = `totalMemory - freeMemory` (Java heap, not the whole process)
- Heap max = `maxMemory`
- Native = `Debug.getNativeHeapAllocatedSize()`
- PSS = `MemoryInfo.totalPss` in MB (Java + native + proportional shared)

### Thermal (API 29+)

`PowerManager.currentThermalStatus` mapped to `none` … `shutdown`. Below Q: `n/a`.

### Budget (55 FPS floor)

`TARGET_FPS = 55`. After each 500ms sample:

- `fps <= 0` → no hold (startup)
- `fps < 55` → `holdSpawn` and `skipSmallCubes` (`LOD`, skip size &lt; 18px)
- else → both off

HUD label: `Budget ok` / `Hold spawn` / `LOD` (LOD wins if both).

`onSceneCleared()` and `reset()` force both flags off. Needed because a previous design resumed spawn only at **62 FPS**, which a **60Hz** panel never reaches: clear still worked, spawn did not, until Restart.

Hold spawn only **stops new cubes**. Existing cubes still step and draw (unless LOD hides the small ones). Clearing the board is what recovers FPS.

`reset()` (Restart) also zeros FPS, jank, and sample clocks.

---

## 9. Theme

### Swatches (`Color.kt`)

| Name | Hex | Role |
| --- | --- | --- |
| `MikuTeal` | `#39C5BB` | official Miku teal, Material primary |
| `MikuTealLight` / `Dark` | `#7AEEE6` / `#137A74` | containers / outlines |
| `MikuMint` | `#D4FFFB` | light secondary / background |
| `MikuInk` / `Night` | `#0B1C1B` / `#102826` | dark ink / canvas night |
| `MikuAccent` | `#FF5AAD` | ribbon pink, Material tertiary |
| `MikuGrey` / `White` | `#B8C4C3` / `#F4FFFD` | onSurface variants |

### Schemes (`Theme.kt`)

Dark: primary teal, secondary **night** (canvas), tertiary accent, surface night.  
Light: primary teal, secondary **mint** (canvas), tertiary accent, surface white.

Follows system dark/light. `Type.kt` `bodyLarge` 16sp is for app bar / Material text. Canvas HUD is not this type scale.

---

## 10. Strings

| Resource | Value | Where |
| --- | --- | --- |
| `app_name` | TouchMoveCompose | launcher |
| `title_touch_move` | Touch Move Compose | app bar |
| `action_restart` | Restart? | app bar |
| `tap_to_start` | Tap To Start | idle overlay |
| `message_text` | Message to User | paused overlay |

---

## 11. Frame while dragging (running)

```
vsync
  telemetry.onFrame
  updatePhysics
    optional clear + unlock budget
    optional one spawn
    step all cubes
  Compose Canvas
    draw background
    sort + fill CubeBatch
    2 native draws
    HUD
```

Physics does not run when `isRunning` is false (idle/pause overlay). Draw still runs so the HUD and empty (or last) scene refresh.

---

## 12. Limits and non-goals

- **Not 3D.** No camera, lighting, or GPU instancing.
- **UI thread bound.** Batching removes per-cube path overhead; sort + vertex fill still scale with cube count.
- **One spawn per frame** while dragging (vsync rate, not touch sample rate).
- **LOD hides small cubes** when FPS &lt; 55; it does not delete them. `Cubes` vs `drawn` will disagree.
- **PSS sample** can hitch every 2s.
- **No persistence**, accounts, or network.
- **No real tests** for engine/telemetry.

A Filament or OpenGL scene would be the path to many thousands of cubes at 120Hz. Quality knobs already in place (hold spawn, LOD) are the Compose-canvas way to stay near 55 FPS across phones.

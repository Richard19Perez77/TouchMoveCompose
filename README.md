# TouchMoveCompose

TouchMoveCompose is an Android app built with Jetpack Compose that turns a touch interaction into an animated visual effect. The app fills the screen with a blue canvas, lets the user tap and drag to create moving squares, and shows overlay status messages while the app is paused or restarted.

## What the app does

- Displays a full-screen Compose interface with a Material 3 theme.
- Starts in an intro animation and then waits for user input.
- Tracks touch gestures on the screen.
- Spawns square objects when the user touches and drags.
- Moves each square along a computed line to a random destination.
- Updates the scene every frame using Compose animation callbacks.
- Shows a simple HUD with date, square count, draw count, and touch count.
- Supports restart and lifecycle-aware pause/resume behavior.

## Project architecture

The project follows a small Android app structure centered around the `app` module.

### Main entry point

- `MainActivity.kt`  
  Hosts the app and sets the Compose content view. It initializes the theme and renders `TouchMoveScreen`.

### Screen and UI layer

- `TouchMoveScreen.kt`  
  Contains the main screen composable. It handles:
  - the intro scale animation
  - lifecycle state checks
  - frame-by-frame physics updates
  - touch event handling with pointer input
  - the top app bar and restart action
  - overlay text display

### Game / simulation logic

- `TouchMoveEngine.kt`  
  Contains the core simulation logic for the touch-driven animation. It manages:
  - running state and overlays
  - on-screen size setup
  - touch recording
  - spawning and clearing squares
  - the update loop for square motion
  - drawing the background and HUD

- `PlotPoints.kt`  
  Computes a path between two points so squares can travel smoothly across the screen. It calculates arrays of intermediate positions for straight, diagonal, and directional movement.

### Theme package

- `ui/theme/Color.kt`  
  Defines the default color palette.

- `ui/theme/Theme.kt`  
  Configures the Material 3 theme and dynamic color behavior.

- `ui/theme/Type.kt`  
  Defines the baseline typography used by the Material theme.

### Tests

- `app/src/test/.../ExampleUnitTest.kt`  
  A default local unit test example.

- `app/src/androidTest/.../ExampleInstrumentedTest.kt`  
  A default instrumentation test example.

## Typical interaction flow

1. The app launches and shows the intro animation.
2. The user touches the screen.
3. The engine records the touch and spawns a square.
4. The square is assigned a random destination.
5. A line of intermediate points is generated.
6. Each frame, the engine advances the square along that path.
7. A restart button clears the visual state and resets the simulation.

## Build and run

Use Android Studio or the Gradle wrapper to run the app on an emulator or a connected device.

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

## Notes

This project is a lightweight visual experiment that combines Compose UI, touch input, and a custom animation loop instead of relying on a traditional game engine. It is useful as a reference for handling pointer gestures, per-frame updates, and custom drawing in Jetpack Compose.


# JankDetector

An in-app **Jank & Dropped Frame detector** for Jetpack Compose. Wraps any screen with a floating HUD that shows live FPS, jank frame count, dropped frame count, and — most importantly — **which components caused the jank**.

All events are also printed to **ADB logcat** under the tag `JankDetector`.

---

## What it detects

| Metric | Description |
|---|---|
| **FPS** | Frames per second, recalculated every second |
| **Jank frames** | Frames that took longer than 17ms (below 60 FPS threshold) |
| **Dropped frames** | Estimated skipped frames per long frame (e.g. 32ms ≈ 1 dropped) |
| **Culprit components** | Named components that were recomposing during a jank frame |

---

## Installation

### Option A — Local Maven (recommended for same-machine projects)

**Step 1:** Publish the library to your local Maven repository.

```bash
# In the JankDetector project folder:
./gradlew :jankdetector:publishToMavenLocal
```

**Step 2:** Add `mavenLocal()` to your app's `settings.gradle.kts`.

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal() // ← add this before google()
        google()
        mavenCentral()
    }
}
```

**Step 3:** Add the dependency to your `app/build.gradle.kts`.

```kotlin
dependencies {
    debugImplementation("vang.truong:jankdetector:1.0.0")
}
```

> **Tip:** Use `debugImplementation` so the overlay is only included in debug builds and never ships to production.

---

### Option B — AAR file (easiest for sharing)

Copy `jankdetector-1.0.0.aar` from the `release/` folder into your app's `app/libs/` directory, then add to `app/build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation(files("libs/jankdetector-1.0.0.aar"))

    // Required transitive dependencies:
    debugImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    debugImplementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.material3:material3")
    debugImplementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
}
```

---

### Option C — Composite build (live source, for development)

If you have both projects on the same machine and want changes in JankDetector to reflect instantly without re-publishing:

```kotlin
// settings.gradle.kts
includeBuild("../JankDetector") {
    dependencySubstitution {
        substitute(module("vang.truong:jankdetector:1.0.0")).using(project(":jankdetector"))
    }
}
```

```kotlin
// app/build.gradle.kts
debugImplementation("vang.truong:jankdetector:1.0.0")
```

---

## Basic usage

Wrap any screen with `JankOverlay`. That's it.

```kotlin
import vang.truong.jankdetector.JankOverlay

setContent {
    JankOverlay {
        YourScreen()
    }
}
```

The floating HUD appears in the top-right corner of the screen.

---

## Deep detection — identify specific components

By default, the overlay shows FPS and total jank counts. To see **which components** inside `YourScreen` are causing the jank, tag them with `TrackJank`.

### Option A — wrapper composable

```kotlin
import vang.truong.jankdetector.TrackJank

JankOverlay {
    LazyColumn {
        items(list) { item ->
            TrackJank("ProductCard") {
                ProductCard(item) // ← recompositions of this will be tracked
            }
        }
    }
}
```

### Option B — modifier

```kotlin
import vang.truong.jankdetector.trackJank

Box(
    modifier = Modifier
        .trackJank("HeavyHeader")
        .fillMaxWidth()
) {
    HeavyHeader()
}
```

The name you provide (`"ProductCard"`, `"HeavyHeader"`, etc.) will appear in the HUD and in logcat when that component recomposes during a jank frame.

---

## HUD — how to read it

The HUD has 3 modes. **Tap it** to cycle between them.

```
┌──────────────────┐
│  38 FPS          │  ← red = below 40, yellow = 40-54, green = 55+
│  Jank frames  12 │
│  Dropped frames 8│
│  Last frame  28ms│
│  tap for comps   │
└──────────────────┘
```

After one more tap, the component culprit list expands:

```
┌──────────────────────────────────┐
│  38 FPS                          │
│  Jank frames              12     │
│  Dropped frames            8     │
│  Last frame              28ms    │
│  ────────────────────────────    │
│  Jank culprits          reset    │
│  component        hits   last ms │
│  ▌ BadShimmerItem   31    28ms   │  ← red bar = high severity
│  ▌ BadProgressBar   12    22ms   │  ← yellow bar = lower severity
│  tap to collapse                 │
└──────────────────────────────────┘
```

| Severity bar | Meaning |
|---|---|
| 🟡 Yellow | Low occurrence count |
| 🔴 Red | High occurrence count — primary suspect |

---

## ADB Logcat

All events are logged to logcat under the tag `JankDetector`. Filter with:

```bash
adb logcat -s JankDetector
```

### Log output examples

**Component registered** — logged once per unique name, on first recomposition:
```
D/JankDetector: [INIT] Tracking component: "BadShimmerItem"  ←  vang.truong.janks.bad.BadDemoScreen$lambda$1$1.invoke(BadDemoScreen.kt:74)
D/JankDetector: [INIT] Tracking component: "BadProgressBar"  ←  vang.truong.janks.bad.BadDemoScreen$lambda$2.invoke(BadDemoScreen.kt:58)
```

**FPS summary** — logged every second:
```
I/JankDetector: [FPS] ✅ 59 fps  |  jank frames: 0  |  dropped: 0
I/JankDetector: [FPS] ⚠️ 42 fps  |  jank frames: 5  |  dropped: 3
I/JankDetector: [FPS] 🔴 29 fps  |  jank frames: 18  |  dropped: 12
```

**Jank frame with culprits** — WARN for 1 dropped frame, ERROR for 2+:
```
W/JankDetector: [JANK] 28ms frame (~1 dropped frames)
                       Recomposing components during this frame:
                       ├─ "BadShimmerItem"  recomposed 6x
                       │     vang.truong.janks.bad.BadDemoScreen$lambda$1$1.invoke(BadDemoScreen.kt:74)
                       ├─ "BadProgressBar"  recomposed 3x
                       │     vang.truong.janks.bad.BadDemoScreen$lambda$2.invoke(BadDemoScreen.kt:58)
                       └─ Total tracked: 2 component(s)
```

**Jank without tracked components:**
```
W/JankDetector: [JANK] 23ms frame (~1 dropped) — add TrackJank{} inside JankOverlay to identify culprits
```

**Lifecycle events:**
```
D/JankDetector: [START]  JankDetector attached.
D/JankDetector: [PAUSE]  Stopping Choreographer chain.
D/JankDetector: [RESUME] Restarting Choreographer chain.
D/JankDetector: [STOP]   JankDetector detached.
```

---

## API reference

### `JankOverlay`

```kotlin
@Composable
fun JankOverlay(content: @Composable () -> Unit)
```

Wraps `content` with a floating HUD. Provides `LocalJankReporter` to all children so `TrackJank` and `Modifier.trackJank` work anywhere inside.

---

### `TrackJank`

```kotlin
@Composable
fun TrackJank(name: String, content: @Composable () -> Unit)
```

Reports every recomposition of `content` to the nearest `JankOverlay`. The `name` is used in both the HUD and logcat output.

---

### `Modifier.trackJank`

```kotlin
fun Modifier.trackJank(name: String): Modifier
```

Modifier version of `TrackJank`. Attach to any composable to track its recompositions.

---

### `rememberJankDetector`

```kotlin
@Composable
fun rememberJankDetector(
    reporter: JankReporter? = null,
    onJankFrame: ((frameDurationMs: Long) -> Unit)? = null
): JankStats
```

Low-level API. Registers a `Choreographer.FrameCallback` and returns live `JankStats`. Useful if you want to build a custom UI instead of using `JankOverlay`.

---

## How it works

```
Choreographer.FrameCallback
        │
        │  fires every vsync (~16.67ms at 60fps)
        ▼
   measure frame duration
        │
        ├── duration > 17ms → JANK detected
        │       │
        │       └── check which TrackJank components recomposed
        │               → log to logcat
        │               → update HUD overlay
        │
        └── every 1 second → calculate FPS
                             → log FPS summary
```

**Lifecycle awareness:** When the app goes to background, vsync stops and the Choreographer chain breaks. `JankDetector` uses a `LifecycleEventObserver` to restart the chain and reset the frame baseline on `ON_RESUME` — so jank during the app's initial re-render after resume is correctly detected.

---

## Requirements

| | Minimum |
|---|---|
| Android minSdk | 26 |
| Kotlin | 2.0+ |
| Jetpack Compose BOM | 2024.09.00+ |
| lifecycle-runtime-compose | 2.10.0+ |

---

## Project structure

```
JankDetector/
├── jankdetector/
│   └── src/main/java/vang/truong/jankdetector/
│       ├── JankStats.kt        — live FPS/jank counters (Compose observable state)
│       ├── JankDetector.kt     — Choreographer frame timing engine + lifecycle awareness
│       ├── JankReporter.kt     — component recomposition tracker + logcat output
│       ├── TrackJank.kt        — public API: TrackJank{} composable + Modifier.trackJank()
│       └── JankOverlay.kt      — floating HUD overlay
└── release/
    └── jankdetector-1.0.0.aar  — pre-built AAR for direct embedding
```

---

## License

MIT

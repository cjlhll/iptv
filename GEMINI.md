# IPTV 电视端播放器 (IPTV TV Player)

## Project Overview

This is an Android TV application designed to play IPTV live streams. It focuses on a remote-friendly interface, high performance, and simplicity. The app allows users to configure live sources (m3u/txt) and EPG sources, manage favorites, and switch channels easily.

**Key Features:**
-   **Live Streaming:** Supports m3u/txt playlist formats.
-   **EPG Support:** Electronic Program Guide integration.
-   **Remote Control Friendly:** Optimized for D-pad navigation.
-   **Favorites:** Users can mark channels as favorites.
-   **Minimalist UI:** Designed for viewing from a distance (2-5 meters).

## Tech Stack

-   **Language:** Kotlin
-   **UI Toolkit:** Jetpack Compose (Material3 & TV Foundation)
-   **Video Player:** GSYVideoPlayer (wrapping ExoPlayer/IJKPlayer)
-   **Networking:** OkHttp
-   **Local Storage:** Room Database (implied by schema structure) & SharedPreferences (via `Prefs.kt`)
-   **Build System:** Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`)

## Architecture & Key Components

The project follows a standard Android structure, likely MVVM (Model-View-ViewModel), although specific ViewModels were not inspected in detail.

### Key Files

-   **`app/src/main/java/com/cjlhll/iptv/SplashActivity.kt`**:
    -   The application entry point.
    -   Checks if a live source is configured in `Prefs`.
    -   Redirects to `PlayerActivity` if configured and a last played channel exists, otherwise to `MainActivity` for configuration.

-   **`app/src/main/java/com/cjlhll/iptv/PlayerActivity.kt`**:
    -   The main video playback screen.
    -   Uses `StandardGSYVideoPlayer` wrapped in an `AndroidView` for Compose interoperability.
    -   Handles full-screen playback and provides a settings button to return to configuration.

-   **`app/src/main/java/com/cjlhll/iptv/MainActivity.kt`**:
    -   Serves as the configuration/settings screen where users input source URLs (Live Source, EPG Source).

-   **`app/src/main/java/com/cjlhll/iptv/Prefs.kt`**:
    -   A singleton object for managing `SharedPreferences`.
    -   Stores `live_source`, `epg_source`, and details of the last played channel (`url`, `title`).

-   **`iptv_电视端播放器开发文档.md`**:
    -   Comprehensive design document outlining the project goals, UI/UX design, focus interactions, and future roadmap. **Consult this for detailed requirements.**

## Build & Run

To build and install the debug version of the app:

```bash
./gradlew assembleDebug
# Or to install directly to a connected device
./gradlew installDebug
```

## Development Conventions

-   **Jetpack Compose:** The UI is built using Jetpack Compose. Prioritize standard Compose components and `androidx.tv` libraries for TV interfaces.
-   **Focus Management:** Critical for TV apps. Ensure all interactive elements are focusable and handle D-pad navigation correctly. The design doc emphasizes "Focus State Persistence".
-   **Dependencies:** Managed via `gradle/libs.versions.toml`. Add new dependencies there first.

## Note on Missing Files

During initial inspection, `model/` and `utils/` directories appeared empty in some listings despite being present in the structure. Verify the existence of `Channel.kt` and `M3UParser.kt` if working on data parsing logic.

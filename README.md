# 📱 Film Atlas

![Android](https://img.shields.io/badge/Android-App-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)
![Database](https://img.shields.io/badge/Database-Room-green)
![Networking](https://img.shields.io/badge/Networking-Retrofit-orange)

Film Atlas is a movie discovery app for Android focused on recent U.S. theatrical releases.

It is designed for exploring what’s new and popular, learning more about each movie, and quickly finding where to watch or learn more.

## Screenshots

<p align="center">
  <img src="docs/screenshots/discover.png" alt="Discover screen showing randomized movie grid" width="30%" />
  <img src="docs/screenshots/movie-details.png" alt="Movie details dialog showing The Dark Knight" width="30%" />
</p>

<p align="center">
  Discover • Movie Details
</p>

---

## ✨ Features

- Browse **New**, **Popular**, and **Discover** movie categories  
- Results filtered to **U.S. releases** for better relevance  
- Detailed movie view including:
  - Posters and backdrop images
  - Ratings and release information
  - Trailers
  - External links to TMDB
- Fully responsive layouts for **portrait and landscape**
- **Light and dark theme** support using Material Design 3

### Discover Mode

The Discover tab surfaces randomized movie selections from the TMDB catalog.

The goal is to encourage exploration — similar to opening a dictionary to a random page and discovering whatever words appear there, but applied to movies.

---

## 🛠 Tech Stack

- Java
- Android SDK
- MVVM architecture
- Room database
- Retrofit (TMDB API)
- Gson (JSON serialization)
- LiveData & ViewModel
- Material Design 3
- Glide

---

## 🔍 Debugging & Lifecycle Case Studies

Real-world debugging and architecture work from Film Atlas development.

### Lifecycle State Restoration Across Activity Recreation

**Problem**

During Activity recreation (e.g., rotation or process death), UI state could restore inconsistently.  
This affected the movie dataset, RecyclerView scroll position, search state, and filter context.

In some cases, the list would appear restored visually, but paging, empty-state logic, or scroll position would behave incorrectly.

---

**Root Cause**

Restore logic was distributed across multiple lifecycle entry points (`onCreate`, observers, and mode transitions) and relied on a set of loosely coordinated flags.

Critically, restoration depended on **timing between three independent concerns**:
- dataset submission (`submitList`)
- RecyclerView layout pass
- ViewModel/UI mode state (`movieFilterApplied`, `DisplayMode`)

Because these were not synchronized through a single contract, small ordering differences could result in:
- scroll restoration being ignored or snapping to top
- incorrect empty-state visibility
- paging being blocked despite visible data

---

**Solution**

Refactored restoration into a **validated, contract-driven flow**:

- Introduced `FilterRestoreSnapshot` as a single source of truth for a valid restore state
- Built snapshot creators for:
    - `savedInstanceState` (rotation)
    - SharedPreferences (process/external return)
- Centralized restore application into a single `applyFilterSnapshot(...)` entry point
- Ensured invariant:  
  **if a dataset is restored, Filter mode and ViewModel state must also be restored in the same step**

To address timing issues:

- Used `submitList(..., commitCallback)` to wait for adapter dataset commit
- Used `RecyclerView.post { ... }` to restore scroll/layout state on the next UI pass

This guarantees that scroll restoration happens **after both dataset commit and layout**, avoiding race conditions.

Removed legacy fallback logic and reduced reliance on distributed restore flags in favor of an explicit restore contract.

---

**Result**

State restoration is now deterministic and consistent across lifecycle events.

- Dataset, filter state, scroll position, and UI mode are restored together
- No dependency on observer timing or callback ordering
- Eliminated scroll restoration failures and inconsistent empty-state behavior

---

### RecyclerView Scroll State Restoration

**Problem**

After device rotation, the movie grid could reset its scroll position even when the dataset itself was restored correctly.

**Root Cause**

RecyclerView layout state restoration can fail if it occurs before the adapter dataset has been fully applied.  
During Activity recreation, scroll state was sometimes restored while the adapter was still empty or updating.

**Solution**

Implemented a deferred RecyclerView state restoration strategy:

• Saved `RecyclerView.LayoutManager` state during `onSaveInstanceState()`  
• Restored the movie dataset first using serialized snapshots  
• Applied scroll restoration only after the adapter finished submitting the restored dataset

**Result**

Movie grids now reliably restore their previous scroll position after rotation, ensuring that users return to the same location in the list without unexpected jumps.

---

## 📡 Data Source

Powered by **The Movie Database (TMDB)**  
This product uses the TMDB API but is not endorsed or certified by TMDB.

---

## 🚀 Getting Started

### Prerequisites

- Android Studio
- A TMDB API key

---

### TMDB API Key Setup

Film Atlas requires a TMDB API key to run.

1. Create a TMDB account and request an API key.
2. In the project root directory (same level as `app/`), open `local.properties`.
3. Add the following line:

```
TMDB_API_KEY=YOUR_API_KEY_HERE
```

`local.properties` is intentionally excluded from version control and remains local to your machine.

4. Sync Gradle files and run the app in Android Studio.

If the API key is missing, the build will fail with a clear error message.

---

## 🧪 Troubleshooting

If Android Studio shows errors after Gradle or `BuildConfig` changes, run:

```
./gradlew :app:assembleDebug
```

Then re-sync the project.

---

## 📌 Development Notes

Current development tasks and known issues are tracked in:

**[BACKLOG.md](BACKLOG.md)**
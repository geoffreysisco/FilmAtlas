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

When the device rotated or the Activity was recreated, parts of the UI state could be lost or restored inconsistently. This included the movie dataset, RecyclerView scroll position, search query state, and filter selections.

**Root Cause**

Activity recreation triggered lifecycle events that rebuilt parts of the UI while other parts attempted to restore saved state. Without coordination, restored UI state could be overwritten during the recreation process.

**Solution**

Implemented a two-stage state restoration flow:

• Serialized the movie dataset using Gson snapshots for reliable dataset restoration  
• Saved and restored RecyclerView LayoutManager state to preserve scroll position  
• Added restoration guard flags to prevent lifecycle callbacks from overriding restored UI state during Activity recreation

**Result**

Movie lists, scroll position, search state, and filter context now restore reliably across configuration changes, providing a consistent user experience after rotation or Activity recreation.

---

### Search UI State Preservation During Rotation

**Problem**

After rotating the device while in search mode, parts of the search UI could restore inconsistently.  
The search query text, clear button visibility, suggestion dropdown, and focus state could become unsynchronized.

**Root Cause**

Search UI elements were restored independently during Activity recreation.  
Without coordination, lifecycle callbacks and UI observers could trigger additional updates that conflicted with the restored search state.

**Solution**

Implemented explicit search UI restoration handling:

• Saved the search pill text and focus state during `onSaveInstanceState()`  
• Restored the search query, clear button visibility, and focus state during Activity recreation  
• Added a restoration guard (`restoringSearchUi`) to prevent text listeners and observers from triggering unintended updates during restore

**Result**

Search queries, UI focus state, and suggestion behavior now restore consistently after device rotation, maintaining a stable search experience across configuration changes.

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
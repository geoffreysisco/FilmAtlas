# 📱 Film Atlas

![Android](https://img.shields.io/badge/Android-App-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)
![Database](https://img.shields.io/badge/Database-Room-green)
![Networking](https://img.shields.io/badge/Networking-Retrofit-orange)

Film Atlas is a movie discovery app for Android focused on recent U.S. theatrical releases.

Film Atlas is a movie discovery app for Android focused on recent U.S. theatrical releases.  
It is designed for exploring what’s new and popular, learning more about each movie, and quickly finding where to watch or learn more.


## Screenshots

<p align="center">
  <img src="docs/screenshots/discover.png" alt="Discover screen showing randomized movie grid" width="30%" />
  <img src="docs/screenshots/movie-details.png" alt="Movie details dialog showing The Dark Knight" width="30%" />
  <img src="docs/screenshots/landscape.png" alt="Landscape layout of movie details view" width="30%" />
</p>

<p align="center">
  Discover • Movie Details • Landscape Layout
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
- LiveData & ViewModel
- Material Design 3
- Glide

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
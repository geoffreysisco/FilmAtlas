📱 Film Atlas

Film Atlas is a movie discovery app for Android focused on recent U.S. theatrical releases.
It’s designed for exploring what’s new and popular, learning more about each movie, and quickly finding where to watch or learn more.

✨ Features

• Browse New, Popular, and Discover movie categories
• Results filtered to U.S. releases for better relevance
• Detailed movie view including:
– Posters and backdrop images
– Ratings and release information
– Trailers
– External links to TMDB
• Fully responsive layouts for portrait and landscape
• Light and dark theme support using Material Design 3

🛠 Tech Stack

• Java
• Android SDK
• MVVM architecture
• Retrofit (TMDB API)
• LiveData & ViewModel
• Material Design 3
• Glide

📡 Data Source

Powered by The Movie Database (TMDB)
This product uses the TMDB API but is not endorsed or certified by TMDB.

🚀 Getting Started

Prerequisites

Android Studio

A TMDB API key

TMDB API Key Setup

Film Atlas requires a TMDB API key to run.

Create a TMDB account and request an API key.

In the project root directory (same level as app/), open local.properties.

Add the following line:

TMDB_API_KEY=YOUR_API_KEY_HERE


Example:

TMDB_API_KEY=abc123yourkeyhere


local.properties is intentionally excluded from version control and remains local to your machine.

Sync Gradle files and run the app in Android Studio.

If the API key is missing, the build will fail with a clear error message.

🧪 Troubleshooting

If Android Studio shows red errors after Gradle or BuildConfig changes, run:

./gradlew :app:assembleDebug


Then re-sync the project.
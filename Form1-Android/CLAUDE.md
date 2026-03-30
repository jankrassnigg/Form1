# Music Player Android App - Project Overview

## Project Description

An Android music player application that plays MP3 files from local device storage and OneDrive cloud storage. Features include playlist management, playback controls (play/pause/skip), and volume adjustment.

**App Name:** 'Form1 Music Player'
**Short Name:** 'Form1'

## Technology Stack

### Core Technologies

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose

### Key Libraries & Dependencies

#### Media Playback

- **androidx.media3 (ExoPlayer)**

#### Cloud Storage Integration

- **Microsoft Graph SDK for Android**

#### Data Persistence

- **Room Database**
- **Jetpack DataStore**

#### Permissions & Utilities

- **Accompanist Permissions**
  - Essential for file system access on Android 13+

## App Features (Planned)

### MVP Features

1. Browse and play local MP3 files
2. Basic playback controls (play/pause/skip/previous)
3. Volume control
4. Create and manage playlists
5. Background playback with notification controls

### Future Features

1. OneDrive integration for cloud music access
2. Search and filter functionality
3. Playlist shuffle and repeat modes

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/musicplayer/
│   │   │   ├── data/          # Data layer (Room, repositories)
│   │   │   ├── domain/        # Business logic
│   │   │   ├── ui/            # Compose UI components
│   │   │   │   ├── screens/   # Screen composables
│   │   │   │   ├── components/# Reusable UI components
│   │   │   │   └── theme/     # App theme
│   │   │   ├── player/        # Media player service
│   │   │   └── MainActivity.kt
│   │   ├── res/               # Resources (drawables, strings)
│   │   └── AndroidManifest.xml
│   └── test/                  # Unit tests
└── build.gradle.kts
```

## Development Guidelines

### Architecture

- **MVVM (Model-View-ViewModel)** pattern
- **Repository pattern** for data access
- **Dependency Injection** with Hilt (future consideration)

### Code Style

- Follow official Kotlin coding conventions
- Use meaningful variable and function names
- Document complex business logic
- Keep composables small and focused

## Resources & Documentation

### Official Documentation

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- [Room Database](https://developer.android.com/training/data-storage/room)

### Microsoft Graph API

- [Microsoft Graph SDK for Android](https://github.com/microsoftgraph/msgraph-sdk-android)
- [OneDrive API Documentation](https://learn.microsoft.com/en-us/onedrive/developer/)

## Notes

- Target Android API level: 34 (Android 14)
- Minimum SDK: 24 (Android 7.0) - covers ~95% of devices
- Kotlin version: Latest stable
- Gradle: Kotlin DSL

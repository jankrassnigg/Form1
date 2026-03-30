# Music Player Android App - Project Overview

## Project Description

An Android music player application that plays MP3 files from local device storage and OneDrive cloud storage. Features include playlist management, playback controls (play/pause/skip), and volume adjustment.

This is a **complete rewrite** of the Form1 PC app for Android. The PC app exists separately and the two share a common OneDrive-based user profile system (see Profile System below).

**App Name:** 'Form1 Music Player'
**Short Name:** 'Form1'
**Package:** `com.form1.musicplayer`

## Technology Stack

### Core Technologies

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)

### Key Libraries & Dependencies

#### Media Playback

- **androidx.media3 (ExoPlayer)**

#### Cloud Storage Integration

- **MSAL (Microsoft Authentication Library)** for OneDrive OAuth
- **OkHttp** for Microsoft Graph API calls
- Client ID: `90d576a2-9a8b-4a3c-a511-eea1601cddfe` (public, not secret — mobile apps use public client flow / PKCE, no client secret)

#### Data Persistence

- **Room Database** — local playlists
- **Jetpack DataStore**

#### Permissions & Utilities

- **Accompanist Permissions** — essential for file system access on Android 13+

## App Features

### Implemented

1. Browse and play local MP3 files (MediaScanner)
2. Basic playback controls (play/pause/skip/previous) via ExoPlayer
3. Volume control
4. Playlist management (Room Database)
5. OneDrive browser (browse folders and audio files)
6. Navigation drawer UI

### Planned

1. Background playback with notification controls
2. OneDrive folder filtering — only show folders that contain MP3 files somewhere below them (use Graph search endpoint: `GET /me/drive/root/search(q='.mp3')` to build a set of relevant folder paths upfront, then filter the browser)
3. User profile system (see below)
4. Shuffle and repeat modes

## OneDrive Integration

### File Identification

OneDrive files are identified via Microsoft Graph using:
- **`itemId`** — stable across renames/moves/content changes within the same drive; changes on delete+reupload or cross-drive move
- **`driveId`** — needed alongside `itemId` for multi-drive scenarios
- **`relativePath`** — human-readable fallback (from `parentReference.path`)
- **`sha256Hash`** — available from Graph file metadata (`file` facet) without downloading; this is how the PC app identifies songs

A song reference should carry all available identifiers:
```json
{
  "hash": "sha256:abc123...",
  "itemId": "01BYE5RZ...",
  "driveId": "b!xyz...",
  "relativePath": "Music/Artist/Album/song.mp3"
}
```

Resolution priority: ItemID → hash → relative path.

## User Profile System

### Overview

User profile data (playlists, song ratings, play counts, preferences) is stored in a designated OneDrive folder, shared between the PC and Android apps.

### PC App Profile Folder Structure

```
<ProfileDir>/
  library.db                                      # SQLite: song metadata, ratings, play counts
  library/
    YYYY-MM-DD-hh-mm-ss.f1l                       # Library event log (ratings, play dates, etc.)
    YYYY-MM-DD-hh-mm-ss.f1h                       # Audio fingerprint → song GUID mapping log
  playlists/
    YYYY-MM-DD-hh-mm-ss - Playlist Name.f1pl      # Playlist event log
```

### PC App File Formats

All `.f1l`, `.f1h`, `.f1pl` files are **binary** (Qt QDataStream), not JSON or XML. Android cannot read these directly without reimplementing the format.

Each file: `[int version=1][int numMods]` then per record: `[QString modGuid][QDateTime timestamp][type-specific data]`

### PC App Song Identification

Songs are identified by a **persistent GUID** (QUuid), not by a direct file hash. The process:

1. **Audio fingerprint**: MD5 hash of the audio content only — ID3v2 tags at the start and ID3v1 tags at the end (last 128 bytes) are skipped. Only the middle portion of the audio data is hashed.
2. **GUID lookup**: the fingerprint is looked up in the `.f1h` event log. If found, the existing GUID is reused. If new, a fresh UUID is created and recorded.
3. The **song GUID** is the canonical identifier used everywhere — in playlists, library, and ratings.

This means the same song (same audio content) gets the same GUID regardless of filename or location.

### PC App Event Types

**Library events (`.f1l`):** `SetRating`, `SetVolume`, `SetStartOffset`, `SetEndOffset`, `AddPlayDate`, `SetDiscNumber`

**Playlist events (`.f1pl`):** `AddSong`, `RemoveSong`, `RenamePlaylist`, `SetSongDescription`

**Hash mapping events (`.f1h`):** `AddSongFileHash` (audio fingerprint → song GUID)

### PC App Compaction

On startup: all files loaded, events applied in timestamp order. Each event has a mod GUID for deduplication. If multiple files exist, a new compacted file is written first, then old files are deleted — crash-safe. Play count increments when a song reaches 70% played.

### PC App SQLite Schema (library.db, `music` table)

Primary key is song GUID.
Fields: `title`, `artist`, `album`, `disc`, `track`, `year`, `length` (ms), `rating` (-1 to 5), `volume` (adjustment -100 to 100), `start`/`end` (ms offsets), `lastplayed` (unix ts), `dateadded`, `playcount`.

Also a `locations` table: `path` (file path) → `id` (song GUID) + `modified` timestamp.

### Cross-Platform Compatibility

The PC format (binary QDataStream) is not directly readable by Android. Phased approach:

- **Phase 1** (now): Android uses its own format (JSON, since no Qt dependency). No cross-platform sharing yet.
- **Phase 2**: To match songs cross-platform, Android would compute the same audio fingerprint (MD5 of audio content, skipping ID3 tags) to look up or create a song GUID. OneDrive ItemID supplements this for cloud files.
- **Phase 3**: Shared profile folder, both apps reading/writing compatible data.

Canonical song reference spanning both platforms:
- **Song GUID** — derived from audio fingerprint, portable across platforms
- **Audio fingerprint** — MD5 of audio content (no tags)
- **OneDrive itemId + driveId** — Android/OneDrive specific
- **Relative path** — human-readable fallback

## Project Structure

```
app/
└── src/main/java/com/form1/musicplayer/
    ├── data/          # Room Database (playlists)
    ├── media/         # MediaScanner, FileBrowserViewModel
    ├── onedrive/      # OneDriveAuthManager, OneDriveService
    ├── player/        # AudioPlayerManager, AudioPlayerViewModel
    ├── ui/
    │   ├── NavigationDrawer.kt
    │   └── theme/
    ├── MainActivity.kt
    ├── FileBrowserActivity.kt
    ├── OneDriveBrowserActivity.kt
    ├── PlaylistsActivity.kt
    ├── PlaylistDetailsActivity.kt
    └── SettingsActivity.kt
```

## Development Guidelines

### Architecture

- **MVVM** pattern
- **Repository pattern** for data access

### Code Style

- Follow official Kotlin coding conventions
- Keep composables small and focused

## Build & Deploy

```bash
# Build debug APK
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug

# Install on connected device
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug
```

`local.properties` (gitignored) must exist with:
```
sdk.dir=C\:\\Users\\jan\\AppData\\Local\\Android\\Sdk
```

Required SDK: Platform 35 (compileSdk), Platform 34 (targetSdk). Minimum SDK: 24 (Android 7.0).

## Notes

- Kotlin version: 2.1.0
- AGP: 8.7.3
- Gradle: Kotlin DSL

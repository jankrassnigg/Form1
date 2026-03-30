# Setup Guide & Development Roadmap

## Prerequisites Checklist

### Required Software Installation

- [ ] **Java Development Kit (JDK)**
  - Install JDK 17 or later (JDK 21 recommended)
  - Download from: https://adoptium.net/ or use your system package manager
  - Verify: `java -version` should show version 17+

- [ ] **Android Studio**
  - Download: https://developer.android.com/studio
  - Version: Latest stable (Ladybug or later)
  - During installation, ensure these are checked:
    - Android SDK
    - Android SDK Platform
    - Android Virtual Device (if you want emulator)

- [ ] **Android SDK Components** (via Android Studio SDK Manager)
  - Open Android Studio → Settings/Preferences → Appearance & Behavior → System Settings → Android SDK
  - SDK Platforms tab:
    - [ ] Android 14.0 (API 34) - Target platform
    - [ ] Android 7.0 (API 24) - Minimum platform
  - SDK Tools tab:
    - [ ] Android SDK Build-Tools
    - [ ] Android SDK Command-line Tools
    - [ ] Android Emulator (optional, for testing without device)
    - [ ] Android SDK Platform-Tools

- [ ] **Physical Android Device** (Recommended for testing)
  - Android 7.0 or later
  - USB cable for connection
  - Enable Developer Options:
    1. Go to Settings → About Phone
    2. Tap "Build Number" 7 times
    3. Go back to Settings → System → Developer Options
    4. Enable "USB Debugging"

### Optional but Recommended

- [ ] **Git** (for version control)
  - macOS: Already installed or via `brew install git`
  - Verify: `git --version`

- [ ] **ADB (Android Debug Bridge)**
  - Installed with Android SDK Platform-Tools
  - Verify: `adb --version`
  - May need to add to PATH: `~/Library/Android/sdk/platform-tools` (macOS)

## Development Environment Setup

### 1. Configure Android Studio

- [ ] Open Android Studio
- [ ] Go through first-time setup wizard
- [ ] Configure SDK locations
- [ ] Install any additional plugins (Kotlin should be pre-installed)

### 2. Verify Installation

```bash
# Check Java
java -version

# Check ADB (after adding to PATH)
adb --version

# Check connected devices
adb devices
```

## Project Setup Steps

### Phase 1: Basic Project Structure

- [ ] Create new Android project in Android Studio
  - Template: "Empty Activity" with Compose
  - Name: MusicPlayer (or your choice)
  - Package: com.musicplayer (or your choice)
  - Language: Kotlin
  - Minimum SDK: API 24 (Android 7.0)
  - Target SDK: API 34 (Android 14)

- [ ] Configure build.gradle.kts files
  - Set up Kotlin version
  - Add Compose dependencies
  - Configure compileSdk and targetSdk

- [ ] Sync Gradle and verify project builds

### Phase 2: Basic UI Setup

- [ ] Create basic app theme (Material 3)
- [ ] Set up navigation structure
- [ ] Create main screen composable
- [ ] Add placeholder UI elements (buttons, text)

### Phase 3: First Build & Deploy

- [ ] Build the app (Build → Make Project)
- [ ] Connect physical device via USB
- [ ] Accept USB debugging prompt on device
- [ ] Run app (Run → Run 'app' or click green play button)
- [ ] Verify app launches on device

## Build, Deploy & Run Instructions

### Building the App

#### Via Android Studio (Easiest)

1. Open project in Android Studio
2. Wait for Gradle sync to complete
3. Click **Build → Make Project** (or Ctrl+F9 / Cmd+F9)
4. Check "Build" tab at bottom for any errors

#### Via Command Line

```bash
# Navigate to project directory
cd /path/to/MusicPlayer

# Make gradlew executable (first time only, Unix/macOS)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Deploying to Device

#### Via Android Studio (Recommended)

1. Connect device via USB
2. Unlock device and accept USB debugging prompt
3. Select your device from device dropdown (top toolbar)
4. Click **Run → Run 'app'** (or green play button, or Shift+F10)
5. App will build, install, and launch automatically

#### Via Command Line

```bash
# Check device is connected
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Gradle
./gradlew installDebug

# Launch app (replace with your package name)
adb shell am start -n com.musicplayer/.MainActivity
```

### Viewing Logs

```bash
# View app logs in real-time
adb logcat | grep "MusicPlayer"

# Or use Android Studio Logcat tab at bottom
```

### Troubleshooting

**Device not detected:**

```bash
# Check ADB is running
adb devices

# Restart ADB server
adb kill-server
adb start-server

# Check device again
adb devices
```

**Build errors:**

- Clean project: Build → Clean Project
- Invalidate caches: File → Invalidate Caches → Invalidate and Restart
- Delete `.gradle` and `.idea` folders, then re-sync

**App crashes on launch:**

- Check Logcat for stack traces
- Verify minimum SDK matches device Android version

## Development Roadmap

### Milestone 1: Hello World ✓

- [x] Create CLAUDE.md with project overview
- [x] Create SETUP.md with setup instructions
- [x] Install required software
- [x] Create basic project structure
- [x] Build and deploy dummy app to device
- [x] Verify app launches successfully

### Milestone 2: Basic Audio Playback ✓

- [x] Add Media3 (ExoPlayer) dependency
- [x] Request READ_MEDIA_AUDIO permission (Android 13+)
- [x] Implement file picker or file browser
- [x] Create basic audio player service
- [x] Add play/pause button
- [x] Test with sample MP3 file

### Milestone 3: Player Controls UI ✓

- [x] Design player screen with Compose
- [x] Add play/pause/skip/previous buttons
- [x] Display current track information
- [x] Implement progress bar/seek functionality
- [x] Add volume controls
- [x] Show track duration

### Milestone 4: File Browser

- [ ] List MP3 files from device storage
- [ ] Implement file selection
- [ ] Display file metadata (title, artist, duration)
- [ ] Add search/filter functionality
- [ ] Handle storage permissions properly

### Milestone 5: Playlist Management

- [ ] Set up Room database
- [ ] Create playlist data models
- [ ] Implement create/delete/rename playlist
- [ ] Add songs to playlists
- [ ] Display playlists in UI
- [ ] Save and restore playback state

### Milestone 6: Background Playback

- [ ] Implement foreground service
- [ ] Add notification controls
- [ ] Handle audio focus
- [ ] Implement lock screen controls
- [ ] Handle headphone disconnect

### Milestone 7: OneDrive Integration

- [ ] Register app with Microsoft Azure
- [ ] Add Microsoft Graph SDK dependency
- [ ] Implement OAuth authentication
- [ ] Browse OneDrive files
- [ ] Stream audio from OneDrive
- [ ] Cache cloud files locally

### Milestone 8: Polish & Features

- [ ] Add album art display
- [ ] Implement shuffle and repeat modes
- [ ] Add equalizer (optional)
- [ ] Create app icon and splash screen
- [ ] Add settings screen
- [ ] Implement sleep timer
- [ ] Add widget support

## Quick Reference Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Check for dependency updates
./gradlew dependencyUpdates

# Clean build
./gradlew clean

# Full build with tests
./gradlew build
```

## Next Steps

After completing setup:

1. Verify all checkboxes in Prerequisites section are complete
2. Create the starter project (next phase)
3. Build and deploy the dummy app
4. Confirm it runs on your device
5. Begin Milestone 2: Basic Audio Playback

---

**Note**: This is a living document. Update checkboxes and add notes as you progress through the setup and development phases.

# Form1 Music Player

An Android music player application built with Kotlin and Jetpack Compose.

## Quick Start

### Prerequisites
- JDK 17+ installed
- Android Studio Ladybug or later
- Android device with USB debugging enabled (or Android emulator)

### Building the Project

#### Option 1: Android Studio (Recommended)
1. Open Android Studio
2. Select "Open" and navigate to this project directory
3. Wait for Gradle sync to complete
4. Click the green "Run" button or press Shift+F10

#### Option 2: Command Line
```bash
# Build the debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or build and install in one step
./gradlew installDebug
```

### Running on Device

1. Enable Developer Options on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → System → Developer Options
   - Enable "USB Debugging"

2. Connect device via USB and accept debugging prompt

3. Run the app:
   ```bash
   # Check device is connected
   adb devices

   # Install and run
   ./gradlew installDebug
   ```

## Project Structure

- `app/src/main/java/com/form1/musicplayer/` - Kotlin source code
  - `MainActivity.kt` - Main entry point
  - `ui/theme/` - Material 3 theme configuration
- `app/src/main/res/` - Android resources (layouts, strings, icons)
- `app/build.gradle.kts` - App-level build configuration

## Documentation

- [CLAUDE.md](CLAUDE.md) - Complete project overview and technology decisions
- [SETUP.md](SETUP.md) - Detailed setup guide and development roadmap

## Current Status

✅ Basic project structure created
✅ Jetpack Compose UI framework set up
✅ Material 3 theming configured
⏳ Ready for development!

## Next Steps

1. Verify the app builds and runs on your device
2. Follow the development roadmap in [SETUP.md](SETUP.md)
3. Start with Milestone 2: Basic Audio Playback

## License

This project is for personal use.

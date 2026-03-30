# Claude Development Notes

## Project Overview
Form1 is a Qt6-based desktop music player inspired by iTunes. It plays local files only (no streaming) and syncs libraries/playlists across devices via shared folders.

## Build System
- **Build Tool**: CMake
- **Dependencies**: vcpkg (Qt6, TagLib, SQLite3, BASS audio library)
- **Platforms**: Windows, macOS

### Build Commands

```bash
# Setup vcpkg (if needed)
./InstallVcpkg.bat

# Configure and build
mkdir build
cd build
cmake ..
cmake --build .
```

## Key Technologies
- **GUI**: Qt6 (Widgets, Core, Gui, Network)
- **Audio**: BASS audio library
- **Database**: SQLite3
- **Metadata**: TagLib
- **File Watching**: Qt FileSystemWatcher

## Architecture
- `GUI/Form1.*` - Main application window
- `MusicLibrary/` - Music file scanning and management
- `Playlists/` - Different playlist types (Regular, Smart, Radio)
- `SoundDevices/` - Audio playback abstraction
- `Config/` - Application settings and state

## Entry Point
`Misc/Main.cpp` - Application entry point

## Important Files
- `CMakeLists.txt` - Build configuration
- `GUI/Form1.ui` - Main UI layout
- `Config/AppConfig.*` - Application configuration
- `Config/AppState.*` - Runtime state management

## Testing

No test framework available.

## Common Tasks
- Audio playback handled via BASS library
- UI changes require Qt MOC compilation
- New UI files need to be added to CMakeLists.txt
- Cross-platform audio library linking differs (Windows: .lib/.dll, macOS: .dylib)
#!/bin/bash

# Form1 Music Player - Setup Verification Script
# This script checks if your development environment is ready

echo "🎵 Form1 Music Player - Setup Verification"
echo "=========================================="
echo ""

# Check Java
echo "Checking Java installation..."
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo "✅ Java found: $java_version"
else
    echo "❌ Java not found. Please install JDK 17 or later."
    exit 1
fi

echo ""

# Check Android SDK
echo "Checking Android SDK..."
if [ -d "$HOME/Library/Android/sdk" ]; then
    echo "✅ Android SDK found at: $HOME/Library/Android/sdk"
else
    echo "⚠️  Android SDK not found at default location"
    echo "   Please ensure Android Studio is installed and SDK is configured"
fi

echo ""

# Check ADB
echo "Checking ADB..."
if command -v adb &> /dev/null; then
    adb_version=$(adb --version | head -n 1)
    echo "✅ ADB found: $adb_version"

    echo ""
    echo "Checking for connected devices..."
    devices=$(adb devices | tail -n +2 | grep -v "^$")
    if [ -z "$devices" ]; then
        echo "⚠️  No devices connected"
        echo "   Connect your Android device via USB and enable USB debugging"
    else
        echo "✅ Connected devices:"
        echo "$devices"
    fi
else
    echo "⚠️  ADB not found"
    echo "   Add Android SDK platform-tools to your PATH:"
    echo "   export PATH=\$PATH:\$HOME/Library/Android/sdk/platform-tools"
fi

echo ""
echo "=========================================="
echo "Setup verification complete!"
echo ""
echo "Next steps:"
echo "1. Open this project in Android Studio"
echo "2. Wait for Gradle sync to complete"
echo "3. Connect your Android device"
echo "4. Click the green 'Run' button"
echo ""
echo "Or build from command line:"
echo "  ./gradlew assembleDebug"

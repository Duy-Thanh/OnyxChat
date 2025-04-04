#!/bin/bash

# Build and install OnyxChat app

echo "Building OnyxChat..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "Build successful! Installing app..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    
    if [ $? -eq 0 ]; then
        echo "Installation successful! Starting app..."
        adb shell am start -n com.nekkochan.onyxchat/.ui.auth.LoginActivity
    else
        echo "Failed to install app. Make sure your device is connected."
    fi
else
    echo "Build failed. See error messages above."
fi 
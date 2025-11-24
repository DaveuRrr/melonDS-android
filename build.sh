#!/bin/bash

# Clean build script for melonDS-android
# This performs a complete clean and rebuild of the project

echo "Cleaning project..."
./gradlew clean

echo "Removing .cxx directories..."
rm -rf app/.cxx
rm -rf melonDS-android-lib/.cxx

echo "Removing build directories..."
rm -rf app/build
rm -rf melonDS-android-lib/build

echo "Building GitHubProdDebug variant..."
./gradlew :app:assembleGitHubProdDebug

echo "Build complete!"

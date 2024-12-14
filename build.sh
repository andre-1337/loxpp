#!/usr/bin/env bash

set -e

SRC_DIR="src"
SRC_DIR="src"
BIN_DIR="bin"
LIBS_DIR="libs"
JAR_NAME="loxpp.jar"
MAIN_CLASS="com.andre1337.loxpp.Lox"
LIBS=$(find "$LIBS_DIR" -name "*.jar" | tr '\n' ':')

echo "Cleaning old build..."
rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"

echo "Compiling Java source files..."
find "$SRC_DIR" -name "*.java" > sources.txt
javac -d "$BIN_DIR" -cp "$LIBS" @sources.txt
rm sources.txt

echo "Copying resource files..."
rsync -av --exclude='*.java' "$SRC_DIR/" "$BIN_DIR/"

echo "Packaging into $JAR_NAME..."
jar cfe "$JAR_NAME" "$MAIN_CLASS" -C "$BIN_DIR" .

echo "Build completed successfully. Output: $JAR_NAME"
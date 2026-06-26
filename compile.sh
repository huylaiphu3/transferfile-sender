#!/bin/bash
set -e

# Auto-detect javac
if command -v javac &>/dev/null; then
    JAVAC="javac"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
elif [ -x "$HOME/.jdks/corretto-21.0.11/bin/javac" ]; then
    JAVAC="$HOME/.jdks/corretto-21.0.11/bin/javac"
elif [ -x "$HOME/.jdks/openjdk-26.0.1/bin/javac" ]; then
    JAVAC="$HOME/.jdks/openjdk-26.0.1/bin/javac"
else
    echo "Loi: Khong tim thay javac. Can cai JDK >= 11."
    echo "  Ubuntu/Debian : sudo apt install openjdk-17-jdk"
    echo "  Windows       : tai tu https://adoptium.net"
    exit 1
fi

mkdir -p out
find src/main/java -name "*.java" -print0 | xargs -0 "$JAVAC" -d out
echo "Build OK. Chay: ./run.sh"

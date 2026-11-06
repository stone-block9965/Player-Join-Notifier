#!/usr/bin/env bash
# Minecraft / Gradle 不要で動く純 Java 部分のテスト（アドレス正規化・参加検知・通知文面・バスアドレス解析・OS判定・macOS用コマンド）。
# 必要: JDK（javac）。Gradle の build には影響しません。
set -euo pipefail
cd "$(dirname "$0")/.."
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
if command -v javac >/dev/null 2>&1; then JAVAC=(javac); else JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main); fi
CORE=$(find src/main/java/dev/playerjoinnotifier/core src/main/java/dev/playerjoinnotifier/dbus src/main/java/dev/playerjoinnotifier/platform -name '*.java')
"${JAVAC[@]}" -encoding UTF-8 -d "$WORK" $CORE tests/CoreSelfTest.java
java -Dfile.encoding=UTF-8 -cp "$WORK:src/main/resources" CoreSelfTest

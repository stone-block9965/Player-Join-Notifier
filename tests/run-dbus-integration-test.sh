#!/usr/bin/env bash
# 実 dbus-daemon + モック通知サービスで DBusNotificationClient を検証する。
# 必要: java(+jdk.compiler), dbus-daemon, python3-gi
set -euo pipefail
cd "$(dirname "$0")/.."
WORK=$(mktemp -d)
trap 'kill ${MOCK_PID:-0} ${BUS_PID:-0} 2>/dev/null || true; rm -rf "$WORK"' EXIT

javac_() { java -m jdk.compiler/com.sun.tools.javac.Main "$@"; }
javac_ -d "$WORK/out" $(find src/main/java/dev/playerjoinnotifier/core src/main/java/dev/playerjoinnotifier/dbus -name '*.java') tests/DBusIntegrationTest.java

SOCK="$WORK/bus"
dbus-daemon --session --nofork --address="unix:path=$SOCK" --nopidfile &
BUS_PID=$!
for _ in $(seq 50); do [ -S "$SOCK" ] && break; sleep 0.1; done

export DBUS_SESSION_BUS_ADDRESS="unix:path=$SOCK"
LOG="$WORK/notify.log"
python3 tests/mock_notification_server.py "$LOG" > "$WORK/mock.out" 2>&1 &
MOCK_PID=$!
for _ in $(seq 50); do grep -q READY "$WORK/mock.out" && break; sleep 0.1; done

# 本文は UTF-8 ファイルで渡す（JVM の引数エンコーディングがロケール依存のため）
printf '%s' 'Steve がサーバーに参加しました。' > "$WORK/body1.txt"
printf '%s' 'Ünï_cödé &amp; 日本語🎮 がサーバーに参加しました。' > "$WORK/body2.txt"
printf '%s' 'x' > "$WORK/body3.txt"

echo "--- 1) 明示パスで送信"
java -cp "$WORK/out" DBusIntegrationTest "$SOCK" "$WORK/body1.txt"
echo "--- 2) DBUS_SESSION_BUS_ADDRESS から自動検出して送信（Unicode・記号入り）"
java -cp "$WORK/out" DBusIntegrationTest "" "$WORK/body2.txt"
echo "--- 3) 通知サービスがいないバスへの送信はエラーとして例外になる（クラッシュしない）"
kill $MOCK_PID; wait $MOCK_PID 2>/dev/null || true; MOCK_PID=0
sleep 0.3
if java -cp "$WORK/out" DBusIntegrationTest "$SOCK" "$WORK/body3.txt"; then echo "UNEXPECTED success"; exit 1; else echo "(expected failure) ok"; fi
echo "--- 4) バスが存在しない場合も例外になる"
if java -cp "$WORK/out" DBusIntegrationTest "$WORK/nonexistent" "$WORK/body3.txt"; then echo "UNEXPECTED success"; exit 1; else echo "(expected failure) ok"; fi

echo "--- モックが受信した内容"
cat "$LOG"
python3 - "$LOG" <<'PY'
import json, sys
rows = [json.loads(l) for l in open(sys.argv[1], encoding="utf-8")]
assert len(rows) == 2, rows
a, b = rows
assert a["app_name"] == "Minecraft" and a["app_icon"] == "applications-games", a
assert a["summary"] == "Minecraft - プレイヤーが参加しました", a
assert a["body"] == "Steve がサーバーに参加しました。", a
assert a["actions"] == [] and a["hints"] == {} and a["expire_timeout"] == 5000 and a["replaces_id"] == 0, a
assert b["body"] == "Ünï_cödé &amp; 日本語🎮 がサーバーに参加しました。", b
print("ALL ASSERTIONS PASSED")
PY

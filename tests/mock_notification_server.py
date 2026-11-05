#!/usr/bin/env python3
"""org.freedesktop.Notifications のモック。受け取った Notify の引数を JSON 行でファイルに書く。
使い方: DBUS_SESSION_BUS_ADDRESS=... python3 mock_notification_server.py /tmp/notify.log
"""
import json
import sys

import gi

gi.require_version("Gio", "2.0")
from gi.repository import Gio, GLib  # noqa: E402

LOG = sys.argv[1] if len(sys.argv) > 1 else "/tmp/notify.log"

XML = """
<node>
  <interface name="org.freedesktop.Notifications">
    <method name="Notify">
      <arg type="s" name="app_name" direction="in"/>
      <arg type="u" name="replaces_id" direction="in"/>
      <arg type="s" name="app_icon" direction="in"/>
      <arg type="s" name="summary" direction="in"/>
      <arg type="s" name="body" direction="in"/>
      <arg type="as" name="actions" direction="in"/>
      <arg type="a{sv}" name="hints" direction="in"/>
      <arg type="i" name="expire_timeout" direction="in"/>
      <arg type="u" name="id" direction="out"/>
    </method>
  </interface>
</node>
"""

counter = 0


def on_call(conn, sender, path, iface, method, params, invocation):
    global counter
    if method == "Notify":
        counter += 1
        app, rid, icon, summary, body, actions, hints, timeout = params.unpack()
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({
                "app_name": app, "replaces_id": rid, "app_icon": icon,
                "summary": summary, "body": body, "actions": actions,
                "hints": {k: str(v) for k, v in hints.items()},
                "expire_timeout": timeout,
            }, ensure_ascii=False) + "\n")
        invocation.return_value(GLib.Variant("(u)", (counter,)))


def on_bus(conn, name):
    info = Gio.DBusNodeInfo.new_for_xml(XML)
    conn.register_object("/org/freedesktop/Notifications", info.interfaces[0], on_call, None, None)


def on_name(conn, name):
    print("READY", flush=True)


loop = GLib.MainLoop()
Gio.bus_own_name(Gio.BusType.SESSION, "org.freedesktop.Notifications",
                 Gio.BusNameOwnerFlags.NONE, on_bus, on_name, lambda *a: loop.quit())
loop.run()

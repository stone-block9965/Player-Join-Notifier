# Player Join Notifier

A client-side mod that notifies you with desktop notifications when other players join.

A client-only Fabric mod that shows an OS desktop notification whenever **another player joins a Minecraft server** (Linux / Windows / macOS).

- No server-side installation needed — it works from the client alone
- No extra libraries. Linux talks to D-Bus directly from Java, Windows uses the JDK's built-in tray notifications, and macOS uses the OS-provided `osascript` (no `notify-send` or other extra commands required)
- It never communicates with servers or any external service
- English and Japanese included. The language follows the game language automatically, and you can change it with `/joinnotify language` or in Mod Menu
- Settings can be changed in game (`/joinnotify settings ...`) or in [Mod Menu](https://modrinth.com/mod/modmenu) (optional), and are always saved permanently

## Requirements

| Item | Details |
|---|---|
| Minecraft | Java Edition 26.3 |
| Mod loader | Fabric Loader 0.19.5 or newer + Fabric API (built against `0.165.2+26.3`) |
| Mod Menu | Optional. Adds a settings screen (built against Mod Menu 21.0.0 for 26.3) |
| Java | 25 (required by Minecraft 26.x) |
| OS | Linux / Windows 10 & 11 / macOS (see below) |

### Notification method and test status per OS

| OS | Method | Tested |
|---|---|---|
| Linux | Sends `Notify` directly to D-Bus `org.freedesktop.Notifications` (aimed at deepin / CachyOS / KDE Plasma; any daemon implementing this spec should work). The session bus address must be of the form `unix:path=...` (the default on systemd-based distros) | Yes (CachyOS) |
| Windows | The JDK's `SystemTray` / `TrayIcon.displayMessage` (shown as a toast on Windows 10/11). A tray icon appears only while notifying and is removed 30 seconds after the last notification | Not yet tested on real hardware |
| macOS | Runs the OS-provided `osascript` (`display notification`) | **Untested** |

- `icon` and `timeoutMillis` only apply on **Linux**. On Windows and macOS, the look and duration of notifications follow the OS settings
- If notifications do not appear or look wrong on Windows / macOS, please open an issue together with the `PlayerJoinNotifier` WARN lines from the log

## Installation

### Option A: Download the mod (Modrinth / CurseForge / GitHub Releases)

For playing — no building needed.

1. Download the `.jar` for Minecraft 26.3 from Modrinth, CurseForge or the GitHub Releases page
2. Install [Fabric Loader](https://fabricmc.net/) (0.19.5 or newer) and [Fabric API](https://modrinth.com/mod/fabric-api) for your instance
3. Put the jar (and Fabric API) into the instance's `mods` folder
4. Launch the game. The config file is generated on first launch, then register your servers (see "Configuration")

### Option B: Build from source (GitHub "Download ZIP" / `git clone`)

The ZIP you get from GitHub's **Code → Download ZIP** contains the source code, **not** a ready-to-use jar.

1. Build the jar by following "Building" below (`build/libs/player-join-notifier-1.1.0.jar`)
2. Then follow steps 2–4 of Option A with that jar

## Configuration

### Config file (JSON)

`<game folder>/config/player_join_notifier.json`
(With the official launcher: `~/.minecraft/config/` on Linux, `%APPDATA%\.minecraft\config\` on Windows, `~/Library/Application Support/minecraft/config/` on macOS. With Prism Launcher and similar, it is inside the instance's `minecraft/config/`.)

```json
{
  "enabled": true,
  "servers": [
    "example.com",
    "play.example.net:25566",
    "192.168.1.100:25565",
    "[2001:db8::1]:25565"
  ],
  "title": "",
  "body": "",
  "onlyWhenUnfocused": false,
  "language": "auto",
  "icon": "applications-games",
  "timeoutMillis": 5000,
  "initialSyncGraceTicks": 40,
  "duplicateSuppressSeconds": 3
}
```

| Key | Description |
|---|---|
| `enabled` | Set to `false` to stop notifications for every server |
| `servers` | **List of servers to notify for (whitelist).** If empty, nothing is notified (the default) |
| `title` / `body` | Notification text. `{player}` in `body` is replaced by the player name. **Leave empty to use the default text in the game's language** (e.g. "Minecraft - A player joined" / "{player} joined the server.") |
| `language` | Language of the mod's own text: `"auto"` (default, follows the game language), `"en"` or `"ja"` |
| `onlyWhenUnfocused` | `false` (default): notify even while the Minecraft window is focused. `true`: notify only while the window is **not** focused (e.g. you are in another app or alt-tabbed). `/joinnotify test` ignores this setting |
| `icon` | Notification icon name (a freedesktop icon name). **Linux only** |
| `timeoutMillis` | Display time in ms. `-1` = desktop default, `0` = never closes automatically. **Linux only** (Windows / macOS follow the OS settings) |
| `initialSyncGraceTicks` | Grace period treated as the initial sync right after connecting (20 ticks = 1 second) |
| `duplicateSuppressSeconds` | Seconds during which the same player is not notified again (prevents double notifications when the tab list is rebuilt) |

**After editing, run `/joinnotify reload` in game, or restart the game.** If the JSON is broken, the original file is not overwritten; an error is logged and the previous settings keep being used.

### Address format

Register addresses **exactly as you type them on the multiplayer screen** (they are matched as strings, before SRV record resolution).

- A missing port is treated as `25565`: `example.com` ≡ `example.com:25565`
- Case, surrounding whitespace and a trailing dot are ignored
- A different port means a **different server** (`example.com:25566` does not match `example.com`)
- No subdomain or partial matching (registering `example.com` does not cover `evil.example.com`)
- IPv6 uses brackets, e.g. `[::1]:25565` (`[::1]` without a port, or `::1` without brackets, also work). Different spellings of the same address (`2001:db8::1` and `2001:0DB8:0:0:0:0:0:1`) are treated as equal
- No DNS resolution is performed for matching

### In-game commands (client-side only — never sent to the server)

| Command | Description |
|---|---|
| `/joinnotify add` | Register the server you are currently connected to |
| `/joinnotify add <address>` | Register the given address |
| `/joinnotify remove <address>` | Remove a registered address |
| `/joinnotify list` | List registered servers (also shows the normalized form) |
| `/joinnotify status` | Show the current server and whether notifications are active |
| `/joinnotify language` | Show the current language setting |
| `/joinnotify language <auto\|en\|ja>` | Change the language (`auto` follows the game language) |
| `/joinnotify settings` | Show the current settings |
| `/joinnotify settings enabled <true\|false>` | Turn the mod's notifications on or off |
| `/joinnotify settings onlyWhenUnfocused <true\|false>` | Notify only while the Minecraft window is not focused |
| `/joinnotify reload` | Reload the config file |
| `/joinnotify test` | Send a test notification (to check that notifications appear) |

Everything you change with these commands is **saved to the config file immediately and permanently** (it survives restarts).

### Mod Menu (optional)

If [Mod Menu](https://modrinth.com/mod/modmenu) is installed, open it, select Player Join Notifier and click the config button. You can change:

- Notifications on/off (`enabled`)
- Only when the game is unfocused (`onlyWhenUnfocused`)
- Language (Auto / English / 日本語)
- The registered server list (add and remove)

Every click is applied and saved immediately — there is no separate "save" step. Mod Menu is not required; without it, use the commands or edit the JSON file.

## How it works (avoiding false positives)

Every tick, the mod compares the **set of UUIDs** in the client's player list with the previous one (no Mixins that hook packets directly).

- **New join** = a UUID appears that was not in the previous set. Updates to ping, display name, game mode, etc. do not change the set, so they are never mistaken for a join
- **Initial sync is excluded**: for `initialSyncGraceTicks` (2 seconds by default) after the player list starts arriving, everyone who shows up is just recorded as "already known" without a notification. The baseline is re-taken on every server switch (e.g. through a proxy) and reconnect
- **No duplicates**: known UUIDs are not notified. A UUID that leaves is removed from the set, so rejoining notifies again. To guard against the tab list being briefly rebuilt, the same UUID is not notified again within `duplicateSuppressSeconds`
- **Focus filter (optional)**: with `onlyWhenUnfocused` set to `true`, joins that happen while the game window is focused are skipped (you can already see them in the game). Note that in fullscreen the OS may not show notifications at all
- **You are never notified about yourself** (checked by UUID)
- Notifications are sent from a dedicated thread, so the game's main thread never waits. Failures such as no connection to the notification service, authentication errors or timeouts (5 seconds) are only logged as WARN (at most once per minute) and never affect the game

## Building

Requirements: **JDK 25**, **Gradle 9.6** (only used to generate the wrapper the first time), and an internet connection (to fetch Minecraft and Fabric).

```bash
# First time only: generate the Gradle wrapper (uses the system gradle)
gradle wrapper --gradle-version 9.6.0

./gradlew build          # → build/libs/player-join-notifier-1.1.0.jar
./gradlew runClient      # launch a development client to try it out
```

> Mod Menu support is compile-time only: Gradle fetches `com.terraformersmc:modmenu` from `maven.terraformersmc.com` to compile the optional settings screen. The mod runs fine without Mod Menu installed, and the dependency is not bundled into the jar.
> This project does not ship `gradle-wrapper.jar` (a binary). Generate it with `gradle wrapper` as above, or copy the `gradlew` files from Fabric's official example mod.
> Minecraft 26.1 and later is unobfuscated, so Loom is `net.fabricmc.fabric-loom`, dependencies use `implementation`, and no mappings are specified. Versions are managed in `gradle.properties` (recommended values: https://fabricmc.net/develop/).

## Tests

Self-tests that run without Minecraft or Gradle are included (JDK only).

```bash
bash tests/run-core-tests.sh            # address normalization, join detection, notification text, bus address parsing, OS detection, macOS command generation, translations (language switching, key consistency)
bash tests/run-dbus-integration-test.sh # (Linux only) sends a real Notify to a mock notification service on a real dbus-daemon
```

### Manual check on a real machine

1. `./gradlew build` succeeds
2. Put the jar into `mods` and launch; the log shows `Player Join Notifier initialized`
3. Run `/joinnotify test` in game → an OS notification appears (Linux: deepin / KDE Plasma, plus Windows and macOS)
4. Connect to the target server and run `/joinnotify add`. `/joinnotify status` shows notifications as enabled
5. Join the same server with another account → exactly **one** notification appears, and no notifications for players who were already online when you connected
6. The other account leaves and rejoins → a notification appears again
7. On an unregistered server no notification appears (`/joinnotify status` shows disabled)

## Troubleshooting

### Windows

- **Nothing appears**: check Settings → System → Notifications that notifications are on and Focus assist / Do not disturb is off. If notifications for Java (`javaw.exe` / Java(TM) Platform SE binary) are turned off, nothing is shown
- If the log says `System tray notifications are unavailable here`, `java.awt.headless=true` is set or the environment has no tray support

### macOS

- **Nothing appears**: notifications are shown under the name "Script Editor". Allow notifications for Script Editor in System Settings → Notifications, and turn off Focus mode

### Linux

- **`/joinnotify test` shows nothing**: check the WARN lines in the log (`PlayerJoinNotifier`). Make sure `echo $DBUS_SESSION_BUS_ADDRESS` and `echo $XDG_RUNTIME_DIR` are not empty and that the launcher was started from the desktop session. If the address is not found, `$XDG_RUNTIME_DIR/bus` → `/run/user/<uid>/bus` are tried as well
- **Checking the notification daemon**: `busctl --user introspect org.freedesktop.Notifications /org/freedesktop/Notifications` shows whether the service exists
- **Flatpak launchers**: the sandbox may restrict D-Bus access. Grant talk permission to `org.freedesktop.Notifications` (`--talk-name=org.freedesktop.Notifications`)
- **`unix:abstract=` buses** cannot be reached from Java and are not supported

## Known limitations

- Targets are matched by "the address you typed on the multiplayer screen". If an SRV record resolves to a different host, you still register the address you type
- Singleplayer and LAN hosts are not targeted
- Windows / macOS notifications use OS-standard mechanisms, so their look, duration and icon cannot be changed
- On Windows the toast may show "Java" as its source

## License

MIT — see [LICENSE](LICENSE).

# ProxyMe

A simple, lightweight Android proxy configuration tool. Route any app's traffic through HTTP or SOCKS5 proxies using Android's VPN Service.

## Features

### Core
- **HTTP & SOCKS5** proxy support with optional authentication
- **Per-app routing** — choose which apps go through the proxy (allow or disallow mode)
- **Hostname & IP support** — connect using IP addresses or domain names

### Configuration Management
- **Saved configurations** — create, edit, and switch between proxy configs via a form-based UI
- **QR code sharing** — generate a QR code for any saved config; scan with any QR reader to import
- **Deep link import** — `proxyme://` URI scheme for one-tap config import
- **Bulk import** — paste JSON arrays, proxy URLs, or subscription data to import multiple configs at once
- **Clipboard detection** — copies a proxy URL? The app detects it and offers to use it

### Reliability
- **Proxy failover** — set a backup proxy that activates automatically if the primary fails
- **Network-aware auto-reconnect** — VPN reconnects automatically when network drops and restores
- **Proxy health check** — test if a proxy is reachable before connecting (Menu > Test)
- **Proxy rotation** — automatically cycle through a list of saved configs on a schedule (5min to 4hr intervals)

### Security
- **Encrypted storage** — all saved configs, credentials, and logs use AES-256 encrypted SharedPreferences
- **Credential masking** — passwords are never shown in the UI, notifications, or logs
- **Silent notification** — VPN status notification shows no proxy details, includes a Disconnect action button

### Extras
- **Quick Settings tile** — toggle VPN from the notification shade without opening the app
- **Home screen widget** — glanceable connection status with one-tap access
- **Connection log** — timestamped history of connect/disconnect/fail events, exportable to CSV
- **First-run onboarding** — guided setup on first launch
- **Material Design** — clean, dark-themed interface with Material Components

## Screenshots

| Main Screen | Connected | New Config |
|:-----------:|:---------:|:----------:|
| ![Main](images/main.png) | ![Connected](images/connected.png) | ![New Config](images/new_config.png) |

| My Configs | Settings | About |
|:----------:|:--------:|:-----:|
| ![My Configs](images/my_configs.png) | ![Settings](images/settings.png) | ![About](images/about.png) |

## Getting Started

### Requirements

- Android 6.0 (API 23) or later
- Java 17 (for building)

### Build

```bash
# Build both debug and release APKs
./build.sh

# Or build manually
cd android_app
./gradlew assembleDebug assembleRelease
```

APKs are output to `build-output/`.

## Usage

### Quick Start

1. Enter a proxy address in the input field (e.g. `192.168.1.1:8080`)
2. Tap **Connect**
3. Accept the VPN permission prompt

### Proxy Formats

The app accepts proxy addresses in several formats:

| Format | Example |
|--------|---------|
| Host and port (defaults to HTTP) | `10.10.10.1:8080` |
| Hostname and port | `proxy.example.com:8080` |
| HTTP | `http://10.10.10.1:8080` |
| SOCKS5 | `socks5://10.10.10.1:8080` |
| HTTP with auth | `http://user:pass@10.10.10.1:8080` |
| SOCKS5 with auth | `socks5://user:pass@10.10.10.1:8080` |

### Saved Configurations

Instead of typing proxy addresses every time, you can save and manage configurations:

1. Tap **New Config** to open the configuration form
2. Select the protocol (HTTP or SOCKS5)
3. Enter host, port, and optional authentication credentials
4. Give it a name and tap **Save**

To use a saved config:
- Tap **My Configs** to see all saved configurations
- Tap a config card to load it into the proxy input, ready to connect
- Tap the pencil icon to edit, share icon for QR code, or trash icon to delete

### QR Code Sharing

Share proxy configs with others without typing:

1. Open **My Configs** and tap the share icon on any config
2. A QR code appears — the other person scans it with their phone's camera
3. ProxyMe auto-opens on their device with the config pre-filled

### Proxy Rotation

Automatically cycle through multiple proxies on a timer:

1. Open **Menu > Rotation**
2. Add 2 or more saved configs to the rotation list
3. Set the interval (5 min to 4 hours)
4. Toggle on — the VPN starts and automatically switches proxies at each interval

### Bulk Import

Import multiple proxy configs at once via **Menu > Import**:

- **JSON array**: `[{"host":"1.2.3.4","port":8080,"protocol":"http","name":"Proxy 1"}]`
- **Proxy URLs** (one per line): `socks5://user:pass@host:port`
- Accepts flexible field names: `host`/`server`, `port`, `user`/`username`, `pass`/`password`, `name`/`remarks`

### App Filtering

Control which apps use the proxy via **Menu > Settings**:

- **Disallowed Application** (default) — all apps use the proxy *except* the ones you select
- **Allowed Application** — *only* the apps you select use the proxy

### Connection Log

View connection history via **Menu > Log**:

- Timestamped events: Connected, Disconnected, Failed
- Duration and error details
- Export to CSV via the **Export** button

## Project Structure

```
android_app/
  app/src/main/
    java/tun/proxy/
      MainActivity.kt              # Main UI and VPN control
      SettingsActivity.java         # App filtering settings
      MyApplication.java            # Global state
      service/
        Tun2SocksVpnService.kt      # VPN service with foreground notification
        VpnState.kt                 # Connection state enum + broadcast constants
        VpnTileService.kt           # Quick Settings tile
        NetworkMonitor.kt           # Network change detection for auto-reconnect
        ProxyRotationManager.kt     # Scheduled proxy rotation logic
        RotationReceiver.kt         # AlarmManager broadcast receiver for rotation
      model/
        ProxyConfig.kt              # Saved configuration data model
        ProxyData.kt                # Proxy URL parsing model
        ConnectionEvent.kt          # Connection log entry model
      repository/
        ConfigRepository.kt         # Encrypted config persistence (AES-256)
        ConnectionLogRepository.kt  # Encrypted connection log persistence
      adapter/
        SavedConfigsAdapter.kt      # Saved configs list adapter
        ConnectionLogAdapter.kt     # Connection log list adapter
        RotationConfigAdapter.kt    # Rotation config list adapter
      util/
        QrGenerator.kt              # QR code generation + URI encoding
        ProxyHealthCheck.kt         # TCP socket reachability test
        ClipboardProxyDetector.kt   # Clipboard monitoring for proxy URLs
        ConfigImporter.kt           # Multi-format bulk config import
      widget/
        VpnWidgetProvider.kt        # Home screen widget
    res/
      layout/                       # UI layouts (12 files)
      values/                       # Colors, strings, styles
      drawable/                     # Icons and shape drawables
      xml/                          # Widget info, preferences, network security
      color/                        # Color state lists for toggle buttons
      menu/                         # Toolbar menus
```

## Acknowledgments

- [TunProxy](https://github.com/raise-isayan/TunProxy)
- [appproxy](https://github.com/ys1231/appproxy)

## License

See [LICENSE](LICENSE) for details.

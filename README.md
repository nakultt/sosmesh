# MeshSOS

A disaster-resilient emergency SOS relay app for Android. Devices relay SOS messages
through a chain of peers via BLE mesh until one reaches internet and uploads to a server.

## Architecture

```
Presentation (Jetpack Compose + ViewModel)
    ↕
Domain (State Machine, Use Cases, Services)
    ↕
Data (Transport Layer, Room DB, Retrofit API)
    ↕
Background (Foreground Service — keeps relay alive)
```

## Transport modes

| Mode | Uses | Range | Speed |
|---|---|---|---|
| NearbyConnections | Bluetooth / BLE / Wi-Fi Direct (chosen by Play services) | 30–60m | High |
| BLE GATT | Pure BLE (GATT server + client) | 10–15m | Low |

Both transports run **at the same time**. Packets are sent on every running transport and
receivers deduplicate them by packet id, so two phones always find each other even if one
of them is missing a permission or radio needed by the other transport. Transports are
started/stopped automatically when Bluetooth, Location or permissions change.

Peers exchange their mesh device id (BLE scan response + HELLO message, Nearby endpoint
name), so each physical device is counted once no matter how many links exist.

### Store-and-forward
Every SOS a device originates or relays is re-sent whenever a new peer connects (and on a
periodic safety net) until its TTL expires. ACKs and helper updates are flooded through
the mesh and deduplicated by ACK id, so they reach the originator even if the route changed.

## Setup

### 1. Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Physical Android devices (API 26+) for testing — emulators do NOT support BLE

### 2. Clone and open
```bash
git clone <repo-url>
cd MeshSOS
# Open in Android Studio
```

### 3. Set your server URL
Edit `app/src/main/java/com/meshsos/di/AppModule.kt`:
```kotlin
fun provideServerBaseUrl(): String = "https://YOUR-SERVER.com/"
```

### 4. Server API contract

POST `/api/emergency/sos`

Request:
```json
{
  "packet": { /* SosPacket model */ },
  "relayDeviceId": "DEVICE-ID",
  "relayLocation": { "lat": 0.0, "lng": 0.0, "accuracy": 10.0 }
}
```

Response:
```json
{
  "success": true,
  "alertId": "server-generated-id",
  "respondersNotified": 3,
  "estimatedArrival": "12 minutes"
}
```

### 5. Build, test and install
```bash
./gradlew testDebugUnitTest   # packet parsing, wire codec, BLE framing
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 6. Test with 3 devices
- Install on 3 physical devices
- Device A: Send SOS
- Device B (no internet): Should receive and relay
- Device C (has internet): Should receive, upload, send ACK back

## Testing checklist

- [ ] 3 physical devices (not emulators)
- [ ] All Bluetooth + Location permissions granted
- [ ] Device B: Turn off mobile data + WiFi (airplane mode then re-enable BLE)
- [ ] Observe relay hop trail in Log tab
- [ ] Status tab lists each peer once, with the transports it is reachable over
- [ ] Turn Bluetooth off/on: the readiness banner appears and the mesh recovers by itself
- [ ] Send an SOS with no peers in range, then bring a device in range: it is delivered

## Permissions required

| Permission | Why |
|---|---|
| `BLUETOOTH_SCAN/ADVERTISE/CONNECT` | BLE mesh |
| `NEARBY_WIFI_DEVICES` | Nearby Connections WiFi Direct |
| `ACCESS_FINE_LOCATION` | Required by Nearby Connections |
| `FOREGROUND_SERVICE` | Background relay |
| `WAKE_LOCK` | Keep CPU alive during relay |
| `RECEIVE_BOOT_COMPLETED` | Auto-restart service after reboot |

## Key files

| File | Purpose |
|---|---|
| `MeshStateMachine.kt` | Core state: IDLE → ORIGINATOR → RELAY → UPLOADING → CONFIRMED, store-and-forward, ACK flooding |
| `MeshForegroundService.kt` | Keeps BLE relay alive in background |
| `NearbyConnectionsTransport.kt` | Primary WiFi Direct + BLE transport |
| `BleGattTransport.kt` | Pure BLE transport with a serialized GATT queue |
| `BleFraming.kt` | MTU-sized framing and reassembly for BLE |
| `TransportManager.kt` | Runs all transports concurrently and merges peers/packets |
| `DeduplicationService.kt` | Prevents relay loops via UUID set |
| `UploadPacketUseCase.kt` | Upload + retry with persistent queue |

## Known limitations

1. **iOS not supported** — Nearby Connections is Android-only. iOS BLE background
   advertising is heavily restricted by Apple.
2. **Relay requires app in foreground OR foreground service running** — if the user
   force-stops the app, relaying stops.
3. **BLE GATT fallback range is ~10–15m** — reliable indoors but requires devices
   to be closer than in WiFi Direct mode.
4. **ACK delivery is best-effort** — ACKs are flooded through whatever peers are in range;
   if the originator is completely isolated it will not see the confirmation, but the SOS
   is still uploaded.
5. **Server URL is hardcoded** — replace before production deployment.

## Packet flow

```
[Originator] --SOS--> [Relay A] --SOS--> [Relay B (online)] --HTTP POST--> [Server]
                                                              <--ACK--
             <--ACK---          <--ACK---
```

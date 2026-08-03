# Functional Specification - Meshtastic Mock Server

This document describes the functional aspects of the Meshtastic node simulator ("Mock Server"), designed to allow development and testing of the Galaxy Watch 7 (Wear OS) application without depending on a physical LoRa radio.

---

## 1. Solution Overview

Developing applications for wearable devices (like Wear OS) integrated with the Meshtastic LoRa radio ecosystem faces physical barriers, such as the need for multiple physical radios (for example, boards based on the ESP32 or nRF52 chip) and the variability of signal reception in the development environment.

The **Meshtastic Mock Server** solves this problem by emulating the logical behavior of a physical device directly via the local Wi-Fi network (TCP/IP) or by simulating a GATT server (BLE), following the official protocol based on Protocol Buffers (Protobuf). In this way, the watch can execute its full synchronization, message sending, and message receiving routines as if it were connected to a real Meshtastic node.

```
┌──────────────────┐               ┌──────────────────┐
│  Galaxy Watch 7  │   Wi-Fi/TCP   │   Mock Server    │
│    (Wear OS)     │ ─────────────►│    (macOS)       │
│                  │   BLE GATT    │                  │
└──────────────────┘               └──────────────────┘
```

---

## 2. Use Cases

### Use Case 01: Initial Synchronization (Handshake)
* **Actors:** Wear OS Application (Client) and Mock Server (Server).
* **Pre-conditions:** The Wear OS application opened a TCP connection on port `4403` or paired with the simulator's BLE GATT service.
* **Main Flow:**
  1. Wear OS sends an initial `ToRadio` message indicating that it wants to receive the configuration (`want_config_id` filled).
  2. The Mock Server receives the request and starts streaming the state configuration packets (`FromRadio`):
     - **MyNodeInfo:** Basic node information (node number, reboot count).
     - **DeviceMetadata:** Information about the hardware and the emulated firmware version.
     - **NodeInfo:** Identity details (Long Name: "Mock LoRa Node", Short Name: "MCK1", MAC Address and GPS coordinates of São Paulo).
     - **Config:** LoRa settings (Geographic region and Modem Preset).
     - **Channel:** Node channel definitions (Primary channel named "LongFast" with default PSK encryption key).
  3. The Mock Server sends a configuration complete confirmation (`config_complete_id` matching the original `want_config_id`).
  4. The Wear OS application changes its visual state from "Connecting..." to "Connected".

---

### Use Case 02: Sending Voice Converted to Text by the Watch
* **Actors:** User (operating the watch) and Mock Server.
* **Pre-conditions:** The watch is in the "Connected" state after the Handshake.
* **Main Flow:**
  1. The user dictates a voice message on the Galaxy Watch 7.
  2. The Wear OS application processes the speech and converts it locally to a text string (e.g., "Emergency on trail 3").
  3. The application encapsulates this text into a `MeshPacket` protobuf containing the `TEXT_MESSAGE_APP` application.
  4. The packet is transmitted to the Mock Server (via TCP or BLE).
  5. The Mock Server intercepts the message, displays it on the macOS system console with a timestamp, the source node ID, and the content in plain text.

---

### Use Case 03: Echo Bot Behavior (Auto-Response)
* **Actors:** Mock Server and Wear OS Application.
* **Pre-conditions:** The Mock Server successfully received a text packet containing data from the watch.
* **Main Flow:**
  1. The Mock Server decodes the received message.
  2. The system waits for a predefined interval of 1 second to emulate the actual reception latency of the LoRa network.
  3. The Mock Server builds a response containing the text `"Received via LoRa Mock: [Original message]"`.
  4. The server sends this new `MeshPacket` wrapped in a `FromRadio` envelope back to the client (Wear OS).
  5. The watch receives the packet, decodes the message, and displays it on the user's chat screen, confirming the success of the round-trip of the message.

---

## 3. Echo Bot Behavior and Data Flow

The detailed flow of the Echo Bot is purely event-reactive:

```
[Client: Wear OS]                       [Server: Mock Server]
        │                                         │
        │─── 1. Sends ToRadio(MeshPacket) ───────►│ (Decodes Protobuf)
        │                                         │ (Logs to macOS console)
        │                                         │ (Waits 1.0 second)
        │◄── 2. Sends FromRadio(MeshPacket) ──────│ (Generates Echo response)
        │                                         │
```

This intentional 1-second delay is essential. Real LoRa networks operating on the `LONG_FAST` preset have low data rates and significant transmission times ("Time-on-Air"). Although the simulation uses high-speed connections (Wi-Fi or Bluetooth), the delay helps validate the asynchronous behavior of the Wear OS graphical user interface (UI) when dealing with the transit time of data packets.

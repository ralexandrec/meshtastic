# Software Design Document (SDD) - Wear OS Client PTT

This document describes the technical architecture, data structures, internal behavior, and testing strategy of the smartwatch application (Wear OS) focused on 100% offline operation via the Meshtastic protocol.

---

## 1. Application Architecture

The application adopts the **MVVM (Model-View-ViewModel)** architecture with unidirectional data flow (UDF) implemented in **Jetpack Compose for Wear OS**.

```
  ┌─────────────────────────────────────────────────────────┐
  │                           UI                            │
  │  PttScreen (Jetpack Compose) / MainActivity (KeyEvent)   │
  └────────────────────────────┬────────────────────────────┘
                               │ Screen Events / Key Presses
                               ▼
  ┌─────────────────────────────────────────────────────────┐
  │                        VIEWMODEL                        │
  │                      PttViewModel                       │
  └────────────────────────────┬────────────────────────────┘
                               │ Commands / States
                               ▼
  ┌─────────────────────────────────────────────────────────┐
  │                          MODEL                          │
  │       MeshConnection (TCP/BLE) & TtsManager (TTS)       │
  └─────────────────────────────────────────────────────────┘
```

### Main Components:
1. **MainActivity:** The entry point of the application. Responsible for intercepting physical button clicks (such as the rotary crown or shortcut buttons) and passing them to the ViewModel.
2. **PttScreen:** Composable containing the user interface screen, optimized for Wear OS circular displays. Manages the dynamic rendering of the main button and side telemetry buttons.
3. **PttViewModel:** Centralizes business logic and in-memory persistence. Controls audio recording state, current connection, incoming/outgoing messages, and optional features visibility.
4. **MeshConnection / MiniProto:** Binary packet encoding and decoding layer. Abstraits text data, telemetry, and positioning serialization.

---

## 2. Offline Strategy (No Internet)

### 2.1 Speech-to-Text (STT)
For local voice transcription, the application consumes Android local APIs linked to the **Sayboard** offline keyboard:
- The app dynamically checks for the presence of the `com.elishaazaria.sayboard` package via `PackageManager`.
- Uses the `SpeechRecognizer` API directed to the Sayboard service, automatically detecting pauses in speech to end recording and transmit without additional user commands.

### 2.2 Text-to-Speech (TTS)
Text messages received in **Voice Mode** are synthesized locally on the device using `android.speech.tts.TextToSpeech`.
- **GPS Exception:** Messages containing geographical coordinates patterns (`GPS:`) are explicitly filtered and are **not** spoken by the synthesizer, avoiding reading confusing numeric strings to the user.

---

## 3. Hardware Keys Mapping (Physical Buttons)

The application maps physical watch buttons to trigger voice transmission without touching the screen (walkie-talkie style):
- **`KeyEvent.KEYCODE_STEM_1`** and **`KeyEvent.KEYCODE_STEM_2`** are captured in `MainActivity`:
  - **Press (onKeyDown):** Triggers `viewModel.startVoiceRecording()`.
  - **Release (onKeyUp):** Triggers `viewModel.stopVoiceRecordingAndTrigger()`.

---

## 4. TCP Communication Contract (Protobuf) and Parsing

All communication between the data layer and the simulated server (`mesh_mock`) uses the Meshtastic TCP header framing (4 bytes):
- `0x94 0xC3` (2 bytes start) + `Length` (2 bytes Big-Endian) + `Protobuf Payload`.

### 4.1 Position Packet (`POSITION_APP` - Portnum 3)
The encoding and decoding of the GPS payload use zigzag representation integer fields to support negative numbers (South/West coordinates):
- **ZigZag Mathematical Representation:**
  - Encoding: `z = (value shl 1) xor (value shr 31)`
  - Decoding: `value = (z ushr 1) xor -(z and 1)`
- **Structured Fields:**
  - Field 1: `latitude_i` (sint32 containing latitude * 10^7)
  - Field 2: `longitude_i` (sint32 containing longitude * 10^7)
  - Field 3: `altitude` (int32 containing altitude in meters)

```kotlin
// Extraction algorithm implemented in FromRadioParser
val latD = decodeZigZag(readVarint(stream)) / 1e7
val lonD = decodeZigZag(readVarint(stream)) / 1e7
```

### 4.2 Telemetry Packet (`TELEMETRY_APP` - Portnum 4)
Battery status data is transmitted and extracted from telemetry messages:
- The message contains a `Telemetry` envelope where field 2 (`device_metrics`) contains a serialized `DeviceMetrics` sub-message.
- Within `DeviceMetrics`, field 1 (`battery_level`) provides the percentage (uint32).

---

## 5. External Map Integration (Intents)

Upon receiving a `POSITION_APP` packet, the parser translates the data to the structured string `"GPS: Lat [lat], Lon [lon], Alt [alt]m"`.
The screen detects this format and exposes an explicit geographic Intent:
- **URI Scheme:** `geo:lat,lon?q=lat,lon`
- **Action:** `Intent.ACTION_VIEW`
- The use of a standard Android geolocation URI triggers the OS resolver, displaying a native chooser for the user to open the point in **Google Maps**, **Waze**, or another map client installed on Wear OS.

---

## 6. BDD Testing Strategy (Cucumber)

Instrumented automated tests validate logic and data flow in an emulated environment:
1. **TTS Mock:** An implementation of the `TtsManager` (`MockTtsManager`) intercepts messages sent to synthesis and makes assertions in BDD.
2. **Keyboard/Voice Keyboard Mock:** `Intents.intending` intercepts typing or audio requests and returns controlled strings in Cucumber for validation without real human interaction.

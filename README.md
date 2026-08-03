# Meshtastic Wear OS Client

An offline-first, walkie-talkie-style communication client for Wear OS smartwatches that operates entirely without internet connectivity by interfacing with the Meshtastic LoRa mesh network.

---

## Architecture Overview

The application is built using modern Android development practices, emphasizing a clean separation of concerns and circular-optimized user interface elements:

- **UI Layer (Jetpack Compose for Wear OS):** Otimized for circular screens (e.g., Samsung Galaxy Watch) to avoid edge text clipping. It features a Double Scroll mechanism allowing simultaneous vertical navigation of the main screen and independent scrolling of message history.
- **ViewModel (PttViewModel):** Manages local state, connection statuses, audio recording triggers, and message persistence. Following the Single Responsibility Principle, message formatting and GPS packet parsing are encapsulated in the ViewModel using structured `UiMessage` models.
- **Model Layer:** Handles background speech operations:
  - **Speech-to-Text (STT):** Local, offline transcription by interfacing with the open-source **Sayboard** keyboard via native `SpeechRecognizer` API.
  - **Text-to-Speech (TTS):** Automatic, smart read-aloud of received text messages (skipping geographic coordinates).
  - **MiniProto:** Encapsulates the binary encoding/decoding of Meshtastic packets (positions, telemetry/battery, and text messages) over standard 4-byte TCP socket framing (`0x94 0xC3 [Length] [Payload]`).

---

## Prerequisites

To run and test the application, ensure your environment meets the following requirements:

- **Operating System:** macOS (recommended) or Linux.
- **Java Development Kit (JDK):** Java 17.
- **Android SDK:** Installed with Android SDK Command-line Tools and platform tools (`adb`, `emulator`).
- **Python:** Python 3.10+ (for running the simulator `mesh_mock`).
- **Wear OS Emulator:** An Android Virtual Device (AVD) running Wear OS 3 or 4 (API 30+).

---

## Building the Project

Compile the debug APK using the Gradle Wrapper from the `wear/` directory:

```bash
cd wear/
./gradlew assembleDebug
```

The compiled APK will be located at:
`wear/wear/build/outputs/apk/debug/wear-debug.apk`

---

## Running the Simulator (Mesh Mock)

The `mesh_mock` is a lightweight Python simulator that acts as a local LoRa radio node and handles packet broadcast between connected watches.

1. Navigate to the simulator folder:
   ```bash
   cd mesh_mock/
   ```
2. Create and activate a Python virtual environment:
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Start the mock server on port 4403:
   ```bash
   python mock_server.py --no-ble --port 4403
   ```

---

## Launching Two Emulators (P2P Chat Demo)

To simulate a real point-to-point walkie-talkie conversation between two smartwatches on the mock network:

1. Ensure a Wear OS emulator (AVD) is installed.
2. Run the automation script:
   ```bash
   ./scripts/launch_two_emulators.sh            # Portuguese-Brazil (default)
   ./scripts/launch_two_emulators.sh --locale en # English (US)
   ```
This script will:
- Clean up previous emulator/simulator processes.
- Start the `mesh_mock` server.
- Spin up two independent instances of the same Wear OS AVD (using `-read-only` and `-port` flags).
- Wait for both devices to boot completely.
- Build and install the Meshtastic Wear app and the **Sayboard** keyboard on both emulators.
- Launch the main screen on both devices.

---

## Running BDD Tests (Cucumber / Espresso)

The integration tests validate the complete end-to-end communication flow (UI interaction, network packet exchange, and TTS playback) using Cucumber:

### Running on a Running Emulator
To execute BDD tests on an already running and connected Wear OS emulator, use:
```bash
cd wear/
./gradlew clean installDebug connectedDebugAndroidTest
```

### Headless Automation Run
To automatically spin up an emulator, start the mock server, run the test suite, capture screenshots, and clean up everything:
```bash
./scripts/run_integration_tests.sh
```

---

## SOLID & Clean Code Compliance

This project adheres to strict clean architecture principles:
- **Single Responsibility Principle (SRP):** UI layout is completely separated from networking and parsing logic. `PttScreen` handles view rendering only, while `PttViewModel` controls state and coordinates parsing.
- **Dependency Inversion Principle (DIP):** The ViewModel operates on the `MeshConnection` and `TtsManager` interfaces, allowing clean dependency injection of standard production implementations (`TcpMeshClient`, `NativeTtsManager`) or mock replacements during BDD runs.
- **Internationalization (i18n):** All user-facing strings are migrated to Android resource dictionaries, providing seamless default English and localized Portuguese-Brazil support.

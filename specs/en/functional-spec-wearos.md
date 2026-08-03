# Functional Specifications (FSD) - Wear OS Client PTT

This document describes the features, user interface (UI), behavior, and user experience (UX) of the Wear OS application for offline communication on the Meshtastic network.

---

## 1. Circular Interface and Navigation

The application is designed under smartwatch design principles (Wear OS), optimized for circular screens (like the Galaxy Watch) to prevent text clipping and maximize the touch area.

### 1.1 Double Scroll Behavior
To accommodate both the reading of long messages and quick access to control buttons, the application implements two independent scroll levels:
1. **Main Scroll (General):**
   - Drags the entire screen vertically to scroll between the connection status Chip (top), the Message Card (center), and the Action Bar (bottom).
   - **Side Touch Areas:** The central message card has expanded margins on the left and right edges (with screen width adjusted to `fillMaxWidth(0.78f)`). The user can drag their finger on these side edges to scroll the general screen without interfering with the text.
2. **Internal Scroll (Conversation History):**
   - The message card has a fixed physical height of `72.dp`.
   - Long messages that wrap lines accumulate in an independent scrollable container. By dragging the finger vertically in the center of the card, only the messages slide, allowing full reading of the text from start to finish.
   - **Auto Scroll:** Whenever a new message (voice, text, GPS, or battery) is received or sent, the display automatically scrolls to the bottom, ensuring that the latest traffic is always visible.

---

## 2. Transmission Controls and Modes

### 2.1 Unified Primary Action Button
The interface concentrates the primary sending action in a single highlighted centered circular button (`size(72.dp)`):
* **Voice Mode (Default):**
   - The button displays the label **PTT**.
   - **Walkie-Talkie Behavior:** The user holds down the button to start local recording (the label changes to **TALK**). Upon releasing the button, recording stops and the transcribed text is immediately transmitted over the network.
* **Text Mode:**
   - The button displays the label **TXT**.
   - **Writing Behavior:** A simple tap on the button opens the native input interface (virtual keyboard) of Wear OS for direct text message entry.

### 2.2 Mode Switch Button (Microphone/Speech Bubble)
* Located on the **left side** of the main button, aligned to the center.
* Displays the microphone icon (🎙️) in Voice Mode and a speech bubble icon (💬) in Text Mode.
* Allows switching the behavior of the main button instantly between PTT (voice) and TXT (typing).

---

## 3. Telemetry and Geographical Position (GPS)

### 3.1 Sending GPS Coordinates
* Located on the **right side** of the main button.
* Displays the pin icon (📍).
* When clicked, instantly sends the watch's current GPS position to all connected nodes on the LoRa mesh.
* The sender views the local confirmation in the history: `"You: GPS sent: [lat], [lon]"`.

### 3.2 Dynamic Battery Button Display (Optional)
By default, the battery button (🔋) is hidden to reduce visual clutter on the circular screen.
* **Configuration:** The user can enable the battery display by opening the settings dialog ("Configure Connection" at the top of the screen) and toggling the **"Show Battery"** switch (ToggleChip).
* **Dynamic Layout Adaptation:**
  - **Battery Disabled (Default):** The right side of the central button displays only the GPS button (📍) aligned to the center.
  - **Battery Enabled:** The right side of the central button adapts to a vertical column of two buttons: the GPS button (📍) positioned on the upper line of the PTT, and the Battery button (🔋) positioned on the bottom line of the PTT.

---

## 4. Coordinate Visualization and Map Integration

When a GPS coordinate is transmitted on the network and reaches the other watch:
1. **Text Display:** The message is formatted and displayed as `"GPS: Lat [lat], Lon [lon], Alt [alt]m"`.
2. **TTS Silencing:** The smart voice engine detects the `"GPS:"` pattern and remains silent (does not read the geographic coordinates aloud).
3. **Integrated Map Button:** A map icon (🗺️) is rendered on the right side of the message bubble in the history.
4. **Open in Google Maps/Waze:** Upon tapping the map icon (🗺️), the application fires a native system Intent chooser. The user can choose to open the geographical point directly in **Google Maps**, **Waze**, or any other map application installed on the watch, allowing them to route and see the position in real time.

---

## 5. Automated Speech Recognition (STT) Test and Audio Injection

### 5.1 Audio Injection Test and Locale Validation
* The system supports automated BDD integration tests (Cucumber/Espresso) with audio simulation and injection.
* **BCP-47 Language Configuration:** The `ACTION_RECOGNIZE_SPEECH` intent configures `EXTRA_LANGUAGE`, `EXTRA_LANGUAGE_PREFERENCE`, and `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` extras to `"pt-BR"`.
* **Synthesized Audio Injection:** During the integration test, synthesized Portuguese-Brazil audio files (e.g., *"olá testando o chat em português"*) are injected into the test pipeline to validate that the speech recognizer transcribes the text in Portuguese and transmits the packet over the Mesh network.

# Standards and Frequencies - Meshtastic Brazil Community

This document presents the technical specifications recommended by the **Meshtastic Brazil** community, aligned with ANATEL regulations for the use of radio frequencies and the default configurations adopted to ensure interoperability in the country.

---

## 1. ANATEL Regulation for ISM (Industrial, Scientific, and Medical) Bands

In Brazil, the use of radiocommunication equipment is regulated by the **National Telecommunications Agency (ANATEL)**. Devices using LoRa technology operate in ISM bands that are intended for restricted radiation radiocommunication, exempt from station operating licenses, provided they meet the limits established in **Act No. 14448** (Technical Requirements for Assessing the Conformity of Restricted Radiation Radiocommunication Equipment).

The two main frequency bands used for Meshtastic in Brazil are:

### 1.1 The 915 MHz Band (902 MHz to 907.5 MHz & 915 MHz to 928 MHz)
*   **Meshtastic Firmware Default:** Region setting **`US`** (United States).
*   **Power Limits:** The maximum transmitter output power must not exceed **1 Watt (30 dBm)** for spread spectrum or digital modulation systems. The power spectral density must not be greater than 8 dBm in any 3 kHz band.
*   **Practical Use:** It is the most common band for radios such as Heltec V3, LILYGO T-Beam, and T-Echo in Brazil. By sharing frequencies with the US standard (`US`), devices can operate in the 915-928 MHz sub-band without violating local guidelines.

### 1.2 The 2.4 GHz Band (2400 MHz to 2483.5 MHz)
*   **Meshtastic Firmware Default:** Region setting **`LORA_24`**.
*   **Characteristics:** It operates on the same physical frequency as technologies like Wi-Fi and Bluetooth, but uses LoRa modulation (usually based on transceivers like the Semtech SX1280 chip).
*   **Advantages:** Allows much smaller antennas, higher bandwidth (faster data rate), and unified international use (without complex regional divisions).

---

## 2. Region Settings in the Simulator (RegionCode)

To ensure that the simulated node is correctly identified by the watch as a regulated Brazilian node, the default region setting defined in the Protobuf `Config.LoRaConfig` should be:

*   **`RegionCode.US`** (Integer value = `1`) for 915 MHz operation.
*   **`RegionCode.LORA_24`** (Integer value = `13`) for operation in the global 2.4 GHz band.

---

## 3. Channel Parameterization of the Brazilian Community

The Meshtastic Brazil community adopts the international modulation and channel standards, but with name customizations to facilitate local identification in urban or rural clusters.

### 3.1 Modem Preset: `LONG_FAST`
This is the recommended and default modulation parameterization for the general-purpose Mesh network. It optimally balances range distance and transmission speed for small texts.

*   **Bandwidth:** 125 kHz
*   **Spreading Factor (SF):** 9
*   **Coding Rate (CR):** 4/5

### 3.2 The Primary Channel (Channel 0)
Every Meshtastic Brazil node must be configured with the public primary channel so that it can listen to and retransmit general mesh traffic (including geolocation and telemetry packets from neighboring nodes).

*   **Channel Name:** `"Brasil"` or `"LongFast"` (Case-sensitive).
*   **Pre-Shared Key (PSK):** Uses the default Meshtastic public encryption key (represented by a single `0x01` byte in the protobuf).
*   **Usage:** Main channel for public chat, sending geographical coordinates, and forming the mesh network topology.

### 3.3 Secondary Channels (Channels 1 to 7)
These are optional channels configured by groups of users (e.g., rescue teams, hikers, or private condominium networks).
*   They can have specific names (e.g., `"Seguranca"`, `"Trilha"`).
*   They use custom PSK keys generated via the app's 128/256-bit key generator (AES) to keep messages private and unreadable for nodes outside the group.

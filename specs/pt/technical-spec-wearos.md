# Documento de Design de Software (SDD) - Wear OS Client PTT

Este documento descreve a arquitetura técnica, estruturas de dados, comportamento interno e estratégia de teste do aplicativo para relógios inteligentes (Wear OS) focado na operação 100% offline via protocolo Meshtastic.

---

## 1. Arquitetura do Aplicativo

O aplicativo adota a arquitetura **MVVM (Model-View-ViewModel)** com fluxo de dados unidirecional (UDF) implementado em **Jetpack Compose para Wear OS**.

```
  ┌─────────────────────────────────────────────────────────┐
  │                           UI                            │
  │  PttScreen (Jetpack Compose) / MainActivity (KeyEvent)   │
  └────────────────────────────┬────────────────────────────┘
                               │ Eventos de Tela / Teclas
                               ▼
  ┌─────────────────────────────────────────────────────────┐
  │                        VIEWMODEL                        │
  │                      PttViewModel                       │
  └────────────────────────────┬────────────────────────────┘
                               │ Comandos / Estados
                               ▼
  ┌─────────────────────────────────────────────────────────┐
  │                          MODEL                          │
  │       MeshConnection (TCP/BLE) & TtsManager (TTS)       │
  └─────────────────────────────────────────────────────────┘
```

### Componentes Principais:
1. **MainActivity:** Ponto de entrada do aplicativo. Responsável por interceptar cliques físicos de botões (como a coroa rotativa ou botões de atalho) e repassá-los para o ViewModel.
2. **PttScreen:** Composable contendo a tela de interface do usuário, otimizada para displays circulares Wear OS. Gerencia o desenho dinâmico do botão principal e botões laterais de telemetria.
3. **PttViewModel:** Centraliza a lógica de negócios e persistência em memória. Controla o estado de gravação de áudio, conexão atual, mensagens recebidas/enviadas e visibilidade de recursos opcionais.
4. **MeshConnection / MiniProto:** Camada de codificação e decodificação binária de pacotes. Abstrai a serialização de dados de texto, telemetria e posicionamento.

---

## 2. Estratégia Offline (Sem Internet)

### 2.1 Reconhecimento de Fala (Speech-to-Text - STT)
Para transcrição local de voz, o aplicativo consome APIs locais do Android vinculadas ao teclado offline **Sayboard**:
- O app verifica dinamicamente a presença do pacote `com.elishaazaria.sayboard` via `PackageManager`.
- Utiliza a API `SpeechRecognizer` direcionada ao serviço do Sayboard, detectando automaticamente pausas na fala para encerrar a gravação e transmitir sem comandos adicionais do usuário.

### 2.2 Síntese de Voz (Text-to-Speech - TTS)
Mensagens de texto recebidas no **Modo Voz** são sintetizadas localmente no dispositivo usando `android.speech.tts.TextToSpeech`.
- **Exceção de GPS:** Mensagens contendo padrões de coordenadas geográficas (`GPS:`) são explicitamente filtradas e **não** são faladas pelo sintetizador, evitando leituras de strings numéricas confusas para o usuário.

---

## 3. Mapeamento de Hardware Keys (Botões Físicos)

O aplicativo mapeia botões físicos do relógio para acionar a transmissão de voz sem toque na tela (estilo walkie-talkie):
- **`KeyEvent.KEYCODE_STEM_1`** e **`KeyEvent.KEYCODE_STEM_2`** são capturados na `MainActivity`:
  - **Press (onKeyDown):** Aciona `viewModel.startVoiceRecording()`.
  - **Release (onKeyUp):** Aciona `viewModel.stopVoiceRecordingAndTrigger()`.

---

## 4. Contrato de Comunicação TCP (Protobuf) e Parsing

Toda comunicação entre a camada de dados e o servidor simulado (`mesh_mock`) utiliza o enquadramento de cabeçalho TCP do Meshtastic (4 bytes):
- `0x94 0xC3` (2 bytes start) + `Length` (2 bytes Big-Endian) + `Protobuf Payload`.

### 4.1 Pacote de Posição (`POSITION_APP` - Portnum 3)
A codificação e decodificação do payload de GPS usam campos inteiros com representação ZigZag para suportar números negativos (coordenadas Sul/Oeste):
- **Representação matemática ZigZag:**
  - Codificação: `z = (value shl 1) xor (value shr 31)`
  - Decodificação: `value = (z ushr 1) xor -(z and 1)`
- **Campos estruturados:**
  - Campo 1: `latitude_i` (sint32 contendo latitude * 10^7)
  - Campo 2: `longitude_i` (sint32 contendo longitude * 10^7)
  - Campo 3: `altitude` (int32 contendo altitude em metros)

```kotlin
// Algoritmo de extração implementado no FromRadioParser
val latD = decodeZigZag(readVarint(stream)) / 1e7
val lonD = decodeZigZag(readVarint(stream)) / 1e7
```

### 4.2 Pacote de Telemetria (`TELEMETRY_APP` - Portnum 4)
Os dados de status de bateria são transmitidos e extraídos das mensagens de telemetria:
- A mensagem contém um envelope `Telemetry` onde o campo 2 (`device_metrics`) contém uma sub-mensagem serializada `DeviceMetrics`.
- Dentro de `DeviceMetrics`, o campo 1 (`battery_level`) fornece o percentual (uint32).

---

## 5. Integração com Mapas Externos (Intents)

Ao receber um pacote `POSITION_APP`, o parser traduz os dados para a string estruturada `"GPS: Lat [lat], Lon [lon], Alt [alt]m"`.
A tela detecta este formato e expõe uma Intent geográfica explícita:
- **Esquema URI:** `geo:lat,lon?q=lat,lon`
- **Ação:** `Intent.ACTION_VIEW`
- O uso de uma URI de geolocalização padrão do Android aciona o resolvedor do sistema operacional, exibindo um seletor nativo para o usuário abrir o ponto no **Google Maps**, **Waze** ou outro cliente cartográfico instalado no Wear OS.

---

## 6. Estratégia de Testes BDD (Cucumber)

Os testes automatizados instrumentados validam a lógica e o fluxo de dados em ambiente emulado:
1. **Mock de TTS:** Uma implementação do `TtsManager` (`MockTtsManager`) intercepta as mensagens enviadas para síntese e faz asserções no BDD.
2. **Mock de Teclado/Teclado de Voz:** `Intents.intending` intercepta as requisições de digitação ou áudio e retorna strings controladas no Cucumber para validação sem interação humana real.

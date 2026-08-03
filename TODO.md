# TODO & Roadmap - Meshtastic Wear OS Client

Este documento descreve as tarefas pendentes, melhorias planejadas e próximos passos do projeto.

---

## 🛠️ Tarefas Pendentes (TODO)

### 🛰️ 1. Integração com Hardware Real & Testes de Campo
- [ ] **Conexão com Nós Físicos Meshtastic:** Testar e validar a comunicação via Bluetooth Low Energy (BLE) e Serial/TCP com rádios reais (Heltec V3, LilyGO T-Beam, Station G1, RAK Wireless).
- [ ] **Testes de Alcance LoRa em Campo:** Realizar testes práticos de PTT em ambientes urbanos e selvagens (Off-Grid) operando nas frequências 868 MHz / 915 MHz.
- [ ] **Medição de Latência:** Avaliar a latência de transmissão de pacotes Protobuf entre o relógio Wear OS e os rádios físicos sob condições reais de tráfego.

### 🎙️ 2. Reconhecimento de Voz Offline (STT) em Português Nativamente
- [ ] **Empacotamento do Modelo Vosk `pt-BR` no Sayboard:** Empacotar o modelo acústico `vosk-model-small-pt-0.3` dentro da APK do Sayboard para permitir reconhecimento de voz 100% offline em Português-Brasil sem necessidade de conta ou nuvem Google.
- [ ] **Suporte Multi-idioma Dinâmico:** Permitir chaveamento dinâmico do modelo acústico offline entre Português e Inglês na interface do relógio.

### 🔐 3. Gerenciamento de Canais e Criptografia
- [ ] **Seleção de Canais (Channel Management):** Interface Wear OS para alternar entre canal principal (`Primary`) e canais secundários (`Secondary`).
- [ ] **Configuração de Chaves de Criptografia PSK:** Suporte à digitação/exibição de chaves AES-256 para grupos fechados de comunicação.

### 🔋 4. Otimização de Bateria e Wear OS Ambient Mode
- [ ] **Ambient Mode / Always-on Display:** Implementar o modo de baixo consumo do Jetpack Compose Wear OS para economizar bateria da tela durante sessões prolongadas de PTT.
- [ ] **Gestão Eficiente do Bluetooth BLE:** Desativar varreduras ativas de BLE quando o aplicativo estiver em segundo plano para preservar a bateria do smartwatch.

### 📍 5. Telemetria Avançada e Sensores do Smartwatch
- [ ] **Leitura de Sensores Locais:** Enviar bateria real do smartwatch e batimentos cardíacos como telemetria de emergência na malha LoRa.
- [ ] **Histórico e Exportação de Rastros GPS:** Salvar localmente o histórico de pontos GPS recebidos dos nós da malha para consulta offline.

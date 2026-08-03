# Especificação Funcional - Mock Server Meshtastic

Este documento descreve os aspectos funcionais do simulador de nó Meshtastic ("Mock Server"), projetado para permitir o desenvolvimento e teste do aplicativo para Galaxy Watch 7 (Wear OS) sem a dependência de um rádio LoRa físico.

---

## 1. Visão Geral da Solução

O desenvolvimento de aplicações para dispositivos vestíveis (como o Wear OS) integrados ao ecossistema de rádio LoRa Meshtastic enfrenta barreiras físicas, como a necessidade de múltiplos rádios físicos (por exemplo, placas baseadas no chip ESP32 ou nRF52) e a variabilidade de recepção de sinal no ambiente de desenvolvimento.

O **Mock Server Meshtastic** soluciona esse problema ao emular o comportamento lógico de um dispositivo físico diretamente via rede Wi-Fi local (TCP/IP) ou simulando um servidor GATT (BLE), seguindo o protocolo oficial baseado em Protocol Buffers (Protobuf). Desta forma, o relógio pode executar suas rotinas completas de sincronização, envio e recebimento de mensagens como se estivesse conectado a um nó Meshtastic real.

```
┌──────────────────┐               ┌──────────────────┐
│  Galaxy Watch 7  │   Wi-Fi/TCP   │   Mock Server    │
│    (Wear OS)     │ ─────────────►│    (macOS)       │
│                  │   BLE GATT    │                  │
└──────────────────┘               └──────────────────┘
```

---

## 2. Casos de Uso

### Caso de Uso 01: Sincronização Inicial (Handshake)
* **Atores:** Aplicativo Wear OS (Cliente) e Mock Server (Servidor).
* **Pré-condições:** O aplicativo Wear OS abriu uma conexão TCP na porta `4403` ou pareou com o serviço BLE GATT do simulador.
* **Fluxo Principal:**
  1. O Wear OS envia uma mensagem inicial do tipo `ToRadio` informando que deseja receber a configuração (`want_config_id` preenchido).
  2. O Mock Server recebe a solicitação e inicia o streaming dos pacotes de configuração de estado (`FromRadio`):
     - **MyNodeInfo:** Informações básicas do nó (número do nó, contagem de reboots).
     - **DeviceMetadata:** Informações sobre o hardware e a versão do firmware emulado.
     - **NodeInfo:** Detalhes de identidade (Long Name: "Mock LoRa Node", Short Name: "MCK1", MAC Address e Coordenadas GPS de São Paulo).
     - **Config:** Configurações de LoRa (Região geográfica e Modem Preset).
     - **Channel:** Definições dos canais do nó (Canal primário denominado "LongFast" com chave de criptografia PSK padrão).
  3. O Mock Server envia uma confirmação de conclusão da configuração (`config_complete_id` combinando com o `want_config_id` original).
  4. O aplicativo Wear OS muda seu estado visual de "Conectando..." para "Conectado".

---

### Caso de Uso 02: Envio de Voz Convertida em Texto pelo Relógio
* **Atores:** Usuário (operando o relógio) e Mock Server.
* **Pré-condições:** O relógio está no estado "Conectado" após o Handshake.
* **Fluxo Principal:**
  1. O usuário dita uma mensagem de voz no Galaxy Watch 7.
  2. O aplicativo Wear OS processa a fala e a converte localmente para uma string de texto (ex: "Emergência na trilha 3").
  3. O aplicativo encapsula esse texto em um protobuf do tipo `MeshPacket` contendo a aplicação `TEXT_MESSAGE_APP`.
  4. O pacote é transmitido para o Mock Server (via TCP ou BLE).
  5. O Mock Server intercepta a mensagem, exibe no console do sistema macOS com timestamp, o ID do nó de origem e o conteúdo em texto claro.

---

### Caso de Uso 03: Comportamento do Echo Bot (Resposta Automática)
* **Atores:** Mock Server e Aplicativo Wear OS.
* **Pré-condições:** O Mock Server recebeu com sucesso um pacote de texto contendo dados do relógio.
* **Fluxo Principal:**
  1. O Mock Server decodifica a mensagem recebida.
  2. O sistema aguarda um intervalo predefinido de 1 segundo para emular a latência real de recepção da rede LoRa.
  3. O Mock Server monta uma resposta contendo o texto `"Received via LoRa Mock: [Mensagem original]"`.
  4. O servidor envia este novo `MeshPacket` empacotado em um envelope `FromRadio` de volta para o cliente (Wear OS).
  5. O relógio recebe o pacote, decodifica a mensagem e a exibe no chat/tela do usuário, confirmando o sucesso da ida e volta da mensagem.

---

## 3. Comportamento do Echo Bot e Fluxo de Dados

O fluxo detalhado do Echo Bot é puramente reativo a eventos:

```
[Cliente: Wear OS]                       [Servidor: Mock Server]
        │                                         │
        │─── 1. Envia ToRadio(MeshPacket) ───────►│ (Decodifica o Protobuf)
        │                                         │ (Registra no console do macOS)
        │                                         │ (Aguarda 1.0 segundo)
        │◄── 2. Envia FromRadio(MeshPacket) ──────│ (Gera resposta com o Echo)
        │                                         │
```

Esse atraso intencional de 1 segundo é essencial. Redes LoRa reais operando no preset `LONG_FAST` possuem taxas de dados baixas e tempos de transmissão ("Time-on-Air") significativos. Embora a simulação utilize conexões de alta velocidade (Wi-Fi ou Bluetooth), o atraso ajuda a validar o comportamento assíncrono da interface gráfica (UI) do Wear OS ao lidar com o tempo de trânsito dos pacotes de dados.

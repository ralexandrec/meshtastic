# Padrões e Frequências - Comunidade Meshtastic Brasil

Este documento apresenta as especificações técnicas recomendadas pela comunidade **Meshtastic Brasil**, alinhadas com as regulamentações da ANATEL para o uso de frequências de rádio e as configurações padrão adotadas para garantir a interoperabilidade no país.

---

## 1. Regulamentação ANATEL para Faixas ISM (Industrial, Scientific, and Medical)

No Brasil, o uso de equipamentos de radiocomunicação é regulamentado pela **Agência Nacional de Telecomunicações (ANATEL)**. Dispositivos que utilizam tecnologia LoRa operam em faixas ISM que são destinadas a radiocomunicação de radiação restrita, dispensadas de licença de funcionamento de estação, desde que atendam aos limites estabelecidos no **Ato nº 14448** (Requisitos Técnicos para Avaliação da Conformidade de Equipamentos de Radiocomunicação de Radiação Restrita).

As duas principais faixas de frequência utilizadas para o Meshtastic no Brasil são:

### 1.1 A Faixa de 915 MHz (902 MHz a 907.5 MHz & 915 MHz a 928 MHz)
*   **Padrão do Firmware Meshtastic:** Configuração de região **`US`** (United States).
*   **Limites de Potência:** A potência máxima de saída do transmissor não deve exceder **1 Watt (30 dBm)** para sistemas de espalhamento espectral ou modulação digital. A densidade espectral de potência não deve ser maior que 8 dBm em qualquer faixa de 3 kHz.
*   **Uso Prático:** É a faixa mais comum para rádios como Heltec V3, LILYGO T-Beam e T-Echo no Brasil. Por compartilhar as frequências com o padrão americano (`US`), os dispositivos conseguem operar na subfaixa de 915-928 MHz sem violar as diretrizes locais.

### 1.2 A Faixa de 2.4 GHz (2400 MHz a 2483.5 MHz)
*   **Padrão do Firmware Meshtastic:** Configuração de região **`LORA_24`**.
*   **Características:** Opera na mesma frequência física de tecnologias como Wi-Fi e Bluetooth, porém utiliza modulação LoRa (geralmente baseada em transceptores como o chip Semtech SX1280).
*   **Vantagens:** Permite antenas muito menores, maior largura de banda (taxa de dados mais rápida) e uso internacional unificado (sem divisões regionais complexas).

---

## 2. Configurações de Região no Simulador (RegionCode)

Para garantir que o nó simulado seja identificado corretamente pelo relógio como um nó brasileiro regulamentado, a configuração padrão de região definida no Protobuf `Config.LoRaConfig` deve ser:

*   **`RegionCode.US`** (Valor inteiro = `1`) para operação em 915 MHz.
*   **`RegionCode.LORA_24`** (Valor inteiro = `13`) para operação na faixa global de 2.4 GHz.

---

## 3. Parametrização dos Canais da Comunidade Brasileira

A comunidade Meshtastic Brasil adota o padrão internacional de modulação e canais, mas com personalizações de nomes para facilitar a identificação local em clusters urbanos ou rurais.

### 3.1 Modem Preset: `LONG_FAST`
É a parametrização de modulação recomendada e padrão para a rede Mesh de uso geral. Equilibra de maneira ideal a distância de alcance (range) e a velocidade de transmissão de pequenos textos.

*   **Largura de Banda (Bandwidth):** 125 kHz
*   **Fator de Espalhamento (Spreading Factor - SF):** 9
*   **Taxa de Codificação (Coding Rate - CR):** 4/5

### 3.2 O Canal Primário (Canal 0)
Todo nó Meshtastic Brasil deve vir configurado com o canal primário público para que consiga escutar e retransmitir tráfego geral da malha (incluindo pacotes de geolocalização e telemetria de nós vizinhos).

*   **Nome do Canal:** `"Brasil"` ou `"LongFast"` (Sensível a maiúsculas/minúsculas).
*   **Pre-Shared Key (PSK):** Utiliza a chave padrão de criptografia pública do Meshtastic (representada por um único byte `0x01` no protobuf).
*   **Uso:** Canal principal de chat público, envio de coordenadas geográficas e formação da topologia da rede mesh.

### 3.3 Canais Secundários (Canais 1 a 7)
São canais opcionais configurados por grupos de usuários (ex: equipes de resgate, trilheiros ou redes privadas de condomínios).
*   Podem ter nomes específicos (ex: `"Seguranca"`, `"Trilha"`).
*   Utilizam chaves PSK customizadas geradas via gerador de chaves de 128/256 bits (AES) do aplicativo para manter as mensagens privadas e ilegíveis para nós fora do grupo.

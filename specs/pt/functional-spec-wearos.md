# Especificações Funcionais (FSD) - Wear OS Client PTT

Este documento descreve as funcionalidades, interface do usuário (UI), comportamento e experiência de uso (UX) do aplicativo Wear OS para comunicação offline na rede Meshtastic.

---

## 1. Interface Circular e Navegação

O aplicativo foi projetado sob os princípios de design de smartwatches (Wear OS), otimizado para telas circulares (como o Galaxy Watch) para evitar cortes de texto e maximizar a área de toque.

### 1.1 Duplo Comportamento de Rolagem (Double Scroll)
Para acomodar tanto a leitura de mensagens longas quanto o acesso rápido aos botões de controle, o aplicativo implementa dois níveis independentes de rolagem:
1. **Rolagem Principal (Geral):**
   - Arrasta a tela inteira verticalmente para rolar entre o Chip de status (topo), o Card de Mensagens (centro) e a Barra de Ações (base).
   - **Áreas de Toque Laterais:** O card central de mensagens possui margens expandidas nas bordas esquerda e direita (com largura de tela ajustada para `fillMaxWidth(0.78f)`). O usuário pode arrastar o dedo nessas bordas laterais para rolar a tela geral sem interferir no texto.
2. **Rolagem Interna (Histórico de Conversa):**
   - O card de mensagens possui altura física fixa de `72.dp`.
   - Mensagens longas que quebram linha acumulam-se em um container rolável independente. Ao arrastar o dedo verticalmente no centro do card, apenas as mensagens deslizam, permitindo a leitura completa do texto do início ao fim.
   - **Rolagem Automática:** Sempre que uma nova mensagem (de áudio, texto, GPS ou bateria) é recebida ou enviada, a exibição rola automaticamente para o final, garantindo que o tráfego mais recente esteja sempre visível.

---

## 2. Controles de Transmissão e Modos

### 2.1 Botão de Ação Primário Unificado
A interface concentra a ação primária de envio em um único botão circular centralizado de destaque (`size(72.dp)`):
* **Modo Voz (Padrão):**
   - O botão exibe o rótulo **PTT**.
   - **Comportamento Walkie-Talkie:** O usuário mantém o dedo pressionado sobre o botão para iniciar a gravação local (o rótulo muda para **FALE**). Ao soltar o botão, a gravação é interrompida e o texto transcrito é imediatamente transmitido pela rede.
* **Modo Texto:**
   - O botão exibe o rótulo **TXT**.
   - **Comportamento de Escrita:** Um toque simples sobre o botão abre a interface nativa de digitação (teclado virtual) do Wear OS para entrada direta da mensagem de texto.

### 2.2 Botão de Alternância de Modo (Microfone/Balão)
* Localizado no **lado esquerdo** do botão principal, alinhado ao centro.
* Exibe o ícone de microfone (🎙️) no Modo Voz e um balão de conversa (💬) no Modo Texto.
* Permite mudar instantaneamente o comportamento do botão principal entre PTT (voz) e TXT (digitação).

---

## 3. Telemetria e Posição Geográfica (GPS)

### 3.1 Envio de Coordenadas de GPS
* Localizado no **lado direito** do botão principal.
* Exibe o ícone de alfinete (📍).
* Ao ser clicado, envia instantaneamente a posição GPS atual do relógio para todos os nós conectados na malha LoRa.
* O remetente visualiza no histórico a confirmação local: `"Você: GPS enviado: [lat], [lon]"`.

### 3.2 Exibição Dinâmica do Botão de Bateria (Opcional)
Por padrão, o botão de bateria (🔋) fica oculto para reduzir a poluição visual na tela circular.
* **Configuração:** O usuário pode habilitar a exibição da bateria abrindo o diálogo de configurações ("Configurar Conexão" no topo da tela) e ativando a chave **"Exibir Bateria"** (ToggleChip).
* **Adaptação Dinâmica do Layout:**
  - **Bateria Desabilitada (Padrão):** O lado direito do botão central exibe apenas o botão de GPS (📍) alinhado ao centro.
  - **Bateria Habilitada:** O lado direito do botão central adapta-se para uma coluna vertical de dois botões: o de GPS (📍) posicionado na linha superior (`upper line` do PTT) e o de Bateria (🔋) posicionado na linha inferior (`bottom line` do PTT).

---

## 4. Visualização de Coordenadas e Integração com Mapas

Quando uma coordenada de GPS é transmitida na rede e chega ao outro relógio:
1. **Exibição do Texto:** A mensagem é formatada e exibida como `"GPS: Lat [lat], Lon [lon], Alt [alt]m"`.
2. **Silenciamento de TTS:** O motor de voz inteligente detecta o padrão `"GPS:"` e permanece em silêncio (não lê as coordenadas geografias em voz alta).
3. **Botão de Mapa Integrado:** Um ícone de mapa (🗺️) é renderizado no lado direito do balão de mensagem no histórico.
4. **Abrir no Google Maps/Waze:** Ao tocar no ícone do mapa (🗺️), o aplicativo dispara um seletor nativo do sistema operacional. O usuário pode escolher abrir o ponto geográfico diretamente no **Google Maps**, **Waze** ou qualquer outro app de mapas instalado no relógio, permitindo traçar rotas e ver a posição em tempo real.

# language: pt
Funcionalidade: Comunicação por Voz e Texto com Mock Meshtastic

  Cenário: Transmitir mensagem de voz via PTT físico e receber resposta lida em voz alta (Modo Voz)
    Dado que o simulador Meshtastic está escutando na porta local
    E o aplicativo Wear OS iniciou no Modo Voz com o Mock de TTS injetado
    Quando o usuário pressiona o botão físico de hardware (STEM_1) do relógio
    E a transcrição da voz simulada resulta em "Mensagem de áudio de teste"
    E o usuário solta o botão físico
    Então a mensagem "Mensagem de áudio de teste" deve ser enviada ao simulador
    E quando o simulador envia a resposta de confirmação de texto "Received via LoRa Mock: Mensagem de áudio de teste"
    Então o relógio deve receber o texto e falar automaticamente "Received via LoRa Mock: Mensagem de áudio de teste" via TTS

  Cenário: Alternar para Modo Texto e conversar sem leitura por voz
    Dado que o simulador Meshtastic está escutando na porta local
    E o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o usuário altera o modo de operação para "Texto"
    E o usuário envia o texto "Alerta de coordenadas" para a malha
    Então a mensagem "Alerta de coordenadas" deve ser transmitida
    E quando o simulador envia a resposta de confirmação de texto "Received via LoRa Mock: Alerta de coordenadas"
    Então a mensagem "Received via LoRa Mock: Alerta de coordenadas" deve ser exibida na tela do relógio
    E o motor de voz TTS não deve falar nenhuma mensagem

  Cenário: Não exibir popup de alerta de Sayboard Ausente quando o teclado está instalado
    Dado que o teclado Sayboard está registrado como instalado
    Quando o aplicativo Wear OS inicia
    Então o alerta de "Sayboard Ausente" não deve ser exibido

  Cenário: Exibir popup de alerta de Sayboard Ausente quando o teclado não está instalado
    Dado que o teclado Sayboard está registrado como não instalado
    Quando o aplicativo Wear OS inicia
    Então o alerta de "Sayboard Ausente" deve ser exibido na tela

  Cenário: Configurar e alternar para conexão por pareamento Bluetooth BLE
    Dado o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o usuário abre as configurações de conexão
    E seleciona pareamento Bluetooth BLE com sucesso
    Então o status da conexão na tela deve mudar para "CONECTADO (BLE)"

  Cenário: Desconectar da malha Meshtastic
    Dado o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o usuário abre as configurações de conexão
    E seleciona desconectar da malha
    Então o status da conexão na tela deve mudar para "DESCONECTADO"

  Cenário: Exibir mensagens longas com quebra de linha sem cortar o texto
    Dado que o simulador Meshtastic está escutando na porta local
    E o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o usuário envia o texto "Esta é uma mensagem muito longa que deve quebrar em múltiplas linhas para testar o limite de linhas da tela do relógio" para a malha
    Então a mensagem "Esta é uma mensagem muito longa que deve quebrar em múltiplas linhas para testar o limite de linhas da tela do relógio" deve ser exibida na tela do relógio

  Cenário: Clicar no ícone de mapa de uma mensagem de coordenada GPS para abrir o mapa
    Dado que o simulador Meshtastic está escutando na porta local
    E o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o simulador envia um pacote de coordenadas com latitude "-23.5615" e longitude "-46.6560"
    Então a mensagem contendo "GPS: Lat -23.5615, Lon -46.656" deve ser exibida na tela do relógio
    E o motor de voz TTS não deve falar nenhuma mensagem
    E o ícone de mapa deve estar visível ao lado da mensagem
    Quando o usuário clica no ícone de mapa da mensagem
    Então uma Intent de geolocalização com URI "geo:-23.5615,-46.656?q=-23.5615,-46.656" deve ser disparada

  Cenário: Validar que a Intent de reconhecimento de voz configura o idioma Português-Brasil (pt-BR)
    Dado o aplicativo Wear OS está ativo com o Mock de TTS injetado
    Quando o usuário aciona o PTT com a injeção de áudio simulado "olá testando o chat em português"
    Então a Intent de reconhecimento de voz deve ser configurada com o tag BCP-47 "pt-BR"
    E a mensagem "olá testando o chat em português" deve ser transmitida

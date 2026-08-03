# Próximos Passos - Preparação para o GitHub

Este documento detalha o planejamento das tarefas necessárias para preparar o repositório do cliente Wear OS do Meshtastic para publicação no GitHub.

## 1. Internacionalização (i18n)
- **Documentação Multilíngue:**
  - Converter e organizar todas as especificações e guias na pasta `specs/` em subpastas por idioma:
    - `/specs/pt/` (mantendo os documentos originais em português).
    - `/specs/en/` (versões traduzidas em inglês).
- **Strings do Aplicativo:**
  - Identificar todas as strings em português que estão com valores fixos no código (*hardcoded*) nas telas Compose (como em `PttScreen.kt`) e lógicas de mensagens.
  - Migrar todas as mensagens e textos de interface para o sistema de recursos padrão do Android (`strings.xml`) e configurar suporte a múltiplos idiomas (Inglês como padrão e Português-Brasil como alternativa).

## 2. Documentação Principal (README)
- **Criação do README em Inglês:**
  - Criar um arquivo `README.md` completo em inglês na raiz do projeto contendo:
    - Descrição geral do projeto e arquitetura de voz/texto PTT.
    - Pré-requisitos (SDK Android, emuladores Wear OS).
    - Passo a passo detalhado de compilação (*build*).
    - Instruções de como inicializar o simulador Mesh Mock.
    - Guia para execução dos testes BDD (Cucumber/Espresso).

## 3. Limpeza de Caminhos Absolutos
- **Varredura de Paths:**
  - Rastrear e remover qualquer referência a caminhos absolutos do sistema de arquivos local (como `/Users/renatoalexandredacunha/...`) em scripts de inicialização, arquivos de configuração Gradle ou configurações de teste.
  - Garantir que todos os scripts (como `launch_two_emulators.sh`) utilizem caminhos relativos ao workspace ou variáveis de ambiente padrão (`$ANDROID_HOME`, etc.).

## 4. Oportunidades de Refatoração e SOLID
- **Redução de Responsabilidades:**
  - Revisar classes grandes ou acopladas, garantindo que a lógica de interface (Compose) permaneça puramente visual e que regras de negócio, decodificação de pacotes e disparo de intents fiquem no `ViewModel` ou em casos de uso dedicados.
- **Princípios SOLID:**
  - Aplicar o Princípio de Responsabilidade Única (SRP) e o Princípio de Inversão de Dependência (DIP), facilitando a testabilidade e isolamento de componentes de rede e áudio.

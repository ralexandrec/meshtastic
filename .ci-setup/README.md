# Guia de Testes de Integração e CI/CD Local (.ci-setup)

Este diretório contém toda a infraestrutura de suporte para execução local de testes de integração e para a pipeline de integração contínua (CI/CD) do GitHub Actions.

---

## 📁 Estrutura de Arquivos

```
.ci-setup/
├── README.md               # Este guia explicativo (PT-BR)
├── .actrc                  # Configurações do container Docker para a ferramenta 'act'
├── run-local-tests.sh      # Script automatizado para rodar a pipeline localmente via 'act'
├── mock_server.py          # Simulador em Python da rede Meshtastic (TCP port 4403)
├── config.py               # Constantes e configurações do simulador
├── requirements.txt        # Dependências Python do simulador
└── logs/                   # Diretório de saída dos logs (mock_server.log, relatórios)
```

---

## 🚀 Como Executar a Pipeline de CI Localmente com `act`

A ferramenta [`act`](https://github.com/nektos/act) permite rodar os workflows do GitHub Actions localmente dentro de containers Docker.

### 1. Pré-requisitos
- **Docker Desktop** (em execução).
- **act CLI** instalado na sua máquina:
  - **macOS**: `brew install act`
  - **Linux**: `curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash`

---

### 2. Passo a Passo para Execução Local

1. Certifique-se de que o **Docker Desktop está rodando**.
2. No terminal, navegue para a raiz do repositório:
   ```bash
   cd /caminho/para/meshtastic
   ```
3. Execute o script de testes locais:
   ```bash
   ./.ci-setup/run-local-tests.sh
   ```

O script irá automaticamente:
- Verificar o ambiente e suporte a KVM.
- Subir o container Docker configurado no `.actrc`.
- Executar os passos do workflow `.github/workflows/meshtastic_integration_tests.yml`:
  1. Instalação das dependências Python do simulador.
  2. Configuração do Java JDK 17 (Temurin) e Gradle.
  3. Inicialização do `mock_server.py` em background na porta TCP 4403.
  4. Healthcheck aguardando a porta 4403.
  5. Boot do emulador Android Wear OS (API 30 `x86_64`).
  6. Compilação e execução dos testes Cucumber: `./gradlew clean installDebug connectedDebugAndroidTest`.

---

## 🔍 Como Debugar e Analisar Erros

Caso ocorra alguma falha na execução local ou no GitHub Actions:

### 1. Logs do Servidor Simulador (`mesh_mock`)
Os logs gerados durante a execução do simulador ficam gravados no diretório:
```
.ci-setup/logs/mock_server.log
```
Você pode inspecioná-los com:
```bash
cat .ci-setup/logs/mock_server.log
```

### 2. Relatórios de Testes Gradle / Cucumber
Após a execução dos testes Android, os relatórios detalhados em HTML e XML ficam localizados em:
```
wear/wear/build/reports/androidTests/connected/index.html
wear/wear/build/outputs/androidTest-results/connected/
```

### 3. Debugando Erros de Emulador / Docker via `act`
Para rodar o `act` com suporte interativo ou modo verboso (debug):
```bash
act push -W .github/workflows/meshtastic_integration_tests.yml --config-file .ci-setup/.actrc -v
```

---

## ⚙️ Regras do Projeto & Boas Práticas

- **Comando Obrigatório Gradle:** Sempre utilze a sequência limpa `./gradlew clean installDebug connectedDebugAndroidTest` ao alterar os testes Cucumber.
- **Estrutura Limpa:** Mantenha arquivos de suporte e logs estritamente dentro da pasta `.ci-setup/`.

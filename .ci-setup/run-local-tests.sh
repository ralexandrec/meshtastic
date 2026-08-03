#!/bin/bash
# -*- coding: utf-8 -*-

# Script para executar a pipeline do GitHub Actions localmente via 'act'

set -e

# Cores para logs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CLEAR='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}[CI-LOCAL] Inicializando ambiente de testes locais via 'act'...${CLEAR}"

# 1. Garantir diretório de logs
mkdir -p "$SCRIPT_DIR/logs"

# 2. Verificar instalação do 'act'
if ! command -v act &> /dev/null; then
    echo -e "${RED}[ERRO] A ferramenta 'act' não foi encontrada.${CLEAR}"
    echo -e "${YELLOW}Para instalar no macOS:${CLEAR} brew install act"
    echo -e "${YELLOW}Para instalar no Linux:${CLEAR} curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash"
    exit 1
fi

# 3. Verificar daemon do Docker
if ! docker info &> /dev/null; then
    echo -e "${RED}[ERRO] O daemon do Docker não está rodando. Inicie o Docker Desktop antes de continuar.${CLEAR}"
    exit 1
fi

# 4. Verificar suporte a KVM / Aceleração de Hardware no Host
if [ -e /dev/kvm ]; then
    echo -e "${GREEN}[CI-LOCAL] Dispositivo /dev/kvm detectado no host!${CLEAR}"
    KVM_ARG="--container-options \"--device /dev/kvm\""
else
    echo -e "${YELLOW}[CI-LOCAL] /dev/kvm não encontrado diretamente no host (comum no macOS/Colima).${CLEAR}"
    KVM_ARG=""
fi

# 5. Carregar configurações do .ci-setup/.actrc
ACT_RC_FLAGS=""
if [ -f "$SCRIPT_DIR/.actrc" ]; then
    ACT_RC_FLAGS=$(grep -v '^#' "$SCRIPT_DIR/.actrc" | tr '\n' ' ')
fi

# 6. Executar 'act' apontando para a pipeline do repositório
cd "$PROJECT_ROOT"
echo -e "${BLUE}[CI-LOCAL] Disparando execução do workflow .github/workflows/meshtastic_integration_tests.yml...${CLEAR}"

act push \
    -W .github/workflows/meshtastic_integration_tests.yml \
    $ACT_RC_FLAGS \
    $KVM_ARG \
    "$@"

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo -e "\n${GREEN}============================================================${CLEAR}"
    echo -e "${GREEN}  SUCESSO: Testes de integração locais via 'act' concluídos!${CLEAR}"
    echo -e "${GREEN}============================================================${CLEAR}"
else
    echo -e "\n${RED}============================================================${CLEAR}"
    echo -e "${RED}  FALHA: Ocorreram erros na pipeline local. Verifique os logs. ${CLEAR}"
    echo -e "${RED}============================================================${CLEAR}"
fi

exit $EXIT_CODE

#!/bin/bash
# -*- coding: utf-8 -*-

# Automation script to run mesh_mock BDD tests (Python Behave)

# Console colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CLEAR='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}[INFO] Accessing mesh_mock folder...${CLEAR}"
cd "$PROJECT_ROOT/mesh_mock" || { echo -e "${RED}[ERROR] Folder mesh_mock not found!${CLEAR}"; exit 1; }

# 1. Activate virtualenv
if [ -d ".venv" ]; then
    echo -e "${BLUE}[INFO] Activating Python virtual environment...${CLEAR}"
    source .venv/bin/activate
else
    echo -e "${RED}[ERROR] Virtual environment .venv not found in mesh_mock/.${CLEAR}"
    exit 1
fi

# 2. Run Behave tests (Python Cucumber)
echo -e "${BLUE}[INFO] Starting Cucumber (Behave) tests...${CLEAR}"
behave
TEST_EXIT_CODE=$?

# 3. Return status
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "\n${GREEN}============================================================${CLEAR}"
    echo -e "${GREEN}  SUCCESS: All mesh_mock BDD tests passed!        ${CLEAR}"
    echo -e "${GREEN}============================================================${CLEAR}"
    exit 0
else
    echo -e "\n${RED}============================================================${CLEAR}"
    echo -e "${RED}  FAILURE: One or more mesh_mock BDD tests failed.        ${CLEAR}"
    echo -e "${RED}============================================================${CLEAR}"
    exit 1
fi

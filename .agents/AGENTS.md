# Regras do Projeto - Meshtastic Wear OS Client

Este arquivo contém as diretrizes e regras de comportamento para os agentes programando neste repositório.

## Execução de Testes e Recarga de Versões
- **Compilação e Instalação Limpa:** Sempre que for rodar os testes BDD (Cucumber/AndroidTest), garanta que o aplicativo seja completamente limpo, rebuildado e reinstalado nos emuladores.
- **Comando Obrigatório:** Em vez de rodar apenas `connectedDebugAndroidTest`, utilize sempre a sequência completa:
  ```bash
  ./gradlew clean installDebug connectedDebugAndroidTest
  ```
- Isso garante que a versão do app em execução no emulador seja sempre a mais recente com as últimas alterações visuais e lógicas.

## Manutenção de Especificações Bilíngues (i18n)
- **Documentação de Specs:** Sempre que uma funcionalidade (feature) for criada, alterada ou removida, as especificações correspondentes localizadas em `specs/` e `mesh_mock/specs/` devem ser rigorosamente atualizadas em ambos os idiomas (Português-Brasil em `pt/` e Inglês em `en/`).
- **Nomes de Arquivo Localizados:** Certifique-se de que os nomes dos arquivos sejam equivalentes e traduzidos para o respectivo idioma da pasta (ex: `especificacao-funcional.md` em `pt/` correspondendo a `functional-specification.md` em `en/`).


# Next Steps - Preparing for GitHub

This document details the planning of tasks required to prepare the Meshtastic Wear OS client repository for publishing on GitHub.

## 1. Internationalization (i18n)
- **Multilingual Documentation:**
  - Convert and organize all specifications and guides in the `specs/` folder into language subfolders:
    - `/specs/pt/` (keeping the original Portuguese documents).
    - `/specs/en/` (translated English versions).
- **Application Strings:**
  - Identify all Portuguese strings that are hardcoded in the codebase within Compose screens (such as in `PttScreen.kt`) and message logic.
  - Migrate all messages and interface texts to the standard Android resource system (`strings.xml`) and configure support for multiple languages (English as default and Portuguese-Brazil as alternative).

## 2. Main Documentation (README)
- **Creation of README in English:**
  - Create a complete `README.md` file in English in the root of the project containing:
    - Overview of the project and voice/text PTT architecture.
    - Prerequisites (Android SDK, Wear OS emulators).
    - Detailed step-by-step build instructions.
    - Instructions on how to initialize the Mesh Mock simulator.
    - Guide for running BDD tests (Cucumber/Espresso).

## 3. Absolute Paths Cleanup
- **Paths Sweep:**
  - Track and remove any reference to local filesystem absolute paths (such as `/Users/renatoalexandredacunha/...`) in startup scripts, Gradle configuration files, or test setups.
  - Ensure all scripts (such as `launch_two_emulators.sh`) use paths relative to the workspace or standard environment variables (`$ANDROID_HOME`, etc.).

## 4. Refactoring Opportunities and SOLID
- **Responsibility Reduction:**
  - Review large or coupled classes, ensuring Compose UI logic remains purely visual and that business logic, packet decoding, and intent broadcasting are in the `ViewModel` or dedicated use cases.
- **SOLID Principles:**
  - Apply the Single Responsibility Principle (SRP) and Dependency Inversion Principle (DIP), facilitating testability and isolation of network and audio components.

<p align="center">
  <h1 align="center">MiniMe-core</h1>
  <p align="center">
    AI-powered coding assistant for Android · Built-in Linux terminal · AI Agent · MCP · Git integration
    <br />
    <a href="README.md">中文</a> · <a href="README.en.md">English</a>
  </p>
</p>

<p align="center">
  <a href="https://github.com/Lisir2002/MiniMe-core/releases/latest"><img src="https://img.shields.io/github/v/release/Lisir2002/MiniMe-core?display_name=tag&include_prereleases" alt="Latest Release" /></a>
  <a href="https://github.com/Lisir2002/MiniMe-core/releases"><img src="https://img.shields.io/github/downloads/Lisir2002/MiniMe-core/total" alt="Total Downloads" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License GPL-3.0" /></a>
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Jetpack Compose UI" />
  <img src="https://img.shields.io/badge/MinSDK-26-orange.svg" alt="Min SDK 26 (Android 8.0)" />
</p>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Companion App](#companion-app)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [FAQ](#faq)
- [For Developers: Build & Test](#for-developers-build--test)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Documentation](#documentation)
- [Known Limitations](#known-limitations)
- [Contributing](#contributing)
- [Acknowledgements](#acknowledgements)
- [License](#license)

## Overview

MiniMe-core is an AI-powered coding assistant that runs natively on Android. It integrates large language models with a local Linux development environment. The built-in Alpine Linux container and terminal emulator let the AI directly read/write files, execute shell commands, and run build tools. It also supports remote SSH servers as the execution backend, turning your phone into a mobile workstation for remote projects.

**Latest Release**: [v0.0.0.15](https://github.com/Lisir2002/MiniMe-core/releases/tag/v0.0.0.15) — Per-model sampling parameter override & default model selection experience upgrade

## Features

- **AI Agent** — Supports Anthropic (Claude), OpenAI (GPT), Gemini, and other providers. Deeply interacts with the dev environment via 20+ built-in tools (file read/write/edit, shell execution, terminal management, web search/fetch, image generation, MCP management, etc.). Supports streaming output, context compression, multi-session management, and PLAN/BUILD/AUTO execution modes
- **Model Management** — Custom model provider configuration, 8-dimension capability tags (vision/video/audio/tools/reasoning/code/structured output/context window), per-model sampling parameter override, provider-level connectivity test, failover with backup provider
- **Permission & Safety** — Seven-layer permission evaluation engine: catastrophic command interception, PLAN-mode read-only constraints, shell static analysis, built-in read-only whitelist, user approval and rule memory. Provider editor supports screenshot/screen-recording prevention
- **Checkpoints & Rollback** — Automatic file snapshots before modifications, with rollback to any checkpoint
- **Built-in Terminal** — Based on Termux components + PRoot Alpine Linux container, providing a full Linux command-line environment with background persistence, multi-tab management, and 7 built-in bundles (Python/Node/Git/Bash/rg/Network tools/QEMU x86 translator)
- **Remote SSH Mode** — Connect to a remote SSH server as the execution backend. Commands via exec channel, file I/O via exec + cat/base64, terminal via shell channel, with auto-reconnect and status indicator
- **MCP Protocol** — Model Context Protocol client, connecting to local (stdio) or remote (HTTP) MCP servers to dynamically extend tool capabilities
- **Git Integration** — Built-in visual Git operations (status/branches/commits/tags/graph), with unified credential management across three endpoints (UI Git / AI Bash / terminal git)
- **Remote Sync** — SFTP / FTP workspace sync, with a built-in FTP server for desktop access
- **Backup & Restore** — AES-256-GCM encrypted full data backup (sessions/config/credentials/workspace), with optional passphrase protection
- **Markdown Rendering** — Real-time Markdown rendering in AI conversations, with code highlighting
- **Custom Prompts** — System prompts support user-defined overrides, preserved across app upgrades

## Companion App

| App | Description | Latest Release |
|-----|-------------|----------------|
| **MiniMe Logs** | Independent log viewer companion app. Reads the main app's runtime logs with level filtering, keyword search, real-time tailing, crash analysis, and long-press menus | [v0.0.6](https://github.com/Lisir2002/MiniMe-core/releases/tag/logviewer-v0.0.6) |

## Installation

> MiniMe-core is distributed as an APK. No compilation needed — just download and install.

1. Go to the [Releases page](https://github.com/Lisir2002/MiniMe-core/releases) and download the latest APK.
2. Transfer the APK to your phone/emulator (browser download / cloud drive / USB).
3. Tap the APK to install. If prompted about "unknown sources", allow "Install unknown apps" in system settings (path varies by brand).

**APK naming convention**: Starting from v0.0.0.16, APKs follow `MiniMe-{version}-{variant}.apk` (e.g. `MiniMe-v0.0.0.16-release.apk`). Historical releases use the `minime-{version}.apk` naming.

**Prerequisites**

- **Physical device (officially supported)**: Android 8.0+ (API 26) arm64-v8a device (the mainstream ABI for current Android handsets)
- **Virtual environment (emulator / VM)**: x86_64 or arm64 system images both work — the same universal package installs and runs; the container auto-selects by host architecture (x86_64 native proot, arm64 native execution)

## Quick Start

1. **Add an AI provider**: Go to "Settings → Model Management" and add a custom provider with API Key, Base URL, and model list.
2. **Start chatting**: Return to the home screen and describe what you need; the AI can read/write files and run commands. Use the PLAN / BUILD / AUTO execution modes to control how much freedom the AI has.
3. **Use the terminal**: Open the "Terminal" tab and install the built-in bundles you need (Python / Node / Git / Bash, etc.) for a full Linux command-line environment.
4. **Connect remote**: Configure a remote SSH server in Settings to turn your phone into a mobile workstation for remote projects.
5. **Extend capabilities**: Add MCP servers to dynamically extend tools; connect the built-in browser so the AI can read pages behind your login session.

For more details, see the in-app help (Settings → Help) and the [Documentation](#documentation) section.

## FAQ

**Q: Why can't this be published on Google Play?**
A: To support PRoot container execution, targetSdk is locked at 28 to bypass Android 10+ W^X policy — the same trade-off Termux makes. This does not meet Google Play's targetSdk requirement.

**Q: Why is the APK so large?**
A: The APK is a dual-ABI universal package (arm64-v8a + x86_64) with an embedded Alpine Linux rootfs and 7 built-in bundles, ensuring it works out of the box.

**Q: Which AI providers are supported?**
A: All providers compatible with OpenAI / Anthropic / Gemini API protocols, including but not limited to OpenAI, Anthropic, Google Gemini, DeepSeek, Qwen, Zhipu GLM, Doubao, StepFun, etc.

**Q: Is my data secure?**
A: All API keys and credentials are encrypted with Android Keystore. Backup files use AES-256-GCM encryption. The provider editor supports screenshot/screen-recording prevention. Conversation data is stored locally only and never uploaded to third-party servers (except the AI provider API you configure).

**Q: How do I view app logs?**
A: Use the companion app [MiniMe Logs](https://github.com/Lisir2002/MiniMe-core/releases/tag/logviewer-v0.0.6) to read and analyze the main app's runtime logs.

## For Developers: Build & Test

### Prerequisites

- JDK 17

### Build

```bash
# Daily dev smoke build (recommended, debug APK only; faster, no R8)
./gradlew :app:assembleDebug

# Release build (signing config required; auto-falls back to debug keystore when missing)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk (dual-ABI universal package: arm64-v8a + x86_64)

# Release AAB
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

<details>
<summary>Release signing configuration</summary>

Add to `app/keystore.properties`:

```properties
storeFile=minime.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_key_password
```

`storeFile` path is customizable (filename not fixed). CI restores it from secrets to `app/minime.jks`. When signing config is not present, release build auto-falls back to the debug keystore, so `assembleRelease` always produces an APK.

</details>

### Test

```bash
# Unit tests on release classpath (matches CI gate & user runtime; recommended)
./gradlew :app:testReleaseUnitTest
# Unit tests on debug classpath
./gradlew :app:testDebugUnitTest
```

### Cloud build (GitHub Actions release automation)

Releases are tag-driven: push a `v*` tag on a `main` commit (e.g. `git push origin v0.0.0.16` / `v0.0.0.16-rc1`) and [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) takes over automatically: unit tests → assembleRelease → production signing → dual-ABI artifact validation → upload R8 mapping → create GitHub Release → attach APK → write Run Summary. RC tags (containing `-rc`) are auto-marked as prerelease.

- **Production-signing prerequisite**: the repository `Settings → Secrets → Actions` must define 4 secrets — `AICODE_KEYSTORE_BASE64` / `AICODE_KEYSTORE_PASSWORD` / `AICODE_KEY_ALIAS` / `AICODE_KEY_PASSWORD`. Missing any one silently falls back to the debug keystore, and the artifact cannot be published.
- **Real-time monitoring & artifact verification**, full commands, and CI job details: see [docs/ci-release.md](./docs/ci-release.md) (cloud build & release operations manual).
- **Release conventions**: APK naming, title format, and body format mandatory constraints are in [AGENTS.md Release Conventions](./AGENTS.md#发版规范最高优先级--强制约束--发版前逐条核对).

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.2.21 |
| Build | Android Gradle Plugin 8.9.3 + KSP |
| UI | Jetpack Compose (BOM 2025.12.01) + Material 3 |
| DI | Hilt 2.56.1 (Dagger) |
| Database | SQLDelight 2.2.1 (6-DB topology: agent / credentials / settings / workspace / t2i / infra; optional SQLCipher AES-256 encryption, plaintext by default, can be enabled in settings) |
| Network | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson |
| Async | Kotlin Coroutines / Flow |
| Terminal | Termux terminal-emulator + terminal-view (JNI libtermux.so) |
| Container | PRoot + Alpine Linux 3.21 rootfs (arm64-v8a / x86_64) |
| Remote SSH | SSHJ 0.38.0 (exec channel + shell channel) |
| Crypto | BouncyCastle bcprov-jdk18on 1.75 + Android Keystore AES-GCM |
| FTP | Apache Commons Net 3.10.0 |
| Compression | Apache Commons Compress 1.26.2 (tar.gz / XZ) |
| Serialization | Gson + kotlinx.serialization |

## Project Structure

```
app/src/main/java/com/mini/me_core/
├── core/                # Core infrastructure (FileLogger, AILogger, db/MigrationLoader, CredentialEncryptor, LineDiff, theme)
├── di/                  # Hilt DI (AgentModule, RepositoryModule, BackupModule)
├── feature/
│   ├── agent/           # AI Agent (MVI workflow, 20+ tools, permission engine, MCP, skills, memory, checkpoints, provider adapters)
│   ├── backup/          # AES-encrypted backup & restore
│   ├── browser/         # Built-in browser (WebView sessions, login takeover, dynamic data capture)
│   ├── capability/      # Capability hub (aggregated tools/agents/skills view)
│   ├── credentials/     # Git credential management (3-endpoint IPC bridge, file sync, global dialog)
│   ├── git/             # Git visualization (status/branches/commits/tags/graph/diff)
│   ├── proxy/           # Network proxy (mihomo core, subscriptions, routing injection)
│   ├── settings/        # App settings (model management, container, MCP, remote, logs, etc.)
│   ├── t2i/             # Text-to-image (provider abstraction, SYNC/ASYNC/AUTO endpoints)
│   ├── terminal/        # Terminal emulation & session management (local PRoot + remote SSH, 7 built-in bundles)
│   └── workspace/       # Workspace & document management (local + remote SFTP/FTP sync)
├── MiniMeCore.kt       # Application entry (BC registration, credential bridge, MCP, keepalive init)
└── MainActivity.kt      # Main Activity (NavHost + Drawer + global credential dialog)
```

End-to-end architecture notes around core modules: `agent` (AI Agent & MCP integration), `terminal` (terminal & container), `settings`, `git`, `workspace`, `credentials`, `backup` (see [AGENTS.md](./AGENTS.md#架构概览)).

## Documentation

| Document | Description |
|---|---|
| [AGENTS.md](./AGENTS.md) | AI collaboration guidelines: asset sync discipline, Conventional Commits, branching workflow, release conventions (highest-priority mandatory constraints) |
| [CHANGELOG.md](./docs/Version%20Log/CHANGELOG.md) | User-facing version changelog (per-version logs) |
| [docs/ci-release.md](./docs/ci-release.md) | Cloud build & release operations manual: full CI pipeline, artifact verification, signing strategy |
| [SECURITY.md](./SECURITY.md) | Security policy and vulnerability reporting |
| `app/src/main/assets/docs/` | In-app help documents (viewable at runtime via Settings → Help) |

## Known Limitations

- `targetSdk` is locked at 28 to bypass Android 10+ W^X policy, enabling PRoot execution; trade-off: ineligible for Google Play (same as Termux).
- Release artifacts are dual-ABI universal packages (arm64-v8a + x86_64):
  - Supports all mainstream Android physical devices (Snapdragon/Dimensity/Kirin and other 64-bit ARM chipsets) plus x86_64 / arm64 emulators and VMs;
  - On an extremely rare host ABI (neither arm64 nor x86_64) the container is unavailable; the AI core (chat / files / remote SSH) still works, while container/terminal show an explicit degradation notice.

## Contributing

Issues and PRs are welcome. Please follow the rules in [AGENTS.md](./AGENTS.md): Conventional Commits, asset sync discipline (UI strings go to `strings.xml`). Enable local validation before committing: `git config core.hooksPath .githooks`.

## Acknowledgements

- [OpenCode](https://github.com/anomalyco/opencode) — Terminal-based AI coding tool, the core inspiration for this project
- [Termux](https://github.com/termux/termux-app) — Android terminal emulator, provided terminal components and PRoot solution
- [Kelivo](https://github.com/Chevey339/kelivo) — Cross-platform LLM chat client, AI conversation UI design reference

## License

This project is licensed under [GPL-3.0](LICENSE).

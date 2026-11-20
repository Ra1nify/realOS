# ~~realOS~~

**English** | [简体中文](README.zh-CN.md)

realOS is a modular LSPosed enhancement suite for ColorOS & realme UI.

Detailed documentation, architecture specifications, and guides are hosted on the **[realOS Wiki](https://github.com/Ra1nify/realOS/wiki)**.

## Scope

| Feature | Scope Key | Package | Wiki |
| --- | --- | --- | --- |
| Keyboard | `opluskeyboard` | `com.oplus.keyboard` | [English](https://github.com/Ra1nify/realOS/wiki/Keyboard) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Keyboard-zh) |
| Navigation | `navigation` | `com.android.systemui` · Settings | [English](https://github.com/Ra1nify/realOS/wiki/Navigation) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Navigation-zh) |
| System Fonts | `font` | `com.android.settings` | [English](https://github.com/Ra1nify/realOS/wiki/Fonts) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Fonts-zh) |
| Clock Fonts | `clockfont` | `com.oplus.aod` · `com.android.systemui` | [English](https://github.com/Ra1nify/realOS/wiki/Clock-Fonts) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Clock-Fonts-zh) |
| Launcher | `launcher` | `com.android.launcher` | [English](https://github.com/Ra1nify/realOS/wiki/Launcher) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Launcher-zh) |
| Camera | `camera` | `com.oplus.camera` | [English](https://github.com/Ra1nify/realOS/wiki/Camera) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Camera-zh) |
| Wallet | `wallet` | `com.finshell.wallet` | [English](https://github.com/Ra1nify/realOS/wiki/Wallet) · [简体中文](https://github.com/Ra1nify/realOS/wiki/Wallet-zh) |
| System Enhancements | `featureswitch` · `mediaprovider` · `fido` · `intentroute` | `system` · Settings · Media | [English](https://github.com/Ra1nify/realOS/wiki/System-Enhancements) · [简体中文](https://github.com/Ra1nify/realOS/wiki/System-Enhancements-zh) |

## Clone & Submodules

This repository uses submodules (DexKit). Clone recursively:

```bash
git clone --recursive https://github.com/Ra1nify/realOS.git
```

Or initialize after cloning:

```bash
git submodule update --init --recursive
```

## License

GPLv3 — see [LICENSE](LICENSE). Documentation is CC BY-SA 4.0.

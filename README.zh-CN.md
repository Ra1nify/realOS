# ~~realOS~~

[English](README.md) | **简体中文**

realOS 是面向 ColorOS 与 realme UI 的模块化 LSPosed 体验增强套件。

详细功能介绍、架构规范与配置指南均维护在 **[realOS Wiki](https://github.com/Ra1nify/realOS/wiki/Home-zh)**。

## 支持的特性

| 特性 | Scope 标识 | 目标包名 | 文档 (Wiki) |
| --- | --- | --- | --- |
| 键盘定制 | `opluskeyboard` | `com.oplus.keyboard` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Keyboard-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Keyboard) |
| 导航与手势 | `navigation` | `com.android.systemui` · 系统设置 | [简体中文](https://github.com/Ra1nify/realOS/wiki/Navigation-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Navigation) |
| 系统字体 | `font` | `com.android.settings` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Fonts-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Fonts) |
| 锁屏时钟字体 | `clockfont` | `com.oplus.aod` · `com.android.systemui` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Clock-Fonts-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Clock-Fonts) |
| 桌面与多任务 | `launcher` | `com.android.launcher` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Launcher-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Launcher) |
| 相机专业特性 | `camera` | `com.oplus.camera` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Camera-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Camera) |
| 钱包卡面 | `wallet` | `com.finshell.wallet` | [简体中文](https://github.com/Ra1nify/realOS/wiki/Wallet-zh) · [English](https://github.com/Ra1nify/realOS/wiki/Wallet) |
| 系统底层增强 | `featureswitch` · `mediaprovider` · `fido` · `intentroute` | `system` · 设置 · 媒体 | [简体中文](https://github.com/Ra1nify/realOS/wiki/System-Enhancements-zh) · [English](https://github.com/Ra1nify/realOS/wiki/System-Enhancements) |

## 克隆与子模块

本项目包含子模块（DexKit）。克隆时请附带 `--recursive`：

```bash
git clone --recursive https://github.com/Ra1nify/realOS.git
```

或者在已有仓库中初始化：

```bash
git submodule update --init --recursive
```

## 许可

GPLv3，见 [LICENSE](LICENSE)。文档采用 CC BY-SA 4.0。

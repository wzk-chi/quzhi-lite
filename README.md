# 智趣轻享

`quzhi-lite` 是一个无广告、轻量的原生 Android 客户端项目。

本项目仅供学习使用。

## 技术架构

- 开发语言：Kotlin
- UI：Jetpack Compose
- 设计系统：Material 3
- 应用模型：单 Activity、单 `app` 模块
- 最低支持版本：Android 7.0（API 24）
- 编译/目标版本：Android 16（API 36）
- Java 版本：17
- 构建工具：Android Gradle Plugin 8.11.1、Gradle 8.13
- ABI：`armeabi-v7a`、`arm64-v8a`

## 分层结构

```text
app/src/main/java/com/quzhi/lite/
├── MainActivity.kt       # 应用入口与页面状态组装
├── data/                  # 网络访问、数据模型、设备发现与本地存储
└── ui/                    # Compose 页面、主题与界面状态
```

### UI 层

使用 Jetpack Compose 和 Material 3 构建界面，通过 Kotlin 协程执行异步操作。项目不使用 View XML、WebView 或额外的第三方 UI 框架。

### 数据层

- HTTP：OkHttp
- JSON：Gson
- 摄像头与二维码：CameraX、ML Kit Barcode Scanning
- 设备发现：Android 原生经典蓝牙与 BLE API
- 本地数据：SharedPreferences
- 会话保护：Android Keystore 生成 AES-GCM 密钥，对本地会话数据加密存储

### 依赖原则

仅保留核心编译和运行所需依赖，不集成广告、推送、埋点等非必要组件。原生 Android API 能满足需求时优先使用系统能力。

## 构建配置

- `applicationId`：`com.quzhi.lite`
- Release 签名参数通过本机 Gradle 用户配置提供，不写入仓库。
- 本地构建产物、IDE 配置和个人文件分别由项目级 `.gitignore` 与本机 `.git/info/exclude` 排除。

## 参与完善

欢迎通过 Pull Request 完善代码质量、兼容性、构建配置和技术文档。提交前请保持改动聚焦，并确保不提交本地环境文件、构建产物或个人配置。

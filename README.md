# 宁家影视 TVBox

> 基于 [TVBox](https://github.com/CatVodTVOfficial/TVBoxOSC) 开源项目二次开发的影视聚合客户端，内置数据源、支持手机与电视盒子，可自动检查更新。

本程序为个人研究自己测试使用，勿传播或用于商业用途。

<p>
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%205.0%2B-brightgreen" />
  <img alt="kotlin" src="https://img.shields.io/badge/language-Java-orange" />
  <img alt="gradle" src="https://img.shields.io/badge/build-Gradle%207.5-blue" />
  <img alt="sdk" src="https://img.shields.io/badge/compileSdk-33-blue" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-lightgrey" />
</p>

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [工程结构](#工程结构)
- [安装使用](#安装使用)
- [数据源配置](#数据源配置)
- [本地编译](#本地编译)
- [自动构建与发布](#自动构建与发布)
- [常见问题](#常见问题)
- [免责声明](#免责声明)

---

## 项目简介

这是一个 Android 端的影视聚合播放器：本身不提供任何视频资源，只负责把各类「采集接口 / 解析接口」聚合到一起，提供统一的搜索、分类、播放体验。

相比原版，本仓库做了以下定制：

- **内置数据源**：配置文件随 APK 打包，首次安装即可直接使用，无需手动输入接口地址。
- **自动更新**：启动时检查 GitHub Release，关于页也可手动检查更新，支持下载进度与速度显示。
- **自动构建**：GitHub Actions 监听代码推送与上游更新，自动编译并发布 APK。

## 功能特性

| 功能 | 说明 |
|------|------|
| 影视聚合 | 支持 JSON / XML / JAR / 远程等多种采集接口，多站点统一搜索 |
| 多播放器 | 系统播放器、IJK、ExoPlayer 三种内核可切换，支持硬解 / 软解 |
| 电视直播 | 支持直播源、EPG 节目单、台标、自定义 UA |
| 点播解析 | 内置解析接口、嗅探直链播放 |
| 弹幕 | 支持弹幕显示，可自定义弹幕设置 |
| 数据源管理 | 内置源 + 手动输入 + 局域网推送，随时切换 |
| 自动更新 | 启动静默检查 + 关于页手动检查，增量提示更新内容 |
| 多架构 | 支持 arm64-v8a / armeabi-v7a，覆盖手机与电视盒子 |

## 技术栈

**基础环境**

| 项目 | 版本 |
|------|------|
| 语言 | Java 8（部分 lambda） |
| 构建工具 | Gradle 7.5 + Android Gradle Plugin 7.2.2 |
| JDK | 17 |
| compileSdk / targetSdk / minSdk | 33 / 28 / 19 |

**核心依赖**

| 分类 | 框架 |
|------|------|
| 网络 | OkGo 3.0.4、OkHttp 3.12.11、Cronet（HTTP/3）、NanoHTTPD（内置本地 HTTP 服务） |
| 播放器 | Doikki VideoPlayer（`player` 模块，封装 ExoPlayer / IJK） |
| 脚本引擎 | QuickJS（`quickjs` 模块，运行 JS 爬虫） |
| Python 支持 | Chaquopy 12.0.1（`pyramid` 模块，运行 Python 爬虫） |
| 数据解析 | Gson、Jsoup、XStream、commons-io、juniversalchardet |
| 本地存储 | Room 2.3.0、Hawk 2.0.1 |
| UI | RecyclerView、BaseRecyclerViewAdapterHelper、LoadSir、Glide、Picasso、DanmakuFlameMaster |
| 事件 / 工具 | EventBus 3.2.0、XXPermissions、AutoSize（屏幕适配） |

## 工程结构

```
TVBoxOS/
├── app/                     # 主工程：UI、播放、数据源解析、更新模块
│   ├── src/main/java/com/github/tvbox/osc/
│   │   ├── api/             # 接口配置解析（ApiConfig）
│   │   ├── base/            # Application / Activity 基类
│   │   ├── server/          # 内置本地 HTTP 服务（局域网推送、clan:// 协议）
│   │   ├── ui/              # 页面、Fragment、Dialog
│   │   └── util/            # 工具类（UpdateChecker 等）
│   ├── src/main/assets/json/bkystvbox.json   # 内置数据源
│   └── build.gradle
├── player/                  # 播放器封装模块
├── quickjs/                 # QuickJS 脚本引擎模块
├── pyramid/                 # Chaquopy Python 运行时模块
├── json/bkystvbox.json      # 数据源配置原件（构建时同步到 assets）
└── .github/workflows/       # CI 自动构建与发布
```

## 安装使用

### 1. 下载安装

前往 [Releases](https://github.com/bkys/TVBoxOS/releases) 下载最新 APK：

| 安装包 | 适用设备 |
|--------|----------|
| `NingJiaTV_release-java.apk` | 通用包（arm64 + armv7），推荐 |
| `NingJiaTV_release-java64.apk` | 64 位设备，体积更小 |
| `NingJiaTV_release-java32.apk` | 32 位老设备 / 老电视盒子 |

安装前需允许「安装未知来源应用」。电视盒子可用 U 盘、当贝市场或 adb 安装：

```bash
adb install -r NingJiaTV_release-java.apk
```

### 2. 首次启动

应用内置数据源（`assets/json/bkystvbox.json`），安装后直接可用，无需任何配置。

### 3. 切换 / 编辑数据源

首页 → 设置 → 数据源，可输入接口地址、选择本地文件，或通过局域网推送配置。

### 4. 检查更新

**设置 → 关于**，可查看当前版本号并点击「检查更新」；应用启动时也会自动检查一次。

## 数据源配置

配置文件为标准 TVBox JSON 格式，主要字段：

| 字段 | 说明 |
|------|------|
| `sites` | 站点列表，`type` 0/1/3/4 分别对应 XML / JSON / JAR / 远程接口 |
| `parses` | 解析接口，`type` 0 为嗅探、1 为解析直链 |
| `lives` | 直播源，支持 `ua`、`epg`、`logo` 参数 |
| `flags` | 站点分组标签 |
| `ijk` | IJK 播放器参数 |
| `ads` | 广告过滤规则 |

内置配置位于 [`json/bkystvbox.json`](json/bkystvbox.json)，构建时会自动复制到 `app/src/main/assets/json/` 并打包进 APK。

## 本地编译

### 环境要求

| 依赖 | 版本 / 说明 |
|------|-------------|
| JDK | **17**（Gradle 7.5 不支持 JDK 21+） |
| Android SDK | `platforms;android-33`、`build-tools;33.0.1`、`build-tools;30.0.3` |
| Gradle | 无需单独安装，使用仓库自带 `gradlew`（7.5） |
| Python | 仅编译 `python*` 包时需要，3.10，并通过 `BUILD_PYTHON` 指定路径 |

### 编译步骤

```bash
# 1. 克隆
git clone https://github.com/bkys/TVBoxOS.git
cd TVBoxOS

# 2. 指定 SDK 路径
echo "sdk.dir=/your/android/sdk" > local.properties

# 3. 编译（Java 系列，推荐）
./gradlew :app:assembleJavaRelease

# 或按架构分别编译
./gradlew :app:assembleJava32Release
./gradlew :app:assembleJava64Release

# 需要 Python 源支持时（需 Python 3.10）
export BUILD_PYTHON=/path/to/python3.10
./gradlew :app:assemblePythonRelease

# Debug 包
./gradlew :app:assembleJavaDebug
```

产物路径：`app/build/outputs/apk/<flavor>/<buildType>/NingJiaTV_<type>-<flavor>.apk`

### 签名配置

Release 包使用仓库内 `app/宁家影视.jks` 签名，配置见 [`app/build.gradle`](app/build.gradle)。如需替换为自己证书，修改：

```gradle
signingConfigs {
    release {
        storeFile file("你的.jks")
        storePassword "..."
        keyAlias "..."
        keyPassword "..."
    }
}
```

## 自动构建与发布

[`.github/workflows/auto-build-release.yml`](.github/workflows/auto-build-release.yml) 会在以下时机自动编译并发布：

| 触发方式 | 说明 |
|----------|------|
| `push` | 推送到 `main` 且改动涉及 `app/`、`player/`、`pyramid/`、`json/`、构建脚本等路径 |
| `schedule` | 每天 10:00（北京时间）检查上游 TVBoxOS 是否有更新 |
| `workflow_dispatch` | 手动触发 |

流程：同步上游 → 同步数据源配置 → 版本号自增 → 编译 APK → 发布 Release。

## 常见问题

**Q：打开后没有站点 / 提示接口错误？**
A：确认使用的是内置数据源的版本；设置 → 数据源中应显示 `clan://localhost/json/bkystvbox.json`。

**Q：更新时卡在下载没有反应？**
A：GitHub 资源域名在国内可能访问缓慢，可在「关于 → 检查更新」中查看下载进度与速度，失败时点击「浏览器下载」手动安装。

**Q：安装时提示「解析包错误」？**
A：多为下载不完整，删除后重新下载；或使用通用包 `java`。

## 免责声明

本程序为个人研究自己测试使用，勿传播或用于商业用途。

- 本应用不提供、不制作、不上传、不存储任何视频资源，所有内容均来自第三方采集接口。
- 所有资源版权归原作者所有，请在下载后 24 小时内删除。
- 使用本程序产生的一切后果由使用者自行承担。

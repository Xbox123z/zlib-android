徒弟沈舒心同学请尝试注册GitHub,提交pr, 改进readme,发出issue,锻炼计算机能力，为大学做准备
# Z-Library for Android

Z-Library 原生安卓客户端，Kotlin + Jetpack Compose 实现，是 [heartleo/zlib](https://github.com/heartleo/zlib) 命令行客户端的安卓移植版，功能尽可能 1:1 还原。

## 功能

- 🔍 **多维搜索** — 按书名 / 作者 / ISBN / 出版社搜索，支持精确匹配
- 🗂️ **筛选** — 按文件格式（EPUB / PDF / MOBI / AZW3…）、语言、年份范围过滤
- 📖 **书籍详情** — 封面、出版社、年份、语言、格式、大小、评分、简介
- 📥 **下载管理** — 实时进度显示，保存到系统「下载」目录，点击即可调起阅读器
- 📚 **下载历史** — 分页浏览历史记录，支持一键重新下载
- 📊 **配额查看** — 今日已下载 / 限额 / 剩余一目了然
- 📧 **发送到 Kindle** — 通过 SMTP（SSL 465 / STARTTLS）直接把书投递到你的 Kindle
- 🩺 **域名诊断（doctor）** — 一键探测所有候选镜像，自动标记 DiamWall 拦截 / JS 挑战 / 可用状态，点击即可切换主域名
- 🌐 **代理支持** — 自定义 HTTP 代理，适配受限网络环境
- 🎨 **主题** — 自动 / 亮色 / 暗色

## 截图

> 安装后自行体验。

## 安装

从 [Releases](https://github.com/Xbox123z/zlib-android/releases) 下载最新 APK，或自行构建：

```bash
git clone https://github.com/Xbox123z/zlib-android
cd zlib-android
gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

要求 Android 8.0（API 26）及以上。

## 使用

1. 首次打开需要登录 Z-Library 账号。镜像域名经常变动，登录前可点「域名诊断」挑一个可用镜像。
2. 搜索书籍 → 点进详情 → 下载或发送到 Kindle。
3. Kindle 投递需要在「我的 → 设置」里配置 SMTP（如 Gmail 应用专用密码）。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）
- OkHttp + Gson（Z-Library EAPI）
- Coil（封面加载）
- Android DownloadManager（系统级下载）
- 裸 socket SMTP 实现（Kindle 投递，无额外依赖）

## License

MIT License — see [LICENSE](LICENSE)

Copyright (c) 2026 Xbox123z

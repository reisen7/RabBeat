# RabBeat（兔拍）

离线优先的 Android 本地音乐播放器，Apple Music 风格界面。包名 `com.rabbeat.app`，支持 Android 8.0（API 26）及以上。

## 下载

在 [Releases](https://github.com/reisen7/RabBeat/releases) 下载最新的 `RabBeat-<版本>.apk` 直接安装。每个 APK 旁边附有 `.sha256` 校验文件。

## 功能

### 曲库

- **扫描本机**：读取系统媒体库，按扫描规则过滤非歌曲文件、小文件和短音频，并匹配同名 `.lrc` 歌词。
- **导入文件夹**：通过系统文件管理器选择文件夹导入，之后可一键重新扫描。
- **自动去重**：歌名、歌手相同且时长相差 2 秒以内视为同一首；优先保留无损、体积更大的文件，歌单、播放记录、「我喜欢」和播放次数会合并到保留的那份上。
- **数据库分页**：曲库的歌曲、专辑、艺人、文件夹和首页列表都从数据库按页读取，大曲库也不会一次性读进内存；排序和筛选在 SQL 中完成。

### 播放

- 基于 Media3 / ExoPlayer，支持后台播放、通知栏和锁屏控制、播放进度持久化、睡眠定时（可淡出）和倍速。
- 播放页背景取封面主色；点击封面进入歌词，歌词支持逐行滚动、点击跳转、翻译显示、字号调节，以及借用其他歌曲的歌词。
- 长按歌名或歌手名可复制。
- 「…」菜单：添加到歌单、关联歌词、歌词字号、睡眠定时、播放速度。

### 歌单

- 每个人都有固定在首位的系统歌单「我喜欢」，播放页的心形按钮即加入 / 移出「我喜欢」。
- 「我的歌单」管理页：拖动调整歌单顺序、批量删除歌单。
- 歌单详情：长按拖动调整歌曲顺序、搜索、排序、从文件夹导入歌曲；多选后可批量「下一首播放」「添加到歌单」「从歌单删除」。

### 网易云歌单导入

- 设置 → 导入网易云歌单，粘贴分享链接（网页链接、App 分享文字、`163cn.tv` 短链或歌单 ID 均可）。
- 本地已有的歌直接使用本地文件；其余可播放的歌曲在线播放，边听边缓存（上限 2 GB，按最久未听淘汰），缓存后可离线播放；VIP 和已下架歌曲跳过。
- 自动获取歌词（含翻译）并缓存。
- 重名歌单自动加 `new` 后缀；重新导入同一歌单会按网易云顺序更新原歌单。
- 之后扫描到同一首歌的本地文件时，会自动替换为本地文件。

> 网易云接口并非官方开放接口，可能随时变更或限流。缓存的音频仅供个人收听。

### 个性化

- 自定义背景图：从相册选择后按手机屏幕比例裁剪（双指缩放、拖动、双击放大），保留原图以便之后重新裁剪。
- 背景模糊、背景压暗、卡片不透明度可调；有背景图时卡片为半透明毛玻璃。
- 五种强调色，深色 / 浅色 / 跟随系统三种主题。
- 一键恢复默认外观（会先确认）。

### 其他

- 曲库备份与恢复（歌单、「我喜欢」、播放记录）。
- 播放历史与播放统计（本周播放、累计播放、常听艺人）。

## 开发环境

- JDK 17（`gradle/gradle-daemon-jvm.properties` 将 Gradle daemon 固定为 17）
- Android SDK：`compileSdk` 37.2，`targetSdk` 35，`minSdk` 26
- Gradle 9.9.0（wrapper 使用腾讯镜像），Android Gradle Plugin 9.2.1（自带 Kotlin，不要再应用 `org.jetbrains.kotlin.android`）
- 本机 SDK 路径写在 `local.properties` 的 `sdk.dir`（不入库）

### 主要依赖

| 组件 | 版本 |
|---|---|
| Kotlin | 2.4.21 |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Navigation Compose | 2.10.2 |
| Hilt | 2.60.1 |
| Room / Room Paging | 2.8.5 |
| Paging Compose | 3.5.3 |
| Media3 | 1.11.1 |
| Coil 3（含 OkHttp 网络组件） | 3.6.3 |
| DataStore | 1.3.0 |
| OkHttp | 4.12.0 |
| kotlinx-serialization | 1.9.0 |
| Reorderable | 3.1.0 |

### 构建

用 Android Studio 打开本目录，或在项目根目录执行：

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 签名与发布

本地打 release 包时，在项目根目录放置签名文件，并新建 `keystore.properties`（两者都已被 `.gitignore` 排除）：

```properties
storeFile=rabbeat-release.jks
storePassword=******
keyAlias=rabbeat
keyPassword=******
```

然后执行 `gradlew.bat :app:assembleRelease`。没有签名配置时会生成未签名的 APK。

GitHub Actions（`.github/workflows/release.yml`）在推送 `v*` tag 或手动运行时，从仓库 Secrets（`RABBEAT_KEYSTORE_BASE64`、`RABBEAT_KEYSTORE_PASSWORD`、`RABBEAT_KEY_ALIAS`、`RABBEAT_KEY_PASSWORD`）恢复签名，构建 release APK 并发布 GitHub Release。版本名取自 tag（`v1.2.3` → `1.2.3`），版本号按 `主×10000 + 次×100 + 修订` 计算，带后缀的 tag（如 `v1.2.3-beta.1`）发布为预发布。

```bash
git tag v0.0.2
git push origin v0.0.2
```

## 项目结构

```
app/src/main/java/com/rabbeat/app/
├── data/
│   ├── local/        Room 数据库、本机扫描、文件夹导入、去重、网易云导入、备份
│   ├── remote/       网易云接口（netease）、Subsonic 占位
│   └── repository/   曲库、歌单、设置、歌词仓库
├── domain/           模型、仓库接口、用例
├── playback/         播放服务、PlayerController、在线缓存
├── ui/               Compose 页面、组件、主题、导航
└── util/             LRC 解析、封面取色、网络状态等
```

## 已知限制

- Subsonic / SMB / WebDAV 远程来源只有占位实现，界面入口已隐藏。
- 下载页的进度是占位实现。
- 背景模糊依赖 Android 12（API 31）及以上，更低版本只有压暗遮罩。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

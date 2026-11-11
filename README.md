# RabBeat（兔拍）v0.1

离线优先的个人音乐播放器原型。包名 `com.rabbeat.app`。当前实现覆盖设计文档的 M0–M3，并已接上本机 MediaStore 扫描。

## 环境

- JDK 17（`gradle/gradle-daemon-jvm.properties` 将 Gradle daemon 固定为 17）
- Android SDK：`compileSdk` 37.2，`targetSdk` 35，`minSdk` 26。AGP 固定在 9.2.1，以便当前 Android Studio 能打开；依赖库仍要求编译 SDK 37.2，因此 `gradle.properties` 里抑制了这条兼容性警告。
- 本机 SDK 路径写在 `local.properties` 的 `sdk.dir`（该文件不入库）
- Gradle 9.9.0。官方分发地址曾返回 504，wrapper 使用腾讯镜像：`https://mirrors.cloud.tencent.com/gradle/gradle-9.9.0-bin.zip`
- AGP 9.2 自带 Kotlin，不要再应用 `org.jetbrains.kotlin.android`

## 依赖版本

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.4.21 |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Navigation Compose | 2.10.2 |
| Hilt | 2.60.1 |
| Hilt Navigation Compose | 1.4.0 |
| Room | 2.8.5 |
| Media3 | 1.11.1 |
| Coil 3 | 3.6.3 |
| DataStore | 1.3.0 |
| WorkManager | 2.12.0 |
| OkHttp | 4.12.0 |
| kotlinx-serialization | 1.9.0 |
| Coroutines | 1.10.2 |

## 运行

用 Android Studio 打开本目录，或在项目根目录执行：

```bat
gradlew.bat :app:assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。安装到 API 26 及以上的设备或模拟器。首次启动会进入引导页。

演示数据有两条路：

- 引导页或设置里的「扫描本机」会读取系统媒体库里的音频。曲库只保留本机来源。
- Debug 包里点「注入演示数据」，会写入一套本机演示曲。

## 里程碑

- **M0** 主题、类型安全导航、底部四个 Tab、Mini Player、Hilt 入口。
- **M1** Room 曲库。演示数据只保留本机来源；客厅和 NAS 已移除。真实歌曲来自本机扫描。
- **M2** `RabBeatPlaybackService` + ExoPlayer：播放、队列、通知、进度持久化、睡眠定时。冷启动恢复上次曲目，暂停恢复时不会拉起前台服务。
- **M3** 首页、曲库、搜索、队列、下载、设置、引导页和专辑 / 艺人 / 来源详情。页面含 Loading / Empty / Error。
- **M4** 本机扫描已查询 MediaStore，写入来源「本机音乐」。演示数据仍可从调试入口注入。
- **M5** 界面重做：新增首页与「个性化」设置。
- **M6** 界面改为 Apple Music 风格：纯色底、iOS 字号、全宽 Tab 栏、分组列表设置页、封面主色驱动的播放页。

## 界面与个性化

界面基于 Material 3 自建了一套 Apple Music 风格的设计系统，没有引入额外的 UI 依赖：

- 配色 token 在 `ui/theme/Color.kt` 的 `RabBeatPalette`（background / groupedBackground / card / elevated / fill / separator / secondaryLabel 等，对应 iOS 系统色）；字号在 `Type.kt`，对应 iOS Large Title / Title2 / Headline / Body / Footnote。
- 全局背景：纯色 + 可选的自定义图片；有背景图时卡片类表面变为 92% 不透明（`LocalHasBackgroundImage`）。
- 播放页背景取当前封面主色，暂停时封面缩小；首页「继续播放」卡同样取封面主色。
- 个性化（设置 → 个性化，或设置页第一组）：从相册选择背景图片、开关背景、模糊强度 0–40dp、压暗 0–90%、五种强调色（紫罗兰 / 琥珀 / 海洋 / 薄荷 / 玫瑰，默认玫瑰红 #FA2D48）、系统 / 深色 / 浅色三种主题，以及一键恢复默认。
- 背景图直接用系统相册（Android Photo Picker，低版本自动回退到文件选择器）选取，选中后会复制一份到应用私有目录 `files/backgrounds`，所以重启、甚至原图被删除后都仍然可用；移除背景时会一并删除这份副本。设置存放在 DataStore（`appearance_accent`、`appearance_background_uri`、`appearance_background_enabled`、`appearance_background_blur`、`appearance_background_dim`）。
- 模糊使用 Compose `Modifier.blur`，在 Android 12（API 31）及以上生效；更低版本自动退化为渐变遮罩。

## 导航与个人页

- 首页、曲库、搜索、个人四个主页面左上角都有三横按钮，点击从左侧滑出抽屉（宽 86%、右侧圆角、半透明玻璃底），分为「浏览 / 我的 / 设置」三组，底部是返回桌面、版本号与「关于兔拍」。
- 底部导航第 4 项由「设置」改为「个人」：顶部是个人卡片与收藏 / 累计播放 / 歌单三个数字，接着是「我的歌单」横向卡片（可新建并直接进入歌单）、播放数据（本周播放、累计播放、听过歌曲）、常听艺人 Top 3 与最近播放。
- 歌单不再放在曲库，统一在个人页管理；新建、改名、删除与添加曲目沿用歌单详情页。
- 设置仍可从个人页右上角或抽屉进入。

## 已知限制

- 所有示例曲目共用 `res/raw/sample_tone.wav`，按每首歌的时长截断播放。
- 下载是内存里的假进度（排队 → 下载中 → 已下载），WorkManager 任务是占位。
- Subsonic 客户端是 stub，没有真实 `/rest` 请求。SMB / WebDAV 同样只走 Mock。
- 密码存在 DataStore，尚未换成 EncryptedSharedPreferences。
- 设备离线时曲库只保留可离线曲目。
- 尚未在真机上跑过播放和通知，只确认 `assembleDebug` 通过。
- 背景模糊在 Android 11 及以下不生效，仅保留渐变遮罩。

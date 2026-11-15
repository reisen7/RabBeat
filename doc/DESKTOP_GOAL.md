# RabBeat 桌面端功能全量移植目标

## 总目标

Windows 桌面端与 Android 端**功能对等**："功能一样，界面差不多"。
同一 Room schema/数据库、同一套共享 UI 组件与业务逻辑跑两端；
平台差异收敛在薄实现层（播放引擎、文件扫描、系统集成）。

## 架构约束（不可违反）

- `commonMain` 最大化：模型/DAO/迁移/用例/状态/UI 共享；平台代码只留薄壳
- Android 端**零回归**：每步 `:app:assembleDebug` 必须绿
- 桌面验证以 `:desktopApp:run` 实际跑通为准，不以编译通过为准
- 字符串/图片资源走 sharedUi `composeResources`（`Res`），禁止 `R.*`

## 已完成 ✅

- KMP 数据层（实体/DAO/迁移/TrackQuerySql，schema v8 两端通用）
- `PlaybackController` 接口 + `QueueManager` 共享，`DesktopPlayerController`（javax.sound）
- 曲库 4 tab（歌曲/专辑/艺人/文件夹）、搜索过滤、分页浏览
- 歌单 CRUD、右键菜单（下一首/队列/喜欢/隐藏/加歌单）、详情页（专辑/艺人/文件夹/歌单）
- 隐藏/恢复、设置页（深浅色/五色主题）、`.lrc` 歌词行、音量、睡眠定时、队列持久化恢复
- `.ncm`/`.mgg` 解密播放、文件夹导入扫描（jaudiotagger）

## 待移植清单（按优先级）

### P0 — 曲库/播放补齐
- [x] 左栏新增：最近添加（`observeRecentlyAdded`）、最常播放（`TrackSort.PLAY_COUNT`）
- [x] 排序切换（标题/添加时间/时长/播放次数）+ 升降序（曲库工具栏排序菜单）
- [ ] 大曲库 Paging——暂缓：LazyColumn 已虚拟化渲染，桌面内存无手机级约束，
      10k 级曲库实测可控；真瓶颈出现再接 paging-common
- [x] 失效文件清理（启动自动 `prune()` + 设置页手动按钮，按 file:// 存在性删行）
- [x] 全局快捷键：Ctrl+空格=播放暂停、Ctrl+←/→=切歌（裸空格留给输入框）、Esc=收起播放页
- [x] 系统托盘：Tray 图标 + 关窗收进托盘 + 托盘播放控制/退出
- [ ] Windows 媒体键/SMTC——暂缓：需原生注册表/JNA 窗口钩子，与当前零原生依赖
      架构冲突；托盘菜单已覆盖核心控制
- [x] 倍速——决议：桌面端不支持。javax.sound 无变速 API，tritonus 实时重采样引入
      原生级复杂度且音质受损；桌面版无变速 UI，`setSpeed` 仅保留状态字段兼容

### P1 — 歌单/歌词/喜欢体系
- [x] "我喜欢"系统歌单——`LikedPlaylist` 迁移 core 共用（ID/语义与安卓一致），左栏直达
- [x] 歌单重命名 + 右键上移/下移排序（双行 position 交换）/拖拽排序暂缓
- [x] 歌单自定义封面 + 居中裁剪（文件选择器 → 512px 方形 → art 目录 → `setCover`）
- [x] 喜欢列表页（左栏"我喜欢"入口，即 system_liked 歌单详情）
- [x] 文件夹级隐藏（文件夹 tab 右键"隐藏此文件夹"）
- [x] 歌词：在线匹配（网易云公共 API，写入同名 .lrc）、±0.5s 偏移持久化
      （`lyricOffsetMs`）、播放页完整歌词视图（滚动列表+高亮+自动跟随）

### P2 — 在线源/设置全量
- [x] 网易云歌单导入——DTO/Mapper/Links/歌词合并迁 core 共用；
      `NeteaseClient`（java.net.http）+ `NeteaseImporter`（同安卓实体语义/去重/目录重建）
- [x] 在线流播 + 缓存——outer-url 下载进 `~/.rabbeat/cache` 后播放，二次播放零流量、可 seek
- [x] 设置页全量：背景图+压暗+卡片透明度（LocalCardOpacity 共享）、失效歌曲清理、
      在线缓存清理、数据库备份/恢复、7 天滚动日志+导出、统计、检查更新（GitHub API+直链）
- [x] 桌面通知——切歌时 AWT TrayIcon 气泡

### P3 — UI 迁移收尾/分发
- [x] 安卓屏幕迁移——改判：桌面端用共享组件**原生实现**了等价交互（队列面板、
      播放覆盖层、搜索过滤、歌单详情、设置页），无逐屏 R→Res 移植必要；后续
      安卓屏若要共享再按此路径逐个迁移
- [x] 窗口尺寸记忆——`settings.properties` 存 windowW/H，关窗/退出时写回
- [x] `nativeDistributions` exe 配置 + `packagePortableZip` 便携 zip 任务
      （本机 GitHub 不可达拉不到 WiX 工具链；msi 配置移除改走 exe/portable zip，
      打包验证在 CI windows runner 上跑）
- [x] GitHub Release desktop job——windows runner 构建 app image → portable zip → 上 Release

## 验收标准

1. 清单每项在桌面端真实可操作（非摆设按钮）
2. `:app:assembleDebug`、`:core:compileKotlinDesktop`、`:desktopApp:run` 全绿
3. Android 用户无感知：包名、schema、行为均不变

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
- [ ] 左栏新增：最近添加（`observeRecentlyAdded`）、最常播放（`TrackSort.PLAY_COUNT`）
- [ ] 排序切换（标题/添加时间/时长/播放次数）+ 升降序
- [ ] 大曲库 Paging（`paging-common` 桌面可用，替换全量内存列表）
- [ ] 失效文件清理（文件被删/移动后 DB 标记或剔除）
- [ ] 全局快捷键：空格=播放暂停、Ctrl+←/→=切歌、Esc=收起播放页
- [ ] 系统托盘：Tray 图标 + 最小化到托盘 + 托盘播放控制
- [ ] Windows 媒体键/SMTC（JNA 或 jna-platform，可缓）
- [ ] 倍速（javax.sound 无变速：评估 tritonus 重采样或 UI 禁用）

### P1 — 歌单/歌词/喜欢体系
- [ ] "我喜欢"系统歌单（与 `track.isFavorite` 打通，对齐安卓语义）
- [ ] 歌单拖拽排序（`setTrackPosition` 已有）+ 重命名 + 批量选择/批量删除
- [ ] 歌单自定义封面 + 裁剪（桌面文件选择器）
- [ ] 喜欢列表页
- [ ] 文件夹级隐藏
- [ ] 歌词：在线匹配（网易云歌词 API）、持久偏移量、播放页完整歌词视图（多行滚动高亮）

### P2 — 在线源/设置全量
- [ ] 网易云歌单导入（`NeteaseApi` 从 app 迁 commonMain/桌面对象；OkHttp JVM 可用）
- [ ] 在线流播 + 缓存策略（对齐安卓 offline 设置语义）
- [ ] 设置页全量：背景图/模糊/卡片透明度、扫描管理、缓存清理、备份/恢复、
      持久日志（7天）+ 导出、统计页、检查更新（GitHub Releases API，桌面直链 zip/msi）
- [ ] 桌面通知（系统 toast）

### P3 — UI 迁移收尾/分发
- [ ] 安卓屏幕继续搬 sharedUi：QueueScreen（队列重排 UI）、PlayerScreen 完整交互、
      SearchScreen、歌单详情、Settings——逐屏 R→Res + 平台钩子
- [ ] 窗口尺寸/位置记忆
- [ ] `compose.desktop.nativeDistributions`：msi/exe + 便携 zip
- [ ] GitHub Release 增加 desktop 产物 job + CHANGELOG 版本抽取复用

## 验收标准

1. 清单每项在桌面端真实可操作（非摆设按钮）
2. `:app:assembleDebug`、`:core:compileKotlinDesktop`、`:desktopApp:run` 全绿
3. Android 用户无感知：包名、schema、行为均不变

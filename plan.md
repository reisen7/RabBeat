# RabBeat 开发设计文档（原型版 v0.1）

> 给本地 coding agent 的执行文档。目标：**在 Android 上跑出一个可运行原型**，覆盖“连接/导入 → 统一曲库 → 播放 → 离线状态”主链路。允许先用 Mock 数据跑通 UI 和播放，再接真实本地扫描与 OpenSubsonic。

---

## 1. 文档信息

| 项 | 内容 |
|---|---|
| 产品名 | RabBeat（兔拍） |
| 包名 | `com.rabbeat.app` |
| 平台 | Android 优先，后期 KMP/跨平台 |
| 原型目标 | 可安装 APK，主链路可演示 |
| 文档版本 | v0.1 原型 |
| Slogan | Your music, your burrow. |

---

## 2. 产品概述

RabBeat 是一个 **Library-first、离线优先** 的个人音乐播放器。它把本地音乐、WebDAV/SMB、自托管服务器（Navidrome/Subsonic/OpenSubsonic）统一成一个个人曲库。

**三条设计原则：**
1. **打开 3 秒内能听**：首页低决策成本入口。
2. **离线可见**：每首歌都标出本地/缓存/流式/待同步。
3. **不做推荐流**：只基于用户自己的曲库做智能歌单。

---

## 3. 原型范围

### ✅ 原型必须做

- 首次连接向导（本地扫描 + Subsonic 表单，可 Mock）
- 首页：继续播放、最近添加、智能歌单入口、来源状态
- 曲库：歌曲 / 专辑 / 艺人 / 文件夹 四个 Tab
- 全局搜索
- 播放页：封面、动态取色、进度、控制、队列、收藏、歌词占位
- 全局迷你播放器
- 队列页
- 离线下载管理页（状态展示 + Mock 下载）
- 设置页：来源管理、缓存、主题
- Media3 后台播放 + 媒体通知
- Room 持久化曲库
- Mock 数据一键注入（开发用）

### ❌ 原型不做

- 真实 SMB/WebDAV 完整实现（先留接口 + Mock）
- 真实下载引擎（先做状态机 + 假进度）
- 账号系统、云同步、社交
- 在线音源、版权曲库
- 智能歌单规则编辑器（只做固定智能歌单）
- iOS / 跨平台

---

## 4. 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose（类型安全路由） |
| 播放 | Media3 ExoPlayer + MediaSessionService |
| 数据库 | Room |
| DI | Hilt |
| 异步 | Coroutines + Flow |
| 设置 | DataStore Preferences |
| 图片 | Coil（Compose） |
| 后台任务 | WorkManager（扫描/下载占位） |
| 网络 | OkHttp + Kotlinx Serialization（Subsonic 预留） |
| 最低 SDK | minSdk 26，targetSdk 35 |

**原型阶段允许：** 用 `MockMusicRepository` 提供数据，用 `MockDownloader` 模拟下载进度。真实实现通过接口替换。

---

## 5. 项目结构

原型用**单 app 模块 + 包分层**，后期再拆 `core:*` 与 `feature:*`。

```
app/
└── src/main/java/com/rabbeat/app/
    ├── RabBeatApp.kt
    ├── MainActivity.kt
    ├── di/
    │   ├── DatabaseModule.kt
    │   ├── PlayerModule.kt
    │   └── RepositoryModule.kt
    ├── data/
    │   ├── local/
    │   │   ├── RabBeatDatabase.kt
    │   │   ├── entity/
    │   │   ├── dao/
    │   │   └── converter/
    │   ├── repository/
    │   │   ├── MusicRepositoryImpl.kt
    │   │   ├── SourceRepositoryImpl.kt
    │   │   └── SettingsRepositoryImpl.kt
    │   ├── mock/
    │   │   ├── MockMusicRepository.kt
    │   │   └── MockData.kt
    │   └── remote/
    │       └── subsonic/          // 预留，原型可空实现
    ├── domain/
    │   ├── model/
    │   ├── repository/
    │   └── usecase/
    ├── playback/
    │   ├── RabBeatPlaybackService.kt
    │   ├── PlayerController.kt
    │   └── QueueManager.kt
    ├── ui/
    │   ├── theme/
    │   ├── components/
    │   ├── navigation/
    │   └── screens/
    │       ├── onboarding/
    │       ├── home/
    │       ├── library/
    │       ├── search/
    │       ├── player/
    │       ├── queue/
    │       ├── downloads/
    │       └── settings/
    └── util/
```

---

## 6. 数据模型（Room）

### 6.1 枚举

```kotlin
enum class SourceType { LOCAL, WEBDAV, SMB, SUBSONIC }
enum class SourceStatus { CONNECTED, DISCONNECTED, SYNCING, ERROR }
enum class DownloadState { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }
enum class TrackOrigin { LOCAL, REMOTE, CACHED }
```

### 6.2 实体

**SourceEntity**
```kotlin
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,          // UUID
    val type: SourceType,
    val displayName: String,
    val configJson: String,              // 服务器地址/用户名等，密码用 EncryptedSharedPreferences
    val status: SourceStatus,
    val lastSyncAt: Long?,
    val createdAt: Long
)
```

**TrackEntity**
```kotlin
@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artistId"), Index("sourceId"), Index(value = ["sourceId","remoteId"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey val id: String,          // 本地生成 UUID 或 hash
    val sourceId: String,
    val remoteId: String?,               // Subsonic song id
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val fileSize: Long?,
    val uri: String,                     // content:// 或 https://
    val artworkUri: String?,
    val origin: TrackOrigin,
    val downloadState: DownloadState,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val addedAt: Long
)
```

**AlbumEntity**
```kotlin
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val artworkUri: String?,
    val trackCount: Int,
    val addedAt: Long
)
```

**ArtistEntity**
```kotlin
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val trackCount: Int
)
```

**PlaylistEntity / PlaylistTrackCrossRef**（原型可只做系统歌单，但表先建）
```kotlin
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isSystem: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId","trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long
)
```

**PlaybackStateEntity**（单行，存继续播放）
```kotlin
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val trackId: String?,
    val positionMs: Long,
    val queueJson: String?,              // 序列化队列 id 列表
    val updatedAt: Long
)
```

### 6.3 核心 DAO

```kotlin
@Dao interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber")
    fun observeByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%'||:q||'%' OR artist LIKE '%'||:q||'%' OR album LIKE '%'||:q||'%'")
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE origin != 'REMOTE' OR downloadState = 'DOWNLOADED'")
    fun observeOfflineAvailable(): Flow<List<TrackEntity>>

    @Upsert suspend fun upsertAll(tracks: List<TrackEntity>)
    @Query("UPDATE tracks SET downloadState = :state WHERE id = :id")
    suspend fun updateDownloadState(id: String, state: DownloadState)
}
```

其余 DAO 按同样风格：`AlbumDao`、`ArtistDao`、`SourceDao`、`PlaylistDao`、`PlaybackStateDao`。

---

## 7. 领域层接口

```kotlin
interface MusicRepository {
    fun observeTracks(): Flow<List<Track>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeArtists(): Flow<List<Artist>>
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<Track>>
    fun observeOfflineTracks(): Flow<List<Track>>
    fun search(query: String): Flow<SearchResult>
    suspend fun getTrack(id: String): Track?
    suspend fun toggleFavorite(id: String)
    suspend fun recordPlayback(id: String, positionMs: Long)
}

interface SourceRepository {
    fun observeSources(): Flow<List<MusicSource>>
    suspend fun addSource(source: MusicSource): Result<String>
    suspend fun removeSource(id: String)
    suspend fun testConnection(id: String): Result<Unit>
    suspend fun syncSource(id: String): Result<SyncSummary>
}

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>
    suspend fun enqueue(trackIds: List<String>)
    suspend fun cancel(trackId: String)
    suspend fun clearCompleted()
}

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
```

**原型实现策略：** `RepositoryModule` 里绑定 `MockMusicRepository`（内存数据 + 假延迟），后续替换为 `MusicRepositoryImpl(Room)`。Mock 数据要覆盖：多来源、已下载/仅流式、缺失封面、不同时长。

---

## 8. 播放架构

```
RabBeatPlaybackService : MediaSessionService
        ↑
PlayerController（单例，持有 ExoPlayer + MediaSession）
        ↑
UI（PlayerScreen / MiniPlayer / QueueScreen）通过 StateFlow 订阅
```

**PlayerController 暴露：**
```kotlin
data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val sleepTimerRemainingMs: Long? = null
)
```

**必须实现：**
- `MediaSessionService` + 前台通知（播放/暂停/上一首/下一首）
- 音频焦点处理
- 蓝牙/耳机拔出暂停
- 播放队列持久化到 `PlaybackStateEntity`，冷启动恢复
- 睡眠定时器（倒计时 + 渐弱可选）
- 原型阶段 ExoPlayer 播放 `content://` 与 Mock 本地 raw 资源

**音频来源抽象：** 播放前通过 `MediaItemFactory` 把 `Track.uri` 转成 `MediaItem`；远程曲目未来接 Subsonic 流地址，原型先用本地样例音频。

---

## 9. 导航与页面

### 路由

```kotlin
sealed interface Route {
    @Serializable data object Onboarding : Route
    @Serializable data object Home : Route
    @Serializable data object Library : Route
    @Serializable data object Search : Route
    @Serializable data object Player : Route
    @Serializable data object Queue : Route
    @Serializable data object Downloads : Route
    @Serializable data object Settings : Route
    @Serializable data class AlbumDetail(val albumId: String) : Route
    @Serializable data class ArtistDetail(val artistId: String) : Route
    @Serializable data class SourceDetail(val sourceId: String) : Route
}
```

底部导航 4 Tab：**首页 / 曲库 / 搜索 / 设置**。迷你播放器固定在 Scaffold bottomBar 上方，点击展开 `Player`（建议用 bottom sheet 或全屏路由）。

### 页面规格

**① Onboarding**
- 顶部品牌插画（兔子耳朵 + 播放三角）
- 三张来源卡：扫描本机 / 添加服务器 / 稍后再说
- 服务器表单：类型下拉、地址、用户名、密码、测试连接按钮
- 扫描进度条 + 识别结果（歌曲数/专辑数/缺失元数据数）
- “先注入演示数据”开发入口（debug build 显示）

**② Home**
- 继续播放大卡片（封面 + 标题 + 进度 + 播放按钮）
- 横向最近添加
- 智能歌单网格：最近常听、很久没听、无损收藏、未播放
- 来源状态卡：本地 N 首 / NAS 在线 / 待同步
- 空状态：引导导入

**③ Library**
- Tab：歌曲 / 专辑 / 艺人 / 文件夹
- 顶栏：排序（标题/添加时间/时长）、筛选（来源/离线）
- TrackRow：封面 40dp、标题、艺人·专辑、来源徽标、离线徽标、更多菜单
- 专辑用 2 列网格

**④ Search**
- 搜索框自动聚焦
- 结果分组：歌曲 / 专辑 / 艺人
- 最近搜索 chips
- 空结果状态

**⑤ Player**
- 大封面（圆角 24dp，带柔和阴影）
- 动态取色：`Palette` 从封面提取主色生成渐变背景
- 标题、艺人、来源标识
- 进度条可拖动
- 控制：随机、上一首、播放/暂停、下一首、循环
- 底部操作：收藏、歌词、队列、更多（睡眠定时、播放速度）
- 歌词视图：可切换，LRC 解析占位

**⑥ Queue**
- 当前播放高亮
- 拖动排序
- 左滑移除
- 顶部：清空、保存为歌单

**⑦ Downloads**
- 汇总卡：已下载数量、占用空间
- 下载中列表（进度条）
- 已完成列表
- 设置：仅 Wi-Fi、自动离线常用歌单

**⑧ Settings**
- 来源管理列表（状态点 + 同步时间）
- 播放：音质偏好占位、睡眠定时默认值
- 外观：主题（跟随系统/深色/浅色）
- 缓存：清理、占用
- 关于：版本、开源许可

---

## 10. 设计系统

**颜色**
- 主色：`#7C5CFF`（兔耳紫）
- 强调：`#FFB86B`（暖橙）
- 背景深色：`#0E0E12`
- 表面：`#1A1A22`
- 文本主：`#F5F5F7`，次：`#A0A0AB`
- 支持 Material 3 动态取色，但保留品牌色兜底

**字体**
- 标题：系统 Sans，SemiBold
- 正文：系统 Sans，Regular
- 数字/时长用等宽变体

**组件规范**
- 圆角：卡片 16dp，封面 12dp，按钮 24dp
- TrackRow 高度 64dp
- 迷你播放器高度 64dp，含 2dp 进度线
- 来源徽标：小圆点 + 文字，颜色区分来源
- 离线徽标：下载图标 / 云图标

**空状态文案**
- 曲库空：**你的兔拍还是空的，先导入一些音乐吧。**
- 离线：**已回到兔拍，离线也能听。**
- 扫描完成：**兔拍里多了 1,284 首歌。**

---

## 11. 核心流程

### 11.1 首次启动
```
Splash → 检查是否有 Source
  ├─ 无 → Onboarding
  └─ 有 → Home
Onboarding 选择“扫描本机” → 请求权限 → 扫描 → 预览 → 完成 → Home
Onboarding 选择“添加服务器” → 表单 → 测试连接 → 保存 → 同步 → Home
Debug: “注入演示数据” → 写入 Mock → Home
```

### 11.2 播放
```
点击 TrackRow → PlayerController.playQueue(tracks, index)
  → 更新 MediaSession → 通知栏
  → PlayerScreen / MiniPlayer 订阅 StateFlow
  → 每 5s 或暂停时写入 PlaybackStateEntity
```

### 11.3 离线
```
Downloads 页 enqueue → DownloadRepository 状态机
  QUEUED → DOWNLOADING(假进度) → DOWNLOADED
  → TrackEntity.downloadState 更新 → UI 徽标变化
服务器离线 → Source.status=DISCONNECTED → Home 显示离线模式 → 曲库自动过滤可离线曲目
```

---

## 12. 开发任务分解

### M0 项目骨架（0.5 天）
- Gradle + Compose + Hilt + Room + Media3 依赖
- 主题、导航、底部 4 Tab、空页面
- `RabBeatApp` / `MainActivity`
- 全局 MiniPlayer 占位

**验收：** 安装后能在 4 个 Tab 间切换。

### M1 数据与 Mock（0.5—1 天）
- 定义实体、DAO、数据库
- 定义领域 model 与 repository 接口
- 实现 `MockMusicRepository` + 20—30 首 Mock 数据（含 3 个来源、混合下载状态）
- RepositoryModule 绑定 Mock

**验收：** 曲库页能看到 Mock 歌曲、专辑、艺人。

### M2 播放链路（1—2 天）
- `RabBeatPlaybackService` + `PlayerController`
- 播放/暂停/切歌/进度/队列
- 通知栏控制
- 迷你播放器 + PlayerScreen
- 播放状态持久化

**验收：** 点歌能播、切后台不断、通知栏可控、杀进程恢复上次曲目。

### M3 主页面完善（1—2 天）
- Home 卡片
- Library 四 Tab + 排序筛选
- Search
- Queue 拖动排序
- Downloads 状态页（Mock 下载）
- Settings + 来源列表
- Onboarding 向导

**验收：** 主链路可完整演示：Onboarding → Home → 播放 → 离线标识 → 设置。

### M4 真实本地扫描（可选，1—2 天）
- MediaStore 查询音频
- 权限请求
- 写入 Room 替换 Mock
- 封面读取

**验收：** 能扫描到设备真实音乐并播放。

---

## 13. 验收标准

| 项 | 标准 |
|---|---|
| 启动 | 冷启动 ≤ 2s 到首屏（Mock 数据） |
| 播放 | 点歌 500ms 内开始播放；后台播放不中断 |
| 通知 | 通知栏显示封面/标题/控制按钮 |
| 恢复 | 杀进程后重开恢复上次曲目与进度 |
| 曲库 | 歌曲/专辑/艺人/文件夹可切换；搜索有结果分组 |
| 来源 | 每首歌显示来源徽标与离线状态 |
| 离线 | 断网/服务器离线时显示离线模式，不崩溃 |
| 主题 | 深色/浅色切换生效 |
| 空状态 | 无数据时显示引导文案，不白屏 |

---

## 14. 给 Coding Agent 的执行提示

可直接复制：

```
你是一个 Android 工程师。请按《RabBeat 开发设计文档 v0.1》实现原型。

要求：
1. 包名 com.rabbeat.app，Kotlin + Jetpack Compose + Material3 + Hilt + Room + Media3 + Coil。
2. 严格按文档第 5 节包结构创建文件；第 6 节实体与 DAO；第 7 节 repository 接口。
3. 原型阶段用 MockMusicRepository 提供 25 首 Mock 数据，覆盖 LOCAL/SUBSONIC 两种来源、DOWNLOADED/NONE 两种下载状态、部分缺失封面。
4. 实现第 9 节全部页面与第 12 节 M0—M3 任务；M4 真实扫描可留 TODO。
5. 播放使用 Media3 MediaSessionService + ExoPlayer，实现通知栏控制、播放状态持久化、睡眠定时。
6. UI 遵循第 10 节设计系统；Player 页用 Palette 从封面取色生成渐变背景。
7. 所有页面必须有 Loading / Empty / Error 状态；字符串放 strings.xml，中文为主，英文 key。
8. 不要引入在线音源、账号、社交功能。
9. 每完成一个里程碑，输出：新增文件列表、关键类说明、如何运行、已知限制。
10. 代码需可编译通过；若某依赖版本不确定，选择稳定版本并在 README 记录。

先输出 M0 的完整文件清单和代码，等我确认后再继续 M1。
```

---

## 15. 风险与注意事项

- **Media3 版本**：用稳定版，注意 `MediaSessionService` 前台服务类型声明 `mediaPlayback`。
- **Android 13+ 通知权限**：`POST_NOTIFICATIONS` 运行时请求。
- **Android 14+ 前台服务**：需声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`。
- **本地音频权限**：`READ_MEDIA_AUDIO`（33+）/ `READ_EXTERNAL_STORAGE`（26—32）。
- **Mock 与真实实现切换**：Hilt 绑定集中在一个 Module，避免散落。
- **动态取色性能**：封面取色放 IO 线程，缓存结果。
- **密码存储**：原型可明文存 DataStore，但代码里留 `SecureConfigStore` 接口，标注 TODO 用 EncryptedSharedPreferences。
- **许可证**：若后续开源，建议 GPL-3.0 或 AGPL-3.0（自托管服务类项目 AGPL 更合适），商用前再评估。

---

**建议下一步：** 先把这份文档丢给 agent，要求它**只输出 M0 的文件树 + 关键代码**，确认工程能编译后再逐里程碑推进。这样比一次性生成全部代码更稳。
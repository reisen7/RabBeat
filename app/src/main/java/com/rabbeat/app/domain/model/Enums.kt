package com.rabbeat.app.domain.model

enum class SourceType { LOCAL, WEBDAV, SMB, SUBSONIC, NETEASE }

enum class SourceStatus { CONNECTED, DISCONNECTED, SYNCING, ERROR }

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

enum class TrackOrigin { LOCAL, REMOTE, CACHED }

enum class RepeatMode { OFF, ONE, ALL }

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class TrackSort { TITLE, ADDED, DURATION, PLAY_COUNT }

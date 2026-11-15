package com.rabbeat.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rabbeat.app.data.local.MIGRATION_1_2
import com.rabbeat.app.data.local.MIGRATION_2_3
import com.rabbeat.app.data.local.MIGRATION_3_4
import com.rabbeat.app.data.local.MIGRATION_4_5
import com.rabbeat.app.data.local.MIGRATION_5_6
import com.rabbeat.app.data.local.MIGRATION_6_7
import com.rabbeat.app.data.local.MIGRATION_7_8
import com.rabbeat.app.data.local.RabBeatDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers

private fun openDatabase(): RabBeatDatabase {
    val dir = File(System.getProperty("user.home"), ".rabbeat").apply { mkdirs() }
    return Room.databaseBuilder<RabBeatDatabase>(File(dir, "rabbeat.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )
        .fallbackToDestructiveMigration(true)
        .build()
}

@Composable
private fun LibraryScreen(db: RabBeatDatabase) {
    val tracks by db.trackDao().observeAll().collectAsState(initial = emptyList())
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("RabBeat", style = MaterialTheme.typography.headlineMedium)
        Text("${tracks.size} tracks", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks, key = { it.id }) { track ->
                Text(
                    "${track.title} — ${track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

fun main() = application {
    val db = openDatabase()
    Window(onCloseRequest = ::exitApplication, title = "RabBeat") {
        MaterialTheme {
            LibraryScreen(db)
        }
    }
}

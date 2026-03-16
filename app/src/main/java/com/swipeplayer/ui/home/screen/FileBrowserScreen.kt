package com.swipeplayer.ui.home.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.data.StorageVolumeInfo
import com.swipeplayer.data.VideoFile
import java.io.File

@Composable
fun FileBrowserScreen(
    currentDir: File?,
    dirs: List<File>,
    videos: List<VideoFile>,
    loading: Boolean,
    canGoUp: Boolean,
    storageVolumes: List<StorageVolumeInfo>,
    showHiddenFiles: Boolean,
    onDirSelected: (File) -> Unit,
    onGoUp: () -> Unit,
    onVolumeSelected: (StorageVolumeInfo) -> Unit,
    onToggleHiddenFiles: () -> Unit,
    onVideoSelected: (VideoFile) -> Unit,
) {
    if (canGoUp) {
        BackHandler(onBack = onGoUp)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrowserHeader(
            currentDir = currentDir,
            storageVolumes = storageVolumes,
            showHiddenFiles = showHiddenFiles,
            onVolumeSelected = onVolumeSelected,
            onToggleHiddenFiles = onToggleHiddenFiles,
        )
        HorizontalDivider(color = Color(0xFF2A2A2A))

        if (loading || currentDir == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (canGoUp) {
                item {
                    BrowserDirRow(name = "..", onClick = onGoUp)
                }
            }

            items(dirs, key = { it.absolutePath }) { dir ->
                BrowserDirRow(
                    name = dir.name,
                    onClick = { onDirSelected(dir) },
                )
            }

            items(videos, key = { it.uri.toString() }) { video ->
                BrowserVideoRow(
                    name = video.name,
                    onClick = { onVideoSelected(video) },
                )
            }

            if (dirs.isEmpty() && videos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Dossier vide", color = Color(0xFFAAAAAA))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserHeader(
    currentDir: File?,
    storageVolumes: List<StorageVolumeInfo>,
    showHiddenFiles: Boolean,
    onVolumeSelected: (StorageVolumeInfo) -> Unit,
    onToggleHiddenFiles: () -> Unit,
) {
    var showVolumeMenu by remember { mutableStateOf(false) }

    // Find the current volume name by matching the current dir against known volumes
    val currentVolumeName = currentDir?.let { dir ->
        storageVolumes.firstOrNull { vol ->
            dir.absolutePath.startsWith(vol.path.absolutePath)
        }?.name
    } ?: storageVolumes.firstOrNull()?.name ?: "Stockage"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Volume picker button
        Box {
            Row(
                modifier = Modifier
                    .clickable(enabled = storageVolumes.size > 1) { showVolumeMenu = true }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentVolumeName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (storageVolumes.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = showVolumeMenu,
                onDismissRequest = { showVolumeMenu = false },
            ) {
                storageVolumes.forEach { vol ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (vol.isRemovable) Icons.Default.Folder else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (vol.isRemovable) Color(0xFF4CAF50) else Color(0xFF2196F3),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(vol.name)
                            }
                        },
                        onClick = {
                            showVolumeMenu = false
                            onVolumeSelected(vol)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Hidden files toggle
        IconButton(onClick = onToggleHiddenFiles) {
            Icon(
                imageVector = if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (showHiddenFiles) "Masquer les fichiers cachés" else "Afficher les fichiers cachés",
                tint = if (showHiddenFiles) Color(0xFFE50914) else Color(0xFF888888),
            )
        }
    }
}

@Composable
private fun BrowserDirRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = Color(0xFFFFCC44),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowserVideoRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = null,
            tint = Color(0xFFE50914),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            color = Color(0xFFDDDDDD),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

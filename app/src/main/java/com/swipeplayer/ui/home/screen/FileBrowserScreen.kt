package com.swipeplayer.ui.home.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.data.VideoFile
import java.io.File

@Composable
fun FileBrowserScreen(
    currentDir: File?,
    dirs: List<File>,
    videos: List<VideoFile>,
    loading: Boolean,
    canGoUp: Boolean,
    onDirSelected: (File) -> Unit,
    onGoUp: () -> Unit,
    onVideoSelected: (VideoFile) -> Unit,
) {
    if (canGoUp) {
        BackHandler(onBack = onGoUp)
    }

    if (loading || currentDir == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE50914))
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = currentDir.absolutePath,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = Color(0xFF2A2A2A))
        }

        if (canGoUp) {
            item {
                BrowserDirRow(
                    name = "..",
                    onClick = onGoUp,
                )
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

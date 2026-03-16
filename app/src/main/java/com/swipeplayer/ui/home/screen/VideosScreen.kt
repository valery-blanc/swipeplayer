package com.swipeplayer.ui.home.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.swipeplayer.data.VideoFile
import com.swipeplayer.ui.home.components.VideoListItem

@Composable
fun VideosScreen(
    videos: List<VideoFile>,
    loading: Boolean,
    onVideoSelected: (VideoFile) -> Unit,
) {
    when {
        loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE50914))
        }
        videos.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune video trouvee", color = Color(0xFFAAAAAA))
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(videos, key = { it.uri.toString() }) { video ->
                VideoListItem(video = video, onClick = { onVideoSelected(video) })
            }
        }
    }
}

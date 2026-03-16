package com.swipeplayer.ui.home.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swipeplayer.data.FolderInfo
import com.swipeplayer.data.VideoFile
import com.swipeplayer.ui.home.components.CollectionListItem
import com.swipeplayer.ui.home.components.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    collections: List<FolderInfo>,
    loading: Boolean,
    openCollection: FolderInfo?,
    collectionVideos: List<VideoFile>,
    collectionVideosLoading: Boolean,
    onCollectionSelected: (FolderInfo) -> Unit,
    onCloseCollection: () -> Unit,
    onVideoSelected: (VideoFile) -> Unit,
) {
    if (openCollection != null) {
        BackHandler(onBack = onCloseCollection)
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = openCollection.bucketName.ifBlank { "/" },
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCloseCollection) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
            )
            if (collectionVideosLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(collectionVideos, key = { it.uri.toString() }) { video ->
                        VideoListItem(video = video, onClick = { onVideoSelected(video) })
                    }
                }
            }
        }
    } else {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else if (collections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune video trouvee", color = Color(0xFFAAAAAA))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(collections, key = { it.bucketId }) { folder ->
                    CollectionListItem(
                        folder = folder,
                        onClick = { onCollectionSelected(folder) },
                    )
                }
            }
        }
    }
}

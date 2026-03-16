package com.swipeplayer.ui.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.swipeplayer.ui.home.HomeTab
import com.swipeplayer.ui.home.HomeUiState
import com.swipeplayer.ui.home.HomeViewModel

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
) {
    Scaffold(
        containerColor = Color(0xFF121212),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                NavigationBarItem(
                    selected = uiState.activeTab == HomeTab.COLLECTIONS,
                    onClick = { viewModel.onTabSelected(HomeTab.COLLECTIONS) },
                    icon = {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    },
                    label = { Text("Collections") },
                    colors = navigationBarItemColors(),
                )
                NavigationBarItem(
                    selected = uiState.activeTab == HomeTab.VIDEOS,
                    onClick = { viewModel.onTabSelected(HomeTab.VIDEOS) },
                    icon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    },
                    label = { Text("Videos") },
                    colors = navigationBarItemColors(),
                )
                NavigationBarItem(
                    selected = uiState.activeTab == HomeTab.BROWSE,
                    onClick = { viewModel.onTabSelected(HomeTab.BROWSE) },
                    icon = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    label = { Text("Parcourir") },
                    colors = navigationBarItemColors(),
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues),
        ) {
            when (uiState.activeTab) {
                HomeTab.COLLECTIONS -> CollectionsScreen(
                    collections = uiState.collections,
                    loading = uiState.collectionsLoading,
                    openCollection = uiState.openCollection,
                    collectionVideos = uiState.collectionVideos,
                    collectionVideosLoading = uiState.collectionVideosLoading,
                    onCollectionSelected = viewModel::onCollectionSelected,
                    onCloseCollection = viewModel::onCloseCollection,
                    onVideoSelected = viewModel::onVideoSelected,
                )
                HomeTab.VIDEOS -> VideosScreen(
                    videos = uiState.allVideos,
                    loading = uiState.videosLoading,
                    onVideoSelected = viewModel::onVideoSelected,
                )
                HomeTab.BROWSE -> FileBrowserScreen(
                    currentDir = uiState.browseDir,
                    dirs = uiState.browseDirs,
                    videos = uiState.browseVideos,
                    loading = uiState.browseLoading,
                    canGoUp = viewModel.canBrowseUp,
                    onDirSelected = viewModel::onBrowseDir,
                    onGoUp = { viewModel.onBrowseUp() },
                    onVideoSelected = viewModel::onVideoSelected,
                )
            }
        }
    }
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFFE50914),
    selectedTextColor = Color(0xFFE50914),
    unselectedIconColor = Color(0xFF888888),
    unselectedTextColor = Color(0xFF888888),
    indicatorColor = Color(0xFF2A2A2A),
)

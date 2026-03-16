package com.swipeplayer.ui.home.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.ui.home.HomeTab
import com.swipeplayer.ui.home.HomeUiState
import com.swipeplayer.ui.home.HomeViewModel
import kotlinx.coroutines.launch

private val TABS = listOf(HomeTab.COLLECTIONS, HomeTab.VIDEOS, HomeTab.BROWSE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    // Sync pager -> ViewModel when the user swipes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onTabSelected(TABS[page])
        }
    }

    // Sync ViewModel -> pager when tab is changed programmatically (not needed
    // in normal flow, but keeps both sources of truth consistent)
    LaunchedEffect(uiState.activeTab) {
        val targetPage = TABS.indexOf(uiState.activeTab)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = { HomeTopBar() },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    HomeTab.COLLECTIONS -> Icons.Default.VideoLibrary
                                    HomeTab.VIDEOS      -> Icons.Default.PlayArrow
                                    HomeTab.BROWSE      -> Icons.Default.Folder
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                when (tab) {
                                    HomeTab.COLLECTIONS -> "Collections"
                                    HomeTab.VIDEOS      -> "Videos"
                                    HomeTab.BROWSE      -> "Parcourir"
                                }
                            )
                        },
                        colors = navigationBarItemColors(),
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues),
        ) { page ->
            when (TABS[page]) {
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
                    storageVolumes = uiState.storageVolumes,
                    showHiddenFiles = uiState.showHiddenFiles,
                    onDirSelected = viewModel::onBrowseDir,
                    onGoUp = { viewModel.onBrowseUp() },
                    onVolumeSelected = viewModel::onVolumeSelected,
                    onToggleHiddenFiles = viewModel::onToggleHiddenFiles,
                    onVideoSelected = viewModel::onVideoSelected,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar() {
    val context = LocalContext.current
    val appIconBitmap = remember {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SwipePlayer",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
    )
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFFE50914),
    selectedTextColor = Color(0xFFE50914),
    unselectedIconColor = Color(0xFF888888),
    unselectedTextColor = Color(0xFF888888),
    indicatorColor = Color(0xFF2A2A2A),
)

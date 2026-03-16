package com.swipeplayer.ui.home

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipeplayer.data.FolderInfo
import com.swipeplayer.data.VideoFile
import com.swipeplayer.data.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Browse back-stack: list of dirs visited; pop on navigateUp()
    private val browseBackStack = ArrayDeque<File>()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(permissionDenied = true, collectionsLoading = false, videosLoading = false) }
            return
        }
        loadAll()
    }

    fun loadAll() {
        loadCollections()
        loadAllVideos()
        // Initialize browse to external storage root
        val root = Environment.getExternalStorageDirectory()
        browseBackStack.clear()
        browseInto(root)
    }

    // -------------------------------------------------------------------------
    // Collections
    // -------------------------------------------------------------------------

    private fun loadCollections() {
        viewModelScope.launch {
            _uiState.update { it.copy(collectionsLoading = true) }
            val collections = repository.scanCollections()
            _uiState.update { it.copy(collections = collections, collectionsLoading = false) }
        }
    }

    fun onCollectionSelected(folder: FolderInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(openCollection = folder, collectionVideosLoading = true) }
            val videos = repository.listVideosByBucketId(folder.bucketId)
            _uiState.update { it.copy(collectionVideos = videos, collectionVideosLoading = false) }
        }
    }

    fun onCloseCollection() {
        _uiState.update { it.copy(openCollection = null, collectionVideos = emptyList()) }
    }

    // -------------------------------------------------------------------------
    // All Videos
    // -------------------------------------------------------------------------

    private fun loadAllVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(videosLoading = true) }
            val videos = repository.scanAllVideos()
            _uiState.update { it.copy(allVideos = videos, videosLoading = false) }
        }
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    fun onTabSelected(tab: HomeTab) {
        _uiState.update { it.copy(activeTab = tab, openCollection = null) }
    }

    // -------------------------------------------------------------------------
    // Browse
    // -------------------------------------------------------------------------

    private fun browseInto(dir: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(browseDir = dir, browseLoading = true) }
            val (dirs, videos) = repository.browseDirectory(dir)
            _uiState.update { it.copy(browseDirs = dirs, browseVideos = videos, browseLoading = false) }
        }
    }

    fun onBrowseDir(dir: File) {
        val current = _uiState.value.browseDir ?: return
        browseBackStack.addLast(current)
        browseInto(dir)
    }

    /** @return true if navigation up happened, false if already at root */
    fun onBrowseUp(): Boolean {
        val previous = browseBackStack.removeLastOrNull() ?: return false
        browseInto(previous)
        return true
    }

    val canBrowseUp: Boolean get() = browseBackStack.isNotEmpty()

    // -------------------------------------------------------------------------
    // Video selection
    // -------------------------------------------------------------------------

    fun onVideoSelected(video: VideoFile) {
        _uiState.update { it.copy(videoToPlay = video.uri) }
    }

    fun onVideoPlayLaunched() {
        _uiState.update { it.copy(videoToPlay = null) }
    }
}

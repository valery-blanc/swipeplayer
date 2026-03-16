package com.swipeplayer.ui.home

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipeplayer.data.FolderInfo
import com.swipeplayer.data.StorageVolumeInfo
import com.swipeplayer.data.VideoFile
import com.swipeplayer.data.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
        loadStorageVolumes()
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
    // Storage volumes
    // -------------------------------------------------------------------------

    private fun loadStorageVolumes() {
        val primary = Environment.getExternalStorageDirectory()
        val volumes = mutableListOf<StorageVolumeInfo>()
        volumes.add(StorageVolumeInfo(primary, "Stockage interne", isRemovable = false))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(StorageManager::class.java)
            sm.storageVolumes
                .filter { !it.isPrimary }
                .forEach { vol ->
                    val dir = vol.directory ?: return@forEach
                    volumes.add(StorageVolumeInfo(dir, vol.getDescription(context), isRemovable = vol.isRemovable))
                }
        } else {
            // API 26-29: derive secondary volume roots from app-specific dirs
            context.getExternalFilesDirs(null)
                .filterNotNull()
                .drop(1) // skip primary (already added above)
                .forEach { appDir ->
                    // Walk up 4 levels: files/com.pkg/data/Android/<root>
                    var root: File = appDir
                    repeat(4) { root = root.parentFile ?: root }
                    if (root.absolutePath != primary.absolutePath) {
                        volumes.add(StorageVolumeInfo(root, "Carte SD", isRemovable = true))
                    }
                }
        }

        _uiState.update { it.copy(storageVolumes = volumes) }
    }

    fun onVolumeSelected(volume: StorageVolumeInfo) {
        browseBackStack.clear()
        browseInto(volume.path)
    }

    // -------------------------------------------------------------------------
    // Browse
    // -------------------------------------------------------------------------

    private fun browseInto(dir: File) {
        val showHidden = _uiState.value.showHiddenFiles
        viewModelScope.launch {
            _uiState.update { it.copy(browseDir = dir, browseLoading = true) }
            val (dirs, videos) = repository.browseDirectory(dir, showHidden)
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

    fun onToggleHiddenFiles() {
        val newValue = !_uiState.value.showHiddenFiles
        _uiState.update { it.copy(showHiddenFiles = newValue) }
        // Refresh current directory with new filter
        val currentDir = _uiState.value.browseDir ?: return
        val showHidden = newValue
        viewModelScope.launch {
            _uiState.update { it.copy(browseLoading = true) }
            val (dirs, videos) = repository.browseDirectory(currentDir, showHidden)
            _uiState.update { it.copy(browseDirs = dirs, browseVideos = videos, browseLoading = false) }
        }
    }

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

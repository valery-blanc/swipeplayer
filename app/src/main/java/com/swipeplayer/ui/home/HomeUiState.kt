package com.swipeplayer.ui.home

import android.net.Uri
import com.swipeplayer.data.FolderInfo
import com.swipeplayer.data.StorageVolumeInfo
import com.swipeplayer.data.VideoFile
import java.io.File

enum class HomeTab { COLLECTIONS, VIDEOS, BROWSE }

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.COLLECTIONS,

    // Collections tab
    val collections: List<FolderInfo> = emptyList(),
    val collectionsLoading: Boolean = true,

    // Selected collection drill-down (null = top-level list)
    val openCollection: FolderInfo? = null,
    val collectionVideos: List<VideoFile> = emptyList(),
    val collectionVideosLoading: Boolean = false,

    // Videos tab
    val allVideos: List<VideoFile> = emptyList(),
    val videosLoading: Boolean = true,

    // Browse tab
    val browseDir: File? = null,
    val browseDirs: List<File> = emptyList(),
    val browseVideos: List<VideoFile> = emptyList(),
    val browseLoading: Boolean = false,

    // Browse: available storage volumes (internal + SD card + USB)
    val storageVolumes: List<StorageVolumeInfo> = emptyList(),

    // Browse: show/hide files and directories starting with "."
    val showHiddenFiles: Boolean = false,

    // Permission
    val permissionDenied: Boolean = false,

    // Event: non-null when a video has been selected -> launch player
    val videoToPlay: Uri? = null,
)

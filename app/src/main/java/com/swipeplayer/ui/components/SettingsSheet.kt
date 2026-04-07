package com.swipeplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.ui.PlaybackOrder
import com.swipeplayer.ui.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    playbackOrder: PlaybackOrder,
    onAudioTrackSelected: (TrackInfo) -> Unit,
    onSubtitleTrackSelected: (TrackInfo?) -> Unit,
    onPlaybackOrderChange: (PlaybackOrder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // --- Decoder indicator ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Decodeur", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("HW+", fontSize = 14.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // --- Audio tracks ---
            Text("Piste audio", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            if (audioTracks.isEmpty()) {
                Text("Automatique", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            } else {
                audioTracks.forEach { track ->
                    TrackRow(
                        label = track.label,
                        isSelected = track.isSelected,
                        onClick = { onAudioTrackSelected(track) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // --- Subtitle tracks ---
            Text("Sous-titres", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            // "None" option
            val anySubtitleSelected = subtitleTracks.any { it.isSelected }
            TrackRow(
                label = "Aucun",
                isSelected = !anySubtitleSelected,
                onClick = { onSubtitleTrackSelected(null) },
            )
            if (subtitleTracks.isEmpty()) {
                Text("Pas de sous-titres detectes", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            } else {
                subtitleTracks.forEach { track ->
                    TrackRow(
                        label = track.label,
                        isSelected = track.isSelected,
                        onClick = { onSubtitleTrackSelected(track) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // --- Playback order ---
            Text("Ordre de lecture", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            TrackRow(
                label = "Aleatoire",
                isSelected = playbackOrder == PlaybackOrder.RANDOM,
                onClick = { onPlaybackOrderChange(PlaybackOrder.RANDOM) },
            )
            TrackRow(
                label = "Alphabetique",
                isSelected = playbackOrder == PlaybackOrder.ALPHABETICAL,
                onClick = { onPlaybackOrderChange(PlaybackOrder.ALPHABETICAL) },
            )
            TrackRow(
                label = "Par date de modification",
                isSelected = playbackOrder == PlaybackOrder.BY_DATE,
                onClick = { onPlaybackOrderChange(PlaybackOrder.BY_DATE) },
            )
            TrackRow(
                label = "Aleatoire (repertoire parent)",
                isSelected = playbackOrder == PlaybackOrder.PARENT_RANDOM,
                onClick = { onPlaybackOrderChange(PlaybackOrder.PARENT_RANDOM) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(text = label, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

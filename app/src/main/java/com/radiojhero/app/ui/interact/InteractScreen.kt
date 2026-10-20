package com.radiojhero.app.ui.interact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.SongsFetcher
import com.radiojhero.app.normalize
import com.radiojhero.app.services.MediaPlaybackService
import com.radiojhero.app.ui.components.AppTopBar
import kotlinx.coroutines.flow.Flow

/** Maximum number of songs listed in the search dropdown at once. */
private const val MAX_SONG_SUGGESTIONS = 50

@Composable
fun InteractScreen(
    programType: Int,
    scrollToTop: Flow<Unit>,
    viewModel: InteractViewModel = viewModel(),
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect { scrollState.animateScrollTo(0) }
    }

    val focusManager = LocalFocusManager.current
    val isLive = programType == MediaPlaybackService.PROGRAM_TYPE_LIVE
    val isPodcast = programType == MediaPlaybackService.PROGRAM_TYPE_PODCAST
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(scrolled = scrollState.value > 0)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(
                        when (programType) {
                            MediaPlaybackService.PROGRAM_TYPE_LIVE -> R.string.label_interact
                            MediaPlaybackService.PROGRAM_TYPE_PODCAST -> R.string.interact_unavailable
                            else -> R.string.label_interact_playlist
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )

                if (isPodcast) {
                    return@Column
                }

                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .onFocusLost { viewModel.validateName() },
                    label = { Text(stringResource(R.string.label_interact_name)) },
                    isError = viewModel.nameError != null,
                    supportingText = viewModel.nameError?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    textStyle = textStyle,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )

                if (isLive) {
                    OutlinedTextField(
                        value = viewModel.song,
                        onValueChange = viewModel::onSongChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusLost { viewModel.validateSong() },
                        label = { Text(stringResource(R.string.label_interact_song)) },
                        isError = viewModel.songError != null,
                        supportingText = viewModel.songError?.let { { Text(stringResource(it)) } },
                        singleLine = true,
                        textStyle = textStyle,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                } else {
                    SongSearchField(viewModel, textStyle)
                }

                OutlinedTextField(
                    value = viewModel.message,
                    onValueChange = viewModel::onMessageChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusLost { viewModel.validateMessage(programType) },
                    label = { Text(stringResource(R.string.label_interact_message)) },
                    isError = viewModel.messageError != null,
                    supportingText = viewModel.messageError?.let { { Text(stringResource(it)) } },
                    minLines = 5,
                    maxLines = 5,
                    textStyle = textStyle,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Box(Modifier.padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = {
                            focusManager.clearFocus()
                            viewModel.send(programType)
                        }) {
                            Text(stringResource(R.string.interact_send))
                        }
                    }
                }
            }
        }
    }

    viewModel.alert?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissAlert,
            confirmButton = {
                TextButton(onClick = viewModel::dismissAlert) {
                    Text(stringResource(R.string.button_dismiss))
                }
            },
            title = { Text(it.title) },
            text = { Text(it.message) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongSearchField(
    viewModel: InteractViewModel,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    val query = viewModel.songQuery
    val songs = viewModel.songs
    val suggestions = remember(query, songs, viewModel.selectedSong) {
        if (query.isBlank() || viewModel.selectedSong != null) {
            emptyList()
        } else {
            val normalizedQuery = query.normalize()
            songs.asSequence().filter {
                it.album.normalize().contains(normalizedQuery) || it.artist.normalize()
                    .contains(normalizedQuery) || it.title.normalize().contains(normalizedQuery)
            }.take(MAX_SONG_SUGGESTIONS).toList()
        }
    }
    val isExpanded = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                viewModel.onSongQueryChange(it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusLost { viewModel.onSongQueryFocusLost() },
            label = { Text(stringResource(R.string.label_interact_song)) },
            isError = viewModel.songError != null,
            supportingText = viewModel.songError?.let { { Text(stringResource(it)) } },
            singleLine = true,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )

        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { song ->
                DropdownMenuItem(
                    text = { SongItem(song) },
                    onClick = {
                        viewModel.onSongSelected(song)
                        expanded = false
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun SongItem(song: SongsFetcher.Song) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.cover.toString(),
            contentDescription = stringResource(R.string.song_cover_image),
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )

        Column(
            Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                song.title to FontWeight.Bold,
                song.artist to null,
                song.album to null,
            ).forEachIndexed { index, (text, weight) ->
                if (text.isNotBlank()) {
                    Text(
                        text,
                        fontSize = 12.sp,
                        fontWeight = weight,
                        fontStyle = if (index == 2) FontStyle.Italic else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Runs [action] when the element loses focus (but not before it has ever been focused). */
private fun Modifier.onFocusLost(action: () -> Unit): Modifier = composed {
    var wasFocused by remember { mutableStateOf(false) }
    onFocusChanged {
        if (wasFocused && !it.isFocused) {
            action()
        }
        wasFocused = it.isFocused
    }
}

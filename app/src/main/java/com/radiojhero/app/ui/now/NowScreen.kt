package com.radiojhero.app.ui.now

import android.os.Bundle
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.radiojhero.app.R
import com.radiojhero.app.getNow
import com.radiojhero.app.services.MediaPlaybackService
import com.radiojhero.app.ui.components.AppTopBar
import com.radiojhero.app.ui.components.skeleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private data class NowPlaying(
    val programName: String,
    val programGenre: String,
    val programDescription: String,
    val programImage: String?,
    val djName: String,
    val djImage: String?,
    val songTitle: String,
    val songArtist: String,
    val songAlbum: String,
    val songRequester: String,
    val songImage: String?,
    val songLyrics: String,
    val songHistory: List<String>,
    val songDuration: Long,
    val songProgress: Long,
    val lastUpdatedAt: Long,
) {
    val hasDjImage get() = !djImage.isNullOrBlank() && djImage != "about:blank"

    companion object {
        fun from(extras: Bundle?): NowPlaying? {
            if (extras == null || extras.getLong(
                    MediaPlaybackService.LAST_UPDATED_TIME,
                    -1L
                ) == -1L
            ) {
                return null
            }

            return NowPlaying(
                programName = extras.getString(MediaPlaybackService.PROGRAM_NAME) ?: "",
                programGenre = extras.getString(MediaPlaybackService.PROGRAM_GENRE) ?: "",
                programDescription = extras.getString(MediaPlaybackService.PROGRAM_DESCRIPTION)
                    ?: "",
                programImage = extras.getString(MediaPlaybackService.PROGRAM_IMAGE),
                djName = extras.getString(MediaPlaybackService.DJ_NAME) ?: "",
                djImage = extras.getString(MediaPlaybackService.DJ_IMAGE),
                songTitle = extras.getString(MediaPlaybackService.SONG_TITLE) ?: "",
                songArtist = extras.getString(MediaPlaybackService.SONG_ARTIST) ?: "",
                songAlbum = extras.getString(MediaPlaybackService.SONG_ALBUM) ?: "",
                songRequester = extras.getString(MediaPlaybackService.SONG_REQUESTER) ?: "",
                songImage = extras.getString(MediaPlaybackService.SONG_IMAGE),
                songLyrics = extras.getString(MediaPlaybackService.SONG_LYRICS) ?: "",
                songHistory = extras.getStringArray(MediaPlaybackService.SONG_HISTORY)?.toList()
                    ?: emptyList(),
                songDuration = extras.getLong(MediaPlaybackService.SONG_DURATION),
                songProgress = extras.getLong(MediaPlaybackService.CURRENT_TIME) - extras.getLong(
                    MediaPlaybackService.SONG_START_TIME
                ),
                lastUpdatedAt = extras.getLong(MediaPlaybackService.LAST_UPDATED_TIME),
            )
        }
    }
}

private val Placeholder = NowPlaying(
    programName = "Program Name",
    programGenre = "Genre",
    programDescription = "Program description placeholder. It should wrap a few lines so the skeleton will properly pick them up and display them.",
    programImage = null,
    djName = "DJ Name",
    djImage = null,
    songTitle = "Song Title Placeholder",
    songArtist = "Song Artist Placeholder",
    songAlbum = "Song Album Placeholder",
    songRequester = "",
    songImage = null,
    songLyrics = "",
    songHistory = emptyList(),
    songDuration = 0,
    songProgress = 0,
    lastUpdatedAt = 0,
)

@Composable
fun NowScreen(extras: Bundle?, scrollToTop: Flow<Unit>, onOpenSchedule: () -> Unit) {
    val scrollState = rememberScrollState()
    val scrolled by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect { scrollState.animateScrollTo(0) }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(scrolled)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.75f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (extras?.getBoolean(MediaPlaybackService.HAS_ERROR) == true) {
                    Text(
                        stringResource(R.string.label_now_error),
                        modifier = Modifier.padding(top = 15.dp, bottom = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    NowPlayingContent(NowPlaying.from(extras), onOpenSchedule)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingContent(nowPlaying: NowPlaying?, onOpenSchedule: () -> Unit) {
    // Like the old skeleton, it is kept until the first set of images has been loaded.
    var programImageLoaded by remember { mutableStateOf(false) }
    var djImageLoaded by remember { mutableStateOf(false) }
    var songImageLoaded by remember { mutableStateOf(false) }
    val loading = nowPlaying == null || !programImageLoaded || !songImageLoaded ||
            (nowPlaying.hasDjImage && !djImageLoaded)
    val data = nowPlaying ?: Placeholder

    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val black = colorResource(R.color.black)
    val primary = MaterialTheme.colorScheme.primary

    Text(
        stringResource(R.string.label_now),
        modifier = Modifier.padding(top = 15.dp),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
    )

    BoxWithConstraints(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .skeleton(loading, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val size = maxWidth

        AsyncImage(
            model = data.programImage,
            contentDescription = stringResource(R.string.program_image_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { programImageLoaded = true },
            onError = { programImageLoaded = true },
        )

        // Bottom-aligned and allowed to overflow upwards, so the panels below push the program
        // info up instead of being squeezed.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(black.copy(alpha = 0f), black)))
                    .padding(top = 70.dp)
                    .padding(10.dp)
                    .heightIn(min = 50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (data.hasDjImage) {
                    AsyncImage(
                        model = data.djImage,
                        contentDescription = stringResource(R.string.dj_image_description),
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onSuccess = { djImageLoaded = true },
                        onError = { djImageLoaded = true },
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .height(50.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val shadow = Shadow(black, Offset(0f, 1f), 1f)

                    if (data.programName.isNotBlank()) {
                        MarqueeText(
                            data.programName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = shadow,
                        )
                    }

                    val djName = if (data.djName.isBlank()) stringResource(R.string.playlist)
                    else stringResource(R.string.dj_string, data.djName)
                    MarqueeText(
                        stringResource(R.string.dj_and_genre, djName, data.programGenre),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        shadow = shadow,
                    )
                }
            }

            AnimatedVisibility(
                historyOpen,
                enter = expandVertically(tween(200)),
                exit = shrinkVertically(tween(200)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(black)
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White)
                    )
                    Spacer(Modifier.height(5.dp))

                    data.songHistory.takeLast(4).forEach {
                        Text(
                            it,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedVisibility(
                lyricsOpen,
                enter = expandVertically(tween(200)),
                exit = shrinkVertically(tween(200)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(size)
                        .background(black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.songLyrics.isBlank()) {
                        Text(
                            stringResource(R.string.lyrics_missing),
                            modifier = Modifier.padding(15.dp),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            data.songLyrics,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(15.dp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconToggleButton(
            checked = lyricsOpen,
            onCheckedChange = {
                lyricsOpen = it
                if (it) historyOpen = false
            },
        ) {
            Icon(
                painterResource(if (lyricsOpen) R.drawable.ic_baseline_lyrics_24 else R.drawable.ic_outline_lyrics_24),
                contentDescription = stringResource(R.string.action_lyrics),
                tint = primary,
            )
        }

        IconToggleButton(
            checked = historyOpen,
            onCheckedChange = {
                historyOpen = it
                if (it) lyricsOpen = false
            },
        ) {
            Icon(
                painterResource(R.drawable.ic_baseline_history_24),
                contentDescription = stringResource(R.string.action_song_history),
                tint = primary,
            )
        }

        IconButton(onClick = onOpenSchedule) {
            Icon(
                painterResource(R.drawable.ic_outline_calendar_today_24),
                contentDescription = stringResource(R.string.action_schedule),
                tint = primary,
            )
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = data.songImage,
            contentDescription = stringResource(R.string.song_cover_image),
            modifier = Modifier
                .size(65.dp)
                .skeleton(loading, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            onSuccess = { songImageLoaded = true },
            onError = { songImageLoaded = true },
        )

        Column(
            Modifier
                .weight(1f)
                .height(65.dp)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (data.songTitle.isNotBlank()) {
                MarqueeText(
                    data.songTitle,
                    modifier = Modifier.skeleton(loading),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (data.songArtist.isNotBlank()) {
                MarqueeText(
                    data.songArtist,
                    modifier = Modifier.skeleton(loading),
                    fontSize = 13.sp
                )
            }
            if (data.songAlbum.isNotBlank()) {
                MarqueeText(
                    data.songAlbum,
                    modifier = Modifier.skeleton(loading),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
            if (data.songRequester.isNotBlank()) {
                val requester = stringResource(R.string.requester_string, data.songRequester)
                MarqueeText(
                    remember(requester) { AnnotatedString.fromHtml(requester) },
                    modifier = Modifier
                        .alpha(0.6f)
                        .skeleton(loading),
                    fontSize = 11.sp,
                )
            }
        }
    }

    SongProgress(
        data,
        Modifier
            .padding(top = 10.dp)
            .skeleton(loading)
    )

    Text(
        data.programDescription,
        modifier = Modifier
            .padding(top = 10.dp, bottom = 40.dp)
            .skeleton(loading),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SongProgress(nowPlaying: NowPlaying, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(getNow()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = getNow()
                delay(250.milliseconds)
            }
        }
    }

    val duration = nowPlaying.songDuration
    var progress = nowPlaying.songProgress + now - nowPlaying.lastUpdatedAt
    if (duration >= 0) {
        progress = min(duration, progress)
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(progress), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { if (duration > 0) progress.toFloat() / duration else 0f },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            if (duration < 0) stringResource(R.string.live) else formatTime(duration),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    shadow: Shadow? = null,
) = MarqueeText(AnnotatedString(text), modifier, color, fontSize, fontWeight, fontStyle, shadow)

@Composable
private fun MarqueeText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    shadow: Shadow? = null,
) {
    Text(
        text,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        maxLines = 1,
        style = LocalTextStyle.current.copy(shadow = shadow),
    )
}

private fun formatTime(time: Long): String = DateUtils.formatElapsedTime(time / 1000)

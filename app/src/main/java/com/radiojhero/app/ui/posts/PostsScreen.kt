package com.radiojhero.app.ui.posts

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.PostsFetcher
import com.radiojhero.app.pmDocToHTML
import com.radiojhero.app.ui.components.AppTopBar
import com.radiojhero.app.ui.components.skeleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.text.SimpleDateFormat
import java.util.*

/** How close to the end of a list (in items) the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 10

@Composable
fun PostsScreen(
    scrollToTop: Flow<Unit>,
    onOpenPost: (link: String) -> Unit,
    viewModel: PostsViewModel = viewModel(),
) {
    val posts by viewModel.uiState.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val searchListState = rememberLazyListState()
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val isShowingSearch = isSearchOpen && viewModel.searchQuery.isNotBlank()
    val keyboardController = LocalSoftwareKeyboardController.current

    val closeSearch = {
        isSearchOpen = false
        viewModel.search("")
    }
    BackHandler(enabled = isSearchOpen, onBack = closeSearch)

    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            gridState.animateScrollToItem(0)
            searchListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { posts.isNotEmpty() && gridState.isNearEnd() }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.fetch(false) }
    }

    LaunchedEffect(searchListState) {
        snapshotFlow { searchListState.isNearEnd() }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMoreSearchResults() }
    }

    // Hide the keyboard once the user starts scrolling the results.
    LaunchedEffect(gridState, searchListState) {
        snapshotFlow { gridState.isScrollInProgress || searchListState.isScrollInProgress }
            .filter { it }
            .collect { keyboardController?.hide() }
    }

    var isErrorDismissed by remember(hasError) { mutableStateOf(false) }
    if (hasError && posts.isEmpty() && !isErrorDismissed) {
        AlertDialog(
            onDismissRequest = { isErrorDismissed = true },
            confirmButton = {
                TextButton(onClick = { isErrorDismissed = true }) {
                    Text(stringResource(R.string.button_dismiss))
                }
            },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(stringResource(R.string.posts_error)) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        val scrolled = if (isShowingSearch) searchListState.isScrolled() else gridState.isScrolled()

        if (isSearchOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            AppTopBar(
                scrolled = scrolled,
                onNavigateUp = closeSearch,
                title = {
                    TextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::search,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                actions = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_close_24),
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
            )
        } else {
            AppTopBar(
                scrolled = scrolled,
                actions = {
                    IconButton(onClick = { isSearchOpen = true }) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_search_24),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
            )
        }

        Box(Modifier.weight(1f)) {
            if (isShowingSearch) {
                SearchResults(
                    results = viewModel.searchResults,
                    stats = viewModel.searchStats,
                    state = searchListState,
                    onOpenPost = onOpenPost,
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PostGrid(posts, gridState, onOpenPost)
                }
            }
        }
    }
}

@Composable
private fun PostGrid(
    posts: List<PostsFetcher.Post>,
    state: LazyGridState,
    onOpenPost: (link: String) -> Unit,
) {
    val dateFormat = remember {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize(),
        state = state,
    ) {
        if (posts.isEmpty()) {
            items(60) {
                PostItem(null, dateFormat) {}
            }
        } else {
            items(posts) { post ->
                PostItem(post, dateFormat) { onOpenPost(post.link) }
            }
        }
    }
}

/** A post in the list, or a skeleton placeholder when [post] is null. */
@Composable
private fun PostItem(post: PostsFetcher.Post?, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    val loading = post == null
    val black = colorResource(R.color.black)
    val title = remember(post) {
        post?.let { AnnotatedString.fromHtml(pmDocToHTML(it.title)) }
            ?: AnnotatedString("Post title, which can occupy a maximum of two lines")
    }
    val subtitle = remember(post) {
        post?.let { AnnotatedString.fromHtml(pmDocToHTML(it.subtitle)) }
            ?: AnnotatedString("Post subtitle, which has more room: it can occupy a maximum of three lines before it gets truncated.")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            post?.category ?: "Post Column",
            modifier = Modifier.skeleton(loading),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(75.dp)
                    .skeleton(loading, RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (post != null) {
                    AsyncImage(
                        model = post.coverImage,
                        contentDescription = stringResource(R.string.post_cover_image),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        dateFormat.format(post.date.toInstant().toEpochMilli()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        black.copy(alpha = 0f),
                                        black
                                    )
                                )
                            )
                            .padding(top = 30.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(
                                black,
                                Offset(0f, 1f),
                                1f
                            )
                        ),
                    )
                }
            }

            Column(Modifier.padding(start = 8.dp)) {
                if (loading) {
                    Text(
                        "",
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1f, 0.9f)
                            .skeleton(loading),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "",
                        modifier = Modifier
                            .fillMaxSize(0.75f)
                            .scale(1f, 0.9f)
                            .skeleton(loading),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "",
                        modifier = Modifier
                            .alpha(0.6f)
                            .fillMaxSize()
                            .scale(1f, 0.9f)
                            .skeleton(loading),
                        fontSize = 11.sp,
                    )
                    Text(
                        "",
                        modifier = Modifier
                            .alpha(0.6f)
                            .fillMaxSize()
                            .scale(1f, 0.9f)
                            .skeleton(loading),
                        fontSize = 11.sp,
                    )
                    Text(
                        "",
                        modifier = Modifier
                            .alpha(0.6f)
                            .fillMaxSize(0.75f)
                            .scale(1f, 0.9f)
                            .skeleton(loading),
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        title,
                        modifier = Modifier.skeleton(loading),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        modifier = Modifier
                            .alpha(0.6f)
                            .skeleton(loading),
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<PostsFetcher.Post>,
    stats: PostsViewModel.SearchStats?,
    state: LazyListState,
    onOpenPost: (link: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        if (stats != null) {
            item {
                Text(
                    pluralStringResource(
                        R.plurals.search_stats,
                        stats.nbHits,
                        stats.nbHits,
                        stats.processingTimeMS,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                )
            }
        }

        items(results) { post ->
            SearchHit(post) { onOpenPost(post.link) }
        }
    }
}

@Composable
private fun SearchHit(post: PostsFetcher.Post, onClick: () -> Unit) {
    val title = remember(post) { AnnotatedString.fromHtml(post.title) }
    val excerpt = remember(post) { AnnotatedString.fromHtml(post.subtitle) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = post.coverImage,
            contentDescription = stringResource(R.string.post_cover_image),
            modifier = Modifier
                .size(65.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )

        Column(Modifier.padding(start = 8.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                excerpt,
                modifier = Modifier.alpha(0.6f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyGridState.isScrolled() =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

private fun LazyListState.isScrolled() =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

private fun LazyGridState.isNearEnd(): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
}

private fun LazyListState.isNearEnd(): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
}

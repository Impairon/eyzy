@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.abo.avatarblackout

import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.CircleShape
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.SearchBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import android.os.Build
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.launch

/**
 * Compose port of Voice 8.2.4-2's BookOverview and BookPlayView screen structure.
 * App-specific persistence and Media3 actions remain owned by MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceAddContentScreen(
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onChooseFile: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add content") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(112.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Select a folder", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose a folder for one book or your entire library, or add a single audio file.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = onChooseFolder) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Folder")
                    }
                    Spacer(Modifier.size(8.dp))
                    FilledTonalButton(onClick = onChooseFile) {
                        Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Audio file")
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceBooksScreen(
    books: List<AudioBook>,
    loadingBooks: Boolean = false,
    activeBookId: String? = null,
    activePositionMs: Long = 0L,
    searchQuery: String,
    playerBook: AudioBook?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSearchChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onBookClick: (String) -> Unit,
    onBookOptions: (String) -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: (Int) -> Unit,
    onBookmark: () -> Unit,
    onResumeBookmark: (String, Int, Long) -> Unit,
    onDeleteBookmark: (String, String) -> Unit,
    rewindSeconds: Int,
    forwardSeconds: Int,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val voiceDarkColors = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = voiceDarkColors) {
        var showAllBookmarks by remember { mutableStateOf(false) }
        var deletedBookmarkIds by remember { mutableStateOf(emptySet<String>()) }
        if (playerBook == null) {
            BookOverviewScreen(
                books = books,
                loadingBooks = loadingBooks,
                activeBookId = activeBookId,
                activePositionMs = activePositionMs,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                onAddBook = onAddBook,
                onOpenBookmarks = { showAllBookmarks = true },
                onBookClick = onBookClick,
                onBookOptions = onBookOptions,
            )
        } else {
            BookPlayView(
                book = playerBook,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                onBack = onBack,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onRewind = onRewind,
                onForward = onForward,
                onPrevious = onPrevious,
                onNext = onNext,
                onChapters = onChapters,
                onBookmark = onBookmark,
                onBookmarks = { showAllBookmarks = true },
                rewindSeconds = rewindSeconds,
                forwardSeconds = forwardSeconds,
                onSpeed = onSpeed,
                onSleepTimer = onSleepTimer,
            )
        }
        if (showAllBookmarks) {
            val saved = books.flatMap { book -> book.bookmarks.map { bookmark -> Triple(book, bookmark, book.tracks.getOrNull(bookmark.trackIndex)?.title.orEmpty()) } }.filterNot { it.second.id in deletedBookmarkIds }
            AlertDialog(
                onDismissRequest = { showAllBookmarks = false },
                title = { Text("Saved bookmarks") },
                text = {
                    if (saved.isEmpty()) Text("No saved positions yet.")
                    else LazyColumn {
                        items(saved, key = { "${it.first.id}:${it.second.id}" }) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onResumeBookmark(entry.first.id, entry.second.trackIndex, entry.second.positionMs)
                                    showAllBookmarks = false
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.second.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${entry.first.name} · ${entry.third} · ${formatVoiceDuration(entry.second.positionMs)}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    onDeleteBookmark(entry.first.id, entry.second.id)
                                    deletedBookmarkIds = deletedBookmarkIds + entry.second.id
                                }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bookmark")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAllBookmarks = false }) { Text("Close") } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookOverviewScreen(
    books: List<AudioBook>,
    loadingBooks: Boolean,
    activeBookId: String?,
    activePositionMs: Long,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onBookClick: (String) -> Unit,
    onBookOptions: (String) -> Unit,
) {
    var localQuery by remember { mutableStateOf(searchQuery) }
    var searchActive by remember { mutableStateOf(false) }
    val filtered = remember(books, localQuery) { books.filter { book -> fuzzyBookMatch(book, localQuery) } }
    val (currentBooks, notStartedBooks, finishedBooks) = remember(filtered) {
        Triple(
            filtered.filter { bookProgress(it) > 0f && bookProgress(it) < 0.995f }
                .sortedByDescending { it.lastPlayedAt },
            filtered.filter { bookProgress(it) <= 0f }
                .sortedBy { it.name.lowercase() },
            filtered.filter { bookProgress(it) >= 0.995f }
                .sortedByDescending { it.lastPlayedAt }
        )
    }

    Scaffold(
        topBar = {
            SearchBar(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (searchActive) 0.dp else 16.dp),
                query = if (searchActive) localQuery else "",
                onQueryChange = {
                    localQuery = it
                    onSearchChange(it)
                },
                onSearch = { query -> localQuery = query; onSearchChange(query); searchActive = false },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text("Search books") },
                leadingIcon = {
                    if (searchActive) {
                        IconButton(onClick = { searchActive = false }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search")
                        }
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                },
                trailingIcon = {
                    if (!searchActive) {
                        Row {
                            IconButton(onClick = onAddBook) {
                                Icon(Icons.Outlined.Folder, contentDescription = "Add folder")
                            }
                            IconButton(onClick = onOpenBookmarks) {
                                Icon(Icons.Default.Bookmarks, contentDescription = "Saved bookmarks")
                            }
                        }
                    }
                },
            ) { }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (currentBooks.isNotEmpty()) {
                    stickyHeader {
                        Text(
                            "Current",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                    items(currentBooks, key = { it.id }) { book ->
                        VoiceBookRow(book, onBookClick, onBookOptions, activePositionMs.takeIf { book.id == activeBookId })
                    }
                }
                if (notStartedBooks.isNotEmpty()) {
                    stickyHeader {
                        Text(
                            "Not started",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                    items(notStartedBooks, key = { it.id }) { book ->
                        VoiceBookRow(book, onBookClick, onBookOptions, activePositionMs.takeIf { book.id == activeBookId })
                    }
                }
                if (finishedBooks.isNotEmpty()) {
                    stickyHeader {
                        Text(
                            "Finished",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                    items(finishedBooks, key = { it.id }) { book ->
                        VoiceBookRow(book, onBookClick, onBookOptions, activePositionMs.takeIf { book.id == activeBookId })
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            when {
                                loadingBooks -> "Loading your library…"
                                books.isEmpty() -> "Your library is empty"
                                else -> "No matching books"
                            },
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun VoiceBookRow(
    book: AudioBook,
    onBookClick: (String) -> Unit,
    onBookOptions: (String) -> Unit,
    livePositionMs: Long? = null,
) {
    val totalDuration = book.tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
    val index = book.currentTrackIndex.coerceIn(0, book.tracks.lastIndex)
    val elapsedBefore = book.tracks.take(index).sumOf { it.durationMs.coerceAtLeast(0L) }
    val elapsed = (elapsedBefore + (livePositionMs ?: book.positionMs)).coerceIn(0L, totalDuration)
    val progress = if (totalDuration > 0L) elapsed.toFloat() / totalDuration.toFloat() else 0f
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookOptions(book.id) },
            ),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(
                    book.name,
                    book.coverPath,
                    Modifier.padding(8.dp).size(76.dp),
                    audioUri = book.currentTrack?.path,
                    loadEmbeddedArtwork = true,
                    maxDecodeDimension = 320,
                )
                Column(
                    Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    val author = book.currentTrack?.artist.orEmpty()
                    if (author.isNotBlank()) {
                        Text(author.uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Text(book.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text(
                        formatVoiceDuration((totalDuration - elapsed).coerceAtLeast(0L)) + " remaining",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (progress > 0.05f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun fuzzyBookMatch(book: AudioBook, query: String): Boolean {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val searchable = buildString {
        append(book.name).append(' ')
        book.tracks.forEach { track -> append(track.title).append(' ').append(track.artist).append(' ') }
    }.lowercase(java.util.Locale.ROOT)
    return words.all { word -> searchable.contains(word.lowercase(java.util.Locale.ROOT)) }
}

private fun bookProgress(book: AudioBook): Float {
    val total = book.tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
    if (total <= 0L) return 0f
    val index = book.currentTrackIndex.coerceIn(0, book.tracks.lastIndex)
    val elapsedBefore = book.tracks.take(index).sumOf { it.durationMs.coerceAtLeast(0L) }
    val elapsed = (elapsedBefore + book.positionMs).coerceIn(0L, total)
    return elapsed.toFloat() / total.toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookPlayView(
    book: AudioBook,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: (Int) -> Unit,
    onBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    rewindSeconds: Int,
    forwardSeconds: Int,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    var showChapters by remember(book.id) { mutableStateOf(false) }
    var localSliderValue by remember(book.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(book.id) { mutableStateOf(false) }
    var showRemainingTime by remember(book.id) { mutableStateOf(false) }
    val track = book.currentTrack
    val chapterListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding),
        ) {
            val coverSize = (maxHeight * 0.25f)
                .coerceAtMost(maxWidth * 0.68f)
                .coerceAtMost(176.dp)
                .coerceAtLeast(88.dp)

            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                val coverScale by animateFloatAsState(
                    targetValue = if (playing) 1f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
                    label = "playbackCoverScale",
                )
                BookCover(
                    book.name,
                    book.coverPath,
                    Modifier.size(coverSize)
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .clip(RoundedCornerShape(12.dp)),
                    track?.path,
                )

                if (track != null) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (book.tracks.size > 1) {
                            androidx.compose.material3.FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous chapter")
                            }
                        }
                        Surface(
                            onClick = { showChapters = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Chapter ${book.currentTrackIndex + 1} of ${book.tracks.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose chapter")
                            }
                        }
                        if (book.tracks.size > 1) {
                            androidx.compose.material3.FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                Column(Modifier.fillMaxWidth()) {
                    VoiceWavySeekBar(
                        progress = if (isDragging) localSliderValue else if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else 0f,
                        playing = playing,
                        dragging = isDragging,
                        onDragStart = { isDragging = true },
                        onProgressChange = { localSliderValue = it },
                        onSeek = {
                            if (durationMs > 0L) onSeek((durationMs * it).toLong())
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val displayedPosition = if (isDragging) (durationMs * localSliderValue).toLong() else positionMs
                        Text(
                            text = if (showRemainingTime) "-${formatVoiceDuration((durationMs - displayedPosition).coerceAtLeast(0L))}" else formatVoiceDuration(displayedPosition),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { showRemainingTime = !showRemainingTime },
                        )
                        Text(
                            formatVoiceDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { showRemainingTime = !showRemainingTime },
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceSkipButton(
                        forward = false,
                        seconds = rewindSeconds,
                        onClick = onRewind,
                        modifier = Modifier.height(52.dp),
                    )
                    Spacer(Modifier.size(18.dp))
                    val playCornerRadius by animateDpAsState(
                        targetValue = if (playing) 22.dp else 40.dp,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                        label = "playButtonShape",
                    )
                    val playScale by animateFloatAsState(
                        targetValue = if (playing) 1f else 0.96f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                        label = "playButtonScale",
                    )
                    Box(
                        Modifier.size(60.dp)
                            .graphicsLayer { scaleX = playScale; scaleY = playScale }
                            .clip(RoundedCornerShape(playCornerRadius))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.size(18.dp))
                    VoiceSkipButton(
                        forward = true,
                        seconds = forwardSeconds,
                        onClick = onForward,
                        modifier = Modifier.height(52.dp),
                    )
                }

                // Keep the secondary player actions together in one compact row,
                // below the transport controls rather than in the title bar.
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerUtilityAction(
                        icon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
                        label = "Save",
                        onClick = onBookmark,
                    )
                    PlayerUtilityAction(
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        label = "Bookmarks",
                        onClick = onBookmarks,
                    )
                    PlayerUtilityAction(
                        icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        label = "Speed",
                        onClick = onSpeed,
                    )
                    PlayerUtilityAction(
                        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        label = "Sleep",
                        onClick = onSleepTimer,
                    )
                }
            }
        }
    }

    LaunchedEffect(showChapters, book.currentTrackIndex) {
        if (showChapters && book.tracks.isNotEmpty()) {
            chapterListState.animateScrollToItem(
                book.currentTrackIndex.coerceIn(0, book.tracks.lastIndex),
                scrollOffset = -24,
            )
        }
    }
    if (showChapters) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn(state = chapterListState) {
                    itemsIndexed(book.tracks, key = { index, item -> "${book.id}:$index:${item.path}" }) { index, chapter ->
                        Surface(
                            onClick = { onChapters(index); showChapters = false },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (index == book.currentTrackIndex) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                BookCover(chapter.title, null, Modifier.padding(start = 10.dp).size(44.dp), chapter.path)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(chapter.title, color = if (index == book.currentTrackIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2)
                                    Text(formatVoiceDuration(chapter.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (index == book.currentTrackIndex) Icon(Icons.Default.PlayArrow, contentDescription = "Currently playing", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun PlayerUtilityAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VoiceWavySeekBar(
    progress: Float,
    playing: Boolean,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hostView = LocalView.current
    var seekBarWidthPx by remember { mutableFloatStateOf(0f) }
    val phase by rememberInfiniteTransition(label = "seekWave")
        .animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "seekWavePhase",
        )
    val amplitude by animateFloatAsState(
        targetValue = if (playing && !dragging) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "seekWaveAmplitude",
    )
    Canvas(
        modifier = modifier.height(64.dp)
            .onSizeChanged { seekBarWidthPx = it.width.toFloat() }
            .pointerInteropFilter { event ->
                val width = seekBarWidthPx.coerceAtLeast(1f)
                val fraction = (event.x / width).coerceIn(0f, 1f)
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                        onDragStart()
                        onProgressChange(fraction)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> onProgressChange(fraction)
                    android.view.MotionEvent.ACTION_UP -> {
                        onProgressChange(fraction)
                        onSeek(fraction)
                        hostView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        onSeek(fraction)
                        hostView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                true
            },
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val thumbX = size.width * fraction
        val stroke = 6.dp.toPx()
        val gap = 7.dp.toPx()
        val wavelength = 44.dp.toPx()
        val waveAmplitude = 1.75.dp.toPx() * amplitude
        val activeEnd = thumbX - 3.dp.toPx() - gap
        if (activeEnd > 0f) {
            val path = Path()
            var x = 0f
            path.moveTo(x, centerY + waveAmplitude * sin(phase.toDouble()).toFloat())
            val step = 1.5.dp.toPx()
            while (x < activeEnd) {
                x = (x + step).coerceAtMost(activeEnd)
                val angle = (x / wavelength) * 6.2831855f + phase
                path.lineTo(x, centerY + waveAmplitude * sin(angle.toDouble()).toFloat())
            }
            drawPath(
                path = path,
                color = colors.primary,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        val inactiveStart = thumbX + 3.dp.toPx() + gap
        if (inactiveStart < size.width) {
            drawLine(
                color = colors.secondaryContainer,
                start = Offset(inactiveStart, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        val thumbWidth = if (dragging) 7.dp.toPx() else 6.dp.toPx()
        val thumbHeight = if (dragging) 44.dp.toPx() else 32.dp.toPx()
        if (dragging) {
            drawCircle(
                color = colors.primary.copy(alpha = 0.16f),
                radius = 15.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
        }
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
            size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f),
        )
    }
}

@Composable
private fun VoiceSkipButton(
    forward: Boolean,
    seconds: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val widthRatio by animateFloatAsState(
        targetValue = if (pressed) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "skipWidth",
    )
    val cornerPercent by animateFloatAsState(
        targetValue = if (pressed) 25f else 50f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "skipCorner",
    )
    val nudge = remember { Animatable(0f) }
    var bursts by remember { mutableStateOf(emptyList<Long>()) }
    var nextBurstId by remember { mutableLongStateOf(0L) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            onClick = {
                onClick()
                bursts = bursts + nextBurstId++
                scope.launch {
                    nudge.snapTo(0f)
                    nudge.animateTo(1f, tween(90, easing = FastOutSlowInEasing))
                    nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
                }
            },
            modifier = Modifier.fillMaxHeight().aspectRatio(widthRatio, matchHeightConstraintsFirst = true),
            shape = RoundedCornerShape(percent = cornerPercent.roundToInt()),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            interactionSource = interactionSource,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_voice_replay),
                    contentDescription = if (forward) "Fast forward" else "Rewind",
                    modifier = Modifier.fillMaxHeight(0.5f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .graphicsLayer {
                            rotationZ = nudge.value * if (forward) 40f else -40f
                            scaleX = if (forward) -1f else 1f
                        },
                )
            }
        }
        bursts.forEach { id ->
            key(id) {
                VoiceSkipBurst(
                    text = if (forward) "+$seconds" else "−$seconds",
                    onFinish = { bursts = bursts - id },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun VoiceSkipBurst(
    text: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        currentOnFinish()
    }
    Text(
        modifier = modifier.clearAndSetSemantics {}.graphicsLayer {
            val p = progress.value
            translationY = -p * 56.dp.toPx()
            alpha = 1f - p
            scaleX = 0.8f + 0.5f * p
            scaleY = 0.8f + 0.5f * p
        },
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

private object BookThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(32)
    @Synchronized fun get(key: String): ImageBitmap? = cache.get(key)
    @Synchronized fun put(key: String, bitmap: ImageBitmap) { cache.put(key, bitmap) }
}

@Composable
private fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
    audioUri: String? = null,
    loadEmbeddedArtwork: Boolean = true,
    maxDecodeDimension: Int = 1024,
) {
    val context = LocalContext.current
    val coverSignature = remember(coverPath) {
        coverPath?.let { path -> runCatching {
            val file = java.io.File(path)
            "${file.lastModified()}:${file.length()}"
        }.getOrDefault("") }.orEmpty()
    }
    val cacheKey = remember(coverPath, coverSignature, audioUri, loadEmbeddedArtwork, maxDecodeDimension) {
        "cover:${coverPath.orEmpty()}:$coverSignature:audio:${if (loadEmbeddedArtwork) audioUri.orEmpty() else ""}:size:$maxDecodeDimension"
    }
    val bitmap by produceState<ImageBitmap?>(BookThumbnailCache.get(cacheKey), cacheKey) {
        value = BookThumbnailCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            val saved = coverPath?.let { path -> runCatching {
                val file = java.io.File(path)
                if (!file.isFile || file.length() <= 0L) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > maxDecodeDimension || bounds.outHeight / sample > maxDecodeDimension) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull() }
            saved ?: audioUri?.takeIf { loadEmbeddedArtwork }?.let { uriString -> runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(uriString))
                    val bytes = retriever.embeddedPicture ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                    var sample = 1
                    while (bounds.outWidth / sample > maxDecodeDimension || bounds.outHeight / sample > maxDecodeDimension) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                } finally { runCatching { retriever.release() } }
            }.getOrNull() }
        }?.also { BookThumbnailCache.put(cacheKey, it) }
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = "$title cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("♫", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatVoiceDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}

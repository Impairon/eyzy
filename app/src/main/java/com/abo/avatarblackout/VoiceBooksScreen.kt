@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.abo.avatarblackout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Forward30
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.unit.dp

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
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onBookClick: (String) -> Unit,
    onBookOptions: (String) -> Unit,
) {
    var localQuery by remember { mutableStateOf(searchQuery) }
    var searchActive by remember { mutableStateOf(false) }
    val filtered = remember(books, localQuery) {
        books.filter { book ->
            localQuery.isBlank() || book.name.contains(localQuery, true) ||
                book.tracks.any { it.title.contains(localQuery, true) || it.artist.contains(localQuery, true) }
        }
    }
    val currentBooks = filtered.filter { bookProgress(it) > 0f && bookProgress(it) < 0.995f }
        .sortedByDescending { it.lastPlayedAt }
    val notStartedBooks = filtered.filter { bookProgress(it) <= 0f }
        .sortedBy { it.name.lowercase() }
    val finishedBooks = filtered.filter { bookProgress(it) >= 0.995f }
        .sortedByDescending { it.lastPlayedAt }

    Scaffold(
        topBar = {
            SearchBar(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (searchActive) 0.dp else 16.dp),
                query = if (searchActive) localQuery else "",
                onQueryChange = {
                    localQuery = it
                    onSearchChange(it)
                },
                onSearch = onSearchChange,
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
                        VoiceBookRow(book, onBookClick, onBookOptions)
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
                        VoiceBookRow(book, onBookClick, onBookOptions)
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
                        VoiceBookRow(book, onBookClick, onBookOptions)
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            if (books.isEmpty()) "Your library is empty" else "No matching books",
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
) {
    val progress = bookProgress(book)
    val totalDuration = book.tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
    val elapsed = (totalDuration * progress).toLong()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookOptions(book.id) },
            ),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.name, book.coverPath, Modifier.padding(8.dp).size(76.dp), book.currentTrack?.path)
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
    val sliderInteraction = remember(book.id) { MutableInteractionSource() }
    val isDragging by sliderInteraction.collectIsDraggedAsState()
    val track = book.currentTrack
    val chapterListState = rememberLazyListState()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(book.name, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = onBookmark) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save position")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onSleepTimer) { Icon(Icons.Default.Timer, contentDescription = "Sleep timer") }
                    IconButton(onClick = onBookmarks) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Saved bookmarks")
                    }
                    IconButton(onClick = onSpeed) { Icon(Icons.Default.Speed, contentDescription = "Playback speed") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookCover(book.name, book.coverPath, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)), track?.path)
            if (track != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (book.tracks.size > 1) {
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous chapter")
                        }
                    }
                    Row(
                        Modifier.weight(1f).clickable { showChapters = true }.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        if (book.tracks.size > 1) {
                            Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose chapter")
                        }
                    }
                    if (book.tracks.size > 1) {
                        IconButton(onClick = onNext) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter")
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Slider(
                value = if (isDragging) localSliderValue else if (durationMs > 0L) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else 0f,
                onValueChange = { localSliderValue = it },
                onValueChangeFinished = {
                    if (durationMs > 0L) onSeek((durationMs * localSliderValue).toLong())
                },
                interactionSource = sliderInteraction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val displayedPosition = if (isDragging) (durationMs * localSliderValue).toLong() else positionMs
                Text(formatVoiceDuration(displayedPosition), style = MaterialTheme.typography.labelSmall)
                Text(formatVoiceDuration(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onRewind, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Replay, contentDescription = "Rewind $rewindSeconds seconds", modifier = Modifier.size(40.dp))
                        Text(
                            rewindSeconds.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.End,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                IconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.size(16.dp))
                IconButton(onClick = onForward, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Forward30, contentDescription = "Forward $forwardSeconds seconds", modifier = Modifier.size(40.dp))
                        Text(
                            forwardSeconds.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.End,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
                        Text(
                            text = chapter.title,
                            color = if (index == book.currentTrackIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onChapters(index)
                                showChapters = false
                            }.padding(vertical = 14.dp),
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun BookCover(title: String, coverPath: String?, modifier: Modifier = Modifier, audioUri: String? = null) {
    val context = LocalContext.current
    val coverSignature = remember(coverPath) {
        coverPath?.let { path -> runCatching {
            val file = java.io.File(path)
            "${file.lastModified()}:${file.length()}"
        }.getOrDefault("") }.orEmpty()
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, coverPath, coverSignature, audioUri) {
        value = withContext(Dispatchers.IO) {
            val saved = coverPath?.let { path -> runCatching {
                val file = java.io.File(path)
                if (!file.isFile || file.length() <= 0L) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull() }
            saved ?: audioUri?.let { uriString -> runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(uriString))
                    val bytes = retriever.embeddedPicture ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                    var sample = 1
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                } finally { runCatching { retriever.release() } }
            }.getOrNull() }
        }
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

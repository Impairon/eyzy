package com.abo.avatarblackout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.unit.dp

/**
 * Compose port of Voice 8.2.4-2's BookOverview and BookPlayView screen structure.
 * App-specific persistence and Media3 actions remain owned by MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceBooksScreen(
    books: List<AudioBook>,
    activeBookId: String?,
    searchQuery: String,
    playerBook: AudioBook?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSearchChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlayActive: () -> Unit,
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
    onBookmarks: () -> Unit,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val voiceDarkColors = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = voiceDarkColors) {
        if (playerBook == null) {
            BookOverviewScreen(
                books = books,
                activeBookId = activeBookId,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                onAddBook = onAddBook,
                onSettingsClick = onSettingsClick,
                onPlayActive = onPlayActive,
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
                onBookmarks = onBookmarks,
                onSpeed = onSpeed,
                onSleepTimer = onSleepTimer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookOverviewScreen(
    books: List<AudioBook>,
    activeBookId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlayActive: () -> Unit,
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
    val continueBook = books.firstOrNull { it.id == activeBookId }
        ?: books.firstOrNull { it.lastPlayedAt > 0L }

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
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                },
            ) { }
        },
        floatingActionButton = {
            if (activeBookId != null) {
                FloatingActionButton(onClick = onPlayActive) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume playback")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (continueBook != null && localQuery.isBlank()) {
                    item {
                        Text(
                            "Continue listening",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                .clickable { onBookClick(continueBook.id) },
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BookCover(continueBook.name, Modifier.size(72.dp))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(continueBook.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        continueBook.currentTrack?.title.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("Continue listening", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
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
                } else {
                    item {
                        Text(
                            "Books",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(filtered, key = { it.id }) { book ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                .clickable { onBookClick(book.id) },
                            colors = CardDefaults.cardColors(),
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BookCover(book.name, Modifier.size(width = 76.dp, height = 84.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(book.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    Text(
                                        book.currentTrack?.title ?: "${book.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val progress = if (book.currentTrack?.durationMs ?: 0L > 0L) {
                                        (book.positionMs.toFloat() / (book.currentTrack?.durationMs ?: 1L)).coerceIn(0f, 1f)
                                    } else 0f
                                    if (progress > 0f) {
                                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }
                                }
                                IconButton(onClick = { onBookOptions(book.id) }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Book options")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
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
    onBookmarks: () -> Unit,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    var showChapters by remember(book.id) { mutableStateOf(false) }
    var localSliderValue by remember(book.id) { mutableFloatStateOf(0f) }
    val sliderInteraction = remember(book.id) { MutableInteractionSource() }
    val isDragging by sliderInteraction.collectIsDraggedAsState()
    val track = book.currentTrack
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(book.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onSleepTimer) { Icon(Icons.Default.Timer, contentDescription = "Sleep timer") }
                    IconButton(onClick = onBookmarks) { Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks") }
                    IconButton(onClick = onSpeed) { Icon(Icons.Default.Speed, contentDescription = "Playback speed") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookCover(book.name, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)))
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
                    Icon(Icons.Default.Replay, contentDescription = "Rewind 15 seconds", modifier = Modifier.size(40.dp))
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
                    Icon(Icons.Default.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showChapters) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn {
                    items(book.tracks.indices.toList()) { index ->
                        Text(
                            text = book.tracks[index].title,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onChapters(index)
                                showChapters = false
                            }.padding(vertical = 12.dp),
                        )
                        Divider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun BookCover(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("♫", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

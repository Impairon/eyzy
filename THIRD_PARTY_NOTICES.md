Audiobook scanner attribution

The folder-layout detection, cached document-tree traversal, and bounded background
scanning in app/src/main/java/com/abo/avatarblackout/AudioBookScanner.kt are adapted
from the Voice audiobook player:

https://github.com/PaulWoitaschek/Voice

Relevant upstream files:
- core/scanner/src/main/kotlin/voice/core/scanner/MediaScanner.kt
- core/scanner/src/main/kotlin/voice/core/scanner/ChapterParser.kt
- core/scanner/src/main/kotlin/voice/core/scanner/MediaScanTrigger.kt
- core/documentfile/src/main/kotlin/voice/core/documentfile/CachedDocumentFile.kt
- core/documentfile/src/main/kotlin/voice/core/documentfile/RealCachedDocumentFile.kt
- features/folderPicker/src/main/kotlin/voice/features/folderPicker/selectType/FolderStructure.kt

Voice is licensed under GNU GPL version 3. The adapted scanner remains under
GPL-3.0. The full license text is in LICENSE-VOICE.md.

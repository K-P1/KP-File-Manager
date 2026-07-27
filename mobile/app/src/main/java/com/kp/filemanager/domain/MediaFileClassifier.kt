/** mobile/app/src/main/java/com/kp/filemanager/domain/MediaFileClassifier.kt: Classifies discovered files by media role using centralized extension sets; created for the media refactor. */
package com.kp.filemanager.domain

import java.util.Locale

object MediaFileClassifier {
    val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm")
    val subtitleExtensions = setOf("srt", "ass", "ssa", "sub", "vtt")
    val artworkExtensions = setOf("jpg", "jpeg", "png", "webp")
    val metadataExtensions = setOf("nfo")

    fun classify(fileName: String): MediaFileType {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            in videoExtensions -> MediaFileType.VIDEO
            in subtitleExtensions -> MediaFileType.SUBTITLE
            in artworkExtensions -> MediaFileType.ARTWORK
            in metadataExtensions -> MediaFileType.METADATA
            else -> MediaFileType.UNKNOWN
        }
    }
}

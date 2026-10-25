@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.roadplay.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import com.roadplay.player.data.db.VideoEntity

/** Media IDs used in the browse tree that Android Auto sees. */
object MediaIds {
    const val ROOT = "roadplay_root"
    const val RECENT_ROOT = "roadplay_recent_root"
    const val ALL = "all"
    const val FAVOURITES = "favourites"
    const val RECENT = "recent"
    const val PLAYLISTS = "playlists"
    private const val VIDEO_PREFIX = "video:"
    private const val PLAYLIST_PREFIX = "playlist:"

    fun video(id: Long) = "$VIDEO_PREFIX$id"
    fun playlist(id: Long) = "$PLAYLIST_PREFIX$id"
    fun parseVideo(mediaId: String): Long? =
        if (mediaId.startsWith(VIDEO_PREFIX)) mediaId.removePrefix(VIDEO_PREFIX).toLongOrNull() else null
    fun parsePlaylist(mediaId: String): Long? =
        if (mediaId.startsWith(PLAYLIST_PREFIX)) mediaId.removePrefix(PLAYLIST_PREFIX).toLongOrNull() else null
}

fun VideoEntity.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(MediaIds.video(id))
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(label())
                .setDisplayTitle(label())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                .build()
        )
        .build()

fun folderItem(mediaId: String, title: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

object SubtitleSupport {
    /** MIME type for the external subtitle formats Media3 can parse, or null if unsupported. */
    fun mimeFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

data class PlaybackProblem(val message: String, val markUnavailable: Boolean)

object PlaybackErrors {
    fun describe(errorCode: Int): PlaybackProblem = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            PlaybackProblem("This video was moved or deleted. Remove it from the library or re-import it.", true)
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            PlaybackProblem("RoadPlay no longer has permission to read this file. Re-import it to grant access again.", true)
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            PlaybackProblem("This phone can't decode this video's format. Try another file or convert it to H.264/AAC MP4.", false)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            PlaybackProblem("This file looks corrupt or uses an unsupported container.", false)
        else -> PlaybackProblem("Playback failed (error $errorCode). Try again or choose another video.", false)
    }
}

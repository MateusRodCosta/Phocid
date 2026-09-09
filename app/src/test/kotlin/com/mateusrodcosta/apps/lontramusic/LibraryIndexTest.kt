package com.mateusrodcosta.apps.lontramusic

import androidx.compose.ui.graphics.Color
import com.ibm.icu.text.Collator
import com.mateusrodcosta.apps.lontramusic.data.AlbumKey
import com.mateusrodcosta.apps.lontramusic.data.ArtworkType
import com.mateusrodcosta.apps.lontramusic.data.InvalidTrack
import com.mateusrodcosta.apps.lontramusic.data.LibraryIndex
import com.mateusrodcosta.apps.lontramusic.data.Track
import com.mateusrodcosta.apps.lontramusic.data.UnfilteredTrackIndex
import com.mateusrodcosta.apps.lontramusic.data.albumKey
import java.util.Locale
import kotlin.time.Duration
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryIndexTest {
    val collator = Collator.getInstance(Locale.ROOT).freeze()

    @Test
    fun indexing_noCrash_InvalidMetadata() {
        val index =
            LibraryIndex(
                UnfilteredTrackIndex(null, mapOf(InvalidTrack.id to InvalidTrack)),
                collator,
                emptyList(),
                emptyList(),
            )
        assertThat(index.tracks).isEqualTo(mapOf(InvalidTrack.id to InvalidTrack))
    }

    @Test
    fun indexing_noCrash_EmptyLibrary() {
        val index =
            LibraryIndex(UnfilteredTrackIndex(null, emptyMap()), collator, emptyList(), emptyList())
        assertThat(index.tracks).isEqualTo(emptyMap<Long, Track>())
    }

    @Test
    fun indexing_album_EmptyLibrary() {
        val index = emptyList<Track>().libraryIndex()
        assertThat(index.albums.keys).isEmpty()
    }

    @Test
    fun indexing_album_SingleTrack() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val index = listOf(track1).libraryIndex()
        assertThat(index.albums.keys).containsExactlyInAnyOrder(AlbumKey("album1", "artist1"))
    }

    @Test
    fun indexing_album_CaseMerging() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val track2 = track(2, album = "ALBUM1", albumArtist = "artist1")
        val track3 = track(3, album = "ALBUM1", albumArtist = "ARTIST1")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(
                index.albums.keys.map {
                    it.name.toString() to it.albumArtists.firstOrNull()?.toString()
                }
            )
            .containsExactlyInAnyOrder("ALBUM1" to "artist1")
    }

    @Test
    fun indexing_album_TracksWithDifferentAlbums() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val track2 = track(2, album = "album2", albumArtist = "artist2")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(AlbumKey("album1", "artist1"), AlbumKey("album2", "artist2"))
    }

    @Test
    fun indexing_album_Nulls() {
        val track1 = track(1, album = null, albumArtist = null)
        val track2 = track(2, album = "album1", albumArtist = null)
        val track3 = track(3, album = null, albumArtist = "artist1")
        val track4 = track(4, album = "album1", albumArtist = "artist1")
        val index = listOf(track1, track2, track3, track4).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(AlbumKey("album1", null), AlbumKey("album1", "artist1"))
    }

    @Test
    fun indexing_album_SameNameDifferentAlbumArtists() {
        val track1 = track(1, album = "album1", albumArtist = null)
        val track2 = track(2, album = "album1", albumArtist = "artist1")
        val track3 = track(3, album = "album1", albumArtist = "artist2")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(
                AlbumKey("album1", null),
                AlbumKey("album1", "artist1"),
                AlbumKey("album1", "artist2"),
            )
        assertThat(index.albums[AlbumKey("album1", null)]?.albumKey)
            .isEqualTo(AlbumKey("album1", null))
        assertThat(index.albums[AlbumKey("album1", "artist1")]?.albumKey)
            .isEqualTo(AlbumKey("album1", "artist1"))
        assertThat(index.albums[AlbumKey("album1", "artist2")]?.albumKey)
            .isEqualTo(AlbumKey("album1", "artist2"))
    }

    @Test
    fun indexing_folder_EmptyLibrary() {
        val index = emptyList<Track>().libraryIndex()
        assertThat(index.folders.keys).containsExactlyInAnyOrder("")
    }

    @Test
    fun indexing_folder_SingleTrack() {
        val track1 = track(1, path = "/music/track1.mp3", dateAdded = 100, version = 100)
        val index = listOf(track1).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
        assertThat(index.folders["music"]?.dateAdded).isEqualTo(100)
        assertThat(index.folders["music"]?.dateModified).isEqualTo(100)
    }

    @Test
    fun indexing_folder_MultipleTracksInSameFolder() {
        val track1 = track(1, path = "/music/track1.mp3", dateAdded = 100, version = 100)
        val track2 = track(2, path = "/music/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1, track2)
        assertThat(index.folders["music"]?.dateAdded).isEqualTo(100)
        assertThat(index.folders["music"]?.dateModified).isEqualTo(100)
    }

    @Test
    fun indexing_folder_TracksInDifferentFolders() {
        val track1 = track(1, path = "/music/track1.mp3", dateAdded = 100, version = 100)
        val track2 = track(2, path = "/music2/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders.keys).contains("music2")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
        assertThat(index.folders["music"]?.dateAdded).isEqualTo(100)
        assertThat(index.folders["music"]?.dateModified).isEqualTo(100)
        assertThat(index.folders["music2"]?.childTracks).containsExactly(track2)
        assertThat(index.folders["music2"]?.dateAdded).isEqualTo(0)
        assertThat(index.folders["music2"]?.dateModified).isEqualTo(0)
    }

    @Test
    fun indexing_folder_TracksInNestedFolders() {
        val track1 = track(1, path = "/music/track1.mp3")
        val track2 = track(2, path = "/music/album/track2.mp3", dateAdded = 100, version = 100)
        val track3 = track(3, path = "/music/album/track3.mp3")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders.keys).contains("music/album")
        assertThat(index.folders["music"]?.childFolders).containsExactly("music/album")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
        assertThat(index.folders["music"]?.dateAdded).isEqualTo(100)
        assertThat(index.folders["music"]?.dateModified).isEqualTo(100)
        assertThat(index.folders["music/album"]?.childTracks).containsExactly(track2, track3)
    }

    @Test
    fun indexing_folder_TracksInRootFolder() {
        val track1 = track(1, path = "/track1.mp3", dateAdded = 100, version = 100)
        val track2 = track(2, path = "/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).containsExactlyInAnyOrder("")
        assertThat(index.folders[""]?.childTracks).containsExactly(track1, track2)
        assertThat(index.folders[""]?.dateAdded).isEqualTo(100)
        assertThat(index.folders[""]?.dateModified).isEqualTo(100)
    }

    @Test
    fun indexing_folder_ChildTracksRecursive() {
        val track611 = track(611, path = "/6/1/1.mp3")
        val track621 = track(621, path = "/6/2/1.mp3")
        val track622 = track(622, path = "/6/2/2.mp3")
        val track631 = track(631, path = "/6/3/1.mp3")
        val track632 = track(632, path = "/6/3/2.mp3")
        val track633 = track(633, path = "/6/3/3.mp3")
        val track11 = track(11, path = "/1/1.mp3")
        val track1 = track(4, path = "/1.mp3")
        val index =
            listOf(track611, track621, track622, track631, track632, track633, track11, track1)
                .libraryIndex()
        assertThat(index.folders["6"]?.childTracksRecursive(index.folders))
            .containsExactly(track611, track621, track622, track631, track632, track633)
        assertThat(index.folders["6"]?.childTracksCountRecursive).isEqualTo(6)
        assertThat(index.folders["1"]?.childTracksRecursive(index.folders)).containsExactly(track11)
        assertThat(index.folders["1"]?.childTracksCountRecursive).isEqualTo(1)
        assertThat(index.folders[""]?.childTracksRecursive(index.folders))
            .containsExactly(
                track1,
                track11,
                track611,
                track621,
                track622,
                track631,
                track632,
                track633,
            )
        assertThat(index.folders[""]?.childTracksCountRecursive).isEqualTo(8)
    }

    fun track(
        id: Long,
        path: String = InvalidTrack.path,
        fileName: String = InvalidTrack.fileName,
        dateAdded: Long = InvalidTrack.dateAdded,
        version: Long = InvalidTrack.version,
        title: String? = InvalidTrack.title,
        artists: List<String> = InvalidTrack.artists,
        album: String? = InvalidTrack.album,
        albumArtist: String? = InvalidTrack.albumArtist,
        genres: List<String> = InvalidTrack.genres,
        year: Int? = InvalidTrack.year,
        trackNumber: Int? = InvalidTrack.trackNumber,
        discNumber: Int? = InvalidTrack.discNumber,
        duration: Duration = InvalidTrack.duration,
        size: Long = InvalidTrack.size,
        format: String = InvalidTrack.format,
        sampleRate: Int = InvalidTrack.sampleRate,
        bitRate: Long = InvalidTrack.bitRate,
        bitDepth: Int = InvalidTrack.bitDepth,
        hasArtwork: Boolean = InvalidTrack.hasArtwork,
        vibrantColor: Color? = InvalidTrack.vibrantColor,
        mutedColor: Color? = InvalidTrack.mutedColor,
        unsyncedLyrics: String? = InvalidTrack.unsyncedLyrics,
        comment: String? = InvalidTrack.comment,
        artworkHash: Long? = InvalidTrack.artworkHash,
    ): Track {
        return Track(
            id = id,
            path = path,
            fileName = fileName,
            version = version,
            title = title,
            artists = artists,
            album = album,
            albumArtist = albumArtist,
            albumArtists = albumArtist?.let { listOf(it) } ?: emptyList(),
            genres = genres,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            duration = duration,
            size = size,
            format = format,
            sampleRate = sampleRate,
            bitRate = bitRate,
            bitDepth = bitDepth,
            hasArtwork = hasArtwork,
            artworkType = ArtworkType.NONE,
            artworkSourcePath = null,
            vibrantColor = vibrantColor,
            mutedColor = mutedColor,
            dateAdded = dateAdded,
            unsyncedLyrics = unsyncedLyrics,
            comment = comment,
            artworkHash = artworkHash,
        )
    }

    fun List<Track>.libraryIndex(): LibraryIndex {
        return LibraryIndex(
            UnfilteredTrackIndex(null, associateBy { it.id }),
            collator,
            emptyList(),
            emptyList(),
        )
    }
}

package com.google.android.exoplayer2.icydemo

import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.icy.IcyHttpDataSourceFactory
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import okhttp3.OkHttpClient

/**
 * Test application, doesn't necessarily show the best way to do things.
 */
class MainActivity : AppCompatActivity() {
    private var exoPlayer: SimpleExoPlayer? = null
    private val exoPlayerEventListener = ExoPlayerEventListener()
    private lateinit var userAgent: String
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stream.setText(DEFAULT_STREAM)

        userAgent = Util.getUserAgent(applicationContext, applicationContext.getString(R.string.app_name))

        play_pause.setOnClickListener {
            if (isPlaying) {
                stop()
                play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_play_arrow_black_24dp, null))
            } else {
                play()
                play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_stop_black_24dp, null))
            }
        }
    }

    private fun play() {
        GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayerFactory.newSimpleInstance(applicationContext,
                        DefaultRenderersFactory(applicationContext),
                        DefaultTrackSelector(),
                        DefaultLoadControl()
                )
                exoPlayer?.addListener(exoPlayerEventListener)
            }

            val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
            exoPlayer?.audioAttributes = audioAttributes

            // Custom HTTP data source factory which requests Icy metadata and parses it if
            // the stream server supports it
            val client = OkHttpClient.Builder().build()
            val icyHttpDataSourceFactory = IcyHttpDataSourceFactory.Builder(client)
                    .setUserAgent(userAgent)
                    .setIcyHeadersListener { icyHeaders ->
                        Log.d(TAG, "onIcyMetaData: icyHeaders=$icyHeaders")
                    }
                    .setIcyMetadataChangeListener { icyMetadata ->
                        Log.d(TAG, "onIcyMetaData: icyMetadata=$icyMetadata")
                    }
                    .build()

            // Produces DataSource instances through which media data is loaded
            val dataSourceFactory = DefaultDataSourceFactory(
                    applicationContext, null, icyHttpDataSourceFactory
            )
            // Produces Extractor instances for parsing the media data
            val extractorsFactory = DefaultExtractorsFactory()

            // The MediaSource represents the media to be played
            val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
                    .setExtractorsFactory(extractorsFactory)
                    .createMediaSource(Uri.parse(stream.text.toString()))

            // Prepares media to play (happens on background thread) and triggers
            // {@code onPlayerStateChanged} callback when the stream is ready to play
            exoPlayer?.prepare(mediaSource)
        })
    }

    private fun stop() {
        releaseResources(true)
        isPlaying = false
    }

    private fun releaseResources(releasePlayer: Boolean) {
        Log.d(TAG, "releaseResources: releasePlayer=$releasePlayer")

        // Stops and releases player (if requested and available).
        if (releasePlayer && exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer?.removeListener(exoPlayerEventListener)
            exoPlayer = null
        }
    }

    private inner class ExoPlayerEventListener : Player.EventListener {
        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.i(TAG, "onPlayerStateChanged: playWhenReady=$playWhenReady, playbackState=$playbackState")
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY ->
                    isPlaying = true
                Player.STATE_ENDED ->
                    stop()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            Log.e(TAG, "onPlayerStateChanged: error=$error")
        }

        override fun onPositionDiscontinuity(reason: Int) {
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        }

        override fun onSeekProcessed() {
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_STREAM = "http://ice1.somafm.com/indiepop-128-mp3"
    }
}

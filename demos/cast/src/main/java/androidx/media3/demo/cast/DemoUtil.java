/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.demo.cast;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility methods and constants for the Cast demo application. */
/* package */ final class DemoUtil {

  public static final String MIME_TYPE_DASH = MimeTypes.APPLICATION_MPD;
  public static final String MIME_TYPE_HLS = MimeTypes.APPLICATION_M3U8;
  public static final String MIME_TYPE_SS = MimeTypes.APPLICATION_SS;
  public static final String MIME_TYPE_VIDEO_MP4 = MimeTypes.VIDEO_MP4;

  /** The list of samples available in the cast demo app. */
  public static final List<MediaItem> SAMPLES;

  static {
    ArrayList<MediaItem> samples = new ArrayList<>();
    // HLS streams.
    samples.add(
        new MediaItem.Builder()
            .setUri(
                "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("HLS (adaptive): Apple 4x3 basic stream (TS/h264/aac)")
                    .build())
            .setMimeType(MIME_TYPE_HLS)
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri(
                "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("HLS (adaptive): Apple 16x9 basic stream (TS/h264/aac)")
                    .build())
            .setMimeType(MIME_TYPE_HLS)
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/hls/DesigningForGoogleCast.m3u8")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("HLS (1280x720): Designing For Google Cast (TS/h264/aac)")
                    .build())
            .setMimeType(MIME_TYPE_HLS)
            .build());
    // DASH streams
    samples.add(
        new MediaItem.Builder()
            .setUri("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("DASH (adaptive): Tears of steal (HD, MP4, H264/aac)")
                    .build())
            .setMimeType(MIME_TYPE_DASH)
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears_uhd.mpd")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("DASH (3840x1714): Tears of steal (MP4, H264/aac)")
                    .build())
            .setMimeType(MIME_TYPE_DASH)
            .build());
    // Progressive video streams
    samples.add(
        new MediaItem.Builder()
            .setUri("https://html5demos.com/assets/dizzy.mp4")
            .setMediaMetadata(
                new MediaMetadata.Builder().setTitle("MP4 (480x360): Dizzy (H264/aac)").build())
            .setMimeType(MIME_TYPE_VIDEO_MP4)
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri(
                "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv")
            .setMediaMetadata(
                new MediaMetadata.Builder().setTitle("MKV (1280x720): Screens (h264/aac)").build())
            .setMimeType(MIME_TYPE_VIDEO_MP4)
            .build());
    // Progressive audio streams with artwork
    samples.add(
        new MediaItem.Builder()
            .setUri("https://storage.googleapis.com/automotive-media/Keys_To_The_Kingdom.mp3")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle("MP3: Keys To The Kingdom (44100/stereo/320kb/s)")
                    .setArtist("The 126ers")
                    .setAlbumTitle("Youtube Audio Library Rock 2")
                    .setGenre("Rock")
                    .setTrackNumber(1)
                    .setTotalTrackCount(4)
                    .setArtworkUri(
                        Uri.parse(
                            "https://storage.googleapis.com/automotive-media/album_art_3.jpg"))
                    .build())
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .build());
    // DRM content.
    samples.add(
        new MediaItem.Builder()
            .setUri(Uri.parse("https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"))
            .setMediaMetadata(
                new MediaMetadata.Builder().setTitle("Widevine DASH cenc: Tears").build())
            .setMimeType(MIME_TYPE_DASH)
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri("https://proxy.uat.widevine.com/proxy?provider=widevine_test")
                    .build())
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri("https://storage.googleapis.com/wvmedia/cbc1/h264/tears/tears_aes_cbc1.mpd")
            .setMediaMetadata(
                new MediaMetadata.Builder().setTitle("Widevine DASH cbc1: Tears").build())
            .setMimeType(MIME_TYPE_DASH)
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri("https://proxy.uat.widevine.com/proxy?provider=widevine_test")
                    .build())
            .build());
    samples.add(
        new MediaItem.Builder()
            .setUri("https://storage.googleapis.com/wvmedia/cbcs/h264/tears/tears_aes_cbcs.mpd")
            .setMediaMetadata(
                new MediaMetadata.Builder().setTitle("Widevine DASH cbcs: Tears").build())
            .setMimeType(MIME_TYPE_DASH)
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri("https://proxy.uat.widevine.com/proxy?provider=widevine_test")
                    .build())
            .build());

    SAMPLES = Collections.unmodifiableList(samples);
  }

  private DemoUtil() {}
}

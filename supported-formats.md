---
title: Supported formats
---

When defining the formats that ExoPlayer supports, it's important to note that
"media formats" are defined at multiple levels. From the lowest level to the
highest, these are:

* The format of the individual media samples (e.g., a frame of video or a frame
  of audio). These are *sample formats*. Note that a typical video file will
  contain media in at least two sample formats; one for video (e.g., H.264) and
  one for audio (e.g., AAC).
* The format of the container that houses the media samples and associated
  metadata. These are *container formats*. A media file has a single container
  format (e.g., MP4), which is commonly indicated by the file extension. Note
  that for some audio only formats (e.g., MP3), the sample and container formats
  may be the same.
* Adaptive streaming technologies such as DASH, SmoothStreaming and HLS. These
  are not media formats as such, however it's still necessary to define what
  level of support that ExoPlayer provides.

The following sections define ExoPlayer's support at each level, from highest to
lowest. Support for standalone subtitle formats is also described at the bottom
of this page.

## Adaptive streaming ##

### DASH ###

{% include_relative supported-formats-dash.md %}

### SmoothStreaming ###

{% include_relative supported-formats-smoothstreaming.md %}

### HLS ###

{% include_relative supported-formats-hls.md %}

## Progressive container formats ##

{% include_relative supported-formats-progressive.md %}

## Sample formats ##

By default ExoPlayer uses Android's platform decoders. Hence the supported
sample formats depend on the underlying platform rather than on ExoPlayer.
Sample formats supported by Android devices are documented
[here](https://developer.android.com/guide/appendix/media-formats.html#core).
Note that individual devices may support additional formats beyond those listed.

In addition to Android's platform decoders, ExoPlayer can also make use of
software decoder extensions. These must be manually built and included in
projects that wish to make use of them. We currently provide software decoder
extensions for
[VP9]({{ site.release_v2 }}/extensions/vp9),
[Flac]({{ site.release_v2 }}/extensions/flac),
[Opus]({{ site.release_v2 }}/extensions/opus) and
[FFmpeg]({{ site.release_v2 }}/extensions/ffmpeg).

### FFmpeg extension ###

The [FFmpeg extension][] supports decoding a variety of different audio sample
formats. You can choose which decoders to include by passing command line
arguments to FFmpeg's `configure` script:

| Sample format  | Argument(s) to `configure` |
|---------------:|----------------------------|
| Vorbis         | --enable-decoder=vorbis |
| Opus           | --enable-decoder=opus |
| FLAC           | --enable-decoder=flac |
| ALAC           | --enable-decoder=alac |
| PCM μ-law      | --enable-decoder=pcm_mulaw |
| PCM A-law      | --enable-decoder=pcm_alaw |
| MP1, MP2, MP3  | --enable-decoder=mp3 |
| AMR-NB         | --enable-decoder=amrnb |
| AMR-WB         | --enable-decoder=amrwb |
| AAC            | --enable-decoder=aac |
| AC-3           | --enable-decoder=ac3 |
| E-AC-3         | --enable-decoder=eac3 |
| DTS, DTS-HD    | --enable-decoder=dca |
| TrueHD         | --enable-decoder=mlp --enable-decoder=truehd |

See the extension's
[README.md]({{ site.release_v2 }}/extensions/ffmpeg/README.md)
for an example command line to `configure`.

[FFmpeg extension]: {{ site.release_v2 }}/extensions/ffmpeg

## Standalone subtitle formats ##

ExoPlayer supports standalone subtitle files in a variety of formats. Subtitle
files can be side-loaded as described on the [Media source page][].

| Container format      | Supported    | Mime type |
|-----------------------|:------------:|:----------|
| WebVTT                | YES          | `MimeTypes.TEXT_VTT` |
| TTML                  | YES          | `MimeTypes.APPLICATION_TTML` |
| SMPTE-TT              | YES          | `MimeTypes.APPLICATION_TTML` |
| SubRip                | YES          | `MimeTypes.APPLICATION_SUBRIP` |
| SubStationAlpha (SSA) | YES          | `MimeTypes.TEXT_SSA` |
| ASS                   | YES          | `MimeTypes.TEXT_SSA` |

[Media source page]: {{ site.baseurl }}/media-sources.html#side-loading-a-subtitle-file

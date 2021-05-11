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
  level of support ExoPlayer provides.

The following sections define ExoPlayer's support at each level, from highest to
lowest. The last two sections describe support for standalone subtitle formats
and HDR video playback.

## Adaptive streaming ##

### DASH ###

{% include_relative _page_fragments/supported-formats-dash.md %}

### SmoothStreaming ###

{% include_relative _page_fragments/supported-formats-smoothstreaming.md %}

### HLS ###

{% include_relative _page_fragments/supported-formats-hls.md %}

## Progressive container formats ##

{% include_relative _page_fragments/supported-formats-progressive.md %}

## RTSP ##

{% include_relative _page_fragments/supported-formats-rtsp.md %}

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
[AV1]({{ site.release_v2 }}/extensions/av1),
[VP9]({{ site.release_v2 }}/extensions/vp9),
[FLAC]({{ site.release_v2 }}/extensions/flac),
[Opus]({{ site.release_v2 }}/extensions/opus) and
[FFmpeg]({{ site.release_v2 }}/extensions/ffmpeg).

### FFmpeg extension ###

The [FFmpeg extension]({{ site.release_v2 }}/extensions/ffmpeg) supports
decoding a variety of different audio sample formats. You can choose which
decoders to include when building the extension, as documented in the
extension's [README.md]({{ site.release_v2 }}/extensions/ffmpeg/README.md). The
following table provides a mapping from audio sample format to the corresponding
FFmpeg decoder name.

| Sample format  | Decoder name(s) |
|---------------:|----------------------------|
| Vorbis         | vorbis |
| Opus           | opus |
| FLAC           | flac |
| ALAC           | alac |
| PCM μ-law      | pcm_mulaw |
| PCM A-law      | pcm_alaw |
| MP1, MP2, MP3  | mp3 |
| AMR-NB         | amrnb |
| AMR-WB         | amrwb |
| AAC            | aac |
| AC-3           | ac3 |
| E-AC-3         | eac3 |
| DTS, DTS-HD    | dca |
| TrueHD         | mlp truehd |

## Standalone subtitle formats ##

ExoPlayer supports standalone subtitle files in a variety of formats. Subtitle
files can be side-loaded as described on the [Media source page][].

| Container format      | Supported        | MIME type |
|---------------------------|:------------:|:----------|
| WebVTT                    | YES          | MimeTypes.TEXT_VTT |
| TTML / SMPTE-TT           | YES          | MimeTypes.APPLICATION_TTML |
| SubRip                    | YES          | MimeTypes.APPLICATION_SUBRIP |
| SubStationAlpha (SSA/ASS) | YES          | MimeTypes.TEXT_SSA |

[Media source page]: {{ site.baseurl }}/media-sources.html#side-loading-a-subtitle-file

## HDR video playback ##

ExoPlayer handles extracting high dynamic range (HDR) video in various
containers, including Dolby Vision in MP4 and HDR10+ in Matroska/WebM. Decoding
and displaying HDR content depends on support from the Android platform and
device. See
[HDR Video Playback](https://source.android.com/devices/tech/display/hdr.html)
to learn about checking for HDR decoding/display capabilities and limitations of
HDR support across Android versions.

When playing an HDR stream that requires support for a particular codec profile,
ExoPlayer's default `MediaCodec` selector will pick a decoder that supports that
profile (if available), even if another decoder for the same MIME type that
doesn't support that profile appears higher up the codec list. This can result
in selecting a software decoder in cases where the stream exceeds the
capabilities of a hardware decoder for the same MIME type.

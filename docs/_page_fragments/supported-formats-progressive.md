Streams in the following container formats can be played directly by ExoPlayer.
The contained audio and video sample formats must also be supported (see the
[sample formats](supported-formats.html#sample-formats) section for details).

| Container format | Supported    | Comments             |
|------------------|:------------:|:---------------------|
| MP4 | YES ||
| M4A | YES ||
| FMP4 | YES ||
| WebM| YES ||
| Matroska| YES ||
| MP3 | YES | Some streams only seekable using constant bitrate seeking** |
| Ogg | YES | Containing Vorbis, Opus and FLAC |
| WAV | YES ||
| MPEG-TS | YES ||
| MPEG-PS | YES ||
| FLV | YES | Not seekable* |
| ADTS (AAC) | YES | Only seekable using constant bitrate seeking** |
| FLAC | YES | Using the [FLAC extension][] or the FLAC extractor in the [core library][]*** |
| AMR | YES | Only seekable using constant bitrate seeking** |
| JPEG motion photo | YES | Only the MP4 content is extracted |

\* Seeking is unsupported because the container does not provide metadata (e.g.,
a sample index) to allow a media player to perform a seek in an efficient way.
If seeking is required, we suggest using a more appropriate container format.

\*\* These extractors have `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flags for
enabling approximate seeking using a constant bitrate assumption. This
functionality is not enabled by default. The simplest way to enable this
functionality for all extractors that support it is to use
`DefaultExtractorsFactory.setConstantBitrateSeekingEnabled`, as described
[here](customization.html#enabling-constant-bitrate-seeking).

\*\*\* The [FLAC extension][] extractor outputs raw audio, which can be handled
by the framework on all API levels. The [core library][] FLAC extractor outputs
FLAC audio frames and so relies on having a FLAC decoder (e.g., a `MediaCodec`
decoder that handles FLAC (required from API level 27), or the
[FFmpeg extension][] with FLAC enabled). The `DefaultExtractorsFactory` uses the
extension extractor if the application was built with the [FLAC extension][].
Otherwise, it uses the [core library][] extractor.

[FLAC extension]: {{ site.release_v2 }}/extensions/flac
[core library]: {{ site.release_v2 }}/library/core
[FFmpeg extension]: {{ site.release_v2 }}/extensions/ffmpeg

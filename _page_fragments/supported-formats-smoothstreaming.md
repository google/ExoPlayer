ExoPlayer supports SmoothStreaming with the FMP4 container format. Media streams
must be demuxed, meaning that video, audio and text must be defined in distinct
StreamIndex elements in the SmoothStreaming manifest. The contained audio and
video sample formats must also be supported (see the
[sample formats](supported-formats.html#sample-formats) section for details).

| Feature | Supported    | Comments             |
|---------|:------------:|:---------------------|
| **Containers** |||
| FMP4 | YES | Demuxed streams only |
| **Closed&nbsp;captions/subtitles** |||
| TTML | YES | Embedded in FMP4 |
| **Content protection** |||
| PlayReady SL2000 | YES | Android TV only |
| **Live playback** |||
| Regular live playback | YES ||

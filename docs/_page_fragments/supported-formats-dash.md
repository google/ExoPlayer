ExoPlayer supports DASH with multiple container formats. Media streams must be
demuxed, meaning that video, audio and text must be defined in distinct
AdaptationSet elements in the DASH manifest (CEA-608 is an exception as
described in the table below). The contained audio and video sample formats must
also be supported (see the
[sample formats](supported-formats.html#sample-formats) section for details).

| Feature | Supported    | Comments             |
|---------|:------------:|:---------------------|
| **Containers** |||
| FMP4 | YES| Demuxed streams only |
| WebM | YES | Demuxed streams only |
| Matroska | YES | Demuxed streams only |
| MPEG-TS | NO | No support planned |
| **Closed&nbsp;captions/subtitles** |||
| TTML | YES | Raw, or embedded in FMP4 according to ISO/IEC 14496-30 |
| WebVTT | YES | Raw, or embedded in FMP4 according to ISO/IEC 14496-30 |
| CEA-608 | YES | Carried in SEI messages embedded in FMP4 video streams |
| **Metadata** |||
| EMSG metadata | YES | Embedded in FMP4 |
| **Content protection** |||
| Widevine | YES | "cenc" scheme: API 19+; "cbcs" scheme: API 25+ |
| PlayReady SL2000 | YES | Android TV, "cenc" scheme only |
| ClearKey | YES | API 21+, "cenc" scheme only |
| **Live playback** |||
| Regular live playback | YES ||
| Ultra low-latency CMAF live playback | YES ||

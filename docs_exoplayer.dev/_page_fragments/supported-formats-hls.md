ExoPlayer supports HLS with multiple container formats. The contained audio and
video sample formats must also be supported (see the
[sample formats](supported-formats.html#sample-formats) section for details). We
strongly encourage HLS content producers to generate high quality HLS streams,
as described
[here](https://medium.com/google-exoplayer/hls-playback-in-exoplayer-a33959a47be7).

| Feature | Supported    | Comments             |
|---------|:------------:|:---------------------|
| **Containers** |||
| MPEG-TS | YES ||
| FMP4/CMAF | YES ||
| ADTS (AAC) | YES ||
| MP3 | YES ||
| **Closed&nbsp;captions/subtitles** |||
| CEA-608 | YES ||
| WebVTT | YES ||
| **Metadata** |||
| ID3 metadata | YES ||
| **Content protection** |||
| AES-128 | YES ||
| Sample AES-128 | NO ||
| Widevine | YES | API 19+ ("cenc" scheme) and 25+ ("cbcs" scheme) |
| PlayReady SL2000 | YES | Android TV only |
| **Server control** |||
| Delta updates | YES ||
| Blocking playlist reload | YES ||
| Blocking load of preload hints | YES | Except for byteranges with undefined lengths |
| **Live playback** |||
| Regular live playback | YES ||
| Low-latency HLS (Apple) | YES ||
| Low-latency HLS (Community) | NO ||

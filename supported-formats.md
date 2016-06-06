---
layout: default
title: Supported formats
weight: 2
---

## DASH ##

| Feature | Supported    | Comment              |
|---------|:------------:|:---------------------|
| **Containers** |||
| FMP4                          | YES          ||
| WebM | YES ||
| **Closed&nbsp;captions/subtitles** |||
| TTML | YES | Raw or embedded in FMP4 according to ISO/IEC 14496-30 |
| WebVTT | YES | Raw or embedded in FMP4 according to ISO/IEC 14496-30 |
| Tx3g | YES | Embedded in FMP4 |
| SubRip | YES | Embedded in WebM |
| **Content protection**                  |||
| Widevine | YES | API 18 and higher |
| PlayReady SL2000                        | YES          | Android TV only      |

## SmoothStreaming ##

| Feature | Supported    | Comment              |
|---------|:------------:|:---------------------|
| **Containers**                          |||
| FMP4                          | YES          ||
| **Closed&nbsp;captions/subtitles**           |||
| TTML | YES | Embedded in FMP4 |
| **Content protection**                    |||
| Widevine | YES | API 18 and higher |
| PlayReady SL2000                        | YES          | Android TV only      |

## HLS ##

| Feature | Supported    | Comment              |
|---------|:------------:|:---------------------|
| **Containers**                          |||
| MPEG-TS                                 | YES          ||
| ADTS (AAC) | YES ||
| MP3 | YES ||
| **Closed&nbsp;captions/subtitles**           |||
| EIA-608 | YES ||
| WebVTT                                  | YES          ||
| **Metdata** |||
| ID3 metadata                            | YES          ||
| **Content protection**                  |||
| AES-128                                 | YES          ||
| Sample AES-128 | NO ||

## Standalone container formats ##

| Container format | Supported    | Comment              |
|------------------|:------------:|:---------------------|
| MP4 | YES ||
| FMP4 | YES ||
| WebM| YES ||
| Matroska| YES ||
| MP3 | YES ||
| Ogg | YES | Containing Vorbis/Opus |
| WAV | YES ||
| MPEG-TS | YES | Not seekable |
| MPEG-PS | YES | Not seekable |
| FLV | YES | Not seekable |
| ADTS (AAC) | YES | Not seekable |
| Flac | YES | Supported by extension only |

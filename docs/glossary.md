---
title: Glossary
---

## General - Media ##

###### ABR

Adaptive Bitrate. An ABR algorithm is an algorithm that selects between a number
of [tracks](#track) during playback, where each track presents the same media
but at different bitrates.

###### Adaptive streaming

In adaptive streaming, multiple [tracks](#track) are available that present the
same media at different bitrates. The selected track is chosen dynamically
during playback using an [ABR](#abr) algorithm.

###### Access unit

A data item within a media [container](#container). Generally refers to a small
piece of the compressed media bitstream that can be decoded and presented to the
user (a video picture or fragment of playable audio).

###### AV1

AOMedia Video 1 [codec](#codec).

For more information, see the
[Wikipedia page](https://en.wikipedia.org/wiki/AV1).

###### AVC

Advanced Video Coding, also known as the H.264 video [codec](#codec).

For more information, see the
[Wikipedia page](https://en.wikipedia.org/wiki/Advanced_Video_Coding).

###### Codec

This term is overloaded and has multiple meanings depending on the context. The
two following definitions are the most commonly used:

* Hardware or software component for encoding or decoding
  [access units](#access-unit).
* Audio or video sample format specification.

###### Container

A media container format such as MP4 and Matroska. Such formats are called
container formats because they contain one or more [tracks](#track) of media,
where each track uses a particular [codec](#codec) (e.g. AAC audio and H.264
video in an MP4 file). Note that some media formats are both a container format
and a codec (e.g. MP3).

###### DASH

Dynamic [Adaptive Streaming](#adaptive-streaming) over HTTP. An industry driven
adaptive streaming protocol. It is defined by ISO/IEC 23009, which can be found
on the
[ISO Publicly Available Standards page](https://standards.iso.org/ittf/PubliclyAvailableStandards/).

###### DRM

Digital Rights Management.

For more information, see the
[Wikipedia page](https://en.wikipedia.org/wiki/Digital_rights_management).

###### Gapless playback

Process by which the end of a [track](#track) and/or the beginning of the next
track are skipped to avoid a silent gap between tracks.

For more information, see the
[Wikipedia page](https://en.wikipedia.org/wiki/Gapless_playback).

###### HEVC

High Efficiency Video Coding, also known as the H.265 video [codec](#codec).

###### HLS

HTTP Live Streaming. Apple’s [adaptive streaming](#adaptive-streaming) protocol.

For more information, see the
[Apple documentation](https://developer.apple.com/streaming/).

###### Manifest

A file that defines the structure and location of media in
[adaptive streaming](#adaptive-streaming) protocols. Examples include
[DASH](#dash) [MPD](#mpd) files, [HLS](#hls) master playlist files and
[Smooth Streaming](#smooth-streaming) manifest files. Not to be confused with an
AndroidManifest XML file.

###### MPD

Media Presentation Description. The [manifest](#manifest) file format used in
the [DASH](#dash) [adaptive streaming](#adaptive-streaming) protocol.

###### PCM

Pulse-Code Modulation.

For more information, see the
[Wikipedia page](https://en.wikipedia.org/wiki/Pulse-code_modulation).

###### Smooth Streaming

Microsoft’s [adaptive streaming](#adaptive-streaming) protocol.

For more information, see the
[Microsoft documentation](https://www.iis.net/downloads/microsoft/smooth-streaming).

###### Track

A single audio, video, text or metadata stream within a piece of media. A media
file will often contain multiple tracks. For example a video track and an audio
track in a video file, or multiple audio tracks in different languages. In
[adaptive streaming](#adaptive-streaming) there are also multiple tracks
containing the same content at different bitrates.

## General - Android ##

###### AudioTrack

An Android API for playing audio.

For more information, see the
[Javadoc](https://developer.android.com/reference/android/media/AudioTrack).

###### CDM

Content Decryption Module. A component in the Android platform responsible for
decrypting [DRM](#drm) protected content. CDMs are accessed via Android’s
[`MediaDrm`](#mediadrm) API.

For more information, see the
[Javadoc](https://developer.android.com/reference/android/media/MediaDrm).

###### IMA

Interactive Media Ads. IMA is an SDK that makes it easy to integrate multimedia
ads into an app.

For more information, see the
[IMA documentation](https://developers.google.com/interactive-media-ads).

###### MediaCodec

An Android API for accessing media [codecs](#codec) (i.e. encoder and decoder
components) in the platform.

For more information, see the
[Javadoc](https://developer.android.com/reference/android/media/MediaCodec).

###### MediaDrm

An Android API for accessing [CDMs](#cdm) in the platform.

For more information, see the
[Javadoc](https://developer.android.com/reference/android/media/MediaDrm).

###### Audio offload

The ability to send compressed audio directly to a digital signal processor
(DSP) provided by the device. Audio offload functionality is useful for low
power audio playback.

For more information, see the
[Android interaction documentation](https://source.android.com/devices/tv/multimedia-tunneling).

###### Passthrough

The ability to send compressed audio directly over HDMI, without decoding it
first. This is for example used to play 5.1 surround sound on an Android TV.

For more information, see the
[Android interaction documentation](https://source.android.com/devices/tv/multimedia-tunneling).

###### Surface

See the [Javadoc](https://developer.android.com/reference/android/view/Surface)
and the
[Android graphics documentation](https://source.android.com/devices/graphics/arch-sh).

###### Tunneling

Process by which the Android framework receives compressed video and either
compressed or [PCM](#pcm) audio data and assumes the responsibility for
decoding, synchronizing and rendering it, taking over some tasks usually handled
by the application. Tunneling may improve audio-to-video (AV) synchronization,
may smooth video playback and can reduce the load on the application processor.
It is mostly used on Android TVs.

For more information, see the
[Android interaction documentation](https://source.android.com/devices/tv/multimedia-tunneling)
and the
[ExoPlayer article](https://medium.com/google-exoplayer/tunneled-video-playback-in-exoplayer-84f084a8094d).

## ExoPlayer ##

{% include figure.html url="/images/glossary-exoplayer-architecture.png" index="1" caption="ExoPlayer architecture overview" %}

{% include figure.html url="/images/glossary-renderering-architecture.png" index="1" caption="ExoPlayer rendering overview" %}

###### BandwidthMeter

Component that estimates the network bandwidth, for example by listening to data
transfers. In [adaptive streaming](#adaptive-streaming), bandwidth estimates can
be used to select between different bitrate [tracks](#track) during playback.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/BandwidthMeter.html).

###### DataSource

Component for requesting data (e.g. over HTTP, from a local file, etc).

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/DataSource.html).

###### Extractor

Component that parses a media [container](#container) format, outputting
[track](#track) information and individual [access units](#access-unit)
belonging to each track suitable for consumption by a decoder.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/extractor/Extractor.html).

###### LoadControl

Component that decides when to start and stop loading, and when to start
playback.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/LoadControl.html).

###### MediaSource

Provides high-level information about the structure of media (as a
[`Timeline`](#timeline)) and creates [`MediaPeriod`](#mediaperiod) instances
(corresponding to periods of the `Timeline`) for playback.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/source/MediaSource.html).

###### MediaPeriod

Loads a single piece of media (e.g. audio file, ad, content interleaved between
two ads, etc.), and allows the loaded media to be read (typically by
[`Renderers`](#renderer)). The decisions about which [tracks](#track) within the
media are loaded and when loading starts and stops are made by the
[`TrackSelector`](#trackselector) and the [`LoadControl`](#loadcontrol)
respectively.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/source/MediaPeriod.html).

###### Renderer

Component that reads, decodes and renders media samples. [`Surface`](#surface)
and [`AudioTrack`](#audiotrack) are the standard Android platform components to
which video and audio data are rendered.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Renderer.html).

###### Timeline

Represents the structure of media, from simple cases like a single media file
through to complex compositions of media such as playlists and streams with
inserted ads.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Timeline.html).

###### TrackGroup

Group containing one or more representations of the same video, audio or text
content, normally at different bitrates for
[adaptive streaming](#adaptive-streaming).

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/source/TrackGroup.html).

###### TrackSelection

A selection consisting of a static subset of [tracks](#track) from a
[`TrackGroup`](#trackgroup), and a possibly varying selected track from the
subset. For [adaptive streaming](#adaptive-streaming), the `TrackSelection` is
responsible for selecting the appropriate track whenever a new media chunk
starts being loaded.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/TrackSelection.html).

###### TrackSelector

Selects [tracks](#track) for playback. Given track information for the
[`MediaPeriod`](#mediaperiod) to be played, along with the capabilities of the
player’s [`Renderers`](#renderer), a `TrackSelector` will generate a
[`TrackSelection`](#trackselection) for each `Renderer`.

For more information, see the component
[Javadoc](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/TrackSelector.html).

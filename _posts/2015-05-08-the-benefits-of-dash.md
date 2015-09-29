---
layout: default
title: The benefits of DASH
author: Oliver Woodman
disqus: true
---

There exist multiple standards for adaptive streaming over HTTP, the most popular of which are
HTTP Live Streaming (HLS), SmoothStreaming and Dynamic Adaptive Streaming over HTTP (DASH). Whilst
HLS is the most widely used of these three standards today, it's important to note that it has major
disadvantages compared to both DASH and SmoothStreaming. Here we touch on just two of them.

<!--more-->

* HLS requires that video is packaged in the M2TS transport stream; a format that was designed
  for broadcast TV rather than for streaming content over the internet. Conversely, both DASH and
  SmoothStreaming use the fragmented MP4 (fMP4) container format, which was designed specifically
  with streaming content over the internet in mind. M2TS has many inherent disadvantages compared to
  fMP4, both for servers and clients, some of which are summarized by Timothy Siglin's excellent
  [white paper](http://184.168.176.117/reports-public/Adobe/20111116-fMP4-Adobe-Microsoft.pdf).
* All three standards divide media into small chunks, and allow clients to switch between different
  qualities (typically based on the available bandwidth) by having them stop downloading chunks of
  one quality and start downloading chunks of another. DASH and SmoothStreaming with fMP4 both
  require that chunk boundaries are aligned across the different qualities, *and* that each chunk
  starts with a keyframe. This allows seamless switching from one quality to another without ever
  having to download overlapping chunks in multiple qualities. Unfortunately, the same cannot be
  said for HLS. HLS does not require that chunks start with keyframes, and so to seamlessly switch
  from one quality to another requires (in the worst case) that the client download overlapping
  chunks in both the old and new quality, and then splice them together when a keyframe is found.
  This is inefficient and significantly increases the probability of re-buffers, particularly when
  the client is attempting to switch to a lower quality due to a decrease in the available
  bandwidth.

DASH is the only internationally standardized solution, and an increasing number of major streaming
services have either adopted it or are in the process of doing so. With this and the advantages
outlined above in mind, we've decided to prioritize DASH support in ExoPlayer. This means that
whilst we'll continue to support both SmoothStreaming and HLS, new features that cannot be easily
implemented across all three standards will be implemented first for DASH, and later (if at all) for
SmoothStreaming and HLS. Which is yet another reason to choose DASH!

### Further reading ###

* [Wowza &ndash; MPEG-DASH, A bold new world for streaming?](http://www.wowza.com/blog/mpeg-dash-a-bold-new-world-for-streaming)
* [Bitcodin &ndash; MPEG-DASH vs Apple HLS vs Microsoft SmoothStreaming vs Adobe HDS](http://www.bitcodin.com/blog/2015/03/mpeg-dash-vs-apple-hls-vs-microsoft-smooth-streaming-vs-adobe-hds/)

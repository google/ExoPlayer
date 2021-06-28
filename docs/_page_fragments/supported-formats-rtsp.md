ExoPlayer supports both live and on demand RTSP. Supported formats and network
types are listed below.

**Supported formats**
* H264. The SDP media description must include SPS/PPS data in the fmtp
  attribute for decoder initialization.
* AAC (with ADTS bitstream).
* AC3.

**Supported network types**
* RTP over UDP unicast (multicast is not supported).
* Interleaved RTSP, RTP over RTSP using TCP.

> Playback of RTP streams is not supported.

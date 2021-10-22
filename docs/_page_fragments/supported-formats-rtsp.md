ExoPlayer supports both live and on demand RTSP. Supported sample formats and
network types are listed below.

### Supported sample formats ###

* H264 (the SDP media description must include SPS/PPS data in the fmtp
  attribute for decoder initialization).
* AAC (with ADTS bitstream).
* AC3.

Please comment on [this issue](https://github.com/google/ExoPlayer/issues/9210)
to request support for additional sample formats.
{:.info}

### Supported network types ###

* RTP over UDP unicast (multicast is not supported).
* Interleaved RTSP, RTP over RTSP using TCP.

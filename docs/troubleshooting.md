---
title: Troubleshooting
redirect_from:
  - /faqs.html
  - /debugging-playback-issues.html
---

* [Fixing "Cleartext HTTP traffic not permitted" errors][]
* [Fixing "SSLHandshakeException" and "CertPathValidatorException" errors][]
* [Why are some media files not seekable?][]
* [Why is seeking inaccurate in some MP3 files?][]
* [Why do some MPEG-TS files fail to play?][]
* [Why do some MP4/FMP4 files play incorrectly?][]
* [Why do some streams fail with HTTP response code 301 or 302?][]
* [Why do some streams fail with UnrecognizedInputFormatException?][]
* [Why doesn't setPlaybackParameters work properly on some devices?][]
* [What do "Player is accessed on the wrong thread" errors mean?][]
* [How can I fix "Unexpected status line: ICY 200 OK"?][]
* [How can I query whether the stream being played is a live stream?][]
* [How do I keep audio playing when my app is backgrounded?][]
* [Why does ExoPlayer support my content but the Cast extension doesn't?][]
* [Why does content fail to play, but no error is surfaced?]
* [How can I get a decoding extension to load and be used for playback?][]
* [Can I play YouTube videos directly with ExoPlayer?][]

---

#### Fixing "Cleartext HTTP traffic not permitted" errors ####

This error will occur if your app requests cleartext HTTP traffic (i.e.,
`http://` rather than `https://`) when its Network Security Configuration does
not permit it. If your app targets Android 9 (API level 28) or later, cleartext
HTTP traffic is disabled by the default configuration.

If your app needs to work with cleartext HTTP traffic then you need to use a
Network Security Configuration that permits it. Please see Android's
[network security documentation](https://developer.android.com/training/articles/security-config.html)
for details. To enable all cleartext HTTP traffic, you can simply add
`android:usesCleartextTraffic="true"` to the `application` element of your app's
`AndroidManifest.xml`.

The ExoPlayer demo app uses the default Network Security Configuration, and so
does not allow cleartext HTTP traffic. You can enable it using the instructions
above.

#### Fixing "SSLHandshakeException" and "CertPathValidatorException" errors ####

`SSLHandshakeException` and `CertPathValidatorException` both indicate a problem
with the server's SSL certificate. These errors are not ExoPlayer specific.
Please see
[Android's SSL documentation](https://developer.android.com/training/articles/security-ssl#CommonProblems)
for more details.

#### Why are some media files not seekable? ####

By default ExoPlayer does not support seeking in media where the only method for
performing accurate seek operations is for the player to scan and index the
entire file. ExoPlayer considers such files as unseekable. Most modern media
container formats include metadata for seeking (e.g., a sample index), have a
well defined seek algorithm (e.g., interpolated bisection search for Ogg), or
indicate that their content is constant bitrate. Efficient seek operations are
possible and supported by ExoPlayer in these cases.

If you require seeking but have unseekable media, we suggest converting your
content to use a more appropriate container format. For MP3, ADTS and AMR files,
you can also enable seeking under the assumption that the files have a constant
bitrate, as described
[here](customization.html#enabling-constant-bitrate-seeking).

#### Why is seeking inaccurate in some MP3 files? ####

Variable bitrate (VBR) MP3 files are fundamentally unsuitable for use cases that
require exact seeking. There are two reasons for this:

1. For exact seeking, a container format will ideally provide a precise
   time-to-byte mapping in a header. This mapping allows a player to map a
   requested seek time to the corresponding byte offset, and start requesting,
   parsing and playing media from that offset. The headers available for
   specifying this mapping in MP3 (e.g., XING headers) are, unfortunately, often
   imprecise.
1. For container formats that don't provide a precise time-to-byte mapping (or
   any time-to-byte mapping at all), it's still possible to perform an exact
   seek if the container includes absolute sample timestamps in the stream. In
   this case a player can map the seek time to a best guess of the corresponding
   byte offset, start requesting media from that offset, parse the first
   absolute sample timestamp, and effectively perform a guided binary search
   into the media until it finds the right sample. Unfortunately MP3 does not
   include absolute sample timestamps in the stream, so this approach is not
   possible.

For these reasons, the only way to perform an exact seek into a VBR MP3 file is
to scan the entire file and manually build up a time-to-byte mapping in the
player. This strategy can be enabled by using [`FLAG_ENABLE_INDEX_SEEKING`][],
which can be [set on a `DefaultExtractorsFactory`][] using
[`setMp3ExtractorFlags`][]. Note that it doesn't scale well to large MP3 files,
particularly if the user tries to seek to near the end of the stream shortly
after starting playback, which requires the player to wait until it's downloaded
and indexed the entire stream before performing the seek. In ExoPlayer, we
decided to optimize for speed over accuracy in this case and
[`FLAG_ENABLE_INDEX_SEEKING`][] is therefore disabled by default.

If you control the media you're playing, we strongly advise that you use a more
appropriate container format, such as MP4. There are no use cases we're aware of
where MP3 is the best choice of media format.

#### Why do some MPEG-TS files fail to play? ####

Some MPEG-TS files do not contain access unit delimiters (AUDs). By default
ExoPlayer relies on AUDs to cheaply detect frame boundaries. Similarly, some
MPEG-TS files do not contain IDR keyframes. By default these are the only type
of keyframes considered by ExoPlayer.

ExoPlayer will appear to be stuck in the buffering state when asked to play an
MPEG-TS file that lacks AUDs or IDR keyframes. If you need to play such files,
you can do so using [`FLAG_DETECT_ACCESS_UNITS`][] and
[`FLAG_ALLOW_NON_IDR_KEYFRAMES`][] respectively. These flags can be [set on a
`DefaultExtractorsFactory`][] using [`setTsExtractorFlags`][] or on a
`DefaultHlsExtractorFactory` using the
[constructor]({{ site.exo_sdk }}/source/hls/DefaultHlsExtractorFactory.html#DefaultHlsExtractorFactory-int-boolean-).
Use of `FLAG_DETECT_ACCESS_UNITS` has no side effects other than being
computationally expensive relative to AUD based frame boundary detection. Use of
`FLAG_ALLOW_NON_IDR_KEYFRAMES` may result in temporary visual corruption at the
start of playback and immediately after seeks when playing some MPEG-TS files.

#### Why do some MP4/FMP4 files play incorrectly? ####

Some MP4/FMP4 files contain edit lists that rewrite the media timeline by
skipping, moving or repeating lists of samples. ExoPlayer has partial support
for applying edit lists. For example, it can delay or repeat groups of samples
starting on a synchronization sample, but it does not truncate audio samples or
preroll media for edits that don't start on a synchronization sample.

If you are seeing that part of the media is unexpectedly missing or repeated,
try setting [`Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS`][] or
[`FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS`][], which will cause
the extractor to ignore edit lists entirely. These can be [set on a
`DefaultExtractorsFactory`][] using [`setMp4ExtractorFlags`][] or
[`setFragmentedMp4ExtractorFlags`][].

#### Why do some streams fail with HTTP response code 301 or 302? ####

HTTP response codes 301 and 302 both indicate redirection. Brief descriptions
can be found on [Wikipedia][]. When ExoPlayer makes a request and receives a
response with status code 301 or 302, it will normally follow the redirect
and start playback as normal. The one case where this does not happen by default
is for cross-protocol redirects. A cross-protocol redirect is one that redirects
from HTTPS to HTTP or vice-versa (or less commonly, between another pair of
protocols). You can test whether a URL causes a cross-protocol redirect using
the [wget][] command line tool as follows:
```
wget "https://yourserver.com/test.mp3" 2>&1  | grep Location
```
The output should look something like this:
```
$ wget "https://yourserver.com/test.mp3" 2>&1  | grep Location
Location: https://second.com/test.mp3 [following]
Location: http://third.com/test.mp3 [following]
```
In this example there are two redirects. The first redirect is from
`https://yourserver.com/test.mp3` to `https://second.com/test.mp3`. Both are
HTTPS, and so this is not a cross-protocol redirect. The second redirect is from
`https://second.com/test.mp3` to `http://third.com/test.mp3`. This redirects
from HTTPS to HTTP and so is a cross-protocol redirect. ExoPlayer will not
follow this redirect in its default configuration, meaning playback will fail.

If you need to, you can configure ExoPlayer to follow cross-protocol redirects
when instantiating `DefaultHttpDataSourceFactory` instances used in your
application. Learn about selecting and configuring the network stack
[here]({{ site.base_url }}/customization.html#configuring-the-network-stack).

#### Why do some streams fail with UnrecognizedInputFormatException? ####

This question relates to playback failures of the form:
```
UnrecognizedInputFormatException: None of the available extractors
(MatroskaExtractor, FragmentedMp4Extractor, ...) could read the stream.
```
There are two possible causes of this failure. The most common cause is that
you're trying to play DASH (mpd), HLS (m3u8) or SmoothStreaming (ism, isml)
content, but the player tries to play it as a progressive stream. To play such
streams, you must depend on the respective [ExoPlayer module][]. In cases where
the stream URI doesn't end with the standard file extension, you can also pass
`MimeTypes.APPLICATION_MPD`, `MimeTypes.APPLICATION_M3U8` or
`MimeTypes.APPLICATION_SS` to `setMimeType` of `MediaItem.Builder` to explicitly
specify the type of stream.

The second, less common cause, is that ExoPlayer does not support the container
format of the media that you're trying to play. In this case the failure is
working as intended, however feel free to submit a feature request to our
[issue tracker][], including details of the container format and a test stream.
Please search for an existing feature request before submitting a new one.

#### Why doesn't setPlaybackParameters work properly on some devices? ####

When running a debug build of your app on Android M and earlier, you may
experience choppy performance, audible artifacts and high CPU utilization when
using the [`setPlaybackParameters`][] API. This is because an optimization
that's important to this API is disabled for debug builds running on these
versions of Android.

It's important to note that this issue affects debug builds only. It does *not*
affect release builds, for which the optimization is always enabled. Hence the
releases you provide to end users should not be affected by this issue.

#### What do "Player is accessed on the wrong thread" errors mean? ####

See [A note on threading][] on the getting started page.

#### How can I fix "Unexpected status line: ICY 200 OK"? ####

This problem can occur if the server response includes an ICY status line,
rather than one that's HTTP compliant. ICY status lines are deprecated and
should not be used, so if you control the server you should update it to provide
an HTTP compliant response. If you're unable to do this then using the
[OkHttp extension][] will resolve the problem, since it's able to handle ICY
status lines correctly.

#### How can I query whether the stream being played is a live stream? ####

You can query the player's [`isCurrentWindowLive`][] method. In addition, you
can check [`isCurrentWindowDynamic`][] to find out whether the window is dynamic
(i.e., still updating over time).

#### How do I keep audio playing when my app is backgrounded? ####

There are a few steps that you need to take to ensure continued playback of
audio when your app is in the background:

1. You need to have a running [foreground service][]. This prevents the system
   from killing your process to free up resources.
1. You need to hold a [`WifiLock`][] and a [`WakeLock`][]. These ensure that the
   system keeps the WiFi radio and CPU awake. This can be easily done if using
   [`SimpleExoPlayer`][] by calling [`setWakeMode`][], which will automatically
   acquire and release the required locks at the correct times.

It's important that you release the locks (if not using `setWakeMode`) and stop
the service as soon as audio is no longer being played.

#### Why does ExoPlayer support my content but the Cast extension doesn't? ####

It's possible that the content that you are trying to play is not
[CORS enabled][]. The [Cast framework][] requires content to be CORS enabled in
order to play it.

#### Why does content fail to play, but no error is surfaced? ####

It's possible that the device on which you are playing the content does not
support a specific media sample format. This can be easily confirmed by adding
an [`EventLogger`][] as a listener to your player, and looking for a line
similar to this one in Logcat:
```
[ ] Track:x, id=x, mimeType=mime/type, ... , supported=NO_UNSUPPORTED_TYPE
```
`NO_UNSUPPORTED_TYPE` means that the device is not able to decode the media
sample format specified by the `mimeType`. See the [Android media formats
documentation][] for information about supported sample formats. [How can I get
a decoding extension to load and be used for playback?] may also be useful.

#### How can I get a decoding extension to load and be used for playback? ####

* Most extensions have manual steps to check out and build the dependencies, so
  make sure you've followed the steps in the README for the relevant extension.
  For example, for the FFmpeg extension it's necessary to follow the
  instructions in [extensions/ffmpeg/README.md][], including passing
  configuration flags to [enable decoders][] for the format(s) you want to play.
* For extensions that have native code, make sure you're using the correct
  version of the Android NDK as specified in the README, and look out for any
  errors that appear during configuration and building. You should see `.so`
  files appear in the `libs` subdirectory of the extension's path for each
  supported architecture after following the steps in the README.
* To try out playback using the extension in the [demo application][], see
  [enabling extension decoders][]. See the README for the extension for
  instructions on using the extension from your own app.
* If you're using [`DefaultRenderersFactory`][], you should see an info-level
  log line like "Loaded FfmpegAudioRenderer" in Logcat when the extension loads.
  If that's missing, make sure the application has a dependency on the
  extension.
* If you see warning-level logs from [`LibraryLoader`][] in Logcat, this
  indicates that loading the native component of the extension failed. If this
  happens, check you've followed the steps in the extension's README correctly
  and that no errors were output while following the instructions.

If you're still experiencing problems using extensions, please check the
ExoPlayer [issue tracker][] for any relevant recent issues. If you need to file
a new issue and it relates to building the native part of the extension, please
include full command line output from running README instructions, to help us
diagnose the issue.

#### Can I play YouTube videos directly with ExoPlayer? ####

No, ExoPlayer cannot play videos from YouTube, i.e., urls of the form
`https://www.youtube.com/watch?v=...`. Instead, you should use the [YouTube
Android Player API](https://developers.google.com/youtube/android/player/) which
is the official way to play YouTube videos on Android.

[Fixing "Cleartext HTTP traffic not permitted" errors]: #fixing-cleartext-http-traffic-not-permitted-errors
[Fixing "SSLHandshakeException" and "CertPathValidatorException" errors]: #fixing-sslhandshakeexception-and-certpathvalidatorexception-errors
[What formats does ExoPlayer support?]: #what-formats-does-exoplayer-support
[Why are some media files not seekable?]: #why-are-some-media-files-not-seekable
[Why is seeking inaccurate in some MP3 files?]: #why-is-seeking-inaccurate-in-some-mp3-files
[Why do some MPEG-TS files fail to play?]: #why-do-some-mpeg-ts-files-fail-to-play
[Why do some MP4/FMP4 files play incorrectly?]: #why-do-some-mp4fmp4-files-play-incorrectly
[Why do some streams fail with HTTP response code 301 or 302?]: #why-do-some-streams-fail-with-http-response-code-301-or-302
[Why do some streams fail with UnrecognizedInputFormatException?]: #why-do-some-streams-fail-with-unrecognizedinputformatexception
[Why doesn't setPlaybackParameters work properly on some devices?]: #why-doesnt-setplaybackparameters-work-properly-on-some-devices
[What do "Player is accessed on the wrong thread" errors mean?]: #what-do-player-is-accessed-on-the-wrong-thread-errors-mean
[How can I fix "Unexpected status line: ICY 200 OK"?]:  #how-can-i-fix-unexpected-status-line-icy-200-ok
[How can I query whether the stream being played is a live stream?]: #how-can-i-query-whether-the-stream-being-played-is-a-live-stream
[How do I keep audio playing when my app is backgrounded?]: #how-do-i-keep-audio-playing-when-my-app-is-backgrounded
[Why does ExoPlayer support my content but the Cast extension doesn't?]: #why-does-exoplayer-support-my-content-but-the-cast-extension-doesnt
[Why does content fail to play, but no error is surfaced?]: #why-does-content-fail-to-play-but-no-error-is-surfaced
[How can I get a decoding extension to load and be used for playback?]: #how-can-i-get-a-decoding-extension-to-load-and-be-used-for-playback
[Can I play YouTube videos directly with ExoPlayer?]: #can-i-play-youtube-videos-directly-with-exoplayer


[Supported formats]: {{ site.baseurl }}/supported-formats.html
[set on a `DefaultExtractorsFactory`]: {{ site.base_url }}/customization.html#customizing-extractor-flags
[`setMp3ExtractorFlags`]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setMp3ExtractorFlags(int)
[`FLAG_ENABLE_INDEX_SEEKING`]: {{ site.exo_sdk }}/extractor/mp3/Mp3Extractor.html#FLAG_ENABLE_INDEX_SEEKING
[`FLAG_DETECT_ACCESS_UNITS`]: {{ site.exo_sdk }}/extractor/ts/DefaultTsPayloadReaderFactory.html#FLAG_DETECT_ACCESS_UNITS
[`FLAG_ALLOW_NON_IDR_KEYFRAMES`]: {{ site.exo_sdk }}/extractor/ts/DefaultTsPayloadReaderFactory.html#FLAG_ALLOW_NON_IDR_KEYFRAMES
[`setTsExtractorFlags`]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setTsExtractorFlags(int)
[`Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS`]: {{ site.exo_sdk }}/extractor/mp4/Mp4Extractor.html#FLAG_WORKAROUND_IGNORE_EDIT_LISTS
[`FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS`]: {{ site.exo_sdk }}/extractor/mp4/FragmentedMp4Extractor.html#FLAG_WORKAROUND_IGNORE_EDIT_LISTS
[`setMp4ExtractorFlags`]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setMp4ExtractorFlags(int)
[`setFragmentedMp4ExtractorFlags`]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setFragmentedMp4ExtractorFlags(int)
[Wikipedia]: https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
[wget]: https://www.gnu.org/software/wget/manual/wget.html
[`DefaultHttpDataSourceFactory`]: {{ site.exo_sdk }}/upstream/DefaultHttpDataSourceFactory.html
[ExoPlayer module]: {{ site.base_url }}/hello-world.html#add-exoplayer-modules
[issue tracker]: https://github.com/google/ExoPlayer/issues
[`isCurrentWindowLive`]: {{ site.exo_sdk }}/Player.html#isCurrentWindowLive()
[`isCurrentWindowDynamic`]: {{ site.exo_sdk }}/Player.html#isCurrentWindowDynamic()
[`setPlaybackParameters`]: {{ site.exo_sdk }}/Player.html#setPlaybackParameters(com.google.android.exoplayer2.PlaybackParameters)
[foreground service]: https://developer.android.com/guide/components/services.html#Foreground
[`WifiLock`]: {{ site.android_sdk }}/android/net/wifi/WifiManager.WifiLock.html
[`WakeLock`]: {{ site.android_sdk }}/android/os/PowerManager.WakeLock.html
[`SimpleExoPlayer`]: {{ site.exo_sdk }}/SimpleExoPlayer.html
[`setWakeMode`]: {{ site.exo_sdk }}/SimpleExoPlayer.html#setWakeMode(int)
[A note on threading]: {{ site.base_url }}/hello-world.html#a-note-on-threading
[OkHttp extension]: {{ site.release_v2 }}/extensions/okhttp
[CORS enabled]: https://www.w3.org/wiki/CORS_Enabled
[Cast framework]: {{ site.google_sdk }}/cast/docs/chrome_sender/advanced#cors_requirements
[Android media formats documentation]: https://developer.android.com/guide/topics/media/media-formats#core
[extensions/ffmpeg/README.md]: {{ site.release_v2 }}/extensions/ffmpeg/README.md
[enable decoders]: {{ site.base_url }}/supported-formats.html#ffmpeg-extension
[demo application]: {{ site.base_url }}/demo-application.html
[enabling extension decoders]: {{ site.base_url }}/demo-application.html#enabling-extension-decoders
[`DefaultRenderersFactory`]: {{ site.exo_sdk }}/DefaultRenderersFactory.html
[`LibraryLoader`]: {{ site.exo_sdk }}/util/LibraryLoader.html
[`EventLogger`]: {{ site.baseurl }}/debug-logging.html

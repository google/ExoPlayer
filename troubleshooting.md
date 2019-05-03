---
title: Troubleshooting
redirect_from:
  - /faqs.html
---

* [Why are some media files not seekable?][]
* [Why do some MPEG-TS files fail to play?][]
* [Why do some MP4/FMP4 files play incorrectly?][]
* [Why do some streams fail with HTTP response code 301 or 302?][]
* [Why do some streams fail with UnrecognizedInputFormatException?][]
* [Why doesn't setPlaybackParameters work properly on some devices?][]
* [What do "Player is accessed on the wrong thread" warnings mean?][]
* [How can I query whether the stream being played is a live stream?][]
* [How do I keep audio playing when my app is backgrounded?][]

---

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
bitrate using `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flags. The simplest way to
enable this functionality on all extractors that support it is to use
`DefaultExtractorsFactory.setConstantBitrateSeekingEnabled`.

#### Why do some MPEG-TS files fail to play? ####

Some MPEG-TS files do not contain access unit delimiters (AUDs). By default
ExoPlayer relies on AUDs to cheaply detect frame boundaries. Similarly, some
MPEG-TS files do not contain IDR keyframes. By default these are the only type
of keyframes considered by ExoPlayer.

ExoPlayer will appear to be stuck in the buffering state when asked to play an
MPEG-TS file that lacks AUDs or IDR keyframes. If you need to play such files,
you can do so using [FLAG_DETECT_ACCESS_UNITS][] and
[FLAG_ALLOW_NON_IDR_KEYFRAMES][] respectively. These flags can be set on a
DefaultExtractorsFactory using [setTsExtractorFlags][]. Use of
`FLAG_DETECT_ACCESS_UNITS` has no side effects other than being computationally
expensive relative to AUD based frame boundary detection. Use of
`FLAG_ALLOW_NON_IDR_KEYFRAMES` may result in temporary visual corruption at the
start of playback and immediately after seeks when playing some MPEG-TS files.

#### Why do some MP4/FMP4 files play incorrectly? ####

Some MP4/FMP4 files contain edit lists that rewrite the media timeline by
skipping, moving or repeating lists of samples. ExoPlayer has partial support
for applying edit lists. For example, it can delay or repeat groups of samples
starting on a synchronization sample, but it does not truncate audio samples or
preroll media for edits that don't start on a synchronization sample.

If you are seeing that part of the media is unexpectedly missing or repeated,
try setting [Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS][] or
[FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS][], which will cause
the extractor to ignore edit lists entirely. These can be set on a
DefaultExtractorsFactory using [setMp4ExtractorFlags][] or
[setFragmentedMp4ExtractorFlags][].

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
when instantiating the `HttpDataSource.Factory` instances used by ExoPlayer in
your application. [`DefaultHttpDataSourceFactory`][] has constructors that
accept an `allowCrossProtocolRedirects` argument for this purpose, as do other
`HttpDataSource.Factory` implementations. Set these arguments to true to enable
cross-protocol redirects.

#### Why do some streams fail with UnrecognizedInputFormatException? ####

This question relates to playback failures of the form:
```
UnrecognizedInputFormatException: None of the available extractors
(MatroskaExtractor, FragmentedMp4Extractor, ...) could read the stream.
```
There are two possible causes of this failure. The most common cause is that
you're trying to play DASH (mpd), HLS (m3u8) or SmoothStreaming (ism, isml)
content using `ProgressiveMediaSource`. To play such streams you must use the
correct `MediaSource` implementations, which are `DashMediaSource`,
`HlsMediaSource` and `SsMediaSource` respectively. If you don't know the type of
the media then [Util.inferContentType][] can often be used, as demonstrated by
`PlayerActivity` in the ExoPlayer demo app.

The second, less common cause, is that ExoPlayer does not support the container
format of the media that you're trying to play. In this case the failure is
working as intended, however feel free to submit a feature request to our
[issue tracker][], including details of the container format and a test stream.
Please search for an existing feature request before submitting a new one.

#### Why doesn't setPlaybackParameters work properly on some devices? ####

When running a debug build of your app on Android M and earlier, you may
experience choppy performance, audible artifacts and high CPU utilization when
using the [setPlaybackParameters][] API. This is because an optimization that's
important to this API is disabled for debug builds running on these versions of
Android.

It's important to note that this issue affects debug builds only. It does *not*
affect release builds, for which the optimization is always enabled. Hence the
releases you provide to end users should not be affected by this issue.

#### What do "Player is accessed on the wrong thread" warnings mean? ####

If you are seeing this warning, some code in your app is accessing
`SimpleExoPlayer` on the wrong thread (check the reported stack trace!).
ExoPlayer instances need to be accessed from a single thread only. In most
cases, this should be the application's main thread. For details, please read
through the ["Threading model" section of the ExoPlayer Javadoc][].

#### How can I query whether the stream being played is a live stream? ####

You can query ExoPlayer's [isCurrentWindowDynamic][] method. A dynamic window
implies that the stream being played is a live stream.

#### How do I keep audio playing when my app is backgrounded? ####

There are a few steps that you need to take to ensure continued playback of
audio when your app is in the background:

1. You need to have a running [foreground service][]. This prevents the system
   from killing your process to free up resources.
1. You need to hold a [WifiLock][] and a [WakeLock][]. These ensure that the
   system keeps the WiFi radio and CPU awake.

It's important that you stop the service and release the locks as soon as audio
is no longer being played.

[What formats does ExoPlayer support?]: #what-formats-does-exoplayer-support
[Why are some media files not seekable?]: #why-are-some-media-files-not-seekable
[Why do some MPEG-TS files fail to play?]: #why-do-some-mpeg-ts-files-fail-to-play
[Why do some MP4/FMP4 files play incorrectly?]: #why-do-some-mp4fmp4-files-play-incorrectly
[Why do some streams fail with HTTP response code 301 or 302?]: #why-do-some-streams-fail-with-http-response-code-301-or-302
[Why do some streams fail with UnrecognizedInputFormatException?]: #why-do-some-streams-fail-with-unrecognizedinputformatexception
[Why doesn't setPlaybackParameters work properly on some devices?]: #why-doesnt-setplaybackparameters-work-properly-on-some-devices
[What do "Player is accessed on the wrong thread" warnings mean?]: #what-do-player-is-accessed-on-the-wrong-thread-warnings-mean
[How can I query whether the stream being played is a live stream?]: #how-can-i-query-whether-the-stream-being-played-is-a-live-stream
[How do I keep audio playing when my app is backgrounded?]: #how-do-i-keep-audio-playing-when-my-app-is-backgrounded

[Supported formats]: {{ site.baseurl }}/supported-formats.html
[setMp3ExtractorFlags]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setMp3ExtractorFlags-int-
[FLAG_DETECT_ACCESS_UNITS]: {{ site.exo_sdk }}/extractor/ts/DefaultTsPayloadReaderFactory.html#FLAG_DETECT_ACCESS_UNITS
[FLAG_ALLOW_NON_IDR_KEYFRAMES]: {{ site.exo_sdk }}/extractor/ts/DefaultTsPayloadReaderFactory.html#FLAG_ALLOW_NON_IDR_KEYFRAMES
[setTsExtractorFlags]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setTsExtractorFlags-int-
[Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS]: {{ site.exo_sdk }}/extractor/mp4/Mp4Extractor.html#FLAG_WORKAROUND_IGNORE_EDIT_LISTS
[FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS]: {{ site.exo_sdk }}/extractor/mp4/FragmentedMp4Extractor.html#FLAG_WORKAROUND_IGNORE_EDIT_LISTS
[setMp4ExtractorFlags]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setMp4ExtractorFlags-int-
[setFragmentedMp4ExtractorFlags]: {{ site.exo_sdk }}/extractor/DefaultExtractorsFactory#setFragmentedMp4ExtractorFlags-int-
[Wikipedia]: https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
[wget]: https://www.gnu.org/software/wget/manual/wget.html
[`DefaultHttpDataSourceFactory`]: {{ site.exo_sdk }}/upstream/DefaultHttpDataSourceFactory.html
[Util.inferContentType]: {{ site.exo_sdk }}/util/Util.html#inferContentType-android.net.Uri-
[issue tracker]: https://github.com/google/ExoPlayer/issues
[isCurrentWindowDynamic]: {{ site.exo_sdk }}/ExoPlayer.html#isCurrentWindowDynamic--
[setPlaybackParameters]: {{ site.exo_sdk }}/Player.html#setPlaybackParameters-com.google.android.exoplayer2.PlaybackParameters-
[Util.inferContentType]: {{ site.exo_sdk }}/util/Util.html#inferContentType-android.net.Uri-
[foreground service]: https://developer.android.com/guide/components/services.html#Foreground
[WifiLock]: {{ site.android_sdk }}/android/net/wifi/WifiManager.WifiLock.html
[WakeLock]: {{ site.android_sdk }}/android/os/PowerManager.WakeLock.html
["Threading model" section of the ExoPlayer Javadoc]: {{ site.exo_sdk }}/ExoPlayer.html

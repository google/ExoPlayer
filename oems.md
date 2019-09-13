---
title: OEM testing
---

ExoPlayer is used by a large number of Android applications. As an OEM, it's
important to ensure that ExoPlayer works correctly both on new devices, and on
new platform builds for existing devices. This page describes compatibility
tests that we recommend running before shipping a device or platform OTA, and
some of the common failure modes encountered when running them.

## Running the tests ##

To run ExoPlayer's playback tests, first check out the latest release of
ExoPlayer from [GitHub][]. You can then run the tests from the command line or
Android Studio.

### Command line ###

From the root directory, build and install the playback tests:
~~~
./gradlew :playbacktests:installDebug
~~~
{: .language-shell}
Next, run the playback tests in the GTS package:
~~~
adb shell am instrument -w -r -e debug false \
  -e package com.google.android.exoplayer2.playbacktests.gts \
  com.google.android.exoplayer2.playbacktests.test/androidx.test.runner.AndroidJUnitRunner
~~~
{: .language-shell}
Test results appear in STDOUT.

### Android Studio ###

Open the ExoPlayer project, navigate to the `playbacktests` module, right click
on the `gts` folder and run the tests. Test results appear in Android Studio's
Run window.

## Common failure modes ##

Some of the common failure modes encountered when running ExoPlayer's playback
tests are described below, together with the likely root cause in each case. We
will add to this list as further failure modes are discovered.

### Unexpected video buffer presentation timestamp ###

Logcat will contain an error similar to:
~~~
Caused by: java.lang.IllegalStateException: Expected to dequeue video buffer
with presentation timestamp: 134766000. Instead got: 134733000 (Processed
buffers since last flush: 2242).
~~~
{: .language-shell}
This failure is most often caused by the video decoder under test incorrectly
discarding, inserting or re-ordering buffers. In the example above, the test
expected to dequeue a buffer with presentation timestamp `134766000` from
`MediaCodec.dequeueOutputBuffer`, but found that it dequeued a buffer with
presentation timestamp `134733000` instead. We recommend that you check the
decoder implementation when encountering this failure, in particular that it
correctly handles adaptive resolution switches without discarding any buffers.

### Too many dropped buffers ###

Logcat will contain an error similar to:
~~~
junit.framework.AssertionFailedError: Codec(DashTest:Video) was late decoding:
200 buffers. Limit: 25.
~~~
{: .language-shell}
This failure is a performance problem, where the video decoder under test was
late decoding a large number of buffers. In the example above, ExoPlayer dropped
200 buffers because they were late by the time they were dequeued, for a test
that imposes a limit of 25. The most obvious cause is that the video decoder
is too slow decoding buffers. If the failures only occur for the subset of tests
that play Widevine protected content, it's likely that the platform operations
for buffer decryption are too slow. We recommend checking the performance of
these components, and looking at whether any optimizations can be made to speed
them up.

### Native window could not be authenticated ###

Logcat will contain an error similar to:
~~~
SurfaceUtils: native window could not be authenticated
ExoPlayerImplInternal: Internal runtime error.
ExoPlayerImplInternal: android.media.MediaCodec$CodecException: Error 0xffffffff
~~~
{: .language-shell}
This failure is indicative of the platform failing to correctly set the secure
bit flag.

### Test timed out ###

Logcat will contain an error similar to:
~~~
AssertionFailedError: Test timed out after 300000 ms.
~~~
{: .language-shell}
This failure is most often caused by poor network connectivity during the test
run. If the device appears to have good network connectivity then it's possible
that the test is getting stuck calling into a platform component (e.g.
`MediaCodec`, `MediaDrm`, `AudioTrack` etc). Inspect the call stacks of the
threads in the test process to establish whether this is the case.

[GitHub]: https://github.com/google/ExoPlayer

---
title: Demo application
---

ExoPlayer's main demo app serves two primary purposes:

1. To provide a relatively simple yet fully featured example of ExoPlayer usage.
   The demo app can be used as a convenient starting point from which to develop
   your own application.
1. To make it easy to try ExoPlayer. The demo app can be used to test playback
   of your own content in addition to the included samples.

This page describes how to get, compile and run the demo app. It also describes
how to use it to play your own media.

## Getting the code ##

The source code for the main demo app can be found in the `demos/main` folder of
our [GitHub project][]. If you haven't already done so, clone the project into a
local directory:

~~~
git clone https://github.com/google/ExoPlayer.git
~~~
{: .language-shell}

Next, open the project in Android Studio. You should see the following in the
Android Project view (the relevant folders of the demo app have been expanded):

{% include figure.html url="/images/demo-app-project.png" index="1" caption="The project in Android Studio" %}

## Compiling and running ##

To compile and run the demo app, select and run the `demo` configuration in
Android Studio. The demo app will install and run on a connected Android device.
We recommend using a physical device if possible. If you wish to use an emulator
instead, please read the emulators section of [Supported devices][] and ensure
that your Virtual Device uses a system image with an API level of at least 23.

{% include figure.html url="/images/demo-app-screenshots.png" index="2" caption="SampleChooserActivity and PlayerActivity" %}

The demo app presents of a list of samples (`SampleChooserActivity`). Selecting
a sample will open a second activity (`PlayerActivity`) for playback. The demo
features playback controls and track selection functionality. It also uses
ExoPlayer's `EventLogger` utility class to output useful debug information to
the system log. This logging can be viewed (along with error level logging for
other tags) with the command:

~~~
adb logcat EventLogger:V *:E
~~~
{: .language-shell}

### Enabling extension decoders ###

ExoPlayer has a number of extensions that allow use of bundled software
decoders, including AV1, VP9, Opus, FLAC and FFmpeg (audio only). The demo app
can be built to include and use these extensions as follows:

1. Build each of the extensions that you want to include. Note that this is a
   manual process. Refer to the `README.md` file in each extension for
   instructions.
1. In Android Studio's Build Variants view, set the build variant for the demo
   module to `withDecoderExtensionsDebug` or `withDecoderExtensionsRelease` as
   shown below.
1. Compile, install and run the `demo` configuration as normal.

{% include figure.html url="/images/demo-app-build-variants.png" index="3" caption="Selecting the demo withDecoderExtensionsDebug build variant" %}

By default an extension decoder will be used only if a suitable platform decoder
does not exist. It is possible to specify that extension decoders should be
preferred, as described in the sections below.

## Playing your own content ##

There are multiple ways to play your own content in the demo app.

### 1. Editing assets/media.exolist.json ###

The samples listed in the demo app are loaded from `assets/media.exolist.json`.
By editing this JSON file it's possible to add and remove samples from the demo
app. The schema is as follows, where [O] indicates an optional attribute.

~~~
[
  {
    "name": "Name of heading",
    "samples": [
      {
        "name": "Name of sample",
        "uri": "The URI of the sample",
        "extension": "[O] Sample type hint. Values: mpd, ism, m3u8",
        "clip_start_position_ms": "[O] A start point to which the sample should be clipped, in milliseconds"
        "clip_end_position_ms": "[O] An end point from which the sample should be clipped, in milliseconds"
        "drm_scheme": "[O] Drm scheme if protected. Values: widevine, playready, clearkey",
        "drm_license_uri": "[O] URI of the license server if protected",
        "drm_force_default_license_uri": "[O] Whether to force use of "drm_license_uri" for key requests that include their own license URI",
        "drm_key_request_properties": "[O] Key request headers if protected",
        "drm_session_for_clear_content": "[O] Whether to attach a DRM session to clear video and audio tracks"
        "drm_multi_session": "[O] Enables key rotation if protected",
        "subtitle_uri": "[O] The URI of a subtitle sidecar file",
        "subtitle_mime_type": "[O] The MIME type of subtitle_uri (required if subtitle_uri is set)",
        "subtitle_language": "[O] The BCP47 language code of the subtitle file (ignored if subtitle_uri is not set)",
        "ad_tag_uri": "[O] The URI of an ad tag to load via the IMA extension"
      },
      ...etc
    ]
  },
  ...etc
]
~~~
{: .language-json}

Playlists of samples can be specified using the schema:

~~~
[
  {
    "name": "Name of heading",
    "samples": [
      {
        "name": "Name of playlist sample",
        "playlist": [
          {
            "uri": "The URI of the first sample in the playlist",
            "extension": "[O] Sample type hint. Values: mpd, ism, m3u8"
            "clip_start_position_ms": "[O] A start point to which the sample should be clipped, in milliseconds"
            "clip_end_position_ms": "[O] An end point from which the sample should be clipped, in milliseconds"
            "drm_scheme": "[O] Drm scheme if protected. Values: widevine, playready, clearkey",
            "drm_license_uri": "[O] URI of the license server if protected",
            "drm_force_default_license_uri": "[O] Whether to force use of "drm_license_uri" for key requests that include their own license URI",
            "drm_key_request_properties": "[O] Key request headers if protected",
            "drm_session_for_clear_content": "[O] Whether to attach a DRM session to clear video and audio tracks",
            "drm_multi_session": "[O] Enables key rotation if protected",
            "subtitle_uri": "[O] The URI of a subtitle sidecar file",
            "subtitle_mime_type": "[O] The MIME type of subtitle_uri (required if subtitle_uri is set)",
            "subtitle_language": "[O] The BCP47 language code of the subtitle file (ignored if subtitle_uri is not set)"
          },
          {
            "uri": "The URI of the second sample in the playlist",
            ...etc
          },
          ...etc
        ]
      },
      ...etc
    ]
  },
  ...etc
]
~~~
{: .language-json}

If required, key request headers are specified as an object containing a string
attribute for each header:

~~~
"drm_key_request_properties": {
  "name1": "value1",
  "name2": "value2",
  ...etc
}
~~~
{: .language-json}

In the sample chooser activity, the overflow menu contains options for
specifying whether to prefer extension decoders.

### 2. Loading an external exolist.json file ###

The demo app can load external JSON files using the schema above and named
according to the `*.exolist.json` convention. For example if you host such a
file at `https://yourdomain.com/samples.exolist.json`, you can open it in the
demo app using:

~~~
adb shell am start -a com.android.action.VIEW \
    -d https://yourdomain.com/samples.exolist.json
~~~
{: .language-shell}

Clicking a `*.exolist.json` link (e.g., in the browser or an email client) on a
device with the demo app installed will also open it in the demo app. Hence
hosting a `*.exolist.json` JSON file provides a simple way of distributing
content for others to try in the demo app.

### 3. Firing an intent ###

Intents can be used to bypass the list of samples and launch directly into
playback. To play a single sample set the intent's action to
`com.google.android.exoplayer.demo.action.VIEW` and its data URI to that of the
sample to play. Such an intent can be fired from the terminal using:

~~~
adb shell am start -a com.google.android.exoplayer.demo.action.VIEW \
    -d https://yourdomain.com/sample.mp4
~~~
{: .language-shell}

Supported optional extras for a single sample intent are:

* Sample configuration extras:
  * `mime_type` [String] Sample MIME type hint. For example
    `application/dash+xml` for DASH content.
  * `clip_start_position_ms` [Long] A start point to which the sample should be
    clipped, in milliseconds.
  * `clip_end_position_ms` [Long] An end point from which the sample should be
    clipped, in milliseconds.
  * `drm_scheme` [String] DRM scheme if protected. Valid values are `widevine`,
    `playready` and `clearkey`. DRM scheme UUIDs are also accepted.
  * `drm_license_uri` [String] URI of the license server if protected.
  * `drm_force_default_license_uri` [Boolean] Whether to force use of
    `drm_license_uri` for key requests that include their own license URI.
  * `drm_key_request_properties` [String array] Key request headers packed as
    name1, value1, name2, value2 etc. if protected.
  * `drm_session_for_clear_content` [Boolean] Whether to attach a DRM session
    to clear video and audio tracks.
  * `drm_multi_session` [Boolean] Enables key rotation if protected.
  * `subtitle_uri` [String] The URI of a subtitle sidecar file.
  * `subtitle_mime_type` [String] The MIME type of subtitle_uri (required if
    subtitle_uri is set).
  * `subtitle_language` [String] The BCP47 language code of the subtitle file
    (ignored if subtitle_uri is not set).
  * `ad_tag_uri` [String] The URI of an ad tag to load using the
    [IMA extension][].
  * `prefer_extension_decoders` [Boolean] Whether extension decoders are
    preferred to platform ones.

When using `adb shell am start` to fire an intent, an optional string extra can
be set with `--es` (e.g., `--es extension mpd`). An optional boolean extra can
be set with `--ez` (e.g., `--ez prefer_extension_decoders TRUE`). An optional
long extra can be set with `--el` (e.g., `--el clip_start_position_ms 5000`). An
optional string array extra can be set with `--esa` (e.g.,
`--esa drm_key_request_properties name1,value1`).

To play a playlist of samples, set the intent's action to
`com.google.android.exoplayer.demo.action.VIEW_LIST`. The sample configuration
extras remain the same as for `com.google.android.exoplayer.demo.action.VIEW`,
except for two differences:

* The extras' keys should have an underscore and the 0-based index of the sample
  as suffix. For example, `extension_0` would hint the sample type for the first
  sample. `drm_scheme_1` would set the DRM scheme for the second sample.
* The uri of the sample is passed as an extra with key `uri_<sample-index>`.

Other extras, which are not sample dependant, do not change. For example, you
can run the following command in the terminal to play a playlist with two items,
overriding the extension of the second item:
~~~
adb shell am start -a com.google.android.exoplayer.demo.action.VIEW_LIST \
    --es uri_0 https://a.com/sample1.mp4 \
    --es uri_1 https://b.com/sample2.fake_mpd \
    --es extension_1 mpd
~~~
{: .language-shell}

[IMA extension]: {{ site.release_v2 }}/extensions/ima
[GitHub project]: https://github.com/google/ExoPlayer
[Supported devices]: {{ site.baseurl }}/supported-devices.html

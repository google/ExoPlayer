---
layout: default
title: Demo application
weight: 3
exclude_from_menu: true
---

The ExoPlayer demo app serves two primary purposes:

1. To provide a relatively simple yet fully featured example of ExoPlayer usage.
   The demo app can be used as a convenient starting point from which to develop
   your own application.
1. To make it easy to try ExoPlayer. The demo app can be used to test playback
   of your own content in addition to the included samples.

This page describes how to get, compile and run the demo app. It also describes
how to use it to play your own media.

## Getting the code ##

The source code for the demo app can be found in the `demo` folder of our
[GitHub project][]. If you haven't already done so, clone the project into a
local directory:

{% highlight shell %}
git clone https://github.com/google/ExoPlayer.git
{% endhighlight %}

Next, open the project in Android Studio. You should see the following in the
Android Project view (the relevant folders of the demo app have been expanded):

{% include figure.html url="/images/demo-app-project.png" index="1" caption="The project in Android Studio" %}

## Compiling and running ##

To compile and run the demo app, select and run the `demo` configuration in
Android Studio. The demo app will install and run on a connected Android device.
We recommend using a physical device if possible. If you wish to use an emulator
instead, please read [FAQ - Does ExoPlayer support emulators?][] and ensure
that your Virtual Device uses a system image with an API level of at least 23.

{% include figure.html url="/images/demo-app-screenshots.png" index="2" caption="SampleChooserActivity and PlayerActivity" %}

The demo app presents of a list of samples (`SampleChooserActivity`). Selecting
a sample will open a second activity (`PlayerActivity`) for playback. The demo
features playback controls and track selection functionality. It also has an
`EventLogger` class that outputs useful debug information to the system log.
This logging can be viewed (along with error level logging for other tags) with
the command:

{% highlight shell %}
adb logcat EventLogger:V *:E
{% endhighlight %}

### Including extension decoders ###

ExoPlayer has a number of extensions that allow use of bundled software
decoders, including VP9, Opus, FLAC and FFMPEG (audio only). The demo app can
be built to include and use these extensions as follows:

1. Build each of the extensions that you want to include. Note that this is a
   manual process. Refer to the `README.md` file in each extension for
   instructions.
1. In Android Studio's Build Variants view, change the build variant for the
   demo module from `demoDebug` to `demo_extDebug`, as shown in Figure 3.
1. Compile, install and run the `demo` configuration as normal.

{% include figure.html url="/images/demo-app-build-variants.png" index="3" caption="Selecting the demo_extDebug build variant" %}

By default an extension decoder will be used only if a suitable platform decoder
does not exist. It is possible to indicate that extension decoders should be
preferred, as described in the sections below.

## Playing your own content ##

There are multiple ways to play your own content in the demo app.

### 1. Editing assets/media.exolist.json ###

The samples listed in the demo app are loaded from `assets/media.exolist.json`.
By editing this JSON file it's possible add and remove samples from the demo
app. The schema for samples is:

{% highlight json %}
[
  {
    "name": "Name of heading",
    "samples": [
      {
        "name": "Name of sample",
        "uri": "The URI/URL of the sample",
        "extension": "[Optional] Sample type hint. Values: mpd, ism, m3u8",
        "prefer_extension_decoders": "[Optional] Boolean to prefer extension decoders",
        "drm_scheme": "[Optional] Drm scheme if protected. Values: widevine, playready",
        "drm_license_url": "[Optional] URL of the license server if protected"
      },
      ...etc
    ]
  },
  ...etc
]
{% endhighlight %}

Playlists of samples can also be added using the schema:

{% highlight json %}
[
  {
    "name": "Name of heading",
    "samples": [
      {
        "name": "Name of playlist sample",
        "prefer_extension_decoders": "[Optional] Boolean to prefer extension decoders",
        "drm_scheme": "[Optional] Drm scheme if protected. Values: widevine, playready",
        "drm_license_url": "[Optional] URL of the license server if protected",
        "playlist": [
          {
            "uri": "The URI/URL of the first sample in the playlist",
            "extension": "[Optional] Sample type hint. Values: mpd, ism, m3u8"
          },
          {
            "uri": "The URI/URL of the first sample in the playlist",
            "extension": "[Optional] Sample type hint. Values: mpd, ism, m3u8"
          },
          ...etc
        ]
      },
      ...etc
    ]
  },
  ...etc
]
{% endhighlight %}

### 2. Loading an external exolist.json file ###

The demo app can load external JSON files using the schema above and named
according to the `*.exolist.json` convention. For example if you host such a
file at `https://yourdomain.com/samples.exolist.json`, you can open it in the
demo app using:

{% highlight shell %}
adb shell am start -d https://yourdomain.com/samples.exolist.json
{% endhighlight %}

Clicking a `*.exolist.json` link on a device with the demo app installed (e.g.
in the browser or an email client) will also open it in the demo app. Hence
hosting a `*.exolist.json` JSON file provides a simple way of distributing
content for others to try in the demo app.

### 3. Firing an intent ###

Intents can be used to bypass the list of samples and launch directly into
playback. To play a single sample set the intent's action to
`com.google.android.exoplayer.demo.action.VIEW` and its data URI to that of the
sample to play. Such an intent can be fired from the terminal using:

{% highlight shell %}
adb shell am start -a com.google.android.exoplayer.demo.action.VIEW \
    -d https://yourdomain.com/sample.mp4
{% endhighlight %}

Supported optional extras for a single sample intent are:

* `extension` [String] Sample type hint. Valid values: mpd, ism, m3u8
* `drm_scheme_uuid` [String] Drm scheme UUID if protected
* `drm_license_url` [String] Url of the license server if protected
* `prefer_extension_decoders` [Boolean] Whether extension decoders are preferred
  to platform ones

When using `adb shell am start` to fire an intent, an optional string extra can
be set with `--es` (e.g. `--es extension mpd`). An optional boolean extra can be
set with `--ez` (e.g. `--ez prefer_extension_decoders TRUE`).

To play a playlist of samples set the intent's action to
`com.google.android.exoplayer.demo.action.VIEW_LIST` and use a `uri_list` string
array extra instead of a data URI. When using `adb shell am start` to fire an
intent, a string array extra can be set with `--esa`. For example:

{% highlight shell %}
adb shell am start -a com.google.android.exoplayer.demo.action.VIEW_LIST \
    --esa uri_list https://a.com/sample1.mp4,https://b.com/sample2.mp4
{% endhighlight %}

Supported optional extras for a playlist intent are:

* `extension_list` [String array] Sample type hints. Entries may be empty or one
  of: mpd, ism, m3u8
* `drm_scheme_uuid`, `drm_license_url` and `prefer_extension_decoders`, all as
  described above

[GitHub project]: https://github.com/google/ExoPlayer
[FAQ - Does ExoPlayer support emulators?]: https://google.github.io/ExoPlayer/faqs.html#does-exoplayer-support-emulators

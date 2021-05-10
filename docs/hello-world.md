---
title: Hello world!
redirect_from:
  - /guide.html
  - /guide-v1.html
  - /getting-started.html
---

Another way to get started is to work through
[the ExoPlayer codelab](https://codelabs.developers.google.com/codelabs/exoplayer-intro/).
{:.info}

For simple use cases, getting started with `ExoPlayer` consists of implementing
the following steps:

1. Add ExoPlayer as a dependency to your project.
1. Create a `SimpleExoPlayer` instance.
1. Attach the player to a view (for video output and user input).
1. Prepare the player with a `MediaItem` to play.
1. Release the player when done.

These steps are described in more detail below. For a complete example, refer to
`PlayerActivity` in the [main demo app][].

## Adding ExoPlayer as a dependency ##

### Add repositories ###

The first step to getting started is to make sure you have the Google and
JCenter repositories included in the `build.gradle` file in the root of your
project.

~~~
repositories {
    google()
    jcenter()
}
~~~
{: .language-gradle}

### Add ExoPlayer modules ###

Next add a dependency in the `build.gradle` file of your app module. The
following will add a dependency to the full ExoPlayer library:

~~~
implementation 'com.google.android.exoplayer:exoplayer:2.X.X'
~~~
{: .language-gradle}

where `2.X.X` is your preferred version (the latest version can be found by
consulting the [release notes][]).

As an alternative to the full library, you can depend on only the library
modules that you actually need. For example the following will add dependencies
on the Core, DASH and UI library modules, as might be required for an app that
only plays DASH content:

~~~
implementation 'com.google.android.exoplayer:exoplayer-core:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-dash:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-ui:2.X.X'
~~~
{: .language-gradle}

The available library modules are listed below. Adding a dependency to the full
ExoPlayer library is equivalent to adding dependencies on all of the library
modules individually.

* `exoplayer-core`: Core functionality (required).
* `exoplayer-dash`: Support for DASH content.
* `exoplayer-hls`: Support for HLS content.
* `exoplayer-rtsp`: Support for RTSP content.
* `exoplayer-smoothstreaming`: Support for SmoothStreaming content.
* `exoplayer-transformer`: Media transformation functionality.
* `exoplayer-ui`: UI components and resources for use with ExoPlayer.

In addition to library modules, ExoPlayer has multiple extension modules that
depend on external libraries to provide additional functionality. Browse the
[extensions directory][] and their individual READMEs for details.

### Turn on Java 8 support ###

If not enabled already, you need to turn on Java 8 support in all `build.gradle`
files depending on ExoPlayer, by adding the following to the `android` section:

~~~
compileOptions {
  targetCompatibility JavaVersion.VERSION_1_8
}
~~~
{: .language-gradle}

### Enable multidex ###

If your Gradle `minSdkVersion` is 20 or lower, you should
[enable multidex](https://developer.android.com/studio/build/multidex) in order
to prevent build errors.

## Creating the player ##

You can create an `ExoPlayer` instance using `SimpleExoPlayer.Builder`, which
provides a range of customization options. The code below is the simplest
example of creating an instance.

~~~
SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
~~~
{: .language-java}

### A note on threading ###

ExoPlayer instances must be accessed from a single application thread. For the
vast majority of cases this should be the application's main thread. Using the
application's main thread is a requirement when using ExoPlayer's UI components
or the IMA extension.

The thread on which an ExoPlayer instance must be accessed can be explicitly
specified by passing a `Looper` when creating the player. If no `Looper` is
specified, then the `Looper` of the thread that the player is created on is
used, or if that thread does not have a `Looper`, the `Looper` of the
application's main thread is used. In all cases the `Looper` of the thread from
which the player must be accessed can be queried using
`Player.getApplicationLooper`.

If you see `IllegalStateException` being thrown with the message "Player is
accessed on the wrong thread", then some code in your app is accessing a
`SimpleExoPlayer` instance on the wrong thread (the exception's stack trace
shows you where). You can temporarily opt out from these exceptions being thrown
by calling `SimpleExoPlayer.setThrowsWhenUsingWrongThread(false)`, in which case
the issue will be logged as a warning instead. Using this opt out is not safe
and may result in unexpected or obscure errors. It will be removed in ExoPlayer
2.16.
{:.info}

For more information about ExoPlayer's treading model, see the
["Threading model" section of the ExoPlayer Javadoc][].

## Attaching the player to a view ##

The ExoPlayer library provides a range of pre-built UI components for media
playback. These include `StyledPlayerView`, which encapsulates a
`StyledPlayerControlView`, a `SubtitleView`, and a `Surface` onto which video is
rendered. A `StyledPlayerView` can be included in your application's layout xml.
Binding the player to the view is as simple as:

~~~
// Bind the player to the view.
playerView.setPlayer(player);
~~~
{: .language-java}

You can also use `StyledPlayerControlView` as a standalone component, which is
useful for audio only use cases.

Use of ExoPlayer's pre-built UI components is optional. For video applications
that implement their own UI, the target `SurfaceView`, `TextureView`,
`SurfaceHolder` or `Surface` can be set using `SimpleExoPlayer`'s
`setVideoSurfaceView`, `setVideoTextureView`, `setVideoSurfaceHolder` and
`setVideoSurface` methods respectively. `SimpleExoPlayer`'s `addTextOutput`
method can be used to receive captions that should be rendered during playback.

## Populating the playlist and preparing the player ##

In ExoPlayer every piece of media is represented by a `MediaItem`. To play a
piece of media you need to build a corresponding `MediaItem`, add it to the
player, prepare the player, and call `play` to start the playback:

~~~
// Build the media item.
MediaItem mediaItem = MediaItem.fromUri(videoUri);
// Set the media item to be played.
player.setMediaItem(mediaItem);
// Prepare the player.
player.prepare();
// Start the playback.
player.play();
~~~
{: .language-java}

ExoPlayer supports playlists directly, so it's possible to prepare the player
with multiple media items to be played one after the other:

~~~
// Build the media items.
MediaItem firstItem = MediaItem.fromUri(firstVideoUri);
MediaItem secondItem = MediaItem.fromUri(secondVideoUri);
// Add the media items to be played.
player.addMediaItem(firstItem);
player.addMediaItem(secondItem);
// Prepare the player.
player.prepare();
// Start the playback.
player.play();
~~~
{: .language-java}

The playlist can be updated during playback without the need to prepare the
player again. Read more about populating and manipulating the playlist on the
[Playlists page][]. Read more about the different options available when
building media items, such as clipping and attaching subtitle files, on the
[Media items page][].

Prior to ExoPlayer 2.12, the player needed to be given a `MediaSource` rather
than media items. From 2.12 onwards, the player converts media items to the
`MediaSource` instances that it needs internally. Read more about this process
and how it can be customized on the [Media sources page][]. It's still possible
to provide `MediaSource` instances directly to the player using
`ExoPlayer.setMediaSource(s)` and `ExoPlayer.addMediaSource(s)`.
{:.info}

## Controlling the player ##

Once the player has been prepared, playback can be controlled by calling methods
on the player. Some of the most commonly used methods are listed below.

* `play` and `pause` start and pause playback.
* `seekTo` allows seeking within the media.
* `hasPrevious`, `hasNext`, `previous` and `next` allow navigating through the
  playlist.
* `setRepeatMode` controls if and how media is looped.
* `setShuffleModeEnabled` controls playlist shuffling.
* `setPlaybackParameters` adjusts playback speed and audio pitch.

If the player is bound to a `StyledPlayerView` or `StyledPlayerControlView`,
then user interaction with these components will cause corresponding methods on
the player to be invoked.

## Releasing the player ##

It's important to release the player when it's no longer needed, so as to free
up limited resources such as video decoders for use by other applications. This
can be done by calling `ExoPlayer.release`.

[main demo app]: {{ site.release_v2 }}/demos/main/
[extensions directory]: {{ site.release_v2 }}/extensions/
[release notes]: {{ site.release_v2 }}/RELEASENOTES.md
["Threading model" section of the ExoPlayer Javadoc]: {{ site.exo_sdk }}/ExoPlayer.html
[Playlists page]: {{ site.baseurl }}/playlists.html
[Media items page]: {{ site.baseurl }}/media-items.html
[Media sources page]: {{ site.baseurl }}/media-sources.html


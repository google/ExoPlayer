---
title: UI components
---

This documentation may be out-of-date. Please refer to the
[documentation for the latest ExoPlayer release][] on developer.android.com.
{:.info}

An app playing media requires user interface components for displaying media and
controlling playback. The ExoPlayer library includes a UI module that contains
a number of UI components. To depend on the UI module add a dependency as shown
below.

~~~
implementation 'com.google.android.exoplayer:exoplayer-ui:2.X.X'
~~~
{: .language-gradle}

The most important component is `StyledPlayerView`, a view for media
playbacks. It displays video, subtitles and album art during playback, as
well as playback controls.

`StyledPlayerView` has a `setPlayer` method for attaching and detaching (by
passing `null`) player instances.

## StyledPlayerView ##

`StyledPlayerView` can be used for both video and audio playbacks. It renders
video and subtitles in the case of video playback, and can display artwork
included as metadata in audio files. You can include it in your layout files
like any other UI component. For example, a `StyledPlayerView` can be included
with the following XML:

~~~
<com.google.android.exoplayer2.ui.StyledPlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:show_buffering="when_playing"
    app:show_shuffle_button="true"/>
~~~
{: .language-xml}

The snippet above illustrates that `StyledPlayerView` provides several
attributes. These attributes can be used to customize the view's behavior, as
well as its look and feel. Most of these attributes have corresponding setter
methods, which can be used to customize the view at runtime. The
[`StyledPlayerView`][] Javadoc lists these attributes and setter methods in
more detail.

Once the view is declared in the layout file, it can be looked up in the
`onCreate` method of the activity:

~~~
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  // ...
  playerView = findViewById(R.id.player_view);
}
~~~
{: .language-java}

When a player has been initialized, it can be attached to the view by calling
`setPlayer`:

~~~
// Instantiate the player.
player = new ExoPlayer.Builder(context).build();
// Attach player to the view.
playerView.setPlayer(player);
// Set the media item to be played.
player.setMediaItem(mediaItem);
// Prepare the player.
player.prepare();
~~~
{: .language-java}

### Choosing a surface type ###

The `surface_type` attribute of `StyledPlayerView` lets you set the type of
surface used for video playback. Besides the values `spherical_gl_surface_view`
(which is a special value for spherical video playback) and
`video_decoder_gl_surface_view` (which is for video rendering using extension
renderers), the allowed values are `surface_view`, `texture_view` and `none`. If
the view is for audio playback only, `none` should be used to avoid having to
create a surface, since doing so can be expensive.

If the view is for regular video playback then `surface_view` or `texture_view`
should be used. `SurfaceView` has a number of benefits over `TextureView` for
video playback:

* Significantly lower power consumption on many devices.
* More accurate frame timing, resulting in smoother video playback.
* Support for secure output when playing DRM protected content.
* The ability to render video content at the full resolution of the display on
  Android TV devices that upscale the UI layer.

`SurfaceView` should therefore be preferred over `TextureView` where possible.
`TextureView` should be used only if `SurfaceView` does not meet your needs. One
example is where smooth animations or scrolling of the video surface is required
prior to Android N, as described below. For this case, it's preferable to use
`TextureView` only when [`SDK_INT`][] is less than 24 (Android N) and
`SurfaceView` otherwise.

`SurfaceView` rendering wasn't properly synchronized with view animations until
Android N. On earlier releases this could result in unwanted effects when a
`SurfaceView` was placed into scrolling container, or when it was subjected to
animation. Such effects included the view's contents appearing to lag slightly
behind where it should be displayed, and the view turning black when subjected
to animation. To achieve smooth animation or scrolling of video prior to Android
N, it's therefore necessary to use `TextureView` rather than `SurfaceView`.
{:.info}

Some Android TV devices run their UI layer at a resolution that's lower than the
full resolution of the display, upscaling it for presentation to the user. For
example, the UI layer may be run at 1080p on an Android TV that has a 4K
display. On such devices, `SurfaceView` must be used to render content at the
full resolution of the display. The full resolution of the display (in its
current display mode) can be queried using [`Util.getCurrentDisplayModeSize`][].
The UI layer resolution can be queried using Android's [`Display.getSize`] API.
{:.info}

### Overriding drawables ###

We don't guarantee that the customizations described in the following section
will continue to work in future versions of the library. The resource IDs may
change name, or some may be deleted entirely. This is indicated by the
[resource IDs being marked 'private'][].
{:.info}

`StyledPlayerView` uses `StyledPlayerControlView` to display the playback
controls and progress bar. The drawables used by `StyledPlayerControlView` can
be overridden by drawables with the same names defined in your application. See
the [`StyledPlayerControlView`][] Javadoc for a list of control drawables that
can be overridden.

## Further customization ##

Where customization beyond that described above is required, we expect that app
developers will implement their own UI components rather than use those provided
by ExoPlayer's UI module.

[documentation for the latest ExoPlayer release]: https://developer.android.com/guide/topics/media/exoplayer/ui-components
[`StyledPlayerView`]: {{ site.exo_sdk }}/ui/StyledPlayerView.html
[`StyledPlayerControlView`]: {{ site.exo_sdk }}/ui/StyledPlayerControlView.html
[resource IDs being marked 'private']: https://developer.android.com/studio/projects/android-library#PrivateResources
[`SDK_INT`]: {{ site.android_sdk }}/android/os/Build.VERSION.html#SDK_INT
[`Util.getCurrentDisplayModeSize`]: {{ site.exo_sdk }}/util/Util.html#getCurrentDisplayModeSize(android.content.Context)
[`Display.getSize`]: {{ site.android_sdk }}/android/view/Display.html#getSize(android.graphics.Point)

---
title: Track selection
---

Track selection determines which of the available media tracks are played by the
player. This process is configured by [`TrackSelectionParameters`][], which
support many different options to specify constraints and overrides.

## Information about existing tracks

The player needs to prepare the media to know which tracks are available for
selection. You can listen to `Player.Listener.onTracksInfoChanged` to get
notified about changes, which may happen
 * When preparation completes
 * When the available or selected tracks change
 * When the playlist item changes

~~~
player.addListener(new Player.Listener() {
  @Override
  public void onTracksInfoChanged(TracksInfo tracksInfo) {
    // Update UI using current TracksInfo.
  }
});
~~~
{: .language-java}

You can also retrieve the current `TracksInfo` by calling
`player.getCurrentTracksInfo()`.

`TracksInfo` contains a list of `TrackGroupInfo`s with information about the
track type, format details, player support and selection status of each
available track. Tracks are grouped together into one `TrackGroup` if they
represent the same content that can be used interchangeably by the player (for
example, all audio tracks of a single language, but with different bitrates).

~~~
for (TrackGroupInfo groupInfo : tracksInfo.getTrackGroupInfos()) {
  // Group level information.
  @C.TrackType int trackType = groupInfo.getTrackType();
  boolean trackInGroupIsSelected = groupInfo.isSelected();
  boolean trackInGroupIsSupported = groupInfo.isSupported();
  TrackGroup group = groupInfo.getTrackGroup();
  for (int i = 0; i < group.length; i++) {
    // Individual track information.
    boolean isSupported = groupInfo.isTrackSupported(i);
    boolean isSelected = groupInfo.isTrackSelected(i);
    Format trackFormat = group.getFormat(i);
  }
}
~~~
{: .language-java}

* A track is 'supported' if the `Player` is able to decode and render its
  samples. Note that even if multiple track groups of the same type (for example
  multiple audio track groups) are supported, it only means that they are
  supported individually and the player is not necessarily able to play them at
  the same time.
* A track is 'selected' if the track selector chose this track for playback
  using the current `TrackSelectionParameters`. If multiple tracks within one
  track group are selected, the player uses these tracks for adaptive playback
  (for example, multiple video tracks with different bitrates). Note that only
  one of these tracks will be played at any one time. If you want to be notified
  of in-playback changes to the adaptive video track you can listen to
  `Player.Listener.onVideoSizeChanged`.

## Modifying track selection parameters

The selection process can be configured by setting `TrackSelectionParameters` on
the `Player` with `Player.setTrackSelectionParameters`. These updates can be
done before and during playback. In most cases, it's advisable to obtain the
current parameters and only modify the required aspects with the
`TrackSelectionParameters.Builder`. The builder class also allows chaining to
specify multiple options with one command:

~~~
player.setTrackSelectionParameters(
    player.getTrackSelectionParameters()
        .buildUpon()
        .setMaxVideoSizeSd()
        .setPreferredAudioLanguage("hu")
        .build());
~~~
{: .language-java}

### Constraint based track selection

Most options in `TrackSelectionParameters` allow you to specify constraints,
which are independent of the tracks that are actually available. Typical
constraints are:

 * Maximum or minimum video width, height, frame rate, or bitrate.
 * Maximum audio channel count or bitrate.
 * Preferred MIME types for video or audio.
 * Preferred audio languages or role flags.
 * Preferred text languages or role flags.

Note that ExoPlayer already applies sensible defaults for most of these values,
for example restricting video resolution to the display size or preferring the
audio language that matches the user's system Locale setting.

There are several benefits to using constraint based track selection instead of
specifying specific tracks directly:

* You can specify constraints before knowing what tracks the media provides.
  This allows to immediately select the appropriate tracks for faster startup
  time and also simplifies track selection code as you don't have to listen for
  changes in the available tracks.
* Constraints can be applied consistently across all items in a playlist. For
  example, selecting an audio language based on user preference will
  automatically apply to the next playlist item too, whereas overriding a
  specific track will only apply to the current playlist item for which the
  track exists.

### Selecting specific tracks

It's possible to specify specific tracks in `TrackSelectionParameters` that
should be selected for the current set of tracks. Note that a change in the
available tracks, for example when changing items in a playlist, will also
invalidate such a track override.

The simplest way to specify track overrides is to specify the `TrackGroup` that
should be selected for its track type. For example, you can specify an audio
track group to select this audio group and prevent any other audio track groups
from being selected:

~~~
TrackSelectionOverrides overrides =
    new TrackSelectionOverrides.Builder()
        .setOverrideForType(new TrackSelectionOverride(audioTrackGroup))
        .build();
player.setTrackSelectionParameters(
    player.getTrackSelectionParameters()
        .buildUpon().setTrackSelectionOverrides(overrides).build());
~~~
{: .language-java}

### Disabling track types or groups

Track types, like video, audio or text, can be disabled completely by using
`TrackSelectionParameters.Builder.setDisabledTrackTypes`. This will apply
unconditionally and will also affect other playlist items.

~~~
player.setTrackSelectionParameters(
    player.getTrackSelectionParameters()
        .buildUpon()
        .setDisabledTrackTypes(ImmutableSet.of(C.TRACK_TYPE_VIDEO))
        .build());
~~~
{: .language-java}

Alternatively, it's possible to prevent the selection of track groups for the
current playlist item only by specifying empty overrides for these groups:

~~~
TrackSelectionOverrides overrides =
    new TrackSelectionOverrides.Builder()
        .addOverride(
             new TrackSelectionOverride(
                disabledTrackGroup,
                /* select no tracks for this group */ ImmutableList.of()))
        .build();
player.setTrackSelectionParameters(
    player.getTrackSelectionParameters()
        .buildUpon().setTrackSelectionOverrides(overrides).build());
~~~
{: .language-java}

## Customizing the track selector

Track selection is the responsibility of a `TrackSelector`, an instance
of which can be provided whenever an `ExoPlayer` is built and later obtained
with `ExoPlayer.getTrackSelector()`.

~~~
DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
ExoPlayer player =
    new ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build();
~~~
{: .language-java}

`DefaultTrackSelector` is a flexible `TrackSelector` suitable for most use
cases. It uses the `TrackSelectionParameters` set in the `Player`, but also
provides some advanced customization options that can be specified in the
`DefaultTrackSelector.ParametersBuilder`:

~~~
trackSelector.setParameters(
    trackSelector
        .buildUponParameters()
        .setAllowVideoMixedMimeTypeAdaptiveness(true));
~~~
{: .language-java}

### Tunneling

Tunneled playback can be enabled in cases where the combination of renderers and
selected tracks supports it. This can be done by using
`DefaultTrackSelector.ParametersBuilder.setTunnelingEnabled(true)`.

[`TrackSelectionParameters`]: {{ site.exo_sdk }}/trackselection/TrackSelectionParameters.html

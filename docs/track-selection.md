---
title: Track selection
---

Track selection determines which of the available media tracks are played by the
player. Track selection is the responsibility of a `TrackSelector`, an instance
of which can be provided whenever an `ExoPlayer` is built.

~~~
DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
SimpleExoPlayer player =
    new SimpleExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build();
~~~
{: .language-java}

`DefaultTrackSelector` is a flexible `TrackSelector` suitable for most use
cases. When using a `DefaultTrackSelector`, it's possible to control which
tracks it selects by modifying its `Parameters`. This can be done before or
during playback. For example the following code tells the selector to restrict
video track selections to SD, and to select a German audio track if there is
one:

~~~
trackSelector.setParameters(
    trackSelector
        .buildUponParameters()
        .setMaxVideoSizeSd()
        .setPreferredAudioLanguage("deu"));
~~~
{: .language-java}

This is an example of constraint based track selection, in which constraints are
specified without knowledge of the tracks that are actually available. Many
different types of constraint can be specified using `Parameters`. `Parameters`
can also be used to select specific tracks from those that are available. See
the [`DefaultTrackSelector`][], [`Parameters`][] and [`ParametersBuilder`][]
documentation for more details.

[`Parameters`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.Parameters.html
[`ParametersBuilder`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.ParametersBuilder.html
[`DefaultTrackSelector`]: {{ site.exo_sdk }}/trackselection/DefaultTrackSelector.html

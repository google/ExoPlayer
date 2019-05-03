---
title: Customization
---

At the core of the ExoPlayer library is the `ExoPlayer` interface. An
`ExoPlayer` exposes traditional high-level media player functionality such as
the ability to buffer media, play, pause and seek. Implementations are designed
to make few assumptions about (and hence impose few restrictions on) the type of
media being played, how and where it is stored, and how it is rendered. Rather
than implementing the loading and rendering of media directly, `ExoPlayer`
implementations delegate this work to components that are injected when a player
is created or when it's prepared for playback. Components common to all
`ExoPlayer` implementations are:

* A `MediaSource` that defines the media to be played, loads the media, and from
  which the loaded media can be read. A `MediaSource` is injected via
  `ExoPlayer.prepare` at the start of playback.
* `Renderer`s that render individual components of the media. `Renderer`s are
  injected when the player is created.
* A `TrackSelector` that selects tracks provided by the `MediaSource` to be
  consumed by each of the available `Renderer`s. A `TrackSelector` is injected
  when the player is created.
* A `LoadControl` that controls when the `MediaSource` buffers more media, and
  how much media is buffered. A `LoadControl` is injected when the player is
  created.

The library provides default implementations of these components for common use
cases. An `ExoPlayer` can use of these components, but may also be built using
custom implementations if non-standard behaviors are required. Some use cases
for custom implementations are:

* `Renderer` &ndash; You may want to implement a custom `Renderer` to handle a
  media type not supported by the default implementations provided by the
  library.
* `TrackSelector` &ndash; Implementing a custom `TrackSelector` allows an app
  developer to change the way in which tracks exposed by a `MediaSource` are
  selected for consumption by each of the available `Renderer`s.
* `LoadControl` &ndash; Implementing a custom `LoadControl` allows an app
  developer to change the player's buffering policy.
* `Extractor` &ndash; If you need to support a container format not currently
  supported by the library, consider implementing a custom `Extractor` class,
  which can then be used to together with `ProgressiveMediaSource` to play media
  of that type.
* `MediaSource` &ndash; Implementing a custom `MediaSource` class may be
  appropriate if you wish to obtain media samples to feed to renderers in a
  custom way, or if you wish to implement custom `MediaSource` compositing
  behavior.
* `DataSource` &ndash; ExoPlayer’s upstream package already contains a number of
  `DataSource` implementations for different use cases. You may want to
  implement you own `DataSource` class to load data in another way, such as over
  a custom protocol, using a custom HTTP stack, or from a custom persistent
  cache.

The concept of injecting components that implement pieces of player
functionality is present throughout the library. The default implementations of
the components delegate work to further injected components. This allows many
sub-components to be individually replaced with custom implementations. For
example the default `MediaSource` implementations require one or more
`DataSource` factories to be injected via their own factories. By providing a
custom `DataSource` factory it's possible to load data from a non-standard
source or through a different network stack.

When building custom components, we recommend the following:

* If a custom component needs to report events back to the app, we recommend
  that you do so using the same model as existing ExoPlayer components, where an
  event listener is passed together with a `Handler` to the constructor of the
  component.
* We recommended that custom components use the same model as existing ExoPlayer
  components to allow reconfiguration by the app during playback, as described
  in the section below. To do this, custom components should implement
  `PlayerMessage.Target` and receive configuration changes in the
  `handleMessage` method. Application code should pass configuration changes by
  calling ExoPlayer’s `createMessage` method, configuring the message, and
  sending it to the component using `PlayerMessage.send`.

## Sending messages to components ##

It's possible to send messages to ExoPlayer components. These can be created
using `ExoPlayer.createMessage` and then sent using `PlayerMessage.send`. By
default, messages are delivered on the playback thread as soon as possible, but
this can be customized by setting another callback thread (using
`PlayerMessage.setHandler`), or by specifying a delivery playback position
(using `PlayerMessage.setPosition`). Sending messages to be delivered on the
playback thread ensures that they are executed in order with any other
operations being performed on the player.

Most of ExoPlayer's out-of-the-box renderers support messages that allow
changes to their configuration during playback. For example, the audio
renderers accept messages to set the volume and the video renderers accept
messages to set the surface. These messages should be delivered
on the playback thread to ensure thread safety.

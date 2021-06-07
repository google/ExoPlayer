---
title: Transforming media
---

The [Transformer API][] can be used to convert media streams. It takes an input
media stream, applies changes to it as configured by the app, and produces the
corresponding output file. The available transformations are:

* Transmuxing between container formats.
* Track removal.
* Flattening of slow motion videos or, in other words, their conversion into
  normal videos that retain the desired slow motion effects, but can be played
  with a player that is not aware of slow motion video formats. The purpose of
  this transformation is to make slow motion videos suitable for sharing with
  other apps or uploading to a server.

## Starting a transformation ##

To transform media, you need add the following dependency to your app’s
`build.gradle` file:

~~~
implementation 'com.google.android.exoplayer:exoplayer-transformer:2.X.X'
~~~
{: .language-gradle}

where `2.X.X` is your preferred ExoPlayer version.

You can then start a transformation by building a `Transformer` instance and
calling `startTransformation` on it. The code sample below starts a
transformation that removes the audio track from the input and sets the output
container format to WebM:

~~~
// Configure and create a Transformer instance.
Transformer transformer =
   new Transformer.Builder()
       .setContext(context)
       .setRemoveAudio(true)
       .setOutputMimeType(MimeTypes.VIDEO_WEBM)
       .setListener(transformerListener)
       .build();
// Start the transformation.
transformer.startTransformation(inputMediaItem, outputPath);
~~~
{: .language-java}

Other parameters, such as the `MediaSourceFactory`, can be passed to the
builder.

`startTransformation` receives a `MediaItem` describing the input, and a path or
a `ParcelFileDescriptor` indicating where the output should be written. The
input can be a progressive or an adaptive stream, but the output is always a
progressive stream. For adaptive inputs, the highest resolution tracks are
always selected for the transformation.

Multiple transformations can be executed sequentially with the same
`Transformer` instance, but concurrent transformations with the same instance
are not supported.

## Listening to events ##

The `startTransformation` method is asynchronous. It returns immediately and the
app is notified of events via the listener passed to the `Transformer` builder.

~~~
Transformer.Listener transformerListener =
   new Transformer.Listener() {
     @Override
     public void onTransformationCompleted(MediaItem inputMediaItem) {
       playOutput();
     }

     @Override
     public void onTransformationError(MediaItem inputMediaItem, Exception e) {
       displayError(e);
     }
   };
~~~
{: .language-java}

## Displaying progress updates ##

`Transformer.getProgress` can be called to query the current progress of a
transformation. The returned value indicates the progress state. If the progress
state is `PROGRESS_STATE_AVAILABLE` then the passed `ProgressHolder` will have
been updated with the current progress percentage. The snippet below
demonstrates how to periodically query the progress of a transformation, where
the `updateProgressInUi` method could be implemented to update a progress bar
displayed to the user.

~~~
transformer.startTransformation(inputMediaItem, outputPath);
ProgressHolder progressHolder = new ProgressHolder();
mainHandler.post(
   new Runnable() {
     @Override
     public void run() {
       @ProgressState int progressState = transformer.getProgress(progressHolder);
       updateProgressInUi(progressState, progressHolder);
       if (progressState != PROGRESS_STATE_NO_TRANSFORMATION) {
         mainHandler.postDelayed(/* r= */ this, /* delayMillis= */ 500);
       }
     }
   });
~~~
{: .language-java}

## Flattening slow motion videos ##

We define a slow motion video as a media stream whose metadata points to
sections of the stream that should be slowed during playback. Flattening is the
process of converting a slow motion video to a regular media format (for example
MP4) where the slow motion sections are played at the requested speed. The slow
motion metadata is removed, and the video and audio streams are modified so as
to produce the desired effect when the output is played with a standard player
(that is, a player that is not aware of slow motion formats).

To flatten slow motion streams, use the `setFlattenForSlowMotion` builder
method.

~~~
Transformer transformer =
   new Transformer.Builder()
       .setContext(context)
       .setFlattenForSlowMotion(true)
       .setListener(transformerListener)
       .build();
transformer.startTransformation(inputMediaItem, outputPath);
~~~
{: .language-java}

This allows apps to support slow motion videos without having to worry about
handling these special formats. All they need to do is to store and play the
flattened version of the video instead of the original one.

Currently, Samsung's slow motion format is the only one supported.

[Transformer API]: {{ site.exo_sdk }}/transformer/Transformer.html


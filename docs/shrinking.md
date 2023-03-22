---
title: APK shrinking
---

This documentation may be out-of-date. Please refer to the
[documentation for the latest ExoPlayer release][] on developer.android.com.
{:.info}

Minimizing APK size is an important aspect of developing a good Android
application. This is particularly true when targeting developing markets, and
also when developing an Android Instant App. For such cases it may be desirable
to minimize the size of the ExoPlayer library that's included in the APK. This
page outlines some simple steps that can help to achieve this.

## Use modular dependencies ##

The most convenient way to use ExoPlayer is to add a dependency to the full
library:

~~~
implementation 'com.google.android.exoplayer:exoplayer:2.X.X'
~~~
{: .language-gradle}

However this may pull in more features than your app needs. Instead, depend only
on the library modules that you actually need. For example the following will
add dependencies on the Core, DASH and UI library modules, as might be required
for an app that only plays DASH content:

~~~
implementation 'com.google.android.exoplayer:exoplayer-core:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-dash:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-ui:2.X.X'
~~~
{: .language-gradle}

## Enable code and resource shrinking ##

You should enable code and resource shrinking for your application's release
builds. ExoPlayer is structured in a way that allows code shrinking to
effectively remove unused functionality. For example, for an application that
plays DASH content, ExoPlayer's contribution to APK size can be reduced by
approximately 40% by enabling code shrinking.

Read [Shrink, obfuscate, and optimize your app][] on the Android Developer site
to learn how to enable code and resource shrinking.

## Specify which renderers your app needs ##

By default, the player's renderers will be created using
`DefaultRenderersFactory`. `DefaultRenderersFactory` depends on all of the
`Renderer` implementations provided in the ExoPlayer library, and as a result
none of them will be removed by code shrinking. If you know that your app only
needs a subset of renderers, you can specify your own `RenderersFactory`
instead. For example, an app that only plays audio can define a factory like
this when instantiating `ExoPlayer` instances:

~~~
RenderersFactory audioOnlyRenderersFactory =
    (handler, videoListener, audioListener, textOutput, metadataOutput)
        -> new Renderer[] {
            new MediaCodecAudioRenderer(
                context, MediaCodecSelector.DEFAULT, handler, audioListener)
           };
ExoPlayer player =
    new ExoPlayer.Builder(context, audioOnlyRenderersFactory).build();
~~~
{: .language-java}

This will allow other `Renderer` implementations to be removed by code
shrinking. In this particular example video, text and metadata renderers are
removed.

## Specify which extractors your app needs ##

By default, the player will create `Extractor`s to play progressive media using
`DefaultExtractorsFactory`. `DefaultExtractorsFactory` depends on all of the
`Extractor` implementations provided in the ExoPlayer library, and as a result
none of them will be removed by code shrinking. If you know that your app only
needs to play a small number of container formats, or doesn't play progressive
media at all, you can specify your own `ExtractorsFactory` instead. For example,
an app that only needs to play mp4 files can provide a factory like:

~~~
ExtractorsFactory mp4ExtractorFactory =
    () -> new Extractor[] {new Mp4Extractor()};
ExoPlayer player =
    new ExoPlayer.Builder(
           context,
           new DefaultMediaSourceFactory(context, mp4ExtractorFactory))
        .build();
~~~
{: .language-java}

This will allow other `Extractor` implementations to be removed by code
shrinking, which can result in a significant reduction in size.

If your app is not playing progressive content at all, you should pass
`ExtractorsFactory.EMPTY` to the `DefaultMediaSourceFactory` constructor, then
pass that `mediaSourceFactory` to the `ExoPlayer.Builder` constructor.

~~~
ExoPlayer player =
    new ExoPlayer.Builder(
             context,
             new DefaultMediaSourceFactory(context, ExtractorsFactory.EMPTY))
         .build();
~~~
{: .language-java}

## Custom MediaSource instantiation ##

If your app is using a custom `MediaSource.Factory` and you want
`DefaultMediaSourceFactory` to be removed by code stripping, you should pass
your `MediaSource.Factory` directly to the `ExoPlayer.Builder` constructor.

~~~
ExoPlayer player =
    new ExoPlayer.Builder(context, customMediaSourceFactory).build();
~~~
{: .language-java}

If your app is using `MediaSource`s directly instead of `MediaItem`s you should
pass `MediaSource.Factory.UNSUPPORTED` to the `ExoPlayer.Builder` constructor,
to ensure `DefaultMediaSourceFactory` and `DefaultExtractorsFactory` can be
stripped by code shrinking.

~~~
ExoPlayer player =
    new ExoPlayer.Builder(context, MediaSource.Factory.UNSUPPORTED).build();
ProgressiveMediaSource mediaSource =
    new ProgressiveMediaSource.Factory(
            dataSourceFactory, customExtractorsFactory)
        .createMediaSource(MediaItem.fromUri(uri));
~~~
{: .language-java}

[documentation for the latest ExoPlayer release]: https://developer.android.com/guide/topics/media/exoplayer/shrinking
[Shrink, obfuscate, and optimize your app]: https://developer.android.com/studio/build/shrink-code

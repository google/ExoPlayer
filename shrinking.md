---
title: APK shrinking
---

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
for an app that plays DASH content:

~~~
implementation 'com.google.android.exoplayer:exoplayer-core:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-dash:2.X.X'
implementation 'com.google.android.exoplayer:exoplayer-ui:2.X.X'
~~~
{: .language-gradle}

## Use ProGuard and shrink resources ##

Classes that are not used by your app can be removed by enabling ProGuard in
your app module's `build.gradle` file:

~~~
buildTypes {
   release {
       minifyEnabled true
       shrinkResources true
       useProguard true
       proguardFiles = [
           getDefaultProguardFile('proguard-android.txt'),
           'proguard-rules.pro'
       ]
   }
}
~~~
{: .language-gradle}

ExoPlayer is structured in a way that allows ProGuard to remove unused
functionality. For example, for an app that plays DASH content, ExoPlayer's
contribution to the APK size can be reduced by approximately 40%.

Enabling `shrinkResources` in your app module's `build.gradle` file can result
in a further reduction in size.

## Specify which extractors your app needs ##

If your app uses `ProgressiveMediaSource`, be aware that by default it will use
`DefaultExtractorsFactory`. `DefaultExtractorsFactory` depends on all of the
`Extractor` implementations provided in the ExoPlayer library, and as a result
none of them will be removed by ProGuard. If you know that your app only needs
to play a small number of container formats, you can specify your own
`ExtractorsFactory` instead. For example, an app that only needs to play mp4
files can define a factory like:

~~~
private class Mp4ExtractorsFactory implements ExtractorsFactory {
  @Override
  public Extractor[] createExtractors() {
      return new Extractor[] {new Mp4Extractor()};
  }
}
~~~
{: .language-java}

And use it when instantiating `ProgressiveMediaSource` instances, like:

~~~
new ProgressiveMediaSource.Factory(
        mediaDataSourceFactory, new Mp4ExtractorsFactory())
    .createMediaSource(uri);
~~~
{: .language-java}

This will allow other `Extractor` implementations to be removed by ProGuard,
which can result in a significant reduction in size.

## Specify which renderers your app needs ##

If your app uses `SimpleExoPlayer`, be aware that by default the player's
renderers will be created using `DefaultRenderersFactory`.
`DefaultRenderersFactory` depends on all of the `Renderer` implementations
provided in the ExoPlayer library, and as a result none of them will be removed
by ProGuard. If you know that your app only needs a subset of renderers, you can
specify your own `RenderersFactory` instead. For example, an app that only plays
audio can define a factory like:

~~~
private class AudioOnlyRenderersFactory implements RenderersFactory {

  private final Context context;

  public AudioOnlyRenderersFactory(Context context) {
    this.context = context;
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return new Renderer[] {new MediaCodecAudioRenderer(
        MediaCodecSelector.DEFAULT, eventHandler, audioRendererEventListener)};
  }

}
~~~
{: .language-java}

And use it when instantiating `SimpleExoPlayer` instances, like:

~~~
SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(
    context, new AudioOnlyRenderersFactory(context), trackSelector);
~~~
{: .language-java}

This will allow other `Renderer` implementations to be removed by ProGuard. In
this particular example video, text and metadata renderers are removed.

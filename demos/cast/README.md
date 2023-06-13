# Cast demo

This app demonstrates switching between Google Cast and local playback by using
`CastPlayer` and `ExoPlayer`.

## Building the demo app

See the [demos README](../README.md) for instructions on how to build and run
this demo.

Test your streams by adding a `MediaItem` with URI and mime type to the
`DemoUtil` and deploy the app on a real device for casting.

## Customization with `OptionsProvider`

The Cast SDK behaviour in the demo app or your own app can be customized by
providing a custom `OptionsProvider` (see
[`DefaultCastOptionsProvider`](https://github.com/androidx/media/blob/release/libraries/cast/src/main/java/androidx/media3/cast/DefaultCastOptionsProvider.java)
also).

Replace the default options provider in the `AndroidManifest.xml` with your own:

```xml
<meta-data
    android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
    android:value="com.example.cast.MyOptionsProvider"/>
```

### Using a different Cast receiver app with the Media3 cast demo sender app

The Media3 cast demo app is an implementation of an
[Android Cast *sender app*](https://developers.google.com/cast/docs/android_sender)
that uses a *default Cast receiver app* (running on the Cast device) that is
customized to support DRM protected streams
[by passing DRM configuration via `MediaInfo`](https://developers.google.com/cast/docs/android_sender/exoplayer).
Hence Widevine DRM credentials can also be populated with a
`MediaItem.DrmConfiguration.Builder` (see the samples in `DemoUtil` marked with
`Widevine`).

If you test your own streams with this demo app, keep in mind that for your
production app you need to
[choose your own receiver app](https://developers.google.com/cast/docs/web_receiver#choose_a_web_receiver)
and have your own receiver app ID.

If you have a receiver app already and want to quickly test whether it works
well together with the `CastPlayer`, then you can configure the demo app to use
your receiver:

```java
public class MyOptionsProvider implements OptionsProvider {
  @NonNull
  @Override
  public CastOptions getCastOptions(Context context) {
    return new CastOptions.Builder()
        .setReceiverApplicationId(YOUR_RECEIVER_APP_ID)
        // other options
        .build();
  }
}
```

You can also use the plain
[default Cast receiver app](https://developers.google.com/cast/docs/web_receiver#default_media_web_receiver)
by using `CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`.

#### Converting a Media3 `MediaItem` to a Cast `MediaQueueItem`

This demo app uses the
[`DefaultMediaItemConverter`](https://github.com/androidx/media/blob/release/libraries/cast/src/main/java/androidx/media3/cast/DefaultMediaItemConverter.java)
to convert a Media3 `MediaItem` to a `MediaQueueItem` of the Cast API. Apps that
use a custom receiver app, can use a custom `MediaItemConverter` instance by
passing it into the constructor of `CastPlayer`.

### Media session and notification

This Media3 cast demo app uses the media session and notification support
provided by the Cast SDK. If your app already integrates with a `MediaSession`,
the Cast session can be disabled to avoid duplicate notifications or sessions:

```java
public class MyOptionsProvider implements OptionsProvider {
  @NonNull
  @Override
  public CastOptions getCastOptions(Context context) {
    return new CastOptions.Builder()
        .setCastMediaOptions(
            new CastMediaOptions.Builder()
                .setMediaSessionEnabled(false)
                .setNotificationOptions(null)
                .build())
        // other options
        .build();
  }
}
```

## Supported media formats

Whether a specific stream is supported on a Cast device largely depends on the
receiver app, the media player used by the receiver and the Cast device, rather
then the implementation of the sender that basically only provides media URI and
metadata.

Generally, Google Cast and all Cast Web Receiver applications support the media
facilities and types listed on
[this page](https://developers.google.com/cast/docs/media). If you build a
custom receiver that uses a media player different to the media player of the
Cast receiver SDK, your app may support
[other formats or features](https://github.com/shaka-project/shaka-player) than
listed in the reference above.

The Media3 team can't give support for building a receiver app or investigations
regarding support for certain media formats on a cast devices. Please consult
the Cast documentation around
[building a receiver application](https://developers.google.com/cast/docs/web_receiver)
for further details.

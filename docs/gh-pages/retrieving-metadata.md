---
title: Retrieving metadata
---

## During playback ##

Media metadata can be retrieved during playback in multiple ways, depending on
the exact needs. Possible options are to listen to `onTracksChanged`, to listen
to `onStaticMetadataChanged` or to add a `MetadataOutput` to the player.

## Without playback ##

If playback is not needed, it is more efficient to use the
[`MetadataRetriever`][] to extract media metadata because it avoids having to
create and prepare a player.

~~~
ListenableFuture<TrackGroupArray> trackGroupsFuture =
   MetadataRetriever.retrieveMetadata(context, mediaItem);
Futures.addCallback(
   trackGroupsFuture,
   new FutureCallback<TrackGroupArray>() {
     @Override
     public void onSuccess(TrackGroupArray trackGroups) {
       handleMetadata(trackGroups);
     }

     @Override
     public void onFailure(Throwable t) {
       handleFailure(t);
     }
   },
   executor);
~~~
{: .language-java}

## Motion photos ##

It is also possible to extract the metadata of a motion photo, containing the
image and video offset and length for example. The supported formats are:

* JPEG motion photos recorded by Google Pixel and Samsung camera apps. This
  format is playable by ExoPlayer and the associated metadata can therefore be
  retrieved with a player or using the `MetadataRetriever`.
* HEIC motion photos recorded by Google Pixel and Samsung camera apps. This
  format is currently not playable by ExoPlayer and the associated metadata
  should therefore be retrieved using the `MetadataRetriever`.

For motion photos, the `TrackGroupArray` obtained with the `MetadataRetriever`
contains a `TrackGroup` with a single `Format` enclosing a
[`MotionPhotoMetadata`][] metadata entry.

~~~
for (int i = 0; i < trackGroups.length; i++) {
 TrackGroup trackGroup = trackGroups.get(i);
 Metadata metadata = trackGroup.getFormat(0).metadata;
 if (metadata != null && metadata.length() == 1) {
   Metadata.Entry metadataEntry = metadata.get(0);
   if (metadataEntry instanceof MotionPhotoMetadata) {
     MotionPhotoMetadata motionPhotoMetadata = (MotionPhotoMetadata) metadataEntry;
     handleMotionPhotoMetadata(motionPhotoMetadata);
   }
 }
}
~~~
{: .language-java}

[`MetadataRetriever`]: {{ site.exo_sdk }}/MetadataRetriever.html
[`MotionPhotoMetadata`]: {{ site.exo_sdk }}/metadata/mp4/MotionPhotoMetadata.html

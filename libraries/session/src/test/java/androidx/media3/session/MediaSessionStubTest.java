/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static androidx.media3.test.utils.TestUtil.getThrowingBundle;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.BundleListRetriever;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaSessionStub}. */
@RunWith(AndroidJUnit4.class)
public class MediaSessionStubTest {

  @Test
  public void invalidBinderArguments_doNotCrashSession() throws Exception {
    // Access session stub directly and then send invalid arguments. None of them should crash the
    // session app and this test asserts this by running through without throwing an exception.
    Context context = ApplicationProvider.getApplicationContext();
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaSession session = new MediaSession.Builder(context, player).setId("invalidArgs").build();
    IMediaSession binder = (IMediaSession) session.getToken().getBinder();

    // Call methods with caller==null and valid additional parameters.
    binder.setVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ 0);
    binder.setDeviceVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ 0);
    binder.setDeviceVolumeWithFlags(
        /* caller= */ null, /* seq= */ 0, /* volume= */ 0, /* flags= */ 0);
    binder.increaseDeviceVolume(/* caller= */ null, /* seq= */ 0);
    binder.increaseDeviceVolumeWithFlags(/* caller= */ null, /* seq= */ 0, /* flags= */ 0);
    binder.decreaseDeviceVolume(/* caller= */ null, /* seq= */ 0);
    binder.decreaseDeviceVolumeWithFlags(/* caller= */ null, /* seq= */ 0, /* flags= */ 0);
    binder.setDeviceMuted(/* caller= */ null, /* seq= */ 0, /* muted= */ false);
    binder.setDeviceMutedWithFlags(
        /* caller= */ null, /* seq= */ 0, /* muted= */ false, /* flags= */ 0);
    binder.setAudioAttributes(
        /* caller= */ null,
        /* seq= */ 0,
        /* audioAttributes= */ AudioAttributes.DEFAULT.toBundle(),
        /* handleAudioFocus= */ false);
    binder.setMediaItem(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.setMediaItemWithStartPosition(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle(),
        /* startPositionMs= */ 0);
    binder.setMediaItemWithResetPosition(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle(),
        /* resetPosition= */ false);
    binder.setMediaItems(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.setMediaItemsWithResetPosition(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())),
        /* resetPosition= */ false);
    binder.setMediaItemsWithStartIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())),
        /* startIndex= */ 0,
        /* startPositionMs= */ 0);
    binder.setPlayWhenReady(/* caller= */ null, /* seq= */ 0, /* playWhenReady= */ false);
    binder.onControllerResult(
        /* caller= */ null,
        /* seq= */ 0,
        /* controllerResult= */ new SessionResult(SessionResult.RESULT_SUCCESS).toBundle());
    binder.connect(
        /* caller= */ null,
        /* seq= */ 0,
        /* connectionRequest= */ new ConnectionRequest(
                "pkg", /* pid= */ 0, /* connectionHints= */ new Bundle())
            .toBundle());
    binder.onCustomCommand(
        /* caller= */ null,
        /* seq= */ 0,
        /* sessionCommand= */ new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
            .toBundle(),
        /* args= */ new Bundle());
    binder.setRepeatMode(
        /* caller= */ null, /* seq= */ 0, /* repeatMode= */ Player.REPEAT_MODE_OFF);
    binder.setShuffleModeEnabled(/* caller= */ null, /* seq= */ 0, /* shuffleModeEnabled= */ false);
    binder.removeMediaItem(/* caller= */ null, /* seq= */ 0, /* index= */ 0);
    binder.removeMediaItems(/* caller= */ null, /* seq= */ 0, /* fromIndex= */ 0, /* toIndex= */ 0);
    binder.clearMediaItems(/* caller= */ null, /* seq= */ 0);
    binder.moveMediaItem(
        /* caller= */ null, /* seq= */ 0, /* currentIndex= */ 0, /* newIndex= */ 0);
    binder.moveMediaItems(
        /* caller= */ null, /* seq= */ 0, /* fromIndex= */ 0, /* toIndex= */ 0, /* newIndex= */ 0);
    binder.replaceMediaItem(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.replaceMediaItems(
        /* caller= */ null,
        /* seq= */ 0,
        /* fromIndex= */ 0,
        /* toIndex= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.play(/* caller= */ null, /* seq= */ 0);
    binder.pause(/* caller= */ null, /* seq= */ 0);
    binder.prepare(/* caller= */ null, /* seq= */ 0);
    binder.setPlaybackParameters(
        /* caller= */ null,
        /* seq= */ 0,
        /* playbackParametersBundle= */ new PlaybackParameters(/* speed= */ 1f).toBundle());
    binder.setPlaybackSpeed(/* caller= */ null, /* seq= */ 0, /* speed= */ 0);
    binder.addMediaItem(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.addMediaItemWithIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ 0,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.addMediaItems(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.addMediaItemsWithIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.setPlaylistMetadata(
        /* caller= */ null,
        /* seq= */ 0,
        /* playlistMetadata= */ new MediaMetadata.Builder().build().toBundle());
    binder.stop(/* caller= */ null, /* seq= */ 0);
    binder.release(/* caller= */ null, /* seq= */ 0);
    binder.seekToDefaultPosition(/* caller= */ null, /* seq= */ 0);
    binder.seekToDefaultPositionWithMediaItemIndex(
        /* caller= */ null, /* seq= */ 0, /* mediaItemIndex= */ 0);
    binder.seekTo(/* caller= */ null, /* seq= */ 0, /* positionMs= */ 0);
    binder.seekToWithMediaItemIndex(
        /* caller= */ null, /* seq= */ 0, /* mediaItemIndex= */ 0, /* positionMs= */ 0);
    binder.seekBack(/* caller= */ null, /* seq= */ 0);
    binder.seekForward(/* caller= */ null, /* seq= */ 0);
    binder.seekToPreviousMediaItem(/* caller= */ null, /* seq= */ 0);
    binder.seekToNextMediaItem(/* caller= */ null, /* seq= */ 0);
    binder.setVideoSurface(/* caller= */ null, /* seq= */ 0, /* surface= */ null);
    binder.flushCommandQueue(/* caller= */ null);
    binder.seekToPrevious(/* caller= */ null, /* seq= */ 0);
    binder.seekToNext(/* caller= */ null, /* seq= */ 0);
    binder.setTrackSelectionParameters(
        /* caller= */ null,
        /* seq= */ 0,
        /* trackSelectionParametersBundle= */ new TrackSelectionParameters.Builder(context)
            .build()
            .toBundle());
    binder.setRatingWithMediaId(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaId= */ "",
        /* rating= */ new HeartRating().toBundle());
    binder.setRating(/* caller= */ null, /* seq= */ 0, /* rating= */ new HeartRating().toBundle());
    binder.getLibraryRoot(
        /* caller= */ null,
        /* seq= */ 0,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.getItem(/* caller= */ null, /* seq= */ 0, /* mediaId= */ "");
    binder.getChildren(
        /* caller= */ null,
        /* seq= */ 0,
        /* parentId= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.search(
        /* caller= */ null,
        /* seq= */ 0,
        /* query= */ "",
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.getSearchResult(
        /* caller= */ null,
        /* seq= */ 0,
        /* query= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.subscribe(
        /* caller= */ null,
        /* seq= */ 0,
        /* parentId= */ "",
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.unsubscribe(/* caller= */ null, /* seq= */ 0, /* parentId= */ "");

    // Call methods with non-null caller, but other non-primitive parameters set to null.
    MediaController controller =
        new MediaController.Builder(context, session.getToken()).buildAsync().get();
    IMediaController caller = controller.getBinder();
    binder.setAudioAttributes(
        caller, /* seq= */ 0, /* audioAttributes= */ null, /* handleAudioFocus= */ false);
    binder.setMediaItem(caller, /* seq= */ 0, /* mediaItemBundle= */ null);
    binder.setMediaItemWithStartPosition(
        caller, /* seq= */ 0, /* mediaItemBundle= */ null, /* startPositionMs= */ 0);
    binder.setMediaItemWithResetPosition(
        caller, /* seq= */ 0, /* mediaItemBundle= */ null, /* resetPosition= */ false);
    binder.setMediaItems(caller, /* seq= */ 0, /* mediaItems= */ null);
    binder.setMediaItemsWithResetPosition(
        caller, /* seq= */ 0, /* mediaItems= */ null, /* resetPosition= */ false);
    binder.setMediaItemsWithStartIndex(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ null,
        /* startIndex= */ 0,
        /* startPositionMs= */ 0);
    binder.onControllerResult(caller, /* seq= */ 0, /* controllerResult= */ null);
    binder.connect(caller, /* seq= */ 0, /* connectionRequest= */ null);
    binder.onCustomCommand(
        caller, /* seq= */ 0, /* sessionCommand= */ null, /* args= */ new Bundle());
    binder.onCustomCommand(
        caller,
        /* seq= */ 0,
        /* sessionCommand= */ new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
            .toBundle(),
        /* args= */ null);
    binder.replaceMediaItem(caller, /* seq= */ 0, /* index= */ 0, /* mediaItemBundle= */ null);
    binder.replaceMediaItems(
        caller, /* seq= */ 0, /* fromIndex= */ 0, /* toIndex= */ 0, /* mediaItems= */ null);
    binder.setPlaybackParameters(caller, /* seq= */ 0, /* playbackParametersBundle= */ null);
    binder.addMediaItem(caller, /* seq= */ 0, /* mediaItemBundle= */ null);
    binder.addMediaItemWithIndex(caller, /* seq= */ 0, /* index= */ 0, /* mediaItemBundle= */ null);
    binder.addMediaItems(caller, /* seq= */ 0, /* mediaItems= */ null);
    binder.addMediaItemsWithIndex(caller, /* seq= */ 0, /* index= */ 0, /* mediaItems= */ null);
    binder.setPlaylistMetadata(caller, /* seq= */ 0, /* playlistMetadata= */ null);
    binder.setTrackSelectionParameters(
        caller, /* seq= */ 0, /* trackSelectionParametersBundle= */ null);
    binder.setRatingWithMediaId(
        caller, /* seq= */ 0, /* mediaId= */ null, /* rating= */ new HeartRating().toBundle());
    binder.setRatingWithMediaId(caller, /* seq= */ 0, /* mediaId= */ "", /* rating= */ null);
    binder.setRating(caller, /* seq= */ 0, /* rating= */ null);
    binder.getLibraryRoot(caller, /* seq= */ 0, /* libraryParams= */ null);
    binder.getItem(caller, /* seq= */ 0, /* mediaId= */ null);
    binder.getChildren(
        caller,
        /* seq= */ 0,
        /* parentId= */ null,
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.getChildren(
        caller,
        /* seq= */ 0,
        /* parentId= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ null);
    binder.search(
        caller,
        /* seq= */ 0,
        /* query= */ null,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.search(caller, /* seq= */ 0, /* query= */ "", /* libraryParams= */ null);
    binder.getSearchResult(
        caller,
        /* seq= */ 0,
        /* query= */ null,
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.getSearchResult(
        caller,
        /* seq= */ 0,
        /* query= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ null);
    binder.subscribe(
        caller,
        /* seq= */ 0,
        /* parentId= */ null,
        /* libraryParams= */ new MediaLibraryService.LibraryParams.Builder().build().toBundle());
    binder.subscribe(caller, /* seq= */ 0, /* parentId= */ "", /* libraryParams= */ null);
    binder.unsubscribe(caller, /* seq= */ 0, /* parentId= */ null);

    // Call methods with non-null arguments, but invalid Bundles.
    IBinder noopBinder = new Binder() {};
    binder.setAudioAttributes(
        caller,
        /* seq= */ 0,
        /* audioAttributes= */ getThrowingBundle(),
        /* handleAudioFocus= */ false);
    binder.setMediaItem(caller, /* seq= */ 0, /* mediaItemBundle= */ getThrowingBundle());
    binder.setMediaItemWithStartPosition(
        caller, /* seq= */ 0, /* mediaItemBundle= */ getThrowingBundle(), /* startPositionMs= */ 0);
    binder.setMediaItemWithResetPosition(
        caller,
        /* seq= */ 0,
        /* mediaItemBundle= */ getThrowingBundle(),
        /* resetPosition= */ false);
    binder.setMediaItems(caller, /* seq= */ 0, /* mediaItems= */ noopBinder);
    binder.setMediaItems(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())));
    binder.setMediaItemsWithResetPosition(
        caller, /* seq= */ 0, /* mediaItems= */ noopBinder, /* resetPosition= */ false);
    binder.setMediaItemsWithResetPosition(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())),
        /* resetPosition= */ false);
    binder.setMediaItemsWithStartIndex(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ noopBinder,
        /* startIndex= */ 0,
        /* startPositionMs= */ 0);
    binder.setMediaItemsWithStartIndex(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())),
        /* startIndex= */ 0,
        /* startPositionMs= */ 0);
    binder.onControllerResult(caller, /* seq= */ 0, /* controllerResult= */ getThrowingBundle());
    binder.onCustomCommand(
        caller, /* seq= */ 0, /* sessionCommand= */ getThrowingBundle(), /* args= */ new Bundle());
    binder.replaceMediaItem(
        caller, /* seq= */ 0, /* index= */ 0, /* mediaItemBundle= */ getThrowingBundle());
    binder.replaceMediaItems(
        caller, /* seq= */ 0, /* fromIndex= */ 0, /* toIndex= */ 0, /* mediaItems= */ noopBinder);
    binder.replaceMediaItems(
        caller,
        /* seq= */ 0,
        /* fromIndex= */ 0,
        /* toIndex= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())));
    binder.setPlaybackParameters(
        caller, /* seq= */ 0, /* playbackParametersBundle= */ getThrowingBundle());
    binder.addMediaItem(caller, /* seq= */ 0, /* mediaItemBundle= */ getThrowingBundle());
    binder.addMediaItemWithIndex(
        caller, /* seq= */ 0, /* index= */ 0, /* mediaItemBundle= */ getThrowingBundle());
    binder.addMediaItems(caller, /* seq= */ 0, /* mediaItems= */ noopBinder);
    binder.addMediaItems(
        caller,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())));
    binder.addMediaItemsWithIndex(
        caller, /* seq= */ 0, /* index= */ 0, /* mediaItems= */ noopBinder);
    binder.addMediaItemsWithIndex(
        caller,
        /* seq= */ 0,
        /* index= */ 0,
        /* mediaItems= */ new BundleListRetriever(ImmutableList.of(getThrowingBundle())));
    binder.setPlaylistMetadata(caller, /* seq= */ 0, /* playlistMetadata= */ getThrowingBundle());
    binder.setTrackSelectionParameters(
        caller, /* seq= */ 0, /* trackSelectionParametersBundle= */ getThrowingBundle());
    binder.setRatingWithMediaId(
        caller, /* seq= */ 0, /* mediaId= */ "", /* rating= */ getThrowingBundle());
    binder.setRating(caller, /* seq= */ 0, /* rating= */ getThrowingBundle());
    binder.getLibraryRoot(caller, /* seq= */ 0, /* libraryParams= */ getThrowingBundle());
    binder.getChildren(
        caller,
        /* seq= */ 0,
        /* parentId= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ getThrowingBundle());
    binder.search(caller, /* seq= */ 0, /* query= */ "", /* libraryParams= */ getThrowingBundle());
    binder.getSearchResult(
        caller,
        /* seq= */ 0,
        /* query= */ "",
        /* page= */ 0,
        /* pageSize= */ 0,
        /* libraryParams= */ getThrowingBundle());
    binder.subscribe(
        caller, /* seq= */ 0, /* parentId= */ "", /* libraryParams= */ getThrowingBundle());

    // Pass invalid int or float values.
    binder.setVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ -0.00001f);
    binder.setVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ 1.00001f);
    binder.setVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ Float.NaN);
    binder.setDeviceVolume(/* caller= */ null, /* seq= */ 0, /* volume= */ -1);
    binder.setDeviceVolumeWithFlags(
        /* caller= */ null, /* seq= */ 0, /* volume= */ -1, /* flags= */ 0);
    binder.setMediaItemsWithStartIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())),
        /* startIndex= */ -1,
        /* startPositionMs= */ 0);
    binder.setRepeatMode(/* caller= */ null, /* seq= */ 0, /* repeatMode= */ -1);
    binder.setRepeatMode(/* caller= */ null, /* seq= */ 0, /* repeatMode= */ 3);
    binder.removeMediaItem(/* caller= */ null, /* seq= */ 0, /* index= */ -1);
    binder.removeMediaItems(
        /* caller= */ null, /* seq= */ 0, /* fromIndex= */ -1, /* toIndex= */ 0);
    binder.removeMediaItems(/* caller= */ null, /* seq= */ 0, /* fromIndex= */ 1, /* toIndex= */ 0);
    binder.moveMediaItem(
        /* caller= */ null, /* seq= */ 0, /* currentIndex= */ -1, /* newIndex= */ 0);
    binder.moveMediaItem(
        /* caller= */ null, /* seq= */ 0, /* currentIndex= */ 0, /* newIndex= */ -1);
    binder.moveMediaItems(
        /* caller= */ null, /* seq= */ 0, /* fromIndex= */ -1, /* toIndex= */ 0, /* newIndex= */ 0);
    binder.moveMediaItems(
        /* caller= */ null, /* seq= */ 0, /* fromIndex= */ 1, /* toIndex= */ 0, /* newIndex= */ 0);
    binder.moveMediaItems(
        /* caller= */ null, /* seq= */ 0, /* fromIndex= */ 0, /* toIndex= */ 0, /* newIndex= */ -1);
    binder.replaceMediaItem(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ -1,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.replaceMediaItems(
        /* caller= */ null,
        /* seq= */ 0,
        /* fromIndex= */ -1,
        /* toIndex= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.replaceMediaItems(
        /* caller= */ null,
        /* seq= */ 0,
        /* fromIndex= */ 1,
        /* toIndex= */ 0,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.setPlaybackSpeed(/* caller= */ null, /* seq= */ 0, /* speed= */ -0.0001f);
    binder.setPlaybackSpeed(/* caller= */ null, /* seq= */ 0, /* speed= */ Float.NaN);
    binder.addMediaItemWithIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ -1,
        /* mediaItemBundle= */ new MediaItem.Builder().build().toBundle());
    binder.addMediaItemsWithIndex(
        /* caller= */ null,
        /* seq= */ 0,
        /* index= */ -1,
        /* mediaItems= */ new BundleListRetriever(
            ImmutableList.of(new MediaItem.Builder().build().toBundle())));
    binder.seekToDefaultPositionWithMediaItemIndex(
        /* caller= */ null, /* seq= */ 0, /* mediaItemIndex= */ -1);
    binder.seekToWithMediaItemIndex(
        /* caller= */ null, /* seq= */ 0, /* mediaItemIndex= */ -1, /* positionMs= */ 0);
  }

  @Test
  public void binderConnectRequest_withInvalidController_doesNotCrashSession() throws Exception {
    // Obtain a direct reference to binder and attempt to connect with an invalid controller. This
    // should not crash the session app and this test asserts this by running through without
    // throwing an exception.
    Context context = ApplicationProvider.getApplicationContext();
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaSession session = new MediaSession.Builder(context, player).setId("connect").build();
    IMediaSession binder = (IMediaSession) session.getToken().getBinder();
    MediaController controller =
        new MediaController.Builder(context, session.getToken()).buildAsync().get();
    IMediaController caller = controller.getBinder();

    binder.connect(
        caller,
        /* seq= */ 0,
        /* connectionRequest= */ new ConnectionRequest(
                /* packageName= */ "invalid", /* pid= */ 9999, /* connectionHints= */ new Bundle())
            .toBundle());
  }
}

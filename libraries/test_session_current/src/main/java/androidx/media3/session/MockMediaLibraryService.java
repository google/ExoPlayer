/*
 * Copyright 2018 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION_ASSERT_PARAMS;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_BROADCAST;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_DELAY_MS;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_ITEM_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.GET_CHILDREN_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_BROWSABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM_WITH_METADATA;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_PLAYABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_NO_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_TAKES_TIME;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_TIME_IN_MS;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_1;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_2;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.CommonConstants;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** A mock MediaLibraryService */
public class MockMediaLibraryService extends MediaLibraryService {
  /** ID of the session that this service will create. */
  public static final String ID = "TestLibrary";

  /** Key used in connection hints to instruct the mock service to use a given library root. */
  public static final String CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT =
      "CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT";

  /**
   * Key used in connection hints to instruct the mock service to remove a {@link SessionCommand}
   * identified by its command code from the available commands in {@link
   * MediaSession.Callback#onConnect(MediaSession, ControllerInfo)}.
   */
  public static final String CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE =
      "CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE";

  private static final String TEST_IMAGE_PATH = "media/png/non-motion-photo-shortened.png";

  public static final MediaItem ROOT_ITEM =
      new MediaItem.Builder()
          .setMediaId(ROOT_ID)
          .setMediaMetadata(
              new MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
          .build();
  public static final LibraryParams ROOT_PARAMS =
      new LibraryParams.Builder().setExtras(ROOT_EXTRAS).build();

  private static final String TAG = "MockMediaLibrarySvc2";

  @GuardedBy("MockMediaLibraryService.class")
  private static boolean assertLibraryParams;

  @GuardedBy("MockMediaLibraryService.class")
  @Nullable
  private static LibraryParams expectedParams;

  @Nullable private static byte[] testArtworkData;
  private final AtomicInteger boundControllerCount;
  private final ConditionVariable allControllersUnbound;

  @Nullable MediaLibrarySession session;
  @Nullable TestHandler handler;
  @Nullable HandlerThread handlerThread;

  public MockMediaLibraryService() {
    boundControllerCount = new AtomicInteger(/* initialValue= */ 0);
    allControllersUnbound = new ConditionVariable();
    allControllersUnbound.open();
  }

  /** Returns whether at least one controller is bound to this service. */
  public boolean hasBoundController() {
    return !allControllersUnbound.isOpen();
  }

  /**
   * Blocks until all bound controllers unbind.
   *
   * @param timeoutMs The block timeout in milliseconds.
   * @throws TimeoutException If the block timed out.
   * @throws InterruptedException If the block was interrupted.
   */
  public void blockUntilAllControllersUnbind(long timeoutMs)
      throws TimeoutException, InterruptedException {
    if (!allControllersUnbound.block(timeoutMs)) {
      throw new TimeoutException();
    }
  }

  @Override
  public void onCreate() {
    TestServiceRegistry.getInstance().setServiceInstance(this);
    super.onCreate();
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    handler = new TestHandler(handlerThread.getLooper());
  }

  @Override
  public IBinder onBind(@Nullable Intent intent) {
    boundControllerCount.incrementAndGet();
    allControllersUnbound.close();
    return super.onBind(intent);
  }

  @Override
  public boolean onUnbind(Intent intent) {
    if (boundControllerCount.decrementAndGet() == 0) {
      allControllersUnbound.open();
    }
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    synchronized (MockMediaLibraryService.class) {
      assertLibraryParams = false;
      expectedParams = null;
    }
    TestServiceRegistry.getInstance().cleanUp();
    if (Util.SDK_INT >= 18) {
      handlerThread.quitSafely();
    } else {
      handlerThread.quit();
    }
  }

  @Override
  public MediaLibrarySession onGetSession(ControllerInfo controllerInfo) {
    TestServiceRegistry registry = TestServiceRegistry.getInstance();
    TestServiceRegistry.OnGetSessionHandler onGetSessionHandler = registry.getOnGetSessionHandler();
    if (onGetSessionHandler != null) {
      return (MediaLibrarySession) onGetSessionHandler.onGetSession(controllerInfo);
    }

    if (session == null) {
      MockPlayer player =
          new MockPlayer.Builder().setApplicationLooper(handlerThread.getLooper()).build();

      MediaLibrarySession.Callback callback = registry.getSessionCallback();
      session =
          new MediaLibrarySession.Builder(
                  MockMediaLibraryService.this,
                  player,
                  callback != null ? callback : new TestLibrarySessionCallback())
              .setId(ID)
              .build();
    }
    return session;
  }

  /**
   * This changes the visibility of {@link Service#attachBaseContext(Context)} to public. This is a
   * workaround for creating {@link MediaLibrarySession} without starting a service.
   */
  @Override
  public void attachBaseContext(Context base) {
    super.attachBaseContext(base);
  }

  public static void setAssertLibraryParams(@Nullable LibraryParams expectedParams) {
    synchronized (MockMediaLibraryService.class) {
      assertLibraryParams = true;
      MockMediaLibraryService.expectedParams = expectedParams;
    }
  }

  public static Bundle createNotifyChildrenChangedBundle(
      String mediaId, int itemCount, long delayMs, boolean broadcast) {
    Bundle bundle = new Bundle();
    bundle.putString(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID, mediaId);
    bundle.putInt(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, itemCount);
    bundle.putLong(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_DELAY_MS, delayMs);
    bundle.putBoolean(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_BROADCAST, broadcast);
    return bundle;
  }

  private class TestLibrarySessionCallback implements MediaLibrarySession.Callback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (!SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
        return MediaSession.ConnectionResult.reject();
      }
      MediaSession.ConnectionResult connectionResult =
          checkNotNull(MediaLibrarySession.Callback.super.onConnect(session, controller));
      SessionCommands.Builder builder = connectionResult.availableSessionCommands.buildUpon();
      builder.add(new SessionCommand(CUSTOM_ACTION, /* extras= */ Bundle.EMPTY));
      builder.add(new SessionCommand(CUSTOM_ACTION_ASSERT_PARAMS, /* extras= */ Bundle.EMPTY));
      Bundle connectionHints = controller.getConnectionHints();
      int commandCodeToRemove =
          connectionHints.getInt(CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE, /* defaultValue= */ -1);
      if (commandCodeToRemove != -1) {
        builder.remove(commandCodeToRemove);
      }
      return MediaSession.ConnectionResult.accept(
          /* availableSessionCommands= */ builder.build(),
          connectionResult.availablePlayerCommands);
    }

    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
        MediaLibrarySession session, ControllerInfo browser, @Nullable LibraryParams params) {
      assertLibraryParams(params);
      MediaItem rootItem = ROOT_ITEM;
      // Use connection hints to select the library root to test whether the legacy browser root
      // hints are propagated as connection hints.
      String customLibraryRoot =
          browser
              .getConnectionHints()
              .getString(CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT, /* defaultValue= */ null);
      if (customLibraryRoot != null) {
        rootItem =
            new MediaItem.Builder()
                .setMediaId(customLibraryRoot)
                .setMediaMetadata(
                    new MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
                .build();
      }
      if (params != null) {
        boolean browsableRootChildrenOnly =
            params.extras.getBoolean(
                EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, /* defaultValue= */ false);
        if (browsableRootChildrenOnly) {
          rootItem =
              new MediaItem.Builder()
                  .setMediaId(ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY)
                  .setMediaMetadata(
                      new MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
                  .build();
        }
      }
      return Futures.immediateFuture(LibraryResult.ofItem(rootItem, ROOT_PARAMS));
    }

    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
        MediaLibrarySession session, ControllerInfo browser, String mediaId) {
      if (mediaId.startsWith(SUBSCRIBE_PARENT_ID_1)) {
        return Futures.immediateFuture(
            LibraryResult.ofItem(createBrowsableMediaItem(mediaId), /* params= */ null));
      }
      switch (mediaId) {
        case MEDIA_ID_GET_BROWSABLE_ITEM:
        case SUBSCRIBE_PARENT_ID_2:
          return Futures.immediateFuture(
              LibraryResult.ofItem(createBrowsableMediaItem(mediaId), /* params= */ null));
        case MEDIA_ID_GET_PLAYABLE_ITEM:
          return Futures.immediateFuture(
              LibraryResult.ofItem(
                  createPlayableMediaItemWithArtworkData(mediaId), /* params= */ null));
        case MEDIA_ID_GET_ITEM_WITH_METADATA:
          return Futures.immediateFuture(
              LibraryResult.ofItem(createMediaItemWithMetadata(mediaId), /* params= */ null));
        default: // fall out
      }
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
    }

    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
        MediaLibrarySession session,
        ControllerInfo browser,
        String parentId,
        int page,
        int pageSize,
        @Nullable LibraryParams params) {
      assertLibraryParams(params);
      if (Objects.equals(parentId, PARENT_ID_NO_CHILDREN)) {
        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
      } else if (Objects.equals(parentId, PARENT_ID)
          || Objects.equals(parentId, SUBSCRIBE_PARENT_ID_2)) {
        return Futures.immediateFuture(
            LibraryResult.ofItemList(
                getPaginatedResult(GET_CHILDREN_RESULT, page, pageSize), params));
      } else if (Objects.equals(parentId, PARENT_ID_LONG_LIST)) {
        List<MediaItem> list = new ArrayList<>(LONG_LIST_COUNT);
        for (int i = 0; i < LONG_LIST_COUNT; i++) {
          list.add(createPlayableMediaItem(TestUtils.getMediaIdInFakeTimeline(i)));
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(list, params));
      } else if (Objects.equals(parentId, PARENT_ID_ERROR)) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
      } else if (Objects.equals(parentId, PARENT_ID_AUTH_EXPIRED_ERROR)) {
        Bundle bundle = new Bundle();
        Intent signInIntent = new Intent("action");
        int flags = Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        bundle.putParcelable(
            EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT,
            PendingIntent.getActivity(
                getApplicationContext(), /* requestCode= */ 0, signInIntent, flags));
        bundle.putString(
            EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT,
            PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL);
        return Futures.immediateFuture(
            LibraryResult.ofError(
                LibraryResult.RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED,
                new LibraryParams.Builder().setExtras(bundle).build()));
      }
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE, params));
    }

    @Override
    public ListenableFuture<LibraryResult<Void>> onSubscribe(
        MediaLibrarySession session,
        ControllerInfo browser,
        String parentId,
        @Nullable LibraryParams params) {
      if (params != null) {
        String mediaId = params.extras.getString(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_MEDIA_ID, null);
        long delayMs = params.extras.getLong(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_DELAY_MS, 0L);
        if (mediaId != null && delayMs > 0) {
          int itemCount =
              params.extras.getInt(
                  EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, Integer.MAX_VALUE);
          boolean broadcast =
              params.extras.getBoolean(EXTRAS_KEY_NOTIFY_CHILDREN_CHANGED_BROADCAST, false);
          // Post a delayed update as requested.
          handler.postDelayed(
              () -> {
                if (broadcast) {
                  session.notifyChildrenChanged(mediaId, itemCount, params);
                } else {
                  session.notifyChildrenChanged(browser, mediaId, itemCount, params);
                }
              },
              delayMs);
        }
      }
      return MediaLibrarySession.Callback.super.onSubscribe(session, browser, parentId, params);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public ListenableFuture<LibraryResult<Void>> onSearch(
        MediaLibrarySession session,
        ControllerInfo browser,
        String query,
        @Nullable LibraryParams params) {
      assertLibraryParams(params);
      if (SEARCH_QUERY.equals(query)) {
        MockMediaLibraryService.this.session.notifySearchResultChanged(
            browser, query, SEARCH_RESULT_COUNT, params);
      } else if (SEARCH_QUERY_LONG_LIST.equals(query)) {
        MockMediaLibraryService.this.session.notifySearchResultChanged(
            browser, query, LONG_LIST_COUNT, params);
      } else if (SEARCH_QUERY_TAKES_TIME.equals(query)) {
        // Searching takes some time. Notify after 5 seconds.
        Executors.newSingleThreadScheduledExecutor()
            .schedule(
                new Runnable() {
                  @Override
                  public void run() {
                    MockMediaLibraryService.this.session.notifySearchResultChanged(
                        browser, query, SEARCH_RESULT_COUNT, params);
                  }
                },
                SEARCH_TIME_IN_MS,
                MILLISECONDS);
      } else {
        // SEARCH_QUERY_EMPTY_RESULT and SEARCH_QUERY_ERROR will be handled here.
        MockMediaLibraryService.this.session.notifySearchResultChanged(browser, query, 0, params);
      }
      return Futures.immediateFuture(LibraryResult.ofVoid(params));
    }

    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
        MediaLibrarySession session,
        ControllerInfo browser,
        String query,
        int page,
        int pageSize,
        @Nullable LibraryParams params) {
      assertLibraryParams(params);
      if (SEARCH_QUERY.equals(query)) {
        return Futures.immediateFuture(
            LibraryResult.ofItemList(getPaginatedResult(SEARCH_RESULT, page, pageSize), params));
      } else if (SEARCH_QUERY_LONG_LIST.equals(query)) {
        List<MediaItem> list = new ArrayList<>(LONG_LIST_COUNT);
        for (int i = 0; i < LONG_LIST_COUNT; i++) {
          list.add(createPlayableMediaItem(TestUtils.getMediaIdInFakeTimeline(i)));
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(list, params));
      } else if (SEARCH_QUERY_EMPTY_RESULT.equals(query)) {
        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
      } else {
        // SEARCH_QUERY_ERROR will be handled here.
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
      }
    }

    @Override
    public ListenableFuture<SessionResult> onCustomCommand(
        MediaSession session,
        ControllerInfo controller,
        SessionCommand sessionCommand,
        Bundle args) {
      switch (sessionCommand.customAction) {
        case CUSTOM_ACTION:
          return Futures.immediateFuture(
              new SessionResult(SessionResult.RESULT_SUCCESS, CUSTOM_ACTION_EXTRAS));
        case CUSTOM_ACTION_ASSERT_PARAMS:
          @Nullable Bundle paramsBundle = args.getBundle(CUSTOM_ACTION_ASSERT_PARAMS);
          @Nullable
          LibraryParams params =
              paramsBundle == null ? null : LibraryParams.fromBundle(paramsBundle);
          setAssertLibraryParams(params);
          return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
        default: // fall out
      }
      return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE));
    }

    private void assertLibraryParams(@Nullable LibraryParams params) {
      synchronized (MockMediaLibraryService.class) {
        if (assertLibraryParams) {
          MediaTestUtils.assertLibraryParamsEquals(expectedParams, params);
        }
      }
    }
  }

  private List<MediaItem> getPaginatedResult(List<String> items, int page, int pageSize) {
    if (items == null) {
      return null;
    } else if (items.size() == 0) {
      return new ArrayList<>();
    }

    int totalItemCount = items.size();
    int fromIndex = page * pageSize;
    int toIndex = Math.min((page + 1) * pageSize, totalItemCount);

    List<String> paginatedMediaIdList = new ArrayList<>();
    try {
      // The case of (fromIndex >= totalItemCount) will throw exception below.
      paginatedMediaIdList = items.subList(fromIndex, toIndex);
    } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
      Log.d(
          TAG,
          "Result is empty for given pagination arguments: totalItemCount="
              + totalItemCount
              + ", page="
              + page
              + ", pageSize="
              + pageSize,
          e);
    }

    // Create a list of MediaItem from the list of media IDs.
    List<MediaItem> result = new ArrayList<>();
    for (int i = 0; i < paginatedMediaIdList.size(); i++) {
      result.add(createPlayableMediaItemWithArtworkData(paginatedMediaIdList.get(i)));
    }
    return result;
  }

  private MediaItem createBrowsableMediaItem(String mediaId) {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkData(getArtworkData(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  private MediaItem createPlayableMediaItemWithArtworkData(String mediaId) {
    MediaItem mediaItem = createPlayableMediaItem(mediaId);
    MediaMetadata mediaMetadataWithArtwork =
        mediaItem
            .mediaMetadata
            .buildUpon()
            .setArtworkData(getArtworkData(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build();
    return mediaItem.buildUpon().setMediaMetadata(mediaMetadataWithArtwork).build();
  }

  private static MediaItem createPlayableMediaItem(String mediaId) {
    Bundle extras = new Bundle();
    extras.putInt(EXTRAS_KEY_COMPLETION_STATUS, EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  private MediaItem createMediaItemWithMetadata(String mediaId) {
    MediaMetadata mediaMetadataWithArtwork =
        MediaTestUtils.createMediaMetadata()
            .buildUpon()
            .setArtworkData(getArtworkData(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build();
    return new MediaItem.Builder()
        .setMediaId(mediaId)
        .setRequestMetadata(
            new MediaItem.RequestMetadata.Builder()
                .setMediaUri(CommonConstants.METADATA_MEDIA_URI)
                .build())
        .setMediaMetadata(mediaMetadataWithArtwork)
        .build();
  }

  private byte[] getArtworkData() {
    if (testArtworkData != null) {
      return testArtworkData;
    }
    try {
      testArtworkData =
          TestUtils.getByteArrayForScaledBitmap(getApplicationContext(), TEST_IMAGE_PATH);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return testArtworkData;
  }
}

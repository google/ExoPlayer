/*
 * Copyright 2019 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotEmpty;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_NOT_SUPPORTED;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media3.common.Bundleable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Superclass to be extended by services hosting {@link MediaLibrarySession media library sessions}.
 *
 * <p>It enables applications to browse media content provided by an application, ask the
 * application to start playback, and control the playback.
 *
 * <p>To extend this class, declare the intent filter in your {@code AndroidManifest.xml}.
 *
 * <pre>{@code
 * <service android:name="NameOfYourService">
 *   <intent-filter>
 *     <action android:name="androidx.media3.session.MediaLibraryService">
 *   </intent-filter>
 * </service>
 * }</pre>
 *
 * <p>You may also declare {@code android.media.browse.MediaBrowserService} for compatibility with
 * {@link android.support.v4.media.MediaBrowserCompat} so that this service can handle the case.
 *
 * @see MediaSessionService
 */
public abstract class MediaLibraryService extends MediaSessionService {

  /** The action for {@link Intent} filter that must be declared by the service. */
  public static final String SERVICE_INTERFACE = "androidx.media3.session.MediaLibraryService";

  /**
   * An extended {@link MediaSession} for the {@link MediaLibraryService}. Build an instance with
   * {@link Builder} and return it from {@link MediaSessionService#onGetSession(ControllerInfo)}.
   *
   * <h2 id="BackwardCompatibility">Backward compatibility with legacy media browser APIs</h2>
   *
   * <p>A library session supports connection from both {@link MediaBrowser} and {@link
   * android.support.v4.media.MediaBrowserCompat}, but the {@link ControllerInfo} may not be
   * precise. Here are the details.
   *
   * <table>
   * <caption>Summary when controller info isn't precise</caption>
   * <tr>
   *   <th>SDK version</th>
   *   <th>{@link ControllerInfo#getPackageName()}<br>for legacy browser</th>
   *   <th>{@link ControllerInfo#getUid()}<br>for legacy browser</th></tr>
   * <tr>
   *   <td>{@code SDK_INT < 21}</td>
   *   <td>Actual package name via {@link Context#getPackageName()}</td>
   *   <td>Actual UID</td>
   * </tr>
   * <tr>
   *   <td>
   *     {@code 21 <= SDK_INT < 28}<br>
   *     for {@link Callback#onConnect onConnect}<br>
   *     and {@link Callback#onGetLibraryRoot onGetLibraryRoot}
   *   </td>
   *   <td>Actual package name via {@link Context#getPackageName()}</td>
   *   <td>Actual UID</td>
   * </tr>
   * <tr>
   *   <td>
   *     {@code 21 <= SDK_INT < 28}<br>
   *     for other {@link Callback callbacks}
   *   </td>
   *   <td>{@link RemoteUserInfo#LEGACY_CONTROLLER}</td>
   *   <td>Negative value</td>
   * </tr>
   * <tr>
   *   <td>{@code 28 <= SDK_INT}</td>
   *   <td>Actual package name via {@link Context#getPackageName()}</td>
   *   <td>Actual UID</td>
   * </tr>
   * </table>
   */
  public static final class MediaLibrarySession extends MediaSession {

    /**
     * An extended {@link MediaSession.Callback} for the {@link MediaLibrarySession}.
     *
     * <p>When you return {@link LibraryResult} with {@link MediaItem media items}, each item must
     * have valid {@link MediaItem#mediaId} and specify {@link MediaMetadata#isBrowsable} (or {@link
     * MediaMetadata#folderType}) and {@link MediaMetadata#isPlayable} in its {@link
     * MediaItem#mediaMetadata}.
     */
    public interface Callback extends MediaSession.Callback {

      @Override
      default ConnectionResult onConnect(MediaSession session, ControllerInfo controller) {
        SessionCommands sessionCommands =
            new SessionCommands.Builder().addAllLibraryCommands().addAllSessionCommands().build();
        Player.Commands playerCommands = new Player.Commands.Builder().addAllCommands().build();
        return ConnectionResult.accept(sessionCommands, playerCommands);
      }

      /**
       * Called when a {@link MediaBrowser} requests the root {@link MediaItem} by {@link
       * MediaBrowser#getLibraryRoot(LibraryParams)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>The {@link LibraryResult#params} may differ from the given {@link LibraryParams params}
       * if the session can't provide a root that matches with the {@code params}.
       *
       * <p>To allow browsing the media library, return a {@link LibraryResult} with {@link
       * LibraryResult#RESULT_SUCCESS} and a root {@link MediaItem} with a valid {@link
       * MediaItem#mediaId}. The media id is required for the browser to get the children under the
       * root.
       *
       * <p>Interoperability: If this callback is called because a legacy {@link
       * android.support.v4.media.MediaBrowserCompat} has requested a {@link
       * androidx.media.MediaBrowserServiceCompat.BrowserRoot}, then the main thread may be blocked
       * until the returned future is done. If your service may be queried by a legacy {@link
       * android.support.v4.media.MediaBrowserCompat}, you should ensure that the future completes
       * quickly to avoid blocking the main thread for a long period of time.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param params The optional parameters passed by the browser.
       * @return A pending result that will be resolved with a root media item.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT
       */
      default ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
          MediaLibrarySession session, ControllerInfo browser, @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} requests a {@link MediaItem} by {@link
       * MediaBrowser#getItem(String)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>To allow getting the item, return a {@link LibraryResult} with {@link
       * LibraryResult#RESULT_SUCCESS} and a {@link MediaItem} with a valid {@link
       * MediaItem#mediaId}.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param mediaId The non-empty media id of the requested item.
       * @return A pending result that will be resolved with a media item.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_ITEM
       */
      default ListenableFuture<LibraryResult<MediaItem>> onGetItem(
          MediaLibrarySession session, ControllerInfo browser, String mediaId) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} requests the child {@link MediaItem media items} of the
       * given parent id by {@link MediaBrowser#getChildren(String, int, int, LibraryParams)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>The {@link LibraryResult#params} should be the same as the given {@link LibraryParams
       * params}.
       *
       * <p>To allow getting the children, return a {@link LibraryResult} with {@link
       * LibraryResult#RESULT_SUCCESS} and a list of {@link MediaItem media items}. Return an empty
       * list for no children rather than using error codes.
       *
       * @param session The session for this event
       * @param browser The browser information.
       * @param parentId The non-empty parent id.
       * @param page The page number to get the paginated result starting from {@code 0}.
       * @param pageSize The page size to get the paginated result. Will be greater than {@code 0}.
       * @param params The optional parameters passed by the browser.
       * @return A pending result that will be resolved with a list of media items.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_CHILDREN
       */
      default ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
          MediaLibrarySession session,
          ControllerInfo browser,
          String parentId,
          @IntRange(from = 0) int page,
          @IntRange(from = 1) int pageSize,
          @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} subscribes to the given parent id by {@link
       * MediaBrowser#subscribe(String, LibraryParams)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>The {@link LibraryResult#params} should be the same as the given {@link LibraryParams
       * params}.
       *
       * <p>It's your responsibility to keep subscriptions and call {@link
       * MediaLibrarySession#notifyChildrenChanged(ControllerInfo, String, int, LibraryParams)} when
       * the children of the parent are changed until it's {@link #onUnsubscribe unsubscribed}.
       *
       * <p>Interoperability: This will be called by {@link
       * android.support.v4.media.MediaBrowserCompat#subscribe}, but won't be called by {@link
       * android.media.browse.MediaBrowser#subscribe}.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param parentId The non-empty parent id.
       * @param params The optional parameters passed by the browser.
       * @return A pending result that will be resolved with a result code.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_SUBSCRIBE
       */
      default ListenableFuture<LibraryResult<Void>> onSubscribe(
          MediaLibrarySession session,
          ControllerInfo browser,
          String parentId,
          @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} unsubscribes from the given parent id by {@link
       * MediaBrowser#unsubscribe(String)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>Interoperability: This will be called by {@link
       * android.support.v4.media.MediaBrowserCompat#unsubscribe}, but won't be called by {@link
       * android.media.browse.MediaBrowser#unsubscribe}.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param parentId The non-empty parent id.
       * @return A pending result that will be resolved with a result code.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_UNSUBSCRIBE
       */
      default ListenableFuture<LibraryResult<Void>> onUnsubscribe(
          MediaLibrarySession session, ControllerInfo browser, String parentId) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} requests a search with {@link
       * MediaBrowser#search(String, LibraryParams)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>The {@link LibraryResult#params} should be the same as the given {@link LibraryParams
       * params}.
       *
       * <p>Return {@link LibraryResult} with a result code for the search and notify the number of
       * search result ({@link MediaItem media items}) through {@link
       * #notifySearchResultChanged(ControllerInfo, String, int, LibraryParams)}. {@link
       * MediaBrowser} will ask the search result afterwards through {@link
       * #onGetSearchResult(MediaLibrarySession, ControllerInfo, String, int, int, LibraryParams)}.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param query The non-empty search query.
       * @param params The optional parameters passed by the browser.
       * @return A pending result that will be resolved with a result code.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_SEARCH
       */
      default ListenableFuture<LibraryResult<Void>> onSearch(
          MediaLibrarySession session,
          ControllerInfo browser,
          String query,
          @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }

      /**
       * Called when a {@link MediaBrowser} requests a search result with {@link
       * MediaBrowser#getSearchResult(String, int, int, LibraryParams)}.
       *
       * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
       * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
       * {@link Futures#immediateFuture(Object)}.
       *
       * <p>The {@link LibraryResult#params} should be the same as the given {@link LibraryParams
       * params}.
       *
       * <p>To allow getting the search result, return a {@link LibraryResult} with {@link
       * LibraryResult#RESULT_SUCCESS} and a list of {@link MediaItem media items}. Return an empty
       * list for no children rather than using error codes.
       *
       * <p>Typically, the {@code query} is requested through {@link #onSearch(MediaLibrarySession,
       * ControllerInfo, String, LibraryParams)} before, but it may not especially when {@link
       * android.support.v4.media.MediaBrowserCompat#search} is used.
       *
       * @param session The session for this event.
       * @param browser The browser information.
       * @param query The non-empty search query.
       * @param page The page number to get the paginated result starting from {@code 0}.
       * @param pageSize The page size to get the paginated result. Will be greater than {@code 0}.
       * @param params The optional parameters passed by the browser.
       * @return A pending result that will be resolved with a list of media items.
       * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT
       */
      default ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
          MediaLibrarySession session,
          ControllerInfo browser,
          String query,
          @IntRange(from = 0) int page,
          @IntRange(from = 1) int pageSize,
          @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }
    }

    /**
     * A builder for {@link MediaLibrarySession}.
     *
     * <p>Any incoming requests from the {@link MediaBrowser} will be handled on the application
     * thread of the underlying {@link Player}.
     */
    // Note: Don't override #setSessionCallback() because the callback can be set by the
    // constructor.
    public static final class Builder extends BuilderBase<MediaLibrarySession, Builder, Callback> {

      /**
       * Creates a builder for {@link MediaLibrarySession}.
       *
       * @param service The {@link MediaLibraryService} that instantiates the {@link
       *     MediaLibrarySession}.
       * @param player The underlying player to perform playback and handle transport controls.
       * @param callback The callback to handle requests from {@link MediaBrowser}.
       * @throws IllegalArgumentException if {@link Player#canAdvertiseSession()} returns false.
       */
      // Builder requires MediaLibraryService instead of Context just to ensure that the
      // builder can be only instantiated within the MediaLibraryService.
      // Ideally it's better to make it inner class of service to enforce, but it violates API
      // guideline that Builders should be the inner class of the building target.
      public Builder(MediaLibraryService service, Player player, Callback callback) {
        super(service, player, callback);
      }

      /**
       * Sets a {@link PendingIntent} to launch an {@link android.app.Activity} for the {@link
       * MediaLibrarySession}. This can be used as a quick link to an ongoing media screen.
       *
       * @param pendingIntent The pending intent.
       * @return The builder to allow chaining.
       */
      @Override
      public Builder setSessionActivity(PendingIntent pendingIntent) {
        return super.setSessionActivity(pendingIntent);
      }

      /**
       * Sets an ID of the {@link MediaLibrarySession}. If not set, an empty string will be used.
       *
       * <p>Use this if and only if your app supports multiple playback at the same time and also
       * wants to provide external apps to have finer-grained controls.
       *
       * @param id The ID. Must be unique among all {@link MediaSession sessions} per package.
       * @return The builder to allow chaining.
       */
      @Override
      public Builder setId(String id) {
        return super.setId(id);
      }

      /**
       * Sets an extra {@link Bundle} for the {@link MediaLibrarySession}. The {@link
       * MediaLibrarySession#getToken()} session token} will have the {@link
       * SessionToken#getExtras() extras}. If not set, an empty {@link Bundle} will be used.
       *
       * @param extras The extra {@link Bundle}.
       * @return The builder to allow chaining.
       */
      @Override
      public Builder setExtras(Bundle extras) {
        return super.setExtras(extras);
      }

      /**
       * Sets a {@link BitmapLoader} for the {@link MediaLibrarySession} to decode bitmaps from
       * compressed binary data or load bitmaps from {@link Uri}. If not set, a {@link
       * CacheBitmapLoader} with a {@link SimpleBitmapLoader} inside will be used.
       *
       * <p>The provided instance will likely be called repeatedly with the same request, so it
       * would be best if any provided instance does some caching. Simple caching can be added to
       * any {@link BitmapLoader} implementation by wrapping it in {@link CacheBitmapLoader} before
       * passing it to this method.
       *
       * <p>If no instance is set, a {@link CacheBitmapLoader} with a {@link SimpleBitmapLoader}
       * inside will be used.
       *
       * @param bitmapLoader The bitmap loader {@link BitmapLoader}.
       * @return The builder to allow chaining.
       */
      @UnstableApi
      @Override
      public Builder setBitmapLoader(BitmapLoader bitmapLoader) {
        return super.setBitmapLoader(bitmapLoader);
      }

      /**
       * Builds a {@link MediaLibrarySession}.
       *
       * @return A new session.
       * @throws IllegalStateException if a {@link MediaSession} with the same {@link #setId(String)
       *     ID} already exists in the package.
       */
      @Override
      public MediaLibrarySession build() {
        if (bitmapLoader == null) {
          bitmapLoader = new CacheBitmapLoader(new SimpleBitmapLoader());
        }
        return new MediaLibrarySession(
            context, id, player, sessionActivity, callback, extras, checkNotNull(bitmapLoader));
      }
    }

    /* package */ MediaLibrarySession(
        Context context,
        String id,
        Player player,
        @Nullable PendingIntent sessionActivity,
        MediaSession.Callback callback,
        Bundle tokenExtras,
        BitmapLoader bitmapLoader) {
      super(context, id, player, sessionActivity, callback, tokenExtras, bitmapLoader);
    }

    @Override
    /* package */ MediaLibrarySessionImpl createImpl(
        Context context,
        String id,
        Player player,
        @Nullable PendingIntent sessionActivity,
        MediaSession.Callback callback,
        Bundle tokenExtras,
        BitmapLoader bitmapLoader) {
      return new MediaLibrarySessionImpl(
          this,
          context,
          id,
          player,
          sessionActivity,
          (Callback) callback,
          tokenExtras,
          bitmapLoader);
    }

    @Override
    /* package */ MediaLibrarySessionImpl getImpl() {
      return (MediaLibrarySessionImpl) super.getImpl();
    }

    /**
     * Notifies a browser that is {@link Callback#onSubscribe subscribing} to the change to a
     * parent's children. If the browser isn't subscribing to the parent, nothing will happen.
     *
     * <p>This only tells the number of child {@link MediaItem media items}. {@link
     * Callback#onGetChildren} will be called by the browser afterwards to get the list of {@link
     * MediaItem media items}.
     *
     * @param browser The browser to notify.
     * @param parentId The non-empty id of the parent with changes to its children.
     * @param itemCount The number of children.
     * @param params The parameters given by {@link Callback#onSubscribe}.
     */
    public void notifyChildrenChanged(
        ControllerInfo browser,
        String parentId,
        @IntRange(from = 0) int itemCount,
        @Nullable LibraryParams params) {
      checkArgument(itemCount >= 0);
      getImpl()
          .notifyChildrenChanged(checkNotNull(browser), checkNotEmpty(parentId), itemCount, params);
    }

    /**
     * Notifies all browsers that are {@link Callback#onSubscribe subscribing} to the parent of the
     * change to its children regardless of the {@link LibraryParams params} given by {@link
     * Callback#onSubscribe}.
     *
     * @param parentId The non-empty id of the parent with changes to its children.
     * @param itemCount The number of children.
     * @param params The optional parameters.
     */
    // This is for the backward compatibility.
    public void notifyChildrenChanged(
        String parentId, @IntRange(from = 0) int itemCount, @Nullable LibraryParams params) {
      checkArgument(itemCount >= 0);
      getImpl().notifyChildrenChanged(checkNotEmpty(parentId), itemCount, params);
    }

    /**
     * Notifies a browser of a change to the {@link Callback#onSearch search} result.
     *
     * @param browser The browser to notify.
     * @param query The non-empty search query given by {@link Callback#onSearch}.
     * @param itemCount The number of items that have been found in the search.
     * @param params The parameters given by {@link Callback#onSearch}.
     */
    public void notifySearchResultChanged(
        ControllerInfo browser,
        String query,
        @IntRange(from = 0) int itemCount,
        @Nullable LibraryParams params) {
      checkArgument(itemCount >= 0);
      getImpl()
          .notifySearchResultChanged(
              checkNotNull(browser), checkNotEmpty(query), itemCount, params);
    }
  }

  /**
   * Parameters for the interaction between {@link MediaBrowser} and {@link MediaLibrarySession}.
   *
   * <p>When a {@link MediaBrowser} specifies the parameters, the {@link MediaLibrarySession} is
   * recommended to do the best effort to provide a result regarding the parameters, but it's not an
   * error even though {@link MediaLibrarySession} doesn't return the parameters since they are
   * optional.
   */
  public static final class LibraryParams implements Bundleable {

    /**
     * An extra {@link Bundle} for the private contract between {@link MediaBrowser} and {@link
     * MediaLibrarySession}.
     */
    @UnstableApi public final Bundle extras;

    /**
     * Whether the media items are recently played.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code true}, the {@link MediaLibrarySession}
     * is recommended to provide the recently played media items. If so, the implementation must
     * return the parameter with {@code true} as well. The list of media items is sorted by
     * relevance, the first being the most recent.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code false}, the {@link MediaLibrarySession}
     * doesn't have to care about the recentness of media items.
     */
    public final boolean isRecent;

    /**
     * Whether the media items can be played without an internet connection.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code true}, the {@link MediaLibrarySession}
     * is recommended to provide offline media items. If so, the implementation must return the
     * parameter with {@code true} as well.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code false}, the {@link MediaLibrarySession}
     * doesn't have to care about the offline playability of media items.
     */
    public final boolean isOffline;

    /**
     * Whether the media items are suggested.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code true}, the {@link MediaLibrarySession}
     * is recommended to provide suggested media items. If so, the implementation must return the
     * parameter with {@code true} as well. The list of media items is sorted by relevance, the
     * first being the top suggestion.
     *
     * <p>When a {@link MediaBrowser} specifies it as {@code false}, the {@link MediaLibrarySession}
     * doesn't have to care about the suggestion for media items.
     */
    public final boolean isSuggested;

    private LibraryParams(Bundle extras, boolean recent, boolean offline, boolean suggested) {
      this.extras = new Bundle(extras);
      this.isRecent = recent;
      this.isOffline = offline;
      this.isSuggested = suggested;
    }

    /** A builder for {@link LibraryParams}. */
    public static final class Builder {

      private boolean recent;
      private boolean offline;
      private boolean suggested;
      private Bundle extras;

      public Builder() {
        extras = Bundle.EMPTY;
      }

      /** Sets whether the media items are recently played. */
      @CanIgnoreReturnValue
      public Builder setRecent(boolean recent) {
        this.recent = recent;
        return this;
      }

      /** Sets whether the media items can be played without an internet connection. */
      @CanIgnoreReturnValue
      public Builder setOffline(boolean offline) {
        this.offline = offline;
        return this;
      }

      /** Sets whether the media items are suggested. */
      @CanIgnoreReturnValue
      public Builder setSuggested(boolean suggested) {
        this.suggested = suggested;
        return this;
      }

      /** Set an extra {@link Bundle}. */
      @CanIgnoreReturnValue
      @UnstableApi
      public Builder setExtras(Bundle extras) {
        this.extras = checkNotNull(extras);
        return this;
      }

      /** Builds {@link LibraryParams}. */
      public LibraryParams build() {
        return new LibraryParams(extras, recent, offline, suggested);
      }
    }

    // Bundleable implementation.

    private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(0);
    private static final String FIELD_RECENT = Util.intToStringMaxRadix(1);
    private static final String FIELD_OFFLINE = Util.intToStringMaxRadix(2);
    private static final String FIELD_SUGGESTED = Util.intToStringMaxRadix(3);

    @UnstableApi
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBundle(FIELD_EXTRAS, extras);
      bundle.putBoolean(FIELD_RECENT, isRecent);
      bundle.putBoolean(FIELD_OFFLINE, isOffline);
      bundle.putBoolean(FIELD_SUGGESTED, isSuggested);
      return bundle;
    }

    /** Object that can restore {@link LibraryParams} from a {@link Bundle}. */
    @UnstableApi public static final Creator<LibraryParams> CREATOR = LibraryParams::fromBundle;

    private static LibraryParams fromBundle(Bundle bundle) {
      @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
      boolean recent = bundle.getBoolean(FIELD_RECENT, /* defaultValue= */ false);
      boolean offline = bundle.getBoolean(FIELD_OFFLINE, /* defaultValue= */ false);
      boolean suggested = bundle.getBoolean(FIELD_SUGGESTED, /* defaultValue= */ false);
      return new LibraryParams(extras == null ? Bundle.EMPTY : extras, recent, offline, suggested);
    }
  }

  @Override
  @Nullable
  public IBinder onBind(@Nullable Intent intent) {
    if (intent == null) {
      return null;
    }
    if (MediaLibraryService.SERVICE_INTERFACE.equals(intent.getAction())) {
      return getServiceBinder();
    }
    return super.onBind(intent);
  }

  /**
   * {@inheritDoc}
   *
   * <p>It must return a {@link MediaLibrarySession} which is a subclass of {@link MediaSession}.
   */
  @Override
  @Nullable
  public abstract MediaLibrarySession onGetSession(ControllerInfo controllerInfo);
}

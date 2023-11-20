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
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_SESSION_DISCONNECTED;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Executor;
import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Browses media content offered by a {@link MediaLibraryService} in addition to the {@link
 * MediaController} functions.
 */
public final class MediaBrowser extends MediaController {

  /** A builder for {@link MediaBrowser}. */
  public static final class Builder {

    private final Context context;
    private final SessionToken token;
    private Bundle connectionHints;
    private Listener listener;
    private Looper applicationLooper;
    private @MonotonicNonNull BitmapLoader bitmapLoader;

    /**
     * Creates a builder for {@link MediaBrowser}.
     *
     * <p>The type of {@link SessionToken} for a browser would typically be a {@link
     * SessionToken#TYPE_LIBRARY_SERVICE} but it may be other types. The detailed behavior depending
     * on the type is described in {@link MediaController.Builder#Builder(Context, SessionToken)}.
     *
     * @param context The context.
     * @param token The token to connect to.
     */
    public Builder(Context context, SessionToken token) {
      this.context = checkNotNull(context);
      this.token = checkNotNull(token);
      connectionHints = Bundle.EMPTY;
      listener = new Listener() {};
      applicationLooper = Util.getCurrentOrMainLooper();
    }

    /**
     * Sets connection hints for the browser.
     *
     * <p>The hints are session-specific arguments sent to the session when connecting. The contents
     * of this bundle may affect the connection result.
     *
     * <p>The hints are only used when connecting to the {@link MediaSession}. They will be ignored
     * when connecting to {@link MediaSessionCompat}.
     *
     * @param connectionHints A bundle containing the connection hints.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setConnectionHints(Bundle connectionHints) {
      this.connectionHints = new Bundle(checkNotNull(connectionHints));
      return this;
    }

    /**
     * Sets a listener for the browser.
     *
     * @param listener The listener.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setListener(Listener listener) {
      this.listener = checkNotNull(listener);
      return this;
    }

    /**
     * Sets a {@link Looper} that must be used for all calls to the {@link Player} methods and that
     * is used to call {@link Player.Listener} methods on. The {@link Looper#myLooper()} current
     * looper} at that time this builder is created will be used if not specified. The {@link
     * Looper#getMainLooper() main looper} will be used if the current looper doesn't exist.
     *
     * @param looper The looper.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setApplicationLooper(Looper looper) {
      applicationLooper = checkNotNull(looper);
      return this;
    }

    /**
     * Sets a {@link BitmapLoader} for the {@link MediaBrowser} to decode bitmaps from compressed
     * binary data. If not set, a {@link CacheBitmapLoader} that wraps a {@link
     * DataSourceBitmapLoader} will be used.
     *
     * @param bitmapLoader The bitmap loader.
     * @return The builder to allow chaining.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setBitmapLoader(BitmapLoader bitmapLoader) {
      this.bitmapLoader = checkNotNull(bitmapLoader);
      return this;
    }

    // LINT.IfChange(build_async)
    /**
     * Builds a {@link MediaBrowser} asynchronously.
     *
     * <p>The browser instance can be obtained like the following example:
     *
     * <pre>{@code
     * MediaBrowser.Builder builder = new MediaBrowser.Builder(context, sessionToken);
     * ListenableFuture<MediaBrowser> future = builder.buildAsync();
     * future.addListener(() -> {
     *   try {
     *     MediaBrowser browser = future.get();
     *     // The session accepted the connection.
     *   } catch (ExecutionException | InterruptedException e) {
     *     if (e.getCause() instanceof SecurityException) {
     *       // The session rejected the connection.
     *     }
     *   }
     * }, ContextCompat.getMainExecutor(context));
     * }</pre>
     *
     * <p>The future must be kept by callers until the future is complete to get the controller
     * instance. Otherwise, the future might be garbage collected and the listener added by {@link
     * ListenableFuture#addListener(Runnable, Executor)} would never be called.
     *
     * @return A future of the browser instance.
     */
    public ListenableFuture<MediaBrowser> buildAsync() {
      MediaControllerHolder<MediaBrowser> holder = new MediaControllerHolder<>(applicationLooper);
      if (token.isLegacySession() && bitmapLoader == null) {
        bitmapLoader = new CacheBitmapLoader(new DataSourceBitmapLoader(context));
      }
      MediaBrowser browser =
          new MediaBrowser(
              context, token, connectionHints, listener, applicationLooper, holder, bitmapLoader);
      postOrRun(new Handler(applicationLooper), () -> holder.setController(browser));
      return holder;
    }
  }

  /**
   * A listener for events from {@link MediaLibraryService}.
   *
   * <p>The methods will be called from the application thread associated with the {@link
   * #getApplicationLooper() application looper} of the controller.
   */
  public interface Listener extends MediaController.Listener {

    /**
     * Called when there's a change in the parent's children after you've subscribed to the parent
     * with {@link #subscribe}.
     *
     * <p>This method is called when the app calls {@link
     * MediaLibraryService.MediaLibrarySession#notifyChildrenChanged} for the parent, or it is
     * called by the library immediately after calling {@link MediaBrowser#subscribe(String,
     * LibraryParams)}.
     *
     * @param browser The browser for this event.
     * @param parentId The non-empty parent id that you've specified with {@link #subscribe(String,
     *     LibraryParams)}.
     * @param itemCount The number of children, or {@link Integer#MAX_VALUE} if the number of items
     *     is unknown.
     * @param params The optional parameters from the library service. Can be differ from the {@code
     *     params} that you've specified with {@link #subscribe(String, LibraryParams)}.
     */
    default void onChildrenChanged(
        MediaBrowser browser,
        String parentId,
        @IntRange(from = 0) int itemCount,
        @Nullable LibraryParams params) {}

    /**
     * Called when there's change in the search result requested by the previous {@link
     * MediaBrowser#search(String, LibraryParams)}.
     *
     * @param browser The browser for this event.
     * @param query The non-empty search query that you've specified with {@link #search(String,
     *     LibraryParams)}.
     * @param itemCount The number of items for the search result.
     * @param params The optional parameters from the library service. Can be differ from the {@code
     *     params} that you've specified with {@link #search(String, LibraryParams)}.
     */
    default void onSearchResultChanged(
        MediaBrowser browser,
        String query,
        @IntRange(from = 0) int itemCount,
        @Nullable LibraryParams params) {}
  }

  private static final String WRONG_THREAD_ERROR_MESSAGE =
      "MediaBrowser method is called from a wrong thread."
          + " See javadoc of MediaController for details.";

  @NotOnlyInitialized private @MonotonicNonNull MediaBrowserImpl impl;

  /** Creates an instance from the {@link SessionToken}. */
  /* package */ MediaBrowser(
      Context context,
      SessionToken token,
      Bundle connectionHints,
      Listener listener,
      Looper applicationLooper,
      ConnectionCallback connectionCallback,
      @Nullable BitmapLoader bitmapLoader) {
    super(
        context,
        token,
        connectionHints,
        listener,
        applicationLooper,
        connectionCallback,
        bitmapLoader);
  }

  @Override
  /* package */ @UnderInitialization
  MediaBrowserImpl createImpl(
      @UnderInitialization MediaBrowser this,
      Context context,
      SessionToken token,
      Bundle connectionHints,
      Looper applicationLooper,
      @Nullable BitmapLoader bitmapLoader) {
    MediaBrowserImpl impl;
    if (token.isLegacySession()) {
      impl =
          new MediaBrowserImplLegacy(
              context, this, token, applicationLooper, checkNotNull(bitmapLoader));
    } else {
      impl = new MediaBrowserImplBase(context, this, token, connectionHints, applicationLooper);
    }
    this.impl = impl;
    return impl;
  }

  /**
   * Returns the library root item.
   *
   * <p>If it's successfully completed, {@link LibraryResult#value} will have the library root item.
   *
   * @param params The optional parameters for getting library root item.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<MediaItem>> getLibraryRoot(@Nullable LibraryParams params) {
    verifyApplicationThread();
    if (isConnected()) {
      return checkNotNull(impl).getLibraryRoot(params);
    }
    return createDisconnectedFuture();
  }

  /**
   * Subscribes to a parent id for changes to its children. When there's a change, {@link
   * Listener#onChildrenChanged(MediaBrowser, String, int, LibraryParams)} will be called with the
   * {@link LibraryParams}. You may call {@link #getChildren(String, int, int, LibraryParams)} to
   * get the children.
   *
   * @param parentId A non-empty parent id to subscribe to.
   * @param params Optional parameters.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<Void>> subscribe(
      String parentId, @Nullable LibraryParams params) {
    verifyApplicationThread();
    checkNotEmpty(parentId, "parentId must not be empty");
    if (isConnected()) {
      return checkNotNull(impl).subscribe(parentId, params);
    }
    return createDisconnectedFuture();
  }

  /**
   * Unsubscribes from a parent id for changes to its children, which was previously subscribed by
   * {@link #subscribe(String, LibraryParams)}.
   *
   * <p>This unsubscribes from all previous subscriptions with the parent id, regardless of the
   * {@link LibraryParams} that was previously given.
   *
   * @param parentId A non-empty parent id to unsubscribe from.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<Void>> unsubscribe(String parentId) {
    verifyApplicationThread();
    checkNotEmpty(parentId, "parentId must not be empty");
    if (isConnected()) {
      return checkNotNull(impl).unsubscribe(parentId);
    }
    return createDisconnectedFuture();
  }

  /**
   * Returns the children under a parent id.
   *
   * <p>If it's successfully completed, {@link LibraryResult#value} will have the children.
   *
   * @param parentId A non-empty parent id for getting the children.
   * @param page A page number to get the paginated result starting from {@code 0}.
   * @param pageSize A page size to get the paginated result. Should be greater than {@code 0}.
   * @param params Optional parameters.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getChildren(
      String parentId,
      @IntRange(from = 0) int page,
      @IntRange(from = 1) int pageSize,
      @Nullable LibraryParams params) {
    verifyApplicationThread();
    checkNotEmpty(parentId, "parentId must not be empty");
    checkArgument(page >= 0, "page must not be negative");
    checkArgument(pageSize >= 1, "pageSize must not be less than 1");
    if (isConnected()) {
      return checkNotNull(impl).getChildren(parentId, page, pageSize, params);
    }
    return createDisconnectedFuture();
  }

  /**
   * Returns the media item with the given media id.
   *
   * <p>If it's successfully completed, {@link LibraryResult#value} will have the media item.
   *
   * @param mediaId A non-empty media id.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<MediaItem>> getItem(String mediaId) {
    verifyApplicationThread();
    checkNotEmpty(mediaId, "mediaId must not be empty");
    if (isConnected()) {
      return checkNotNull(impl).getItem(mediaId);
    }
    return createDisconnectedFuture();
  }

  /**
   * Requests a search from the library service.
   *
   * <p>Returned {@link LibraryResult} will only tell whether the attempt to search was successful.
   * For getting the search result, listen to {@link Listener#onSearchResultChanged(MediaBrowser,
   * String, int, LibraryParams)} and call {@link #getSearchResult(String, int, int,
   * LibraryParams)}}.
   *
   * @param query A non-empty search query.
   * @param params Optional parameters.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<Void>> search(
      String query, @Nullable LibraryParams params) {
    verifyApplicationThread();
    checkNotEmpty(query, "query must not be empty");
    if (isConnected()) {
      return checkNotNull(impl).search(query, params);
    }
    return createDisconnectedFuture();
  }

  /**
   * Returns the search result from the library service.
   *
   * <p>If it's successfully completed, {@link LibraryResult#value} will have the search result.
   *
   * @param query A non-empty search query that you've specified with {@link #search(String,
   *     LibraryParams)}.
   * @param page A page number to get the paginated result starting from {@code 0}.
   * @param pageSize A page size to get the paginated result. Should be greater than {@code 0}.
   * @param params Optional parameters.
   * @return A {@link ListenableFuture} of {@link LibraryResult} that represents the pending result.
   */
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSearchResult(
      String query,
      @IntRange(from = 0) int page,
      @IntRange(from = 1) int pageSize,
      @Nullable LibraryParams params) {
    verifyApplicationThread();
    checkNotEmpty(query, "query must not be empty");
    checkArgument(page >= 0, "page must not be negative");
    checkArgument(pageSize >= 1, "pageSize must not be less than 1");
    if (isConnected()) {
      return checkNotNull(impl).getSearchResult(query, page, pageSize, params);
    }
    return createDisconnectedFuture();
  }

  private static <V> ListenableFuture<LibraryResult<V>> createDisconnectedFuture() {
    return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
  }

  private void verifyApplicationThread() {
    checkState(Looper.myLooper() == getApplicationLooper(), WRONG_THREAD_ERROR_MESSAGE);
  }

  /* package */ void notifyBrowserListener(Consumer<Listener> listenerConsumer) {
    @Nullable Listener listener = (Listener) this.listener;
    if (listener != null) {
      postOrRun(applicationHandler, () -> listenerConsumer.accept(listener));
    }
  }

  /* package */ interface MediaBrowserImpl extends MediaControllerImpl {

    ListenableFuture<LibraryResult<MediaItem>> getLibraryRoot(@Nullable LibraryParams rootHints);

    ListenableFuture<LibraryResult<Void>> subscribe(
        String parentId, @Nullable LibraryParams params);

    ListenableFuture<LibraryResult<Void>> unsubscribe(String parentId);

    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getChildren(
        String parentId, int page, int pageSize, @Nullable LibraryParams params);

    ListenableFuture<LibraryResult<MediaItem>> getItem(String mediaId);

    ListenableFuture<LibraryResult<Void>> search(String query, @Nullable LibraryParams params);

    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSearchResult(
        String query, int page, int pageSize, @Nullable LibraryParams params);
  }
}

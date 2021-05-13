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

package com.google.android.exoplayer2.session;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.session.MediaLibraryService.LibraryParams;

/**
 * Represents remote {@link MediaBrowser} the client app's MediaControllerService. Users can run
 * {@link MediaBrowser} methods remotely with this object.
 */
public class RemoteMediaBrowser extends RemoteMediaController {

  /**
   * Create a {@link MediaBrowser} in the client app. Should NOT be called main thread.
   *
   * @param waitForConnection true if the remote browser needs to wait for the connection, false
   *     otherwise.
   * @param connectionHints connection hints
   */
  public RemoteMediaBrowser(
      Context context, SessionToken token, boolean waitForConnection, Bundle connectionHints)
      throws RemoteException {
    super(context, token, connectionHints, waitForConnection);
  }

  /** {@link MediaBrowser} methods. */
  public LibraryResult getLibraryRoot(@Nullable LibraryParams params) throws RemoteException {
    Bundle result = binder.getLibraryRoot(controllerId, BundleableUtils.toNullableBundle(params));
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult subscribe(@NonNull String parentId, @Nullable LibraryParams params)
      throws RemoteException {
    Bundle result =
        binder.subscribe(controllerId, parentId, BundleableUtils.toNullableBundle(params));
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult unsubscribe(@NonNull String parentId) throws RemoteException {
    Bundle result = binder.unsubscribe(controllerId, parentId);
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult getChildren(
      @NonNull String parentId, int page, int pageSize, @Nullable LibraryParams params)
      throws RemoteException {
    Bundle result =
        binder.getChildren(
            controllerId, parentId, page, pageSize, BundleableUtils.toNullableBundle(params));
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult getItem(@NonNull String mediaId) throws RemoteException {
    Bundle result = binder.getItem(controllerId, mediaId);
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult search(@NonNull String query, @Nullable LibraryParams params)
      throws RemoteException {
    Bundle result = binder.search(controllerId, query, BundleableUtils.toNullableBundle(params));
    return LibraryResult.CREATOR.fromBundle(result);
  }

  public LibraryResult getSearchResult(
      @NonNull String query, int page, int pageSize, @Nullable LibraryParams params)
      throws RemoteException {
    Bundle result =
        binder.getSearchResult(
            controllerId, query, page, pageSize, BundleableUtils.toNullableBundle(params));
    return LibraryResult.CREATOR.fromBundle(result);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Create a {@link MediaBrowser} in the client app. Should be used after successful connection
   * through {@link #connect()}.
   *
   * @param connectionHints connection hints
   * @param waitForConnection true if this method needs to wait for the connection,
   */
  void create(SessionToken token, Bundle connectionHints, boolean waitForConnection)
      throws RemoteException {
    binder.create(
        /* isBrowser= */ true, controllerId, token.toBundle(), connectionHints, waitForConnection);
  }
}

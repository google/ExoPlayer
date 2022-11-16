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

import static androidx.media3.session.LibraryResult.RESULT_ERROR_PERMISSION_DENIED;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.LibraryResult.RESULT_INFO_SKIPPED;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SEARCH;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.SequencedFutureManager.SequencedFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/** Base implementation of MediaBrowser. */
/* package */ class MediaBrowserImplBase extends MediaControllerImplBase
    implements MediaBrowser.MediaBrowserImpl {

  private final MediaBrowser instance;

  MediaBrowserImplBase(
      Context context,
      @UnderInitialization MediaBrowser instance,
      SessionToken token,
      Bundle connectionHints,
      Looper applicationLooper) {
    super(context, instance, token, connectionHints, applicationLooper);
    this.instance = instance;
  }

  @Override
  /* package */ MediaBrowser getInstance() {
    return instance;
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getLibraryRoot(@Nullable LibraryParams params) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.getLibraryRoot(controllerStub, seq, params == null ? null : params.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> subscribe(
      String parentId, @Nullable LibraryParams params) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_SUBSCRIBE,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.subscribe(
                controllerStub, seq, parentId, params == null ? null : params.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> unsubscribe(String parentId) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.unsubscribe(controllerStub, seq, parentId);
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getChildren(
      String parentId, int page, int pageSize, @Nullable LibraryParams params) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_GET_CHILDREN,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.getChildren(
                controllerStub,
                seq,
                parentId,
                page,
                pageSize,
                params == null ? null : params.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getItem(String mediaId) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_GET_ITEM,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.getItem(controllerStub, seq, mediaId);
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> search(
      String query, @Nullable LibraryParams params) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_SEARCH,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.search(controllerStub, seq, query, params == null ? null : params.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSearchResult(
      String query, int page, int pageSize, @Nullable LibraryParams params) {
    return dispatchRemoteLibrarySessionTask(
        COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT,
        new RemoteLibrarySessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.getSearchResult(
                controllerStub,
                seq,
                query,
                page,
                pageSize,
                params == null ? null : params.toBundle());
          }
        });
  }

  void notifySearchResultChanged(
      String query, int itemCount, @Nullable LibraryParams libraryParams) {
    if (!isConnected()) {
      return;
    }
    getInstance()
        .notifyBrowserListener(
            listener ->
                listener.onSearchResultChanged(getInstance(), query, itemCount, libraryParams));
  }

  void notifyChildrenChanged(
      String parentId, int itemCount, @Nullable LibraryParams libraryParams) {
    if (!isConnected()) {
      return;
    }
    getInstance()
        .notifyBrowserListener(
            listener ->
                listener.onChildrenChanged(getInstance(), parentId, itemCount, libraryParams));
  }

  private <V> ListenableFuture<LibraryResult<V>> dispatchRemoteLibrarySessionTask(
      int commandCode, RemoteLibrarySessionTask task) {
    IMediaSession iSession = getSessionInterfaceWithSessionCommandIfAble(commandCode);
    if (iSession != null) {
      SequencedFuture<LibraryResult<V>> result =
          sequencedFutureManager.createSequencedFuture(LibraryResult.ofError(RESULT_INFO_SKIPPED));
      try {
        task.run(iSession, result.getSequenceNumber());
      } catch (RemoteException e) {
        Log.w(TAG, "Cannot connect to the service or the session is gone", e);
        sequencedFutureManager.setFutureResult(
            result.getSequenceNumber(), LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
      }
      return result;
    } else {
      // Don't create Future with SequencedFutureManager.
      // Otherwise session would receive discontinued sequence number, and it would make
      // future work item 'keeping call sequence when session execute commands' impossible.
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
  }

  /* @FunctionalInterface */
  private interface RemoteLibrarySessionTask {
    void run(IMediaSession iSession, int seq) throws RemoteException;
  }
}

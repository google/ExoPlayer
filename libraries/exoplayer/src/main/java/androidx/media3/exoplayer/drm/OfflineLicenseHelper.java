/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.drm;

import android.media.MediaDrm;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Mode;
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Helper class to download, renew and release offline licenses. */
@UnstableApi
public final class OfflineLicenseHelper {

  private static final Format FORMAT_WITH_EMPTY_DRM_INIT_DATA =
      new Format.Builder().setDrmInitData(new DrmInitData()).build();

  private final ConditionVariable drmListenerConditionVariable;
  private final DefaultDrmSessionManager drmSessionManager;
  private final HandlerThread handlerThread;
  private final Handler handler;
  private final DrmSessionEventListener.EventDispatcher eventDispatcher;

  /**
   * Instantiates a new instance which uses Widevine CDM. Call {@link #release()} when the instance
   * is no longer required.
   *
   * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
   *     their own license URL.
   * @param dataSourceFactory A factory from which to obtain {@link DataSource} instances.
   * @param eventDispatcher A {@link DrmSessionEventListener.EventDispatcher} used to distribute
   *     DRM-related events.
   * @return A new instance which uses Widevine CDM.
   */
  public static OfflineLicenseHelper newWidevineInstance(
      String defaultLicenseUrl,
      DataSource.Factory dataSourceFactory,
      DrmSessionEventListener.EventDispatcher eventDispatcher) {
    return newWidevineInstance(
        defaultLicenseUrl, /* forceDefaultLicenseUrl= */ false, dataSourceFactory, eventDispatcher);
  }

  /**
   * Instantiates a new instance which uses Widevine CDM. Call {@link #release()} when the instance
   * is no longer required.
   *
   * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
   *     their own license URL.
   * @param forceDefaultLicenseUrl Whether to use {@code defaultLicenseUrl} for key requests that
   *     include their own license URL.
   * @param dataSourceFactory A factory from which to obtain {@link DataSource} instances.
   * @param eventDispatcher A {@link DrmSessionEventListener.EventDispatcher} used to distribute
   *     DRM-related events.
   * @return A new instance which uses Widevine CDM.
   */
  public static OfflineLicenseHelper newWidevineInstance(
      String defaultLicenseUrl,
      boolean forceDefaultLicenseUrl,
      DataSource.Factory dataSourceFactory,
      DrmSessionEventListener.EventDispatcher eventDispatcher) {
    return newWidevineInstance(
        defaultLicenseUrl,
        forceDefaultLicenseUrl,
        dataSourceFactory,
        /* optionalKeyRequestParameters= */ null,
        eventDispatcher);
  }

  /**
   * Instantiates a new instance which uses Widevine CDM. Call {@link #release()} when the instance
   * is no longer required.
   *
   * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
   *     their own license URL.
   * @param forceDefaultLicenseUrl Whether to use {@code defaultLicenseUrl} for key requests that
   *     include their own license URL.
   * @param dataSourceFactory A factory from which to obtain {@link DataSource} instances.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest}. May be null.
   * @param eventDispatcher A {@link DrmSessionEventListener.EventDispatcher} used to distribute
   *     DRM-related events.
   * @return A new instance which uses Widevine CDM.
   * @see DefaultDrmSessionManager.Builder
   */
  public static OfflineLicenseHelper newWidevineInstance(
      String defaultLicenseUrl,
      boolean forceDefaultLicenseUrl,
      DataSource.Factory dataSourceFactory,
      @Nullable Map<String, String> optionalKeyRequestParameters,
      DrmSessionEventListener.EventDispatcher eventDispatcher) {
    return new OfflineLicenseHelper(
        new DefaultDrmSessionManager.Builder()
            .setKeyRequestParameters(optionalKeyRequestParameters)
            .build(
                new HttpMediaDrmCallback(
                    defaultLicenseUrl, forceDefaultLicenseUrl, dataSourceFactory)),
        eventDispatcher);
  }

  /**
   * Constructs an instance. Call {@link #release()} when the instance is no longer required.
   *
   * @param defaultDrmSessionManager The {@link DefaultDrmSessionManager} used to download licenses.
   * @param eventDispatcher A {@link DrmSessionEventListener.EventDispatcher} used to distribute
   *     DRM-related events.
   */
  public OfflineLicenseHelper(
      DefaultDrmSessionManager defaultDrmSessionManager,
      DrmSessionEventListener.EventDispatcher eventDispatcher) {
    this.drmSessionManager = defaultDrmSessionManager;
    this.eventDispatcher = eventDispatcher;
    handlerThread = new HandlerThread("ExoPlayer:OfflineLicenseHelper");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    drmListenerConditionVariable = new ConditionVariable();
    DrmSessionEventListener eventListener =
        new DrmSessionEventListener() {
          @Override
          public void onDrmKeysLoaded(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
            drmListenerConditionVariable.open();
          }

          @Override
          public void onDrmSessionManagerError(
              int windowIndex, @Nullable MediaPeriodId mediaPeriodId, Exception e) {
            drmListenerConditionVariable.open();
          }

          @Override
          public void onDrmKeysRestored(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
            drmListenerConditionVariable.open();
          }

          @Override
          public void onDrmKeysRemoved(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
            drmListenerConditionVariable.open();
          }
        };
    eventDispatcher.addEventListener(new Handler(handlerThread.getLooper()), eventListener);
  }

  /**
   * Downloads an offline license.
   *
   * @param format The {@link Format} of the content whose license is to be downloaded. Must contain
   *     a non-null {@link Format#drmInitData}.
   * @return The key set id for the downloaded license.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  public synchronized byte[] downloadLicense(Format format) throws DrmSessionException {
    Assertions.checkArgument(format.drmInitData != null);
    return acquireSessionAndGetOfflineLicenseKeySetIdOnHandlerThread(
        DefaultDrmSessionManager.MODE_DOWNLOAD, /* offlineLicenseKeySetId= */ null, format);
  }

  /**
   * Renews an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be renewed.
   * @return The renewed offline license key set id.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  public synchronized byte[] renewLicense(byte[] offlineLicenseKeySetId)
      throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    return acquireSessionAndGetOfflineLicenseKeySetIdOnHandlerThread(
        DefaultDrmSessionManager.MODE_DOWNLOAD,
        offlineLicenseKeySetId,
        FORMAT_WITH_EMPTY_DRM_INIT_DATA);
  }

  /**
   * Releases an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be released.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  public synchronized void releaseLicense(byte[] offlineLicenseKeySetId)
      throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    acquireSessionAndGetOfflineLicenseKeySetIdOnHandlerThread(
        DefaultDrmSessionManager.MODE_RELEASE,
        offlineLicenseKeySetId,
        FORMAT_WITH_EMPTY_DRM_INIT_DATA);
  }

  /**
   * Returns the remaining license and playback durations in seconds, for an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license.
   * @return The remaining license and playback durations, in seconds.
   * @throws DrmSessionException Thrown when a DRM session error occurs.
   */
  public synchronized Pair<Long, Long> getLicenseDurationRemainingSec(byte[] offlineLicenseKeySetId)
      throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    DrmSession drmSession;
    try {
      drmSession =
          acquireFirstSessionOnHandlerThread(
              DefaultDrmSessionManager.MODE_QUERY,
              offlineLicenseKeySetId,
              FORMAT_WITH_EMPTY_DRM_INIT_DATA);
    } catch (DrmSessionException e) {
      if (e.getCause() instanceof KeysExpiredException) {
        return Pair.create(0L, 0L);
      }
      throw e;
    }

    SettableFuture<Pair<Long, Long>> licenseDurationRemainingSec = SettableFuture.create();
    handler.post(
        () -> {
          try {
            licenseDurationRemainingSec.set(
                Assertions.checkNotNull(WidevineUtil.getLicenseDurationRemainingSec(drmSession)));
          } catch (Throwable e) {
            licenseDurationRemainingSec.setException(e);
          } finally {
            drmSession.release(eventDispatcher);
          }
        });
    try {
      return licenseDurationRemainingSec.get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IllegalStateException(e);
    } finally {
      releaseManagerOnHandlerThread();
    }
  }

  /** Releases the helper. Should be called when the helper is no longer required. */
  public void release() {
    handlerThread.quit();
  }

  /**
   * Returns the result of {@link DrmSession#getOfflineLicenseKeySetId()}, or throws {@link
   * NullPointerException} if it's null.
   *
   * <p>This method takes care of acquiring and releasing the {@link DrmSessionManager} and {@link
   * DrmSession} instances needed.
   */
  private byte[] acquireSessionAndGetOfflineLicenseKeySetIdOnHandlerThread(
      @Mode int licenseMode, @Nullable byte[] offlineLicenseKeySetId, Format format)
      throws DrmSessionException {
    DrmSession drmSession =
        acquireFirstSessionOnHandlerThread(licenseMode, offlineLicenseKeySetId, format);

    SettableFuture<byte @NullableType []> keySetId = SettableFuture.create();
    handler.post(
        () -> {
          try {
            keySetId.set(drmSession.getOfflineLicenseKeySetId());
          } catch (Throwable e) {
            keySetId.setException(e);
          } finally {
            drmSession.release(eventDispatcher);
          }
        });

    try {
      return Assertions.checkNotNull(keySetId.get());
    } catch (ExecutionException | InterruptedException e) {
      throw new IllegalStateException(e);
    } finally {
      releaseManagerOnHandlerThread();
    }
  }

  /**
   * Calls {@link DrmSessionManager#acquireSession(DrmSessionEventListener.EventDispatcher, Format)}
   * on {@link #handlerThread} and blocks until a callback is received via {@link
   * DrmSessionEventListener}.
   *
   * <p>If key loading failed and {@link DrmSession#getState()} returns {@link
   * DrmSession#STATE_ERROR} then this method releases the session and throws {@link
   * DrmSession#getError()}.
   *
   * <p>Callers are responsible for the following:
   *
   * <ul>
   *   <li>Ensuring the {@link
   *       DrmSessionManager#acquireSession(DrmSessionEventListener.EventDispatcher, Format)} call
   *       will trigger a callback to {@link DrmSessionEventListener} (e.g. it will load new keys).
   *       If not, this method will block forever.
   *   <li>Releasing the returned {@link DrmSession} instance (on {@link #handlerThread}).
   *   <li>Releasing {@link #drmSessionManager} if a {@link DrmSession} instance is returned (the
   *       manager will be released before an exception is thrown).
   * </ul>
   */
  private DrmSession acquireFirstSessionOnHandlerThread(
      @Mode int licenseMode, @Nullable byte[] offlineLicenseKeySetId, Format format)
      throws DrmSessionException {
    Assertions.checkNotNull(format.drmInitData);
    SettableFuture<DrmSession> drmSessionFuture = SettableFuture.create();
    drmListenerConditionVariable.close();
    handler.post(
        () -> {
          try {
            drmSessionManager.setPlayer(Assertions.checkNotNull(Looper.myLooper()), PlayerId.UNSET);
            drmSessionManager.prepare();
            try {
              drmSessionManager.setMode(licenseMode, offlineLicenseKeySetId);
              drmSessionFuture.set(
                  Assertions.checkNotNull(
                      drmSessionManager.acquireSession(eventDispatcher, format)));
            } catch (Throwable e) {
              drmSessionManager.release();
              throw e;
            }
          } catch (Throwable e) {
            drmSessionFuture.setException(e);
          }
        });

    DrmSession drmSession;
    try {
      drmSession = drmSessionFuture.get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IllegalStateException(e);
    }

    // drmListenerConditionVariable will be opened by a callback to this.eventDispatcher when key
    // loading is complete (drmSession.state == STATE_OPENED_WITH_KEYS) or has failed
    // (drmSession.state == STATE_ERROR).
    drmListenerConditionVariable.block();

    SettableFuture<@NullableType DrmSessionException> drmSessionError = SettableFuture.create();
    handler.post(
        () -> {
          try {
            DrmSessionException error = drmSession.getError();
            if (drmSession.getState() == DrmSession.STATE_ERROR) {
              drmSession.release(eventDispatcher);
              drmSessionManager.release();
            }
            drmSessionError.set(error);
          } catch (Throwable e) {
            drmSessionError.setException(e);
            drmSession.release(eventDispatcher);
            drmSessionManager.release();
          }
        });
    try {
      if (drmSessionError.get() != null) {
        throw drmSessionError.get();
      } else {
        return drmSession;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Calls {@link DrmSessionManager#release()} on {@link #handlerThread} and blocks until it's
   * complete.
   */
  private void releaseManagerOnHandlerThread() {
    SettableFuture<Void> result = SettableFuture.create();
    handler.post(
        () -> {
          try {
            drmSessionManager.release();
            result.set(null);
          } catch (Throwable e) {
            result.setException(e);
          }
        });
    try {
      result.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }
}

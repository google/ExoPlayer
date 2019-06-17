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
package com.google.android.exoplayer2.drm;

import android.os.Looper;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages a DRM session.
 */
public interface DrmSessionManager<T extends ExoMediaCrypto> {

  /** {@link DrmSessionManager} that supports no DRM schemes. */
  DrmSessionManager<ExoMediaCrypto> DUMMY =
      new DrmSessionManager<ExoMediaCrypto>() {

        @Override
        public boolean canAcquireSession(DrmInitData drmInitData) {
          return false;
        }

        @Override
        public DrmSession<ExoMediaCrypto> acquireSession(
            Looper playbackLooper, DrmInitData drmInitData) {
          return new ErrorStateDrmSession<>(
              new DrmSession.DrmSessionException(
                  new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME)));
        }
      };

  /** Flags that control the handling of DRM protected content. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_PLAY_CLEAR_SAMPLES_WITHOUT_KEYS})
  @interface Flags {}

  /**
   * When this flag is set, clear samples of an encrypted region may be rendered when no keys are
   * available.
   *
   * <p>Encrypted media may contain clear (un-encrypted) regions. For example a media file may start
   * with a short clear region so as to allow playback to begin in parallel with key acquisition.
   * When this flag is set, consumers of sample data are permitted to access the clear regions of
   * encrypted media files when the associated {@link DrmSession} has not yet obtained the keys
   * necessary for the encrypted regions of the media.
   */
  int FLAG_PLAY_CLEAR_SAMPLES_WITHOUT_KEYS = 1;

  /**
   * Returns whether the manager is capable of acquiring a session for the given
   * {@link DrmInitData}.
   *
   * @param drmInitData DRM initialization data.
   * @return Whether the manager is capable of acquiring a session for the given
   *     {@link DrmInitData}.
   */
  boolean canAcquireSession(DrmInitData drmInitData);

  /**
   * Returns a {@link DrmSession} with an acquired reference for the specified {@link DrmInitData}.
   *
   * <p>The caller must call {@link DrmSession#releaseReference} to decrement the session's
   * reference count when the session is no longer required.
   *
   * @param playbackLooper The looper associated with the media playback thread.
   * @param drmInitData DRM initialization data. All contained {@link SchemeData}s must contain
   *     non-null {@link SchemeData#data}.
   * @return The DRM session.
   */
  DrmSession<T> acquireSession(Looper playbackLooper, DrmInitData drmInitData);

  /** Returns flags that control the handling of DRM protected content. */
  @Flags
  default int getFlags() {
    return 0;
  }
}

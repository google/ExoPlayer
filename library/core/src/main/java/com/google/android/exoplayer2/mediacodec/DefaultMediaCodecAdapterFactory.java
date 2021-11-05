/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The default {@link MediaCodecAdapter.Factory}.
 *
 * <p>By default, this factory {@link #createAdapter creates} {@link AsynchronousMediaCodecAdapter}
 * instances on devices with API level &gt;= 31 (Android 12+). For devices with older API versions,
 * the default behavior is to create {@link SynchronousMediaCodecAdapter} instances. The factory
 * offers APIs to force the creation of {@link AsynchronousMediaCodecAdapter} (applicable for
 * devices with API &gt;= 23) or {@link SynchronousMediaCodecAdapter} instances.
 */
public final class DefaultMediaCodecAdapterFactory implements MediaCodecAdapter.Factory {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_DEFAULT, MODE_ENABLED, MODE_DISABLED})
  private @interface Mode {}

  private static final int MODE_DEFAULT = 0;
  private static final int MODE_ENABLED = 1;
  private static final int MODE_DISABLED = 2;

  private static final String TAG = "DefaultMediaCodecAdapterFactory";

  @Mode private int asynchronousMode;
  private boolean enableSynchronizeCodecInteractionsWithQueueing;
  private boolean enableImmediateCodecStartAfterFlush;

  public DefaultMediaCodecAdapterFactory() {
    asynchronousMode = MODE_DEFAULT;
  }

  /**
   * Forces this factory to always create {@link AsynchronousMediaCodecAdapter} instances, provided
   * the device API level is &gt;= 23. For devices with API level &lt; 23, the factory will create
   * {@link SynchronousMediaCodecAdapter SynchronousMediaCodecAdapters}.
   *
   * @return This factory, for convenience.
   */
  public DefaultMediaCodecAdapterFactory forceEnableAsynchronous() {
    asynchronousMode = MODE_ENABLED;
    return this;
  }

  /**
   * Forces the factory to always create {@link SynchronousMediaCodecAdapter} instances.
   *
   * @return This factory, for convenience.
   */
  public DefaultMediaCodecAdapterFactory forceDisableAsynchronous() {
    asynchronousMode = MODE_DISABLED;
    return this;
  }

  /**
   * Enable synchronizing codec interactions with asynchronous buffer queueing.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enabled Whether codec interactions will be synchronized with asynchronous buffer
   *     queueing.
   */
  public void experimentalSetSynchronizeCodecInteractionsWithQueueingEnabled(boolean enabled) {
    enableSynchronizeCodecInteractionsWithQueueing = enabled;
  }

  /**
   * Enable calling {@link MediaCodec#start} immediately after {@link MediaCodec#flush} on the
   * playback thread, when operating the codec in asynchronous mode. If disabled, {@link
   * MediaCodec#start} will be called by the callback thread after pending callbacks are handled.
   *
   * <p>By default, this feature is disabled.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enabled Whether {@link MediaCodec#start()} will be called on the playback thread
   *     immediately after {@link MediaCodec#flush}.
   */
  public void experimentalSetImmediateCodecStartAfterFlushEnabled(boolean enabled) {
    enableImmediateCodecStartAfterFlush = enabled;
  }

  @Override
  public MediaCodecAdapter createAdapter(MediaCodecAdapter.Configuration configuration)
      throws IOException {
    if ((asynchronousMode == MODE_ENABLED && Util.SDK_INT >= 23)
        || (asynchronousMode == MODE_DEFAULT && Util.SDK_INT >= 31)) {
      int trackType = MimeTypes.getTrackType(configuration.format.sampleMimeType);
      Log.i(
          TAG,
          "Creating an asynchronous MediaCodec adapter for track type "
              + Util.getTrackTypeString(trackType));
      AsynchronousMediaCodecAdapter.Factory factory =
          new AsynchronousMediaCodecAdapter.Factory(
              trackType,
              enableSynchronizeCodecInteractionsWithQueueing,
              enableImmediateCodecStartAfterFlush);
      return factory.createAdapter(configuration);
    }
    return new SynchronousMediaCodecAdapter.Factory().createAdapter(configuration);
  }
}

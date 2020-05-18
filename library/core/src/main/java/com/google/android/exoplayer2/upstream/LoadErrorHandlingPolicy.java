/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.upstream.Loader.Callback;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.IOException;

/**
 * Defines how errors encountered by {@link Loader Loaders} are handled.
 *
 * <p>Loader clients may blacklist a resource when a load error occurs. Blacklisting works around
 * load errors by loading an alternative resource. Clients do not try blacklisting when a resource
 * does not have an alternative. When a resource does have valid alternatives, {@link
 * #getBlacklistDurationMsFor(int, long, IOException, int)} defines whether the resource should be
 * blacklisted. Blacklisting will succeed if any of the alternatives is not in the black list.
 *
 * <p>When blacklisting does not take place, {@link #getRetryDelayMsFor(int, long, IOException,
 * int)} defines whether the load is retried. Errors whose load is not retried are propagated. Load
 * errors whose load is retried are propagated according to {@link
 * #getMinimumLoadableRetryCount(int)}.
 *
 * <p>Methods are invoked on the playback thread.
 */
public interface LoadErrorHandlingPolicy {

  /** Holds information about a load task error. */
  final class LoadErrorInfo {

    /** The {@link LoadEventInfo} associated with the load that encountered an error. */
    public final LoadEventInfo loadEventInfo;
    /** {@link MediaLoadData} associated with the load that encountered an error. */
    public final MediaLoadData mediaLoadData;
    /** The exception associated to the load error. */
    public final IOException exception;
    /** The number of errors this load task has encountered, including this one. */
    public final int errorCount;

    /** Creates an instance with the given values. */
    public LoadErrorInfo(
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException exception,
        int errorCount) {
      this.loadEventInfo = loadEventInfo;
      this.mediaLoadData = mediaLoadData;
      this.exception = exception;
      this.errorCount = errorCount;
    }
  }

  /** @deprecated Implement {@link #getBlacklistDurationMsFor(LoadErrorInfo)} instead. */
  @Deprecated
  default long getBlacklistDurationMsFor(
      int dataType, long loadDurationMs, IOException exception, int errorCount) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the number of milliseconds for which a resource associated to a provided load error
   * should be blacklisted, or {@link C#TIME_UNSET} if the resource should not be blacklisted.
   *
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return The blacklist duration in milliseconds, or {@link C#TIME_UNSET} if the resource should
   *     not be blacklisted.
   */
  @SuppressWarnings("deprecation")
  default long getBlacklistDurationMsFor(LoadErrorInfo loadErrorInfo) {
    return getBlacklistDurationMsFor(
        loadErrorInfo.mediaLoadData.dataType,
        loadErrorInfo.loadEventInfo.loadDurationMs,
        loadErrorInfo.exception,
        loadErrorInfo.errorCount);
  }

  /** @deprecated Implement {@link #getRetryDelayMsFor(LoadErrorInfo)} instead. */
  @Deprecated
  default long getRetryDelayMsFor(
      int dataType, long loadDurationMs, IOException exception, int errorCount) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the number of milliseconds to wait before attempting the load again, or {@link
   * C#TIME_UNSET} if the error is fatal and should not be retried.
   *
   * <p>{@link Loader} clients may ignore the retry delay returned by this method in order to wait
   * for a specific event before retrying. However, the load is retried if and only if this method
   * does not return {@link C#TIME_UNSET}.
   *
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return The number of milliseconds to wait before attempting the load again, or {@link
   *     C#TIME_UNSET} if the error is fatal and should not be retried.
   */
  @SuppressWarnings("deprecation")
  default long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
    return getRetryDelayMsFor(
        loadErrorInfo.mediaLoadData.dataType,
        loadErrorInfo.loadEventInfo.loadDurationMs,
        loadErrorInfo.exception,
        loadErrorInfo.errorCount);
  }

  /**
   * Called once {@code loadTaskId} will not be associated with any more load errors.
   *
   * <p>Implementations should clean up any resources associated with {@code loadTaskId} when this
   * method is called.
   */
  default void onLoadTaskConcluded(long loadTaskId) {}

  /**
   * Returns the minimum number of times to retry a load in the case of a load error, before
   * propagating the error.
   *
   * @param dataType One of the {@link C C.DATA_TYPE_*} constants indicating the type of data to
   *     load.
   * @return The minimum number of times to retry a load in the case of a load error, before
   *     propagating the error.
   * @see Loader#startLoading(Loadable, Callback, int)
   */
  int getMinimumLoadableRetryCount(int dataType);
}

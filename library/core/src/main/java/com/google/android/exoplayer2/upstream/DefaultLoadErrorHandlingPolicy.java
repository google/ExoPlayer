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

import static java.lang.Math.min;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.HttpDataSource.CleartextNotPermittedException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Default implementation of {@link LoadErrorHandlingPolicy}. */
public class DefaultLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {

  /** The default minimum number of times to retry loading data prior to propagating the error. */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
  /**
   * The default minimum number of times to retry loading prior to failing for progressive live
   * streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE = 6;
  /** The default duration for which a track is excluded in milliseconds. */
  public static final long DEFAULT_TRACK_EXCLUSION_MS = 60_000;
  /** @deprecated Use {@link #DEFAULT_TRACK_EXCLUSION_MS} instead. */
  @Deprecated public static final long DEFAULT_TRACK_BLACKLIST_MS = DEFAULT_TRACK_EXCLUSION_MS;
  /** The default duration for which a location is excluded in milliseconds. */
  public static final long DEFAULT_LOCATION_EXCLUSION_MS = 5 * 60_000;

  private static final int DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT = -1;

  private final int minimumLoadableRetryCount;
  private final boolean locationExclusionEnabled;

  /**
   * Creates an instance with default behavior.
   *
   * <p>{@link #getMinimumLoadableRetryCount} will return {@link
   * #DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE} for {@code dataType} {@link
   * C#DATA_TYPE_MEDIA_PROGRESSIVE_LIVE}. For other {@code dataType} values, it will return {@link
   * #DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
   *
   * <p>Exclusion of both fallback types {@link #FALLBACK_TYPE_TRACK} and {@link
   * #FALLBACK_TYPE_TRACK} is enabled by default.
   */
  public DefaultLoadErrorHandlingPolicy() {
    this(DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT, /* locationExclusionEnabled= */ true);
  }

  /** @deprecated Use {@link #DefaultLoadErrorHandlingPolicy(int, boolean)} instead. */
  @Deprecated
  public DefaultLoadErrorHandlingPolicy(int minimumLoadableRetryCount) {
    this(minimumLoadableRetryCount, /* locationExclusionEnabled= */ true);
  }

  /**
   * Creates an instance with the given value for {@link #getMinimumLoadableRetryCount(int)}.
   *
   * @param minimumLoadableRetryCount See {@link #getMinimumLoadableRetryCount}.
   * @param locationExclusionEnabled Whether location exclusion is enabled.
   */
  public DefaultLoadErrorHandlingPolicy(
      int minimumLoadableRetryCount, boolean locationExclusionEnabled) {
    this.minimumLoadableRetryCount = minimumLoadableRetryCount;
    this.locationExclusionEnabled = locationExclusionEnabled;
  }

  /**
   * Returns the fallback selection.
   *
   * <p>The exclusion duration is given by {@link #DEFAULT_TRACK_EXCLUSION_MS} or {@link
   * #DEFAULT_LOCATION_EXCLUSION_MS}, if the load error was an {@link InvalidResponseCodeException}
   * with an HTTP response code indicating an unrecoverable error, or {@link C#TIME_UNSET}
   * otherwise.
   *
   * <p>If alternative locations are advertised by the {@link
   * LoadErrorHandlingPolicy.FallbackOptions}, {@link #FALLBACK_TYPE_LOCATION} is selected until all
   * locations are excluded, {@link #FALLBACK_TYPE_TRACK} otherwise.
   */
  @Override
  public FallbackSelection getFallbackSelectionFor(
      FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
    @FallbackType int fallbackType = FALLBACK_TYPE_TRACK;
    boolean fallbackAvailable =
        fallbackOptions.numberOfTracks - fallbackOptions.numberOfExcludedTracks > 1;
    if (locationExclusionEnabled
        && fallbackOptions.numberOfLocations - fallbackOptions.numberOfExcludedLocations > 1) {
      fallbackType = FALLBACK_TYPE_LOCATION;
      fallbackAvailable = true;
    }
    long exclusionDurationMs = C.TIME_UNSET;
    IOException exception = loadErrorInfo.exception;
    if (fallbackAvailable && exception instanceof InvalidResponseCodeException) {
      int responseCode = ((InvalidResponseCodeException) exception).responseCode;
      exclusionDurationMs =
          responseCode == 403 // HTTP 403 Forbidden.
                  || responseCode == 404 // HTTP 404 Not Found.
                  || responseCode == 410 // HTTP 410 Gone.
                  || responseCode == 416 // HTTP 416 Range Not Satisfiable.
                  || responseCode == 500 // HTTP 500 Internal Server Error.
                  || responseCode == 503 // HTTP 503 Service Unavailable.
              ? (fallbackType == FALLBACK_TYPE_TRACK
                  ? DEFAULT_TRACK_EXCLUSION_MS
                  : DEFAULT_LOCATION_EXCLUSION_MS)
              : C.TIME_UNSET;
    }
    return new FallbackSelection(fallbackType, exclusionDurationMs);
  }

  /**
   * Retries for any exception that is not a subclass of {@link ParserException}, {@link
   * FileNotFoundException}, {@link CleartextNotPermittedException} or {@link
   * UnexpectedLoaderException}. The retry delay is calculated as {@code Math.min((errorCount - 1) *
   * 1000, 5000)}.
   */
  @Override
  public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
    IOException exception = loadErrorInfo.exception;
    return exception instanceof ParserException
            || exception instanceof FileNotFoundException
            || exception instanceof CleartextNotPermittedException
            || exception instanceof UnexpectedLoaderException
        ? C.TIME_UNSET
        : min((loadErrorInfo.errorCount - 1) * 1000, 5000);
  }

  /**
   * See {@link #DefaultLoadErrorHandlingPolicy()} and {@link #DefaultLoadErrorHandlingPolicy(int)}
   * for documentation about the behavior of this method.
   */
  @Override
  public int getMinimumLoadableRetryCount(int dataType) {
    if (minimumLoadableRetryCount == DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT) {
      return dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE
          ? DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE
          : DEFAULT_MIN_LOADABLE_RETRY_COUNT;
    } else {
      return minimumLoadableRetryCount;
    }
  }
}

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

import static com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy.DEFAULT_LOCATION_EXCLUSION_MS;
import static com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy.DEFAULT_TRACK_EXCLUSION_MS;
import static com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION;
import static com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultLoadErrorHandlingPolicy}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultLoadErrorHandlingPolicyTest {

  private static final LoadEventInfo PLACEHOLDER_LOAD_EVENT_INFO =
      new LoadEventInfo(
          LoadEventInfo.getNewId(),
          new DataSpec(Uri.EMPTY),
          Uri.EMPTY,
          /* responseHeaders= */ Collections.emptyMap(),
          /* elapsedRealtimeMs= */ 5000,
          /* loadDurationMs= */ 1000,
          /* bytesLoaded= */ 0);
  private static final MediaLoadData PLACEHOLDER_MEDIA_LOAD_DATA =
      new MediaLoadData(/* dataType= */ C.DATA_TYPE_UNKNOWN);

  @Test
  public void getFallbackSelectionFor_responseCode403() {
    InvalidResponseCodeException exception = buildInvalidResponseCodeException(403, "Forbidden");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_TRACK);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_TRACK_EXCLUSION_MS);

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_LOCATION);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_LOCATION_EXCLUSION_MS);
  }

  @Test
  public void getFallbackSelectionFor_responseCode404() {
    InvalidResponseCodeException exception = buildInvalidResponseCodeException(404, "Not found");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_TRACK);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_TRACK_EXCLUSION_MS);

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_LOCATION);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_LOCATION_EXCLUSION_MS);
  }

  @Test
  public void getFallbackSelectionFor_responseCode410() {
    InvalidResponseCodeException exception = buildInvalidResponseCodeException(410, "Gone");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_TRACK);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_TRACK_EXCLUSION_MS);

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_LOCATION);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_LOCATION_EXCLUSION_MS);
  }

  @Test
  public void getFallbackSelectionFor_responseCode500() {
    InvalidResponseCodeException exception =
        buildInvalidResponseCodeException(500, "Internal server error");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_TRACK);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_TRACK_EXCLUSION_MS);

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_LOCATION);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_LOCATION_EXCLUSION_MS);
  }

  @Test
  public void getFallbackSelectionFor_responseCode503() {
    InvalidResponseCodeException exception =
        buildInvalidResponseCodeException(503, "Service unavailable");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_TRACK);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_TRACK_EXCLUSION_MS);

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection.type).isEqualTo(FALLBACK_TYPE_LOCATION);
    assertThat(defaultPolicyFallbackSelection.exclusionDurationMs)
        .isEqualTo(DEFAULT_LOCATION_EXCLUSION_MS);
  }

  @Test
  public void getFallbackSelectionFor_dontExcludeUnexpectedHttpCodes() {
    InvalidResponseCodeException exception = buildInvalidResponseCodeException(418, "I'm a teapot");

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection).isNull();

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection).isNull();
  }

  @Test
  public void getFallbackSelectionFor_dontExcludeUnexpectedExceptions() {
    IOException exception = new IOException();

    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 1,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 10,
            /* numberOfExcludedTracks= */ 0);

    assertThat(defaultPolicyFallbackSelection).isNull();

    defaultPolicyFallbackSelection =
        getDefaultPolicyFallbackSelection(
            exception,
            /* numberOfLocations= */ 2,
            /* numberOfExcludedLocations= */ 0,
            /* numberOfTracks= */ 4,
            /* numberOfExcludedTracks= */ 1);
    assertThat(defaultPolicyFallbackSelection).isNull();
  }

  @Test
  public void getRetryDelayMsFor_dontRetryParserException() {
    assertThat(
            getDefaultPolicyRetryDelayOutputFor(
                ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null),
                1))
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getRetryDelayMsFor_successiveRetryDelays() {
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 3)).isEqualTo(2000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 5)).isEqualTo(4000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 9)).isEqualTo(5000);
  }

  @Nullable
  private static LoadErrorHandlingPolicy.FallbackSelection getDefaultPolicyFallbackSelection(
      IOException exception,
      int numberOfLocations,
      int numberOfExcludedLocations,
      int numberOfTracks,
      int numberOfExcludedTracks) {
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(
            PLACEHOLDER_LOAD_EVENT_INFO,
            PLACEHOLDER_MEDIA_LOAD_DATA,
            exception,
            /* errorCount= */ 1);
    LoadErrorHandlingPolicy.FallbackOptions fallbackOptions =
        new LoadErrorHandlingPolicy.FallbackOptions(
            numberOfLocations, numberOfExcludedLocations, numberOfTracks, numberOfExcludedTracks);
    return new DefaultLoadErrorHandlingPolicy()
        .getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
  }

  private static long getDefaultPolicyRetryDelayOutputFor(IOException exception, int errorCount) {
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(
            PLACEHOLDER_LOAD_EVENT_INFO, PLACEHOLDER_MEDIA_LOAD_DATA, exception, errorCount);
    return new DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(loadErrorInfo);
  }

  private static InvalidResponseCodeException buildInvalidResponseCodeException(
      int statusCode, String message) {
    return new InvalidResponseCodeException(
        statusCode,
        message,
        /* cause= */ null,
        Collections.emptyMap(),
        new DataSpec(Uri.EMPTY),
        /* responseBody= */ Util.EMPTY_BYTE_ARRAY);
  }
}

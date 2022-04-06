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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.os.Build;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps.mp4";
  public static final String MP4_ASSET_SEF_URI_STRING =
      "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final String MP4_REMOTE_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
  /** Test clip transcoded from {@link #MP4_REMOTE_10_SECONDS_URI_STRING} with H264 and MP3. */
  public static final String MP4_REMOTE_H264_MP3_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/%20android-screens-10s-h264-mp3.mp4";

  public static final String MP4_REMOTE_4K60_PORTRAIT_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_4k60.mp4";

  /**
   * Log in logcat and in an analysis file that this test was skipped.
   *
   * <p>Analysis file is a JSON summarising the test, saved to the application cache.
   *
   * <p>The analysis json will contain a {@code skipReason} key, with the reason for skipping the
   * test case.
   */
  public static void recordTestSkipped(Context context, String testId, String reason)
      throws JSONException, IOException {
    Log.i(testId, reason);
    JSONObject testJson = new JSONObject();
    testJson.put("skipReason", reason);

    writeTestSummaryToFile(context, testId, testJson);
  }

  /**
   * A {@link Codec.EncoderFactory} that forces encoding, wrapping {@link DefaultEncoderFactory}.
   */
  public static final Codec.EncoderFactory FORCE_ENCODE_ENCODER_FACTORY =
      new Codec.EncoderFactory() {
        @Override
        public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
            throws TransformationException {
          return Codec.EncoderFactory.DEFAULT.createForAudioEncoding(format, allowedMimeTypes);
        }

        @Override
        public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
            throws TransformationException {
          return Codec.EncoderFactory.DEFAULT.createForVideoEncoding(format, allowedMimeTypes);
        }

        @Override
        public boolean audioNeedsEncoding() {
          return true;
        }

        @Override
        public boolean videoNeedsEncoding() {
          return true;
        }
      };

  /**
   * Returns a {@link JSONObject} containing device specific details from {@link Build}, including
   * manufacturer, model, SDK version and build fingerprint.
   */
  public static JSONObject getDeviceDetailsAsJsonObject() throws JSONException {
    return new JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("sdkVersion", Build.VERSION.SDK_INT)
        .put("fingerprint", Build.FINGERPRINT);
  }

  /**
   * Converts an exception to a {@link JSONObject}.
   *
   * <p>If the exception is a {@link TransformationException}, {@code errorCode} is included.
   */
  public static JSONObject exceptionAsJsonObject(Exception exception) throws JSONException {
    JSONObject exceptionJson = new JSONObject();
    exceptionJson.put("message", exception.getMessage());
    exceptionJson.put("type", exception.getClass());
    if (exception instanceof TransformationException) {
      exceptionJson.put("errorCode", ((TransformationException) exception).errorCode);
    }
    exceptionJson.put("stackTrace", Log.getThrowableString(exception));
    return exceptionJson;
  }

  /**
   * Writes the summary of a test run to the application cache file.
   *
   * <p>The cache filename follows the pattern {@code <testId>-result.txt}.
   *
   * @param context The {@link Context}.
   * @param testId A unique identifier for the transformer test run.
   * @param testJson A {@link JSONObject} containing a summary of the test run.
   */
  public static void writeTestSummaryToFile(Context context, String testId, JSONObject testJson)
      throws IOException, JSONException {
    testJson.put("testId", testId).put("device", getDeviceDetailsAsJsonObject());

    String analysisContents = testJson.toString(/* indentSpaces= */ 2);

    // Log contents as well as writing to file, for easier visibility on individual device testing.
    for (String line : Util.split(analysisContents, "\n")) {
      Log.i(testId, line);
    }

    File analysisFile = createExternalCacheFile(context, /* fileName= */ testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      fileWriter.write(analysisContents);
    }
  }

  /**
   * Creates a {@link File} of the {@code fileName} in the application cache directory.
   *
   * <p>If a file of that name already exists, it is overwritten.
   */
  /* package */ static File createExternalCacheFile(Context context, String fileName)
      throws IOException {
    File file = new File(context.getExternalCacheDir(), fileName);
    checkState(!file.exists() || file.delete(), "Could not delete file: " + file.getAbsolutePath());
    checkState(file.createNewFile(), "Could not create file: " + file.getAbsolutePath());
    return file;
  }

  private AndroidTestUtil() {}
}

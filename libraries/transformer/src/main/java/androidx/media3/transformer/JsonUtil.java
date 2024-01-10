/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import android.os.Build;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for working with JSON */
@UnstableApi
public final class JsonUtil {

  private JsonUtil() {}

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
   * Creates a {@link JSONArray} from {@link ExportResult.ProcessedInput processed inputs}.
   *
   * @param processedInputs The list of {@link ExportResult.ProcessedInput} instances.
   * @return A {@link JSONArray} containing {@link JSONObject} instances representing the {@link
   *     ExportResult.ProcessedInput} instances.
   * @throws JSONException if this method attempts to create a JSON with null key.
   */
  public static JSONArray processedInputsAsJsonArray(
      ImmutableList<ExportResult.ProcessedInput> processedInputs) throws JSONException {
    JSONArray jsonArray = new JSONArray();
    for (ExportResult.ProcessedInput processedInput : processedInputs) {
      JSONObject jsonObject = new JSONObject();
      @Nullable
      MediaItem.LocalConfiguration localConfiguration = processedInput.mediaItem.localConfiguration;
      if (localConfiguration != null) {
        jsonObject.put("mediaItemUri", localConfiguration.uri);
      }
      jsonObject.putOpt("audioDecoderName", processedInput.audioDecoderName);
      jsonObject.putOpt("videoDecoderName", processedInput.videoDecoderName);
      jsonArray.put(jsonObject);
    }
    return jsonArray;
  }

  /**
   * Creates a {@link JSONObject} from the {@link Exception}.
   *
   * <p>If the exception is an {@link ExportException}, {@code errorCode} is included.
   *
   * @param exception The {@link Exception}.
   * @return The {@link JSONObject} containing the exception details, or {@code null} if the
   *     exception was {@code null}.
   * @throws JSONException if this method attempts to create a JSON with null key.
   */
  @Nullable
  public static JSONObject exceptionAsJsonObject(@Nullable Exception exception)
      throws JSONException {
    if (exception == null) {
      return null;
    }
    JSONObject exceptionJson = new JSONObject();
    exceptionJson.put("message", exception.getMessage());
    exceptionJson.put("type", exception.getClass());
    if (exception instanceof ExportException) {
      exceptionJson.put("errorCode", ((ExportException) exception).errorCode);
    }
    exceptionJson.put("stackTrace", Log.getThrowableString(exception));
    return exceptionJson;
  }

  /**
   * Creates a {@link JSONObject} from the {@link ExportResult}.
   *
   * @param exportResult The {@link ExportResult}.
   * @return The {@link JSONObject} describing the {@code exportResult}.
   * @throws JSONException if this method attempts to create a JSON with null key.
   */
  public static JSONObject exportResultAsJsonObject(ExportResult exportResult)
      throws JSONException {
    JSONObject jsonObject =
        new JSONObject()
            .putOpt("audioEncoderName", exportResult.audioEncoderName)
            .putOpt("colorInfo", exportResult.colorInfo)
            .putOpt("videoEncoderName", exportResult.videoEncoderName)
            .putOpt("testException", exceptionAsJsonObject(exportResult.exportException));

    if (!exportResult.processedInputs.isEmpty()) {
      jsonObject.put("processedInputs", processedInputsAsJsonArray(exportResult.processedInputs));
    }

    if (exportResult.averageAudioBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageAudioBitrate", exportResult.averageAudioBitrate);
    }
    if (exportResult.averageVideoBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageVideoBitrate", exportResult.averageVideoBitrate);
    }
    if (exportResult.channelCount != C.LENGTH_UNSET) {
      jsonObject.put("channelCount", exportResult.channelCount);
    }
    if (exportResult.durationMs != C.TIME_UNSET) {
      jsonObject.put("durationMs", exportResult.durationMs);
    }
    if (exportResult.fileSizeBytes != C.LENGTH_UNSET) {
      jsonObject.put("fileSizeBytes", exportResult.fileSizeBytes);
    }
    if (exportResult.height != C.LENGTH_UNSET) {
      jsonObject.put("height", exportResult.height);
    }
    if (exportResult.sampleRate != C.RATE_UNSET_INT) {
      jsonObject.put("sampleRate", exportResult.sampleRate);
    }
    if (exportResult.videoFrameCount > 0) {
      jsonObject.put("videoFrameCount", exportResult.videoFrameCount);
    }
    if (exportResult.width != C.LENGTH_UNSET) {
      jsonObject.put("width", exportResult.width);
    }
    return jsonObject;
  }
}

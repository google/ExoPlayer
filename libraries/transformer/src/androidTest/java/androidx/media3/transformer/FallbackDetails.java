/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.transformer;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.transformer.Composition.HdrMode;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A test only class for holding the details of what fallbacks were applied during a test export.
 */
/* package */ final class FallbackDetails {

  private static final String INFERRED_FROM_SOURCE = "Inferred from source.";

  public final int originalOutputHeight;
  public final int fallbackOutputHeight;

  @Nullable public final String originalAudioMimeType;
  @Nullable public final String fallbackAudioMimeType;

  @Nullable public final String originalVideoMimeType;
  @Nullable public final String fallbackVideoMimeType;

  public final @HdrMode int originalHdrMode;
  public final @HdrMode int fallbackHdrMode;

  public FallbackDetails(
      int originalOutputHeight,
      int fallbackOutputHeight,
      @Nullable String originalAudioMimeType,
      @Nullable String fallbackAudioMimeType,
      @Nullable String originalVideoMimeType,
      @Nullable String fallbackVideoMimeType,
      @HdrMode int originalHdrMode,
      @HdrMode int fallbackHdrMode) {
    this.originalOutputHeight = originalOutputHeight;
    this.fallbackOutputHeight = fallbackOutputHeight;

    this.originalAudioMimeType = originalAudioMimeType;
    this.fallbackAudioMimeType = fallbackAudioMimeType;

    this.originalVideoMimeType = originalVideoMimeType;
    this.fallbackVideoMimeType = fallbackVideoMimeType;

    this.originalHdrMode = originalHdrMode;
    this.fallbackHdrMode = fallbackHdrMode;
  }

  /** Returns a {@link JSONObject} detailing all the fallbacks that have been applied. */
  public JSONObject asJsonObject() throws JSONException {
    JSONObject jsonObject = new JSONObject();
    if (fallbackOutputHeight != originalOutputHeight) {
      jsonObject.put(
          "originalOutputHeight",
          originalOutputHeight != C.LENGTH_UNSET ? originalOutputHeight : INFERRED_FROM_SOURCE);
      jsonObject.put("fallbackOutputHeight", fallbackOutputHeight);
    }
    if (!Objects.equals(fallbackAudioMimeType, originalAudioMimeType)) {
      jsonObject.put(
          "originalAudioMimeType",
          originalAudioMimeType != null ? originalAudioMimeType : INFERRED_FROM_SOURCE);
      jsonObject.put("fallbackAudioMimeType", fallbackAudioMimeType);
    }
    if (!Objects.equals(fallbackVideoMimeType, originalVideoMimeType)) {
      jsonObject.put(
          "originalVideoMimeType",
          originalVideoMimeType != null ? originalVideoMimeType : INFERRED_FROM_SOURCE);
      jsonObject.put("fallbackVideoMimeType", fallbackVideoMimeType);
    }
    if (fallbackHdrMode != originalHdrMode) {
      jsonObject.put("originalHdrMode", originalHdrMode);
      jsonObject.put("fallbackHdrMode", fallbackHdrMode);
    }
    return jsonObject;
  }
}

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

package com.google.android.exoplayer2.transformer;

import android.media.MediaCodecInfo;

/** Provides bitrates for encoders to use as a target. */
public interface EncoderBitrateProvider {

  /**
   * Returns a recommended bitrate that the encoder should target.
   *
   * @param encoderName The name of the encoder, see {@link MediaCodecInfo#getName()}.
   * @param width The output width of the video after encoding.
   * @param height The output height of the video after encoding.
   * @param frameRate The expected output frame rate of the video after encoding.
   * @return The bitrate the encoder should target.
   */
  int getBitrate(String encoderName, int width, int height, float frameRate);
}

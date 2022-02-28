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

/** A test only class for holding additional details alongside a {@link TransformationResult}. */
public class TestTransformationResult {
  /** Represents an unset or unknown SSIM score. */
  public static final double SSIM_UNSET = -1.0d;

  public final TransformationResult transformationResult;
  public final String filePath;
  /** The SSIM score of the transformation, {@link #SSIM_UNSET} if unavailable. */
  public final double ssim;

  public TestTransformationResult(TransformationResult transformationResult, String filePath) {
    this(transformationResult, filePath, /* ssim= */ SSIM_UNSET);
  }

  public TestTransformationResult(
      TransformationResult transformationResult, String filePath, double ssim) {
    this.transformationResult = transformationResult;
    this.filePath = filePath;
    this.ssim = ssim;
  }
}

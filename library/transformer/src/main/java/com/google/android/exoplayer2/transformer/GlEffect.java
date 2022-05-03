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

/**
 * Interface for a video frame effect with a {@link GlFrameProcessor} implementation.
 *
 * <p>Implementations contain information specifying the effect and can be {@linkplain
 * #toGlFrameProcessor() converted} to a {@link GlFrameProcessor} which applies the effect.
 */
public interface GlEffect {

  /** Returns a {@link GlFrameProcessor} that applies the the effect. */
  GlFrameProcessor toGlFrameProcessor();
}

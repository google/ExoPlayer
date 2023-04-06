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
package androidx.media3.effect;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

/**
 * Interface for tasks that may throw a {@link GlUtil.GlException} or {@link
 * VideoFrameProcessingException}.
 */
@UnstableApi
/* package */ interface VideoFrameProcessingTask {
  /** Runs the task. */
  void run() throws VideoFrameProcessingException, GlUtil.GlException;
}

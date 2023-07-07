/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/** A listener for processing input frames. */
@UnstableApi
public interface OnInputFrameProcessedListener {

  /**
   * Called when the given input frame has been processed.
   *
   * @param textureId The identifier of the processed texture.
   * @param syncObject A GL sync object (see https://www.khronos.org/opengl/wiki/Sync_Object) that
   *     has been inserted into the GL command stream after the last use of the texture. Value is 0
   *     if and only if the {@code GLES30#glFenceSync} failed or the EGL context version is less
   *     than OpenGL 3.0. The sync object must be {@link
   *     androidx.media3.common.util.GlUtil#deleteSyncObject deleted} after use.
   * @throws VideoFrameProcessingException Thrown if an error was encountered handling the event.
   */
  void onInputFrameProcessed(int textureId, long syncObject) throws VideoFrameProcessingException;
}

/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.effect;

import android.opengl.GLES30;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

/** A component that outputs {@linkplain GlTextureInfo OpenGL textures}. */
@UnstableApi
public interface GlTextureProducer {

  /** Listener for texture output. */
  interface Listener {
    /**
     * Called when a texture has been rendered to.
     *
     * @param textureProducer The {@link GlTextureProducer} that has rendered the texture.
     * @param outputTexture The texture that has been rendered.
     * @param presentationTimeUs The presentation time of the texture.
     * @param syncObject A GL sync object that has been inserted into the GL command stream after
     *     the last write of the {@code outputTexture}. Value is 0 if and only if the {@link
     *     GLES30#glFenceSync} failed.
     */
    void onTextureRendered(
        GlTextureProducer textureProducer,
        GlTextureInfo outputTexture,
        long presentationTimeUs,
        long syncObject)
        throws VideoFrameProcessingException, GlUtil.GlException;
  }

  /** Releases the output texture at the given {@code presentationTimeUs}. */
  void releaseOutputTexture(long presentationTimeUs);
}

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

import static com.google.android.exoplayer2.util.Assertions.checkState;

import androidx.annotation.IntRange;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Util;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener for fallback {@link TransformationRequest TransformationRequests} from the audio and
 * video renderers.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class FallbackListener {

  private final Composition composition;
  private final ListenerSet<Transformer.Listener> transformerListeners;
  private final HandlerWrapper transformerListenerHandler;
  private final TransformationRequest originalTransformationRequest;
  private final AtomicInteger trackCount;

  private TransformationRequest fallbackTransformationRequest;

  /**
   * Creates a new instance.
   *
   * @param composition The {@link Composition} to export.
   * @param transformerListeners The {@linkplain Transformer.Listener listeners} to call {@link
   *     Transformer.Listener#onFallbackApplied} on.
   * @param transformerListenerHandler The {@link HandlerWrapper} to call {@link
   *     Transformer.Listener#onFallbackApplied} events on.
   * @param originalTransformationRequest The original {@link TransformationRequest}.
   */
  public FallbackListener(
      Composition composition,
      ListenerSet<Transformer.Listener> transformerListeners,
      HandlerWrapper transformerListenerHandler,
      TransformationRequest originalTransformationRequest) {
    this.composition = composition;
    this.transformerListeners = transformerListeners;
    this.transformerListenerHandler = transformerListenerHandler;
    this.originalTransformationRequest = originalTransformationRequest;
    this.fallbackTransformationRequest = originalTransformationRequest;
    trackCount = new AtomicInteger();
  }

  /**
   * Sets the number of output tracks.
   *
   * <p>The track count must be set before a transformation request is {@linkplain
   * #onTransformationRequestFinalized(TransformationRequest) finalized}.
   *
   * <p>Can be called from any thread.
   */
  public void setTrackCount(@IntRange(from = 1) int trackCount) {
    this.trackCount.set(trackCount);
  }

  /**
   * Updates the {@link TransformationRequest}, if fallback is applied.
   *
   * <p>Should be called with the final {@link TransformationRequest} for each track, after any
   * track-specific fallback changes have been applied.
   *
   * <p>Fallback is applied if the finalized {@code TransformationRequest} is different from the
   * original {@code TransformationRequest}. If fallback is applied, calls {@link
   * Transformer.Listener#onFallbackApplied(Composition, TransformationRequest,
   * TransformationRequest)} once this method has been called for each track.
   *
   * @param transformationRequest The final {@link TransformationRequest} for a track.
   * @throws IllegalStateException If called for more tracks than declared in {@link
   *     #setTrackCount(int)}.
   */
  public void onTransformationRequestFinalized(TransformationRequest transformationRequest) {
    checkState(trackCount.getAndDecrement() > 0);

    TransformationRequest.Builder fallbackRequestBuilder =
        fallbackTransformationRequest.buildUpon();
    if (!Util.areEqual(
        transformationRequest.audioMimeType, originalTransformationRequest.audioMimeType)) {
      fallbackRequestBuilder.setAudioMimeType(transformationRequest.audioMimeType);
    }
    if (!Util.areEqual(
        transformationRequest.videoMimeType, originalTransformationRequest.videoMimeType)) {
      fallbackRequestBuilder.setVideoMimeType(transformationRequest.videoMimeType);
    }
    if (transformationRequest.outputHeight != originalTransformationRequest.outputHeight) {
      fallbackRequestBuilder.setResolution(transformationRequest.outputHeight);
    }
    if (transformationRequest.hdrMode != originalTransformationRequest.hdrMode) {
      fallbackRequestBuilder.setHdrMode(transformationRequest.hdrMode);
    }
    TransformationRequest newFallbackTransformationRequest = fallbackRequestBuilder.build();
    fallbackTransformationRequest = newFallbackTransformationRequest;

    if (trackCount.get() == 0
        && !originalTransformationRequest.equals(fallbackTransformationRequest)) {
      transformerListenerHandler.post(
          () ->
              transformerListeners.sendEvent(
                  /* eventFlag= */ C.INDEX_UNSET,
                  listener ->
                      listener.onFallbackApplied(
                          composition,
                          originalTransformationRequest,
                          newFallbackTransformationRequest)));
    }
  }
}

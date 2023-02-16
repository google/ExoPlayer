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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link FallbackListener}. */
@RunWith(AndroidJUnit4.class)
public class FallbackListenerTest {

  private static final MediaItem PLACEHOLDER_MEDIA_ITEM = MediaItem.fromUri(Uri.EMPTY);

  @Test
  public void onTransformationRequestFinalized_withoutTrackRegistration_throwsException() {
    TransformationRequest transformationRequest = new TransformationRequest.Builder().build();
    FallbackListener fallbackListener =
        new FallbackListener(
            PLACEHOLDER_MEDIA_ITEM, createListenerSet(), createHandler(), transformationRequest);

    assertThrows(
        IllegalStateException.class,
        () -> fallbackListener.onTransformationRequestFinalized(transformationRequest));
  }

  @Test
  public void onTransformationRequestFinalized_afterTrackRegistration_completesSuccessfully() {
    TransformationRequest transformationRequest = new TransformationRequest.Builder().build();
    FallbackListener fallbackListener =
        new FallbackListener(
            PLACEHOLDER_MEDIA_ITEM, createListenerSet(), createHandler(), transformationRequest);

    fallbackListener.registerTrack();
    fallbackListener.onTransformationRequestFinalized(transformationRequest);
    ShadowLooper.idleMainLooper();
  }

  @Test
  public void onTransformationRequestFinalized_withUnchangedRequest_doesNotCallback() {
    TransformationRequest originalRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    TransformationRequest unchangedRequest = originalRequest.buildUpon().build();
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    FallbackListener fallbackListener =
        new FallbackListener(
            PLACEHOLDER_MEDIA_ITEM,
            createListenerSet(mockListener),
            createHandler(),
            originalRequest);

    fallbackListener.registerTrack();
    fallbackListener.onTransformationRequestFinalized(unchangedRequest);
    ShadowLooper.idleMainLooper();

    verify(mockListener, never()).onFallbackApplied(any(), any(), any());
  }

  @Test
  public void onTransformationRequestFinalized_withDifferentRequest_callsCallback() {
    TransformationRequest originalRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    TransformationRequest audioFallbackRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AMR_WB).build();
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    FallbackListener fallbackListener =
        new FallbackListener(
            PLACEHOLDER_MEDIA_ITEM,
            createListenerSet(mockListener),
            createHandler(),
            originalRequest);

    fallbackListener.registerTrack();
    fallbackListener.onTransformationRequestFinalized(audioFallbackRequest);
    ShadowLooper.idleMainLooper();

    verify(mockListener)
        .onFallbackApplied(PLACEHOLDER_MEDIA_ITEM, originalRequest, audioFallbackRequest);
  }

  @Test
  public void
      onTransformationRequestFinalized_forMultipleTracks_callsCallbackOnceWithMergedRequest() {
    TransformationRequest originalRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    TransformationRequest audioFallbackRequest =
        originalRequest.buildUpon().setAudioMimeType(MimeTypes.AUDIO_AMR_WB).build();
    TransformationRequest videoFallbackRequest =
        originalRequest.buildUpon().setVideoMimeType(MimeTypes.VIDEO_H264).build();
    TransformationRequest mergedFallbackRequest =
        new TransformationRequest.Builder()
            .setAudioMimeType(MimeTypes.AUDIO_AMR_WB)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build();
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    FallbackListener fallbackListener =
        new FallbackListener(
            PLACEHOLDER_MEDIA_ITEM,
            createListenerSet(mockListener),
            createHandler(),
            originalRequest);

    fallbackListener.registerTrack();
    fallbackListener.registerTrack();
    fallbackListener.onTransformationRequestFinalized(audioFallbackRequest);
    fallbackListener.onTransformationRequestFinalized(videoFallbackRequest);
    ShadowLooper.idleMainLooper();

    verify(mockListener)
        .onFallbackApplied(PLACEHOLDER_MEDIA_ITEM, originalRequest, mergedFallbackRequest);
  }

  private static ListenerSet<Transformer.Listener> createListenerSet(
      Transformer.Listener transformerListener) {
    ListenerSet<Transformer.Listener> listenerSet = createListenerSet();
    listenerSet.add(transformerListener);
    return listenerSet;
  }

  private static ListenerSet<Transformer.Listener> createListenerSet() {
    return new ListenerSet<>(Looper.myLooper(), Clock.DEFAULT, (listener, flags) -> {});
  }

  private static HandlerWrapper createHandler() {
    return Clock.DEFAULT.createHandler(Looper.myLooper(), /* callback= */ null);
  }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link RenderersFactory} that captures interactions with the audio and video {@link
 * MediaCodecAdapter} instances.
 *
 * <p>The captured interactions can be used in a test assertion via the {@link Dumper.Dumpable}
 * interface.
 */
// TODO(internal b/174661563): Add support for capturing subtitles on the output of the
// SubtitleDecoder. And possibly Metadata too (for consistency).
public class CapturingRenderersFactory implements RenderersFactory, Dumper.Dumpable {

  private final Context context;
  private final CapturingMediaCodecAdapter.Factory mediaCodecAdapterFactory;

  public CapturingRenderersFactory(Context context) {
    this.context = context;
    this.mediaCodecAdapterFactory = new CapturingMediaCodecAdapter.Factory();
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput) {
    return new Renderer[] {
      new MediaCodecVideoRenderer(
          context,
          mediaCodecAdapterFactory,
          MediaCodecSelector.DEFAULT,
          DefaultRenderersFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS,
          /* enableDecoderFallback= */ false,
          eventHandler,
          videoRendererEventListener,
          DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY),
      new MediaCodecAudioRenderer(
          context,
          mediaCodecAdapterFactory,
          MediaCodecSelector.DEFAULT,
          /* enableDecoderFallback= */ false,
          eventHandler,
          audioRendererEventListener,
          new DefaultAudioSink(AudioCapabilities.getCapabilities(context), new AudioProcessor[0])),
      new TextRenderer(textRendererOutput, eventHandler.getLooper()),
      new MetadataRenderer(metadataRendererOutput, eventHandler.getLooper())
    };
  }

  @Override
  public void dump(Dumper dumper) {
    mediaCodecAdapterFactory.dump(dumper);
  }

  /**
   * A {@link MediaCodecAdapter} that captures interactions and exposes them for test assertions via
   * {@link Dumper.Dumpable}.
   */
  private static class CapturingMediaCodecAdapter implements MediaCodecAdapter, Dumper.Dumpable {

    private static class Factory implements MediaCodecAdapter.Factory, Dumper.Dumpable {

      private final List<CapturingMediaCodecAdapter> constructedAdapters;

      private Factory() {
        constructedAdapters = new ArrayList<>();
      }

      @RequiresApi(18)
      @Override
      public MediaCodecAdapter createAdapter(Configuration configuration) throws IOException {
        CapturingMediaCodecAdapter adapter =
            new CapturingMediaCodecAdapter(
                MediaCodecAdapter.Factory.DEFAULT.createAdapter(configuration),
                configuration.codecInfo.name);
        constructedAdapters.add(adapter);
        return adapter;
      }

      @Override
      public void dump(Dumper dumper) {
        ImmutableList<CapturingMediaCodecAdapter> sortedAdapters =
            ImmutableList.sortedCopyOf(
                (adapter1, adapter2) -> adapter1.codecName.compareTo(adapter2.codecName),
                constructedAdapters);
        for (int i = 0; i < sortedAdapters.size(); i++) {
          sortedAdapters.get(i).dump(dumper);
        }
      }
    }

    private final MediaCodecAdapter delegate;
    // TODO(internal b/175710547): Consider using MediaCodecInfo, but currently Robolectric (v4.5)
    // doesn't correctly implement MediaCodec#getCodecInfo() (getName() works).
    private final String codecName;

    /**
     * The client-owned buffers, keyed by the index used by {@link #dequeueInputBufferIndex()} and
     * {@link #getInputBuffer(int)}.
     */
    private final SparseArray<ByteBuffer> dequeuedInputBuffers;

    /** All interactions recorded with this adapter. */
    private final List<CapturedInteraction> capturedInteractions;

    private final AtomicBoolean isReleased;

    private CapturingMediaCodecAdapter(MediaCodecAdapter delegate, String codecName) {
      this.delegate = delegate;
      this.codecName = codecName;
      dequeuedInputBuffers = new SparseArray<>();
      capturedInteractions = new ArrayList<>();
      isReleased = new AtomicBoolean();
    }

    // MediaCodecAdapter implementation

    @Override
    public int dequeueInputBufferIndex() {
      return delegate.dequeueInputBufferIndex();
    }

    @Override
    public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
      return delegate.dequeueOutputBufferIndex(bufferInfo);
    }

    @Override
    public MediaFormat getOutputFormat() {
      return delegate.getOutputFormat();
    }

    @Nullable
    @Override
    public ByteBuffer getInputBuffer(int index) {
      @Nullable ByteBuffer inputBuffer = delegate.getInputBuffer(index);
      if (inputBuffer != null) {
        dequeuedInputBuffers.put(index, inputBuffer);
      }
      return inputBuffer;
    }

    @Nullable
    @Override
    public ByteBuffer getOutputBuffer(int index) {
      return delegate.getOutputBuffer(index);
    }

    @Override
    public void queueInputBuffer(
        int index, int offset, int size, long presentationTimeUs, int flags) {
      ByteBuffer inputBuffer = checkNotNull(dequeuedInputBuffers.get(index));
      capturedInteractions.add(new CapturedInputBuffer(peekBytes(inputBuffer, offset, size)));

      delegate.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
      dequeuedInputBuffers.delete(index);
    }

    @Override
    public void queueSecureInputBuffer(
        int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
      delegate.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {
      delegate.releaseOutputBuffer(index, render);
    }

    @RequiresApi(21)
    @Override
    public void releaseOutputBuffer(int index, long renderTimeStampNs) {
      delegate.releaseOutputBuffer(index, renderTimeStampNs);
    }

    @Override
    public void flush() {
      dequeuedInputBuffers.clear();
      delegate.flush();
    }

    @Override
    public void release() {
      dequeuedInputBuffers.clear();
      isReleased.set(true);
      delegate.release();
    }

    @RequiresApi(23)
    @Override
    public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
      delegate.setOnFrameRenderedListener(listener, handler);
    }

    @RequiresApi(23)
    @Override
    public void setOutputSurface(Surface surface) {
      delegate.setOutputSurface(surface);
    }

    @RequiresApi(19)
    @Override
    public void setParameters(Bundle params) {
      delegate.setParameters(params);
    }

    @Override
    public void setVideoScalingMode(int scalingMode) {
      delegate.setVideoScalingMode(scalingMode);
    }

    // Dumpable implementation

    @Override
    public void dump(Dumper dumper) {
      checkState(isReleased.get());

      dumper.startBlock("MediaCodecAdapter (" + codecName + ")");
      // TODO: Update this when capturedInteractions contains more than just input buffers.
      dumper.add("buffers.length", capturedInteractions.size());
      for (int i = 0; i < capturedInteractions.size(); i++) {
        CapturedInputBuffer inputBuffer = (CapturedInputBuffer) capturedInteractions.get(i);
        dumper.add("buffers[" + i + "]", inputBuffer.contents);
      }
      dumper.endBlock();
    }

    private static byte[] peekBytes(ByteBuffer buffer, int offset, int size) {
      int originalPosition = buffer.position();
      buffer.position(offset);
      byte[] bytes = new byte[size];
      buffer.get(bytes);
      buffer.position(originalPosition);
      return bytes;
    }

    /** A marker interface for different interactions with {@link CapturingMediaCodecAdapter}. */
    private interface CapturedInteraction {}

    /**
     * Records the data passed to {@link CapturingMediaCodecAdapter#queueInputBuffer(int, int, int,
     * long, int)}.
     */
    private static class CapturedInputBuffer implements CapturedInteraction {
      // TODO: Add other fields
      private final byte[] contents;

      private CapturedInputBuffer(byte[] contents) {
        this.contents = contents;
      }
    }
  }
}

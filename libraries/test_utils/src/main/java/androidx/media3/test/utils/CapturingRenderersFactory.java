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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.util.SparseArray;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoInfo;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.image.ImageDecoder;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.exoplayer.image.ImageRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link RenderersFactory} that captures interactions with the audio and video {@link
 * MediaCodecAdapter} instances and {@link ImageOutput} instances.
 *
 * <p>The captured interactions can be used in a test assertion via the {@link Dumper.Dumpable}
 * interface.
 */
@UnstableApi
public class CapturingRenderersFactory implements RenderersFactory, Dumper.Dumpable {

  private final Context context;
  private final CapturingMediaCodecAdapter.Factory mediaCodecAdapterFactory;
  private final CapturingAudioSink audioSink;
  private final CapturingImageOutput imageOutput;
  private ImageDecoder.Factory imageDecoderFactory;

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   */
  public CapturingRenderersFactory(Context context) {
    this.context = context;
    this.mediaCodecAdapterFactory = new CapturingMediaCodecAdapter.Factory(context);
    this.audioSink = new CapturingAudioSink(new DefaultAudioSink.Builder(context).build());
    this.imageOutput = new CapturingImageOutput();
    this.imageDecoderFactory = ImageDecoder.Factory.DEFAULT;
  }

  /**
   * Sets the {@link ImageDecoder.Factory} used by the {@link ImageRenderer}.
   *
   * @param imageDecoderFactory The {@link ImageDecoder.Factory}.
   * @return This factory, for convenience.
   */
  public CapturingRenderersFactory setImageDecoderFactory(
      ImageDecoder.Factory imageDecoderFactory) {
    this.imageDecoderFactory = imageDecoderFactory;
    return this;
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput) {
    ArrayList<Renderer> renderers = new ArrayList<>();
    renderers.add(
        new MediaCodecVideoRenderer(
            context,
            mediaCodecAdapterFactory,
            MediaCodecSelector.DEFAULT,
            DefaultRenderersFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS,
            /* enableDecoderFallback= */ false,
            eventHandler,
            videoRendererEventListener,
            DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY) {
          @Override
          protected boolean shouldDropOutputBuffer(
              long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
            // Do not drop output buffers due to slow processing.
            return false;
          }

          @Override
          protected boolean shouldDropBuffersToKeyframe(
              long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
            // Do not drop output buffers due to slow processing.
            return false;
          }

          @Override
          protected boolean shouldSkipBuffersWithIdenticalReleaseTime() {
            // Do not skip buffers with identical vsync times as we can't control this from tests.
            return false;
          }
        });
    renderers.add(
        new MediaCodecAudioRenderer(
            context,
            mediaCodecAdapterFactory,
            MediaCodecSelector.DEFAULT,
            /* enableDecoderFallback= */ false,
            eventHandler,
            audioRendererEventListener,
            audioSink));
    renderers.add(new TextRenderer(textRendererOutput, eventHandler.getLooper()));
    renderers.add(new MetadataRenderer(metadataRendererOutput, eventHandler.getLooper()));
    renderers.add(new ImageRenderer(imageDecoderFactory, imageOutput));

    return renderers.toArray(new Renderer[] {});
  }

  @Override
  public void dump(Dumper dumper) {
    mediaCodecAdapterFactory.dump(dumper);
    audioSink.dump(dumper);
    imageOutput.dump(dumper);
  }

  /**
   * A {@link MediaCodecAdapter} that captures interactions and exposes them for test assertions via
   * {@link Dumper.Dumpable}.
   */
  private static class CapturingMediaCodecAdapter implements MediaCodecAdapter, Dumper.Dumpable {

    private static class Factory implements MediaCodecAdapter.Factory, Dumper.Dumpable {

      private final Context context;
      private final List<CapturingMediaCodecAdapter> constructedAdapters;

      private Factory(Context context) {
        this.context = context;
        constructedAdapters = new ArrayList<>();
      }

      @Override
      public MediaCodecAdapter createAdapter(Configuration configuration) throws IOException {
        CapturingMediaCodecAdapter adapter =
            new CapturingMediaCodecAdapter(
                MediaCodecAdapter.Factory.getDefault(context).createAdapter(configuration),
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

    private static final String INPUT_BUFFER_INTERACTION_TYPE = "inputBuffers";
    private static final String OUTPUT_BUFFER_INTERACTION_TYPE = "outputBuffers";

    private final MediaCodecAdapter delegate;
    // TODO(internal b/175710547): Consider using MediaCodecInfo, but currently Robolectric (v4.5)
    // doesn't correctly implement MediaCodec#getCodecInfo() (getName() works).
    private final String codecName;

    /**
     * The client-owned buffers, keyed by the index used by {@link #dequeueInputBufferIndex()} and
     * {@link #getInputBuffer(int)}, or {@link #dequeueOutputBufferIndex} respectively.
     */
    private final SparseArray<ByteBuffer> dequeuedInputBuffers;

    private final SparseArray<MediaCodec.BufferInfo> dequeuedOutputBuffers;

    /** All interactions recorded with this adapter. */
    private final ArrayListMultimap<String, CapturedInteraction> capturedInteractions;

    private int inputBufferCount;
    private int outputBufferCount;
    private final AtomicBoolean isReleased;

    private CapturingMediaCodecAdapter(MediaCodecAdapter delegate, String codecName) {
      this.delegate = delegate;
      this.codecName = codecName;
      dequeuedInputBuffers = new SparseArray<>();
      dequeuedOutputBuffers = new SparseArray<>();
      capturedInteractions = ArrayListMultimap.create();
      isReleased = new AtomicBoolean();
    }

    // MediaCodecAdapter implementation

    @Override
    public int dequeueInputBufferIndex() {
      return delegate.dequeueInputBufferIndex();
    }

    @Override
    public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
      int index = delegate.dequeueOutputBufferIndex(bufferInfo);
      if (index >= 0) {
        dequeuedOutputBuffers.put(index, bufferInfo);
      }
      return index;
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
      capturedInteractions.put(
          INPUT_BUFFER_INTERACTION_TYPE,
          new CapturedInputBuffer(
              inputBufferCount++, peekBytes(inputBuffer, offset, size), presentationTimeUs, flags));

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
      MediaCodec.BufferInfo bufferInfo = checkNotNull(dequeuedOutputBuffers.get(index));
      capturedInteractions.put(
          OUTPUT_BUFFER_INTERACTION_TYPE,
          new CapturedOutputBuffer(
              outputBufferCount++,
              bufferInfo.size,
              bufferInfo.presentationTimeUs,
              bufferInfo.flags,
              /* rendered= */ render));
      delegate.releaseOutputBuffer(index, render);
      dequeuedOutputBuffers.delete(index);
    }

    @RequiresApi(21)
    @Override
    public void releaseOutputBuffer(int index, long renderTimeStampNs) {
      MediaCodec.BufferInfo bufferInfo = checkNotNull(dequeuedOutputBuffers.get(index));
      capturedInteractions.put(
          OUTPUT_BUFFER_INTERACTION_TYPE,
          new CapturedOutputBuffer(
              outputBufferCount++,
              bufferInfo.size,
              bufferInfo.presentationTimeUs,
              bufferInfo.flags,
              /* rendered= */ true));
      delegate.releaseOutputBuffer(index, renderTimeStampNs);
      dequeuedOutputBuffers.delete(index);
    }

    @Override
    public void flush() {
      dequeuedInputBuffers.clear();
      dequeuedOutputBuffers.clear();
      delegate.flush();
    }

    @Override
    public void release() {
      dequeuedInputBuffers.clear();
      dequeuedOutputBuffers.clear();
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

    @Override
    public void setParameters(Bundle params) {
      delegate.setParameters(params);
    }

    @Override
    public void setVideoScalingMode(int scalingMode) {
      delegate.setVideoScalingMode(scalingMode);
    }

    @RequiresApi(26)
    @Override
    public PersistableBundle getMetrics() {
      return delegate.getMetrics();
    }

    // Dumpable implementation

    @Override
    public void dump(Dumper dumper) {
      checkState(isReleased.get());
      ImmutableSortedMap<String, Collection<CapturedInteraction>> sortedInteractions =
          ImmutableSortedMap.copyOf(capturedInteractions.asMap());

      dumper.startBlock("MediaCodecAdapter (" + codecName + ")");
      for (Map.Entry<String, Collection<CapturedInteraction>> interactionEntry :
          sortedInteractions.entrySet()) {
        String interactionType = interactionEntry.getKey();
        Collection<CapturedInteraction> interactions = interactionEntry.getValue();
        dumper.startBlock(interactionType);
        dumper.add("count", interactions.size());
        for (CapturedInteraction interaction : interactions) {
          dumper.add(interaction);
        }
        dumper.endBlock();
      }
      dumper.endBlock();
    }

    @Override
    public boolean needsReconfiguration() {
      return false;
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
    private interface CapturedInteraction extends Dumper.Dumpable {}

    /**
     * Records the data passed to {@link CapturingMediaCodecAdapter#queueInputBuffer(int, int, int,
     * long, int)}.
     */
    private static class CapturedInputBuffer implements CapturedInteraction {
      private final int inputBufferCounter;
      private final byte[] contents;
      private final long bufferTimeUs;
      private final int flags;

      private CapturedInputBuffer(
          int inputBufferCounter, byte[] contents, long bufferTimeUs, int flags) {
        this.inputBufferCounter = inputBufferCounter;
        this.contents = contents;
        this.bufferTimeUs = bufferTimeUs;
        this.flags = flags;
      }

      @Override
      public void dump(Dumper dumper) {
        dumper.startBlock("input buffer #" + inputBufferCounter);
        dumper.add("timeUs", bufferTimeUs);
        if (flags != 0) {
          dumper.add("flags", flags);
        }
        dumper.add("contents", contents);
        dumper.endBlock();
      }
    }

    /** Records the data passed to {@link CapturingMediaCodecAdapter#releaseOutputBuffer}. */
    private static class CapturedOutputBuffer implements CapturedInteraction {
      private final int outputBufferCounter;
      private final int bufferSize;
      private final long bufferTimeUs;
      private final int flags;
      private final boolean rendered;

      private CapturedOutputBuffer(
          int outputBufferCounter, int bufferSize, long bufferTimeUs, int flags, boolean rendered) {
        this.outputBufferCounter = outputBufferCounter;
        this.bufferSize = bufferSize;
        this.bufferTimeUs = bufferTimeUs;
        this.flags = flags;
        this.rendered = rendered;
      }

      @Override
      public void dump(Dumper dumper) {
        dumper.startBlock("output buffer #" + outputBufferCounter);
        dumper.add("timeUs", bufferTimeUs);
        if (flags != 0) {
          dumper.add("flags", flags);
        }
        dumper.add("size", bufferSize);
        dumper.add("rendered", rendered);
        dumper.endBlock();
      }
    }
  }
}

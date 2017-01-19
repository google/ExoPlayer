/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A renderer for metadata.
 */
public final class MetadataRenderer extends BaseRenderer implements Callback {

  /**
   * Receives output from a {@link MetadataRenderer}.
   */
  public interface Output {

    /**
     * Called each time there is a metadata associated with current playback time.
     *
     * @param metadata The metadata.
     */
    void onMetadata(Metadata metadata);

  }

  private static final int MSG_INVOKE_RENDERER = 0;

  private final MetadataDecoderFactory decoderFactory;
  private final Output output;
  private final Handler outputHandler;
  private final FormatHolder formatHolder;
  private final MetadataInputBuffer buffer;

  private MetadataDecoder decoder;
  private boolean inputStreamEnded;
  private long pendingMetadataTimestamp;
  private Metadata pendingMetadata;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using
   *     {@link android.app.Activity#getMainLooper()}. Null may be passed if the output should be
   *     called directly on the player's internal rendering thread.
   */
  public MetadataRenderer(Output output, Looper outputLooper) {
    this(output, outputLooper, MetadataDecoderFactory.DEFAULT);
  }

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using
   *     {@link android.app.Activity#getMainLooper()}. Null may be passed if the output should be
   *     called directly on the player's internal rendering thread.
   * @param decoderFactory A factory from which to obtain {@link MetadataDecoder} instances.
   */
  public MetadataRenderer(Output output, Looper outputLooper,
      MetadataDecoderFactory decoderFactory) {
    super(C.TRACK_TYPE_METADATA);
    this.output = Assertions.checkNotNull(output);
    this.outputHandler = outputLooper == null ? null : new Handler(outputLooper, this);
    this.decoderFactory = Assertions.checkNotNull(decoderFactory);
    formatHolder = new FormatHolder();
    buffer = new MetadataInputBuffer();
  }

  @Override
  public int supportsFormat(Format format) {
    return decoderFactory.supportsFormat(format) ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    decoder = decoderFactory.createDecoder(formats[0]);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    pendingMetadata = null;
    inputStreamEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!inputStreamEnded && pendingMetadata == null) {
      buffer.clear();
      int result = readSource(formatHolder, buffer);
      if (result == C.RESULT_BUFFER_READ) {
        if (buffer.isEndOfStream()) {
          inputStreamEnded = true;
        } else if (buffer.isDecodeOnly()) {
          // Do nothing. Note this assumes that all metadata buffers can be decoded independently.
          // If we ever need to support a metadata format where this is not the case, we'll need to
          // pass the buffer to the decoder and discard the output.
        } else {
          pendingMetadataTimestamp = buffer.timeUs;
          buffer.subsampleOffsetUs = formatHolder.format.subsampleOffsetUs;
          buffer.flip();
          try {
            pendingMetadata = decoder.decode(buffer);
          } catch (MetadataDecoderException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
          }
        }
      }
    }

    if (pendingMetadata != null && pendingMetadataTimestamp <= positionUs) {
      invokeRenderer(pendingMetadata);
      pendingMetadata = null;
    }
  }

  @Override
  protected void onDisabled() {
    pendingMetadata = null;
    decoder = null;
    super.onDisabled();
  }

  @Override
  public boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  private void invokeRenderer(Metadata metadata) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget();
    } else {
      invokeRendererInternal(metadata);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((Metadata) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(Metadata metadata) {
    output.onMetadata(metadata);
  }

}

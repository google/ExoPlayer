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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.transformer.DefaultEncoderFactory;
import com.google.android.exoplayer2.transformer.DefaultMuxer;
import com.google.android.exoplayer2.transformer.Muxer;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.util.Clock;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Creates a {@link Transformer.Builder} setting up some of the common resources needed for testing
 * {@link Transformer}.
 */
public final class TestTransformerBuilderFactory {

  private final Context context;

  private static @MonotonicNonNull TestMuxer testMuxer;
  private long maxDelayBetweenSamplesMs;

  /** Creates a new instance */
  public TestTransformerBuilderFactory(Context context) {
    this.context = context;
    maxDelayBetweenSamplesMs = DefaultMuxer.Factory.DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS;
  }

  /**
   * Sets the muxer's {@linkplain Muxer#getMaxDelayBetweenSamplesMs() max delay} between samples.
   */
  @CanIgnoreReturnValue
  public TestTransformerBuilderFactory setMaxDelayBetweenSamplesMs(long maxDelayBetweenSamplesMs) {
    this.maxDelayBetweenSamplesMs = maxDelayBetweenSamplesMs;
    return this;
  }

  /** Returns a {@link Transformer.Builder} using the provided values or their defaults. */
  @SuppressLint("VisibleForTests") // Suppresses warning on setting the clock outside of a test file
  public Transformer.Builder create(boolean enableFallback) {
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    Muxer.Factory defaultMuxerFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
    return new Transformer.Builder(context)
        .setClock(clock)
        .setMuxerFactory(new TestMuxerFactory(defaultMuxerFactory))
        .setEncoderFactory(
            new DefaultEncoderFactory.Builder(context).setEnableFallback(enableFallback).build());
  }

  /**
   * Returns the test muxer used in the {@link Transformer.Builder}.
   *
   * <p>This method should only be called after the transformation is completed.
   */
  public TestMuxer getTestMuxer() {
    return checkStateNotNull(testMuxer);
  }

  private static final class TestMuxerFactory implements Muxer.Factory {

    private final Muxer.Factory defaultMuxerFactory;

    public TestMuxerFactory(Muxer.Factory defaultMuxerFactory) {
      this.defaultMuxerFactory = defaultMuxerFactory;
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      testMuxer = new TestMuxer(path, defaultMuxerFactory);
      return testMuxer;
    }

    @Override
    public Muxer create(ParcelFileDescriptor parcelFileDescriptor) throws Muxer.MuxerException {
      testMuxer = new TestMuxer("FD:" + parcelFileDescriptor.getFd(), defaultMuxerFactory);
      return testMuxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return defaultMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }
}

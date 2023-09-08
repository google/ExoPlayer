/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.DumpableFormat;
import androidx.media3.test.utils.Dumper;
import androidx.media3.test.utils.Dumper.Dumpable;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Dumpable} {@link Muxer} implementation that supports dumping information about all
 * interactions (for testing purposes) and forwards method calls to the underlying {@link Muxer}.
 */
public final class CapturingMuxer implements Muxer, Dumpable {

  /**
   * A {@link Muxer.Factory} for {@link CapturingMuxer} that captures and provides access to the
   * {@linkplain #create created} muxer.
   */
  public static final class Factory implements Muxer.Factory {
    private final Muxer.Factory wrappedFactory;

    @Nullable private CapturingMuxer muxer;

    public Factory() {
      this(/* maxDelayBetweenSamplesMs= */ C.TIME_UNSET);
    }

    public Factory(long maxDelayBetweenSamplesMs) {
      this.wrappedFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
    }

    /** Returns the most recently {@linkplain #create created} {@code TestMuxer}. */
    public CapturingMuxer getCreatedMuxer() {
      return checkNotNull(muxer);
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      muxer = new CapturingMuxer(wrappedFactory.create(path));
      return muxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private final Muxer wrappedMuxer;
  private final SparseArray<DumpableFormat> dumpableFormatByTrackType;
  private final SparseArray<ArrayList<DumpableSample>> dumpableSamplesByTrackType;
  private final Map<Integer, Integer> trackIndexToType;
  private final ArrayList<Metadata> metadataList;
  private boolean released;

  /** Creates a new test muxer. */
  private CapturingMuxer(Muxer wrappedMuxer) {
    this.wrappedMuxer = wrappedMuxer;
    dumpableSamplesByTrackType = new SparseArray<>();
    dumpableFormatByTrackType = new SparseArray<>();
    trackIndexToType = new HashMap<>();
    metadataList = new ArrayList<>();
  }

  // Muxer implementation.

  @Override
  public int addTrack(Format format) throws MuxerException {
    int trackIndex = wrappedMuxer.addTrack(format);
    @C.TrackType int trackType = getProcessedTrackType(format.sampleMimeType);

    trackIndexToType.put(trackIndex, trackType);

    dumpableFormatByTrackType.append(
        trackType, new DumpableFormat(format, /* tag= */ Util.getTrackTypeString(trackType)));
    dumpableSamplesByTrackType.append(trackType, new ArrayList<>());

    return trackIndex;
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
      throws MuxerException {
    @C.TrackType int trackType = checkNotNull(trackIndexToType.get(trackIndex));
    dumpableSamplesByTrackType
        .get(trackType)
        .add(
            new DumpableSample(
                trackType,
                data,
                (flags & C.BUFFER_FLAG_KEY_FRAME) == C.BUFFER_FLAG_KEY_FRAME,
                presentationTimeUs));
    wrappedMuxer.writeSampleData(trackIndex, data, presentationTimeUs, flags);
  }

  @Override
  public void addMetadata(Metadata metadata) {
    metadataList.add(metadata);
    wrappedMuxer.addMetadata(metadata);
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    released = true;
    wrappedMuxer.release(forCancellation);
  }

  @Override
  public long getMaxDelayBetweenSamplesMs() {
    return wrappedMuxer.getMaxDelayBetweenSamplesMs();
  }

  // Dumper.Dumpable implementation.

  @Override
  public void dump(Dumper dumper) {
    for (int i = 0; i < dumpableFormatByTrackType.size(); i++) {
      dumpableFormatByTrackType.valueAt(i).dump(dumper);
    }

    Collections.sort(metadataList, Comparator.comparing(Metadata::toString));
    for (Metadata metadata : metadataList) {
      dumper.add("container metadata", metadata);
    }

    for (int i = 0; i < dumpableSamplesByTrackType.size(); i++) {
      for (DumpableSample sample : dumpableSamplesByTrackType.valueAt(i)) {
        sample.dump(dumper);
      }
    }

    dumper.add("released", released);
  }

  private static final class DumpableSample implements Dumpable {

    private final @C.TrackType int trackType;
    private final long presentationTimeUs;
    private final boolean isKeyFrame;
    private final int sampleDataHashCode;
    private final int sampleSize;

    public DumpableSample(
        @C.TrackType int trackType,
        ByteBuffer sample,
        boolean isKeyFrame,
        long presentationTimeUs) {
      this.trackType = trackType;
      this.presentationTimeUs = presentationTimeUs;
      this.isKeyFrame = isKeyFrame;
      int initialPosition = sample.position();
      sampleSize = sample.remaining();
      byte[] data = new byte[sampleSize];
      sample.get(data);
      sample.position(initialPosition);
      sampleDataHashCode = Arrays.hashCode(data);
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("sample")
          .add("trackType", Util.getTrackTypeString(trackType))
          .add("dataHashCode", sampleDataHashCode)
          .add("size", sampleSize)
          .add("isKeyFrame", isKeyFrame)
          .add("presentationTimeUs", presentationTimeUs)
          .endBlock();
    }
  }
}

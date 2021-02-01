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
package com.google.android.exoplayer2.transformer;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.DumpableFormat;
import com.google.android.exoplayer2.testutil.Dumper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An implementation of {@link Muxer} that supports dumping information about all interactions (for
 * testing purposes) and delegates the actual muxing operations to a {@link FrameworkMuxer}.
 */
public final class TestMuxer implements Muxer, Dumper.Dumpable {

  private final Muxer frameworkMuxer;
  private final List<Dumper.Dumpable> dumpables;

  /** Creates a new test muxer. */
  public TestMuxer(String path, String outputMimeType) throws IOException {
    frameworkMuxer = new FrameworkMuxer.Factory().create(path, outputMimeType);
    dumpables = new ArrayList<>();
    dumpables.add(dumper -> dumper.add("containerMimeType", outputMimeType));
  }

  // Muxer implementation.

  @Override
  public boolean supportsSampleMimeType(String mimeType) {
    return frameworkMuxer.supportsSampleMimeType(mimeType);
  }

  @Override
  public int addTrack(Format format) {
    int trackIndex = frameworkMuxer.addTrack(format);
    dumpables.add(new DumpableFormat(format, trackIndex));
    return trackIndex;
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs) {
    dumpables.add(new DumpableSample(trackIndex, data, isKeyFrame, presentationTimeUs));
    frameworkMuxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
  }

  @Override
  public void release(boolean forCancellation) {
    dumpables.add(dumper -> dumper.add("released", true));
    frameworkMuxer.release(forCancellation);
  }

  // Dumper.Dumpable implementation.

  @Override
  public void dump(Dumper dumper) {
    for (Dumper.Dumpable dumpable : dumpables) {
      dumpable.dump(dumper);
    }
  }

  private static final class DumpableSample implements Dumper.Dumpable {

    private final int trackIndex;
    private final long presentationTimeUs;
    private final boolean isKeyFrame;
    private final int sampleDataHashCode;

    public DumpableSample(
        int trackIndex, ByteBuffer sample, boolean isKeyFrame, long presentationTimeUs) {
      this.trackIndex = trackIndex;
      this.presentationTimeUs = presentationTimeUs;
      this.isKeyFrame = isKeyFrame;
      int initialPosition = sample.position();
      byte[] data = new byte[sample.remaining()];
      sample.get(data);
      sample.position(initialPosition);
      sampleDataHashCode = Arrays.hashCode(data);
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("sample")
          .add("trackIndex", trackIndex)
          .add("dataHashCode", sampleDataHashCode)
          .add("isKeyFrame", isKeyFrame)
          .add("presentationTimeUs", presentationTimeUs)
          .endBlock();
    }
  }
}

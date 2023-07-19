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

package com.google.android.exoplayer2.text;

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;

/**
 * A wrapping {@link ExtractorOutput} for use by {@link SubtitleTranscodingExtractor}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ class SubtitleTranscodingExtractorOutput implements ExtractorOutput {

  private final ExtractorOutput delegate;
  private final SubtitleParser.Factory subtitleParserFactory;
  private final SparseArray<SubtitleTranscodingTrackOutput> textTrackOutputs;

  public SubtitleTranscodingExtractorOutput(
      ExtractorOutput delegate, SubtitleParser.Factory subtitleParserFactory) {
    this.delegate = delegate;
    this.subtitleParserFactory = subtitleParserFactory;
    textTrackOutputs = new SparseArray<>();
  }

  public void resetSubtitleParsers() {
    for (int i = 0; i < textTrackOutputs.size(); i++) {
      textTrackOutputs.valueAt(i).resetSubtitleParser();
    }
  }

  // ExtractorOutput implementation

  @Override
  public TrackOutput track(int id, @C.TrackType int type) {
    if (type != C.TRACK_TYPE_TEXT) {
      return delegate.track(id, type);
    }
    SubtitleTranscodingTrackOutput existingTrackOutput = textTrackOutputs.get(id);
    if (existingTrackOutput != null) {
      return existingTrackOutput;
    }
    SubtitleTranscodingTrackOutput trackOutput =
        new SubtitleTranscodingTrackOutput(delegate.track(id, type), subtitleParserFactory);
    textTrackOutputs.put(id, trackOutput);
    return trackOutput;
  }

  @Override
  public void endTracks() {
    delegate.endTracks();
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    delegate.seekMap(seekMap);
  }
}

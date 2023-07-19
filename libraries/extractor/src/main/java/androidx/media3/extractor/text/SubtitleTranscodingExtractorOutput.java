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

package androidx.media3.extractor.text;

import android.util.SparseArray;
import androidx.media3.common.C;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

/** A wrapping {@link ExtractorOutput} for use by {@link SubtitleTranscodingExtractor}. */
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

/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_INCLUDE_SUPPLEMENTAL_DATA;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_IN_BAND_CRYPTO_INFO;

import android.annotation.SuppressLint;
import android.media.MediaParser;
import android.media.MediaParser.SeekPoint;
import android.net.Uri;
import android.util.Pair;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.mediaparser.InputReaderAdapterV30;
import androidx.media3.exoplayer.source.mediaparser.MediaParserUtil;
import androidx.media3.exoplayer.source.mediaparser.OutputConsumerAdapterV30;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** {@link ProgressiveMediaExtractor} implemented on top of the platform's {@link MediaParser}. */
@RequiresApi(30)
@UnstableApi
public final class MediaParserExtractorAdapter implements ProgressiveMediaExtractor {

  /**
   * A {@link ProgressiveMediaExtractor.Factory} for instances of this class, which rely on platform
   * extractors through {@link MediaParser}.
   */
  public static final ProgressiveMediaExtractor.Factory FACTORY = MediaParserExtractorAdapter::new;

  private final OutputConsumerAdapterV30 outputConsumerAdapter;
  private final InputReaderAdapterV30 inputReaderAdapter;
  private final MediaParser mediaParser;
  private String parserName;

  @SuppressLint("WrongConstant")
  public MediaParserExtractorAdapter(PlayerId playerId) {
    // TODO: Add support for injecting the desired extractor list.
    outputConsumerAdapter = new OutputConsumerAdapterV30();
    inputReaderAdapter = new InputReaderAdapterV30();
    mediaParser = MediaParser.create(outputConsumerAdapter);
    mediaParser.setParameter(PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE, true);
    mediaParser.setParameter(PARAMETER_IN_BAND_CRYPTO_INFO, true);
    mediaParser.setParameter(PARAMETER_INCLUDE_SUPPLEMENTAL_DATA, true);
    parserName = MediaParser.PARSER_NAME_UNKNOWN;
    if (Util.SDK_INT >= 31) {
      MediaParserUtil.setLogSessionIdOnMediaParser(mediaParser, playerId);
    }
  }

  @Override
  public void init(
      DataReader dataReader,
      Uri uri,
      Map<String, List<String>> responseHeaders,
      long position,
      long length,
      ExtractorOutput output)
      throws IOException {
    outputConsumerAdapter.setExtractorOutput(output);
    inputReaderAdapter.setDataReader(dataReader, length);
    inputReaderAdapter.setCurrentPosition(position);
    String currentParserName = mediaParser.getParserName();
    if (MediaParser.PARSER_NAME_UNKNOWN.equals(currentParserName)) {
      // We need to sniff.
      mediaParser.advance(inputReaderAdapter);
      parserName = mediaParser.getParserName();
      outputConsumerAdapter.setSelectedParserName(parserName);
    } else if (!currentParserName.equals(parserName)) {
      // The parser was created by name.
      parserName = mediaParser.getParserName();
      outputConsumerAdapter.setSelectedParserName(parserName);
    } else {
      // The parser implementation has already been selected. Do nothing.
    }
  }

  @Override
  public void release() {
    mediaParser.release();
  }

  @Override
  public void disableSeekingOnMp3Streams() {
    if (MediaParser.PARSER_NAME_MP3.equals(parserName)) {
      outputConsumerAdapter.disableSeeking();
    }
  }

  @Override
  public long getCurrentInputPosition() {
    return inputReaderAdapter.getPosition();
  }

  @Override
  public void seek(long position, long seekTimeUs) {
    inputReaderAdapter.setCurrentPosition(position);
    Pair<SeekPoint, SeekPoint> seekPoints = outputConsumerAdapter.getSeekPoints(seekTimeUs);
    mediaParser.seek(seekPoints.second.position == position ? seekPoints.second : seekPoints.first);
  }

  @Override
  public int read(PositionHolder positionHolder) throws IOException {
    boolean shouldContinue = mediaParser.advance(inputReaderAdapter);
    positionHolder.position = inputReaderAdapter.getAndResetSeekPosition();
    return !shouldContinue
        ? Extractor.RESULT_END_OF_INPUT
        : positionHolder.position != C.INDEX_UNSET
            ? Extractor.RESULT_SEEK
            : Extractor.RESULT_CONTINUE;
  }
}

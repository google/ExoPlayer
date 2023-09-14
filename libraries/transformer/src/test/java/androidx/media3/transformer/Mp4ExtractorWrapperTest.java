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
package androidx.media3.transformer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.media3.common.C;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link Mp4ExtractorWrapper}. */
@RunWith(AndroidJUnit4.class)
public class Mp4ExtractorWrapperTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void init_withEmptyFile_throws() throws IOException {
    String emptyFilePath = temporaryFolder.newFile("EmptyFile").getPath();
    Mp4ExtractorWrapper mp4ExtractorWrapper = new Mp4ExtractorWrapper(context, emptyFilePath);

    assertThrows(IllegalStateException.class, mp4ExtractorWrapper::init);
  }

  @Test
  public void init_withNonMp4File_throws() {
    String mp4FilePath = "asset:///media/mkv/sample.mkv";
    Mp4ExtractorWrapper mp4ExtractorWrapper = new Mp4ExtractorWrapper(context, mp4FilePath);

    assertThrows(IllegalStateException.class, mp4ExtractorWrapper::init);
  }

  @Test
  public void getLastSyncSampleTimestampUs_ofSmallMp4File_outputsFirstTimestamp()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample.mp4";
    Mp4ExtractorWrapper mp4ExtractorWrapper = new Mp4ExtractorWrapper(context, mp4FilePath);
    mp4ExtractorWrapper.init();

    long lastSyncSampleTimeStampUs = mp4ExtractorWrapper.getLastSyncSampleTimestampUs();

    long expectedTimestamp = 0;
    assertThat(lastSyncSampleTimeStampUs).isEqualTo(expectedTimestamp);
  }

  @Test
  public void getLastSyncSampleTimestampUs_ofMp4File_outputMatchesExpected() throws IOException {
    String mp4FilePath = "asset:///media/mp4/hdr10-720p.mp4";
    Mp4ExtractorWrapper mp4ExtractorWrapper = new Mp4ExtractorWrapper(context, mp4FilePath);
    mp4ExtractorWrapper.init();

    long lastSyncSampleTimeStampUs = mp4ExtractorWrapper.getLastSyncSampleTimestampUs();

    long expectedTimestamp = 4_003_277L;
    assertThat(lastSyncSampleTimeStampUs).isEqualTo(expectedTimestamp);
  }

  @Test
  public void getLastSyncSampleTimestampUs_ofAudioOnlyMp4File_returnsUnsetValue()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample_ac3.mp4";
    Mp4ExtractorWrapper mp4ExtractorWrapper = new Mp4ExtractorWrapper(context, mp4FilePath);
    mp4ExtractorWrapper.init();

    assertThat(mp4ExtractorWrapper.getLastSyncSampleTimestampUs()).isEqualTo(C.TIME_UNSET);
  }
}

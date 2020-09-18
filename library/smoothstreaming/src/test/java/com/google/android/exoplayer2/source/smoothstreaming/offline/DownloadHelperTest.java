/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test to verify creation of a SmoothStreaming {@link DownloadHelper}. */
@RunWith(AndroidJUnit4.class)
public final class DownloadHelperTest {

  @Test
  public void staticDownloadHelperForSmoothStreaming_doesNotThrow() {
    DownloadHelper.forMediaItem(
        ApplicationProvider.getApplicationContext(),
        new MediaItem.Builder().setUri("http://uri").setMimeType(MimeTypes.APPLICATION_SS).build(),
        (handler, videoListener, audioListener, text, metadata) -> new Renderer[0],
        new FakeDataSource.Factory());
    DownloadHelper.forMediaItem(
        new MediaItem.Builder().setUri("http://uri").setMimeType(MimeTypes.APPLICATION_SS).build(),
        DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT,
        (handler, videoListener, audioListener, text, metadata) -> new Renderer[0],
        new FakeDataSource.Factory(),
        /* drmSessionManager= */ null);
  }
}

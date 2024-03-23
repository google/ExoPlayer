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
package com.google.android.exoplayer2.source.hls;

import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;

public class HlsTestUtils {

  /**
   * Load a mock HLS playlist from a test asset file.
   *
   * @param file - source of the text of the test playlist
   * @param playlistUri - Uri to set as base for the playlist
   * @return test {@link HlsMediaPlaylist}
   */
  public static HlsMediaPlaylist getHlsMediaPlaylist(String file, Uri playlistUri) {
    try {
      return (HlsMediaPlaylist)
          new HlsPlaylistParser()
              .parse(
                  playlistUri,
                  TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), file));
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return null;
  }
}

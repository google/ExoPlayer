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
package androidx.media3.common;

import static androidx.media3.common.FileTypes.HEADER_CONTENT_TYPE;
import static androidx.media3.common.FileTypes.inferFileTypeFromMimeType;
import static androidx.media3.common.FileTypes.inferFileTypeFromUri;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FileTypesTest}. */
@RunWith(AndroidJUnit4.class)
public class FileTypesTest {

  @Test
  public void inferFileFormat_fromResponseHeaders_returnsExpectedFormat() {
    Map<String, List<String>> responseHeaders = new HashMap<>();
    responseHeaders.put(HEADER_CONTENT_TYPE, Collections.singletonList(MimeTypes.VIDEO_MP4));

    assertThat(FileTypes.inferFileTypeFromResponseHeaders(responseHeaders))
        .isEqualTo(FileTypes.MP4);
  }

  @Test
  public void inferFileFormat_fromResponseHeadersWithUnknownContentType_returnsUnknownFormat() {
    Map<String, List<String>> responseHeaders = new HashMap<>();
    responseHeaders.put(HEADER_CONTENT_TYPE, Collections.singletonList("unknown"));

    assertThat(FileTypes.inferFileTypeFromResponseHeaders(responseHeaders))
        .isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void inferFileFormat_fromResponseHeadersWithoutContentType_returnsUnknownFormat() {
    assertThat(FileTypes.inferFileTypeFromResponseHeaders(new HashMap<>()))
        .isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void inferFileFormat_fromMimeType_returnsExpectedFormat() {
    assertThat(FileTypes.inferFileTypeFromMimeType("audio/x-flac")).isEqualTo(FileTypes.FLAC);
  }

  @Test
  public void inferFileFormat_fromUnknownMimeType_returnsUnknownFormat() {
    assertThat(inferFileTypeFromMimeType(/* mimeType= */ "unknown")).isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void inferFileFormat_fromNullMimeType_returnsUnknownFormat() {
    assertThat(inferFileTypeFromMimeType(/* mimeType= */ null)).isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void inferFileFormat_fromUri_returnsExpectedFormat() {
    assertThat(
            inferFileTypeFromUri(
                Uri.parse("http://www.example.com/filename.mp3?query=myquery#fragment")))
        .isEqualTo(FileTypes.MP3);
  }

  @Test
  public void inferFileFormat_fromUriWithExtensionPrefix_returnsExpectedFormat() {
    assertThat(inferFileTypeFromUri(Uri.parse("filename.mka"))).isEqualTo(FileTypes.MATROSKA);
  }

  @Test
  public void inferFileFormat_fromUriWithUnknownExtension_returnsUnknownFormat() {
    assertThat(inferFileTypeFromUri(Uri.parse("filename.unknown"))).isEqualTo(FileTypes.UNKNOWN);
  }

  @Test
  public void inferFileFormat_fromEmptyUri_returnsUnknownFormat() {
    assertThat(inferFileTypeFromUri(Uri.EMPTY)).isEqualTo(FileTypes.UNKNOWN);
  }
}

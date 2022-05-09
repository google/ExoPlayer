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
package androidx.media3.exoplayer.smoothstreaming.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.datasource.PlaceholderDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.Downloader;
import androidx.media3.exoplayer.offline.DownloaderFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit tests for {@link SsDownloader}. */
@RunWith(AndroidJUnit4.class)
public final class SsDownloaderTest {

  @Test
  public void createWithDefaultDownloaderFactory() throws Exception {
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(Mockito.mock(Cache.class))
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY);
    DownloaderFactory factory =
        new DefaultDownloaderFactory(cacheDataSourceFactory, /* executor= */ Runnable::run);

    Downloader downloader =
        factory.createDownloader(
            new DownloadRequest.Builder(/* id= */ "id", Uri.parse("https://www.test.com/download"))
                .setMimeType(MimeTypes.APPLICATION_SS)
                .setStreamKeys(
                    Collections.singletonList(
                        new StreamKey(/* groupIndex= */ 0, /* streamIndex= */ 0)))
                .build());
    assertThat(downloader).isInstanceOf(SsDownloader.class);
  }
}

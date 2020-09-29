/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaSourceDrmHelper}. */
@RunWith(AndroidJUnit4.class)
public class MediaSourceDrmHelperTest {

  @Test
  public void create_noDrmProperties_createsNoopManager() {
    DrmSessionManager drmSessionManager =
        new MediaSourceDrmHelper().create(MediaItem.fromUri(Uri.EMPTY));

    assertThat(drmSessionManager).isEqualTo(DrmSessionManager.DUMMY);
  }

  @Test
  public void create_createsManager() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.EMPTY)
            .setDrmLicenseUri(Uri.EMPTY)
            .setDrmUuid(C.WIDEVINE_UUID)
            .build();

    DrmSessionManager drmSessionManager = new MediaSourceDrmHelper().create(mediaItem);

    assertThat(drmSessionManager).isNotEqualTo(DrmSessionManager.DUMMY);
  }
}

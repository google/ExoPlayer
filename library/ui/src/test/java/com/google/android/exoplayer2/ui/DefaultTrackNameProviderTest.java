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
package com.google.android.exoplayer2.ui;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the {@link DefaultMediaDescriptionAdapter}. */
@RunWith(AndroidJUnit4.class)
public class DefaultTrackNameProviderTest {

  @Test
  public void getTrackName_handlesInvalidLanguage() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    DefaultTrackNameProvider provider = new DefaultTrackNameProvider(resources);
    Format format = new Format.Builder().setLanguage("```").build();

    String name = provider.getTrackName(format);

    assertThat(name).isEqualTo(resources.getString(R.string.exo_track_unknown));
  }
}

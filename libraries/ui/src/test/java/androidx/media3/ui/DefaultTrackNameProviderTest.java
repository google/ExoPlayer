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
package androidx.media3.ui;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;
import androidx.media3.common.Format;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the {@link DefaultMediaDescriptionAdapter}. */
@RunWith(AndroidJUnit4.class)
public class DefaultTrackNameProviderTest {

  @Test
  public void getTrackName_withInvalidLanguage_returnsUnknownWithLanguage() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    DefaultTrackNameProvider provider = new DefaultTrackNameProvider(resources);
    Format format = new Format.Builder().setLanguage("```").build();

    String name = provider.getTrackName(format);

    assertThat(name).isEqualTo(resources.getString(R.string.exo_track_unknown_name, "```"));
  }

  @Test
  public void getTrackName_withLanguageEmptyString_returnsUnknown() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    DefaultTrackNameProvider provider = new DefaultTrackNameProvider(resources);
    Format format = new Format.Builder().setLanguage("").build();

    String name = provider.getTrackName(format);

    assertThat(name).isEqualTo(resources.getString(R.string.exo_track_unknown));
  }

  @Test
  public void getTrackName_withLanguageSpacesNewLine_returnsUnknown() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    DefaultTrackNameProvider provider = new DefaultTrackNameProvider(resources);
    Format format = new Format.Builder().setLanguage("   \n ").build();

    String name = provider.getTrackName(format);

    assertThat(name).isEqualTo(resources.getString(R.string.exo_track_unknown));
  }

  @Test
  public void getTrackName_withLanguageEmptyStringAndLabel_returnsLabel() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    DefaultTrackNameProvider provider = new DefaultTrackNameProvider(resources);
    Format format = new Format.Builder().setLanguage("").setLabel("Main").build();

    String name = provider.getTrackName(format);

    assertThat(name).isEqualTo("Main");
  }
}

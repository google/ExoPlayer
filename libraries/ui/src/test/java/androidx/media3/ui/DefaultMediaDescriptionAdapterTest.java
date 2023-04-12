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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the {@link DefaultMediaDescriptionAdapter}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaDescriptionAdapterTest {

  @Test
  public void getters_withGetMetatadataCommandAvailable_returnMediaMetadataValues() {
    Context context = ApplicationProvider.getApplicationContext();
    Player player = mock(Player.class);
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder().setDisplayTitle("display title").setArtist("artist").build();
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    DefaultMediaDescriptionAdapter adapter = new DefaultMediaDescriptionAdapter(pendingIntent);

    when(player.isCommandAvailable(Player.COMMAND_GET_METADATA)).thenReturn(true);
    when(player.getMediaMetadata()).thenReturn(mediaMetadata);

    assertThat(adapter.createCurrentContentIntent(player)).isEqualTo(pendingIntent);
    assertThat(adapter.getCurrentContentTitle(player).toString())
        .isEqualTo(mediaMetadata.displayTitle.toString());
    assertThat(adapter.getCurrentContentText(player).toString())
        .isEqualTo(mediaMetadata.artist.toString());
  }

  @Test
  public void getters_withoutGetMetatadataCommandAvailable_returnMediaMetadataValues() {
    Context context = ApplicationProvider.getApplicationContext();
    Player player = mock(Player.class);
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder().setDisplayTitle("display title").setArtist("artist").build();
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    DefaultMediaDescriptionAdapter adapter = new DefaultMediaDescriptionAdapter(pendingIntent);

    when(player.isCommandAvailable(Player.COMMAND_GET_METADATA)).thenReturn(false);
    when(player.getMediaMetadata()).thenReturn(mediaMetadata);

    assertThat(adapter.createCurrentContentIntent(player)).isEqualTo(pendingIntent);
    assertThat(adapter.getCurrentContentTitle(player).toString()).isEqualTo("");
    assertThat(adapter.getCurrentContentText(player)).isNull();
  }
}

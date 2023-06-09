/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.upstream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CmcdConfiguration}. */
@RunWith(AndroidJUnit4.class)
public class CmcdConfigurationTest {

  private static final String TEST_CONTENT_ID = "contentId";
  private static final String TEST_MEDIA_ID = "mediaId";
  private static final String TEST_SESSION_ID = "sessionId";
  private static final String LONG_INVALID_ID =
      "9haaks0aousdjts41iczi1ilmkzxrbwf7hkuesvzt2ib44s8cmjtzfcmenzy3ozp67890qwertyuiopasd";

  @Test
  public void invalidSessionId_throwsError() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CmcdConfiguration(
                /* sessionId= */ LONG_INVALID_ID,
                /* contentId= */ null,
                new CmcdConfiguration.RequestConfig() {}));
  }

  @Test
  public void invalidContentId_throwsError() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CmcdConfiguration(
                /* sessionId= */ null,
                /* contentId= */ LONG_INVALID_ID,
                new CmcdConfiguration.RequestConfig() {}));
  }

  @Test
  public void nullRequestConfig_throwsError() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CmcdConfiguration(
                /* sessionId= */ null, /* contentId= */ null, /* requestConfig= */ null));
  }

  @Test
  public void defaultFactory_createsInstance() {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId(TEST_MEDIA_ID).build();

    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);

    assertThat(cmcdConfiguration.contentId).isEqualTo(TEST_MEDIA_ID);
    assertThat(cmcdConfiguration.isBitrateLoggingAllowed()).isTrue();
    assertThat(cmcdConfiguration.requestConfig.getCustomData()).isEmpty();
    assertThat(
            cmcdConfiguration.requestConfig.getRequestedMaximumThroughputKbps(
                /* throughputKbps= */ 0))
        .isEqualTo(C.RATE_UNSET_INT);
  }

  @Test
  public void customFactory_createsInstance() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                TEST_SESSION_ID,
                TEST_CONTENT_ID,
                new CmcdConfiguration.RequestConfig() {
                  @Override
                  public boolean isKeyAllowed(@CmcdConfiguration.CmcdKey String key) {
                    return key.equals("br") || key.equals("rtp");
                  }

                  @Override
                  public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> getCustomData() {
                    return new ImmutableMap.Builder<String, String>()
                        .put("CMCD-Object", "key1=value1")
                        .put("CMCD-Request", "key2=\"stringValue\"")
                        .buildOrThrow();
                  }

                  @Override
                  public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                    return 2 * throughputKbps;
                  }
                });
    MediaItem mediaItem = new MediaItem.Builder().setMediaId(TEST_MEDIA_ID).build();

    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);

    assertThat(cmcdConfiguration.sessionId).isEqualTo(TEST_SESSION_ID);
    assertThat(cmcdConfiguration.contentId).isEqualTo(TEST_CONTENT_ID);
    assertThat(cmcdConfiguration.isBitrateLoggingAllowed()).isTrue();
    assertThat(cmcdConfiguration.isBufferLengthLoggingAllowed()).isFalse();
    assertThat(cmcdConfiguration.isContentIdLoggingAllowed()).isFalse();
    assertThat(cmcdConfiguration.isSessionIdLoggingAllowed()).isFalse();
    assertThat(cmcdConfiguration.isMaximumRequestThroughputLoggingAllowed()).isTrue();
    assertThat(cmcdConfiguration.requestConfig.getCustomData())
        .containsExactly("CMCD-Object", "key1=value1", "CMCD-Request", "key2=\"stringValue\"");
    assertThat(
            cmcdConfiguration.requestConfig.getRequestedMaximumThroughputKbps(
                /* throughputKbps= */ 100))
        .isEqualTo(200);
  }
}

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
package androidx.media3.exoplayer.rtsp;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.exoplayer.rtsp.RtspMessageUtil.RtspAuthUserInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspAuthenticationInfo}. */
@RunWith(AndroidJUnit4.class)
public class RtspAuthenticationInfoTest {

  @Test
  public void getAuthorizationHeaderValue_withBasicAuthenticationMechanism_getsCorrectHeaderValue()
      throws Exception {
    String authenticationRealm = "WallyWorld";
    String username = "Aladdin";
    String password = "open sesame";
    String expectedAuthorizationHeaderValue = "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==\n";
    RtspAuthenticationInfo authenticator =
        new RtspAuthenticationInfo(
            RtspAuthenticationInfo.BASIC, authenticationRealm, /* nonce= */ "", /* opaque= */ "");

    assertThat(
            authenticator.getAuthorizationHeaderValue(
                new RtspAuthUserInfo(username, password), Uri.EMPTY, RtspRequest.METHOD_DESCRIBE))
        .isEqualTo(expectedAuthorizationHeaderValue);
  }

  @Test
  public void getAuthorizationHeaderValue_withDigestAuthenticationMechanism_getsCorrectHeaderValue()
      throws Exception {
    RtspAuthenticationInfo authenticator =
        new RtspAuthenticationInfo(
            RtspAuthenticationInfo.DIGEST,
            /* realm= */ "RTSP server",
            /* nonce= */ "0cdfe9719e7373b7d5bb2913e2115f3f",
            /* opaque= */ "5ccc069c403ebaf9f0171e9517f40e41");

    assertThat(
            authenticator.getAuthorizationHeaderValue(
                new RtspAuthUserInfo("username", "password"),
                Uri.parse("rtsp://localhost:554/imax_cd_2k_264_6ch.mkv"),
                RtspRequest.METHOD_DESCRIBE))
        .isEqualTo(
            "Digest username=\"username\", realm=\"RTSP server\","
                + " nonce=\"0cdfe9719e7373b7d5bb2913e2115f3f\","
                + " uri=\"rtsp://localhost:554/imax_cd_2k_264_6ch.mkv\","
                + " response=\"cb635712efbdd027f0c823d0623449f6\","
                + " opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
  }
}

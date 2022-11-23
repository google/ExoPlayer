/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_SESSION_SERVICE;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link SessionToken}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SessionTokenTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();
  private static final String TAG = "SessionTokenTest";

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);
  private final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();
  @Rule public final TestRule chain = RuleChain.outerRule(threadTestRule).around(sessionTestRule);
  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void constructor_sessionService() {
    SessionToken token =
        new SessionToken(
            context,
            new ComponentName(
                context.getPackageName(), MockMediaSessionService.class.getCanonicalName()));

    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_SESSION_SERVICE);
    assertThat(token.getInterfaceVersion()).isEqualTo(0);
    assertThat(token.getSessionVersion()).isEqualTo(0);
  }

  @Test
  public void constructor_libraryService() {
    ComponentName testComponentName =
        new ComponentName(
            context.getPackageName(), MockMediaLibraryService.class.getCanonicalName());

    SessionToken token = new SessionToken(context, testComponentName);

    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_LIBRARY_SERVICE);
    assertThat(token.getServiceName()).isEqualTo(testComponentName.getClassName());
    assertThat(token.getInterfaceVersion()).isEqualTo(0);
    assertThat(token.getSessionVersion()).isEqualTo(0);
  }

  @Test
  public void getters_whenCreatedBySession() {
    Bundle testTokenExtras = TestUtils.createTestBundle();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, new MockPlayer.Builder().build())
                .setId("getters_whenCreatedBySession")
                .setExtras(testTokenExtras)
                .build());
    SessionToken token = session.getToken();

    assertThat(token.getPackageName()).isEqualTo(context.getPackageName());
    assertThat(token.getUid()).isEqualTo(Process.myUid());
    assertThat(token.getType()).isEqualTo(SessionToken.TYPE_SESSION);
    assertThat(token.getSessionVersion()).isEqualTo(MediaLibraryInfo.VERSION_INT);
    assertThat(token.getInterfaceVersion()).isEqualTo(MediaSessionStub.VERSION_INT);
    assertThat(TestUtils.equals(testTokenExtras, token.getExtras())).isTrue();
    assertThat(token.getServiceName()).isEmpty();
  }

  @Test
  public void createSessionToken_withPlatformTokenFromMedia1Session_returnsTokenForLegacySession()
      throws Exception {
    assumeTrue(Util.SDK_INT >= 21);

    MediaSessionCompat sessionCompat =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSessionCompat(context, "createSessionToken_withLegacyToken"));

    SessionToken token =
        SessionToken.createSessionToken(
                context,
                (android.media.session.MediaSession.Token)
                    sessionCompat.getSessionToken().getToken())
            .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(token.isLegacySession()).isTrue();
  }

  @Test
  public void createSessionToken_withCompatTokenFromMedia1Session_returnsTokenForLegacySession()
      throws Exception {
    MediaSessionCompat sessionCompat =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSessionCompat(context, "createSessionToken_withLegacyToken"));

    SessionToken token =
        SessionToken.createSessionToken(context, sessionCompat.getSessionToken())
            .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(token.isLegacySession()).isTrue();
  }

  @Test
  public void createSessionToken_withCompatTokenFromMedia3Session_returnsTokenForNonLegacySession()
      throws Exception {
    // TODO(b/194458970): Make the callback of session and controller on the same thread work and
    //  remove the threadTestRule
    MediaSession session =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    sessionTestRule.ensureReleaseAfterTest(
                        new MediaSession.Builder(context, new MockPlayer.Builder().build())
                            .setId(TAG)
                            .build()));
    SessionToken token =
        SessionToken.createSessionToken(context, session.getSessionCompatToken())
            .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertThat(token.isLegacySession()).isFalse();
  }

  @Test
  public void getSessionServiceTokens() {
    boolean hasMockBrowserServiceCompat = false;
    boolean hasMockSessionService2 = false;
    boolean hasMockLibraryService2 = false;
    ComponentName mockBrowserServiceCompatName =
        new ComponentName(
            SUPPORT_APP_PACKAGE_NAME, MockMediaBrowserServiceCompat.class.getCanonicalName());

    Set<SessionToken> serviceTokens =
        SessionToken.getAllServiceTokens(ApplicationProvider.getApplicationContext());
    for (SessionToken token : serviceTokens) {
      ComponentName componentName = token.getComponentName();
      if (mockBrowserServiceCompatName.equals(componentName)) {
        hasMockBrowserServiceCompat = true;
      } else if (MOCK_MEDIA3_SESSION_SERVICE.equals(componentName)) {
        hasMockSessionService2 = true;
      } else if (MOCK_MEDIA3_LIBRARY_SERVICE.equals(componentName)) {
        hasMockLibraryService2 = true;
      }
    }

    assertThat(hasMockBrowserServiceCompat).isTrue();
    assertThat(hasMockSessionService2).isTrue();
    assertThat(hasMockLibraryService2).isTrue();
  }
}

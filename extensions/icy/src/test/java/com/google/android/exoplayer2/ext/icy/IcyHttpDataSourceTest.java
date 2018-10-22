package com.google.android.exoplayer2.ext.icy;

import com.google.android.exoplayer2.ext.icy.IcyHttpDataSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import okhttp3.OkHttpClient;
import com.google.android.exoplayer2.ext.icy.test.Constants;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class IcyHttpDataSourceTest {

  @Test
  public void createDataSourceFromBuilder() {
    // Arrange, act
    OkHttpClient client = new OkHttpClient.Builder().build();
    IcyHttpDataSource source = new IcyHttpDataSource.Builder(client)
        .setUserAgent(Constants.TEST_USER_AGENT)
        .build();

    // Assert
    assertNotNull(source);
  }
}

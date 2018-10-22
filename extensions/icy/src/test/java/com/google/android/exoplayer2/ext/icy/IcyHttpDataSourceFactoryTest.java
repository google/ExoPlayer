package com.google.android.exoplayer2.ext.icy;

import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import okhttp3.OkHttpClient;
import com.google.android.exoplayer2.ext.icy.test.Constants;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class IcyHttpDataSourceFactoryTest {

  private final IcyHttpDataSource.IcyHeadersListener TEST_ICY_HEADERS_LISTENER = icyHeaders -> {
  };
  private final IcyHttpDataSource.IcyMetadataListener TEST_ICY_METADATA_LISTENER = icyMetadata -> {
  };

  @Test
  public void createDataSourceViaFactoryFromFactoryBuilder() {
    // Arrange
    OkHttpClient client = new OkHttpClient.Builder().build();
    IcyHttpDataSourceFactory factory = new IcyHttpDataSourceFactory.Builder(client)
        .setUserAgent(Constants.TEST_USER_AGENT)
        .setIcyHeadersListener(TEST_ICY_HEADERS_LISTENER)
        .setIcyMetadataChangeListener(TEST_ICY_METADATA_LISTENER)
        .build();
    HttpDataSource.RequestProperties requestProperties = new HttpDataSource.RequestProperties();

    // Act
    IcyHttpDataSource source = factory.createDataSourceInternal(requestProperties);

    // Assert
    assertNotNull(source);
  }
}

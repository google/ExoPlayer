package com.google.android.exoplayer2.ext.icy;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Predicate;

import okhttp3.CacheControl;
import okhttp3.Call;

/**
 * A {@link HttpDataSource.Factory} that produces {@link IcyHttpDataSource} instances.
 */
public final class IcyHttpDataSourceFactory extends OkHttpDataSource.BaseFactory {

  private Call.Factory callFactory;
  private String userAgent;
  private Predicate<String> contentTypePredicate;
  private CacheControl cacheControl;
  private IcyHttpDataSource.IcyHeadersListener icyHeadersListener;
  private IcyHttpDataSource.IcyMetadataListener icyMetadataListener;

  private IcyHttpDataSourceFactory() {
    // See class Builder
  }

  /**
   * Constructs a IcyHttpDataSourceFactory.
   */
  public final static class Builder {

    private final IcyHttpDataSourceFactory factory;

    public Builder(@NonNull Call.Factory callFactory) {
      // Apply defaults
      factory = new IcyHttpDataSourceFactory();
      factory.callFactory = callFactory;
    }

    public Builder setUserAgent(@NonNull final String userAgent) {
      factory.userAgent = userAgent;
      return this;
    }

    public Builder setContentTypePredicate(@NonNull final Predicate<String> contentTypePredicate) {
      factory.contentTypePredicate = contentTypePredicate;
      return this;
    }

    public Builder setCacheControl(@NonNull final CacheControl cacheControl) {
      factory.cacheControl = cacheControl;
      return this;
    }

    public Builder setIcyHeadersListener(
        @NonNull final IcyHttpDataSource.IcyHeadersListener icyHeadersListener) {
      factory.icyHeadersListener = icyHeadersListener;
      return this;
    }

    public Builder setIcyMetadataChangeListener(
        @NonNull final IcyHttpDataSource.IcyMetadataListener icyMetadataListener) {
      factory.icyMetadataListener = icyMetadataListener;
      return this;
    }

    public IcyHttpDataSourceFactory build() {
      return factory;
    }
  }

  @Override
  protected IcyHttpDataSource createDataSourceInternal(
      @NonNull HttpDataSource.RequestProperties defaultRequestProperties) {
    return new IcyHttpDataSource.Builder(callFactory)
        .setUserAgent(userAgent)
        .setContentTypePredicate(contentTypePredicate)
        .setCacheControl(cacheControl)
        .setDefaultRequestProperties(defaultRequestProperties)
        .setIcyHeadersListener(icyHeadersListener)
        .setIcyMetadataListener(icyMetadataListener)
        .build();
  }
}

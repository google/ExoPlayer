package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.upstream.RtmpDataSource.Factory;

/** A {@link Factory} that produces {@link RtmpDataSource} instances. */
public class RtmpDataSourceFactory implements Factory {

  private final TransferListener<? super DataSource> listener;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;

  /**
   * Constructs a RtmpDataSourceFactory. Sets {@link
   * RtmpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
   * RtmpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout.
   *
   */
  public RtmpDataSourceFactory() {
        this(null);
    }

  /**
   * Constructs a RtmpDataSourceFactory. Sets {@link
   * RtmpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
   * RtmpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
   * cross-protocol redirects.
   *
   * @param listener An optional listener.
   * @see #RtmpDataSourceFactory(TransferListener, int, int)
   */
  public RtmpDataSourceFactory(TransferListener<? super DataSource> listener) {
    this(listener, RtmpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, RtmpDataSource.DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
   *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout that should be used when requesting remote data, in
   *     milliseconds. A timeout of zero is interpreted as an infinite timeout.
   */
  public RtmpDataSourceFactory(TransferListener<? super DataSource> listener,
                               int connectTimeoutMillis,int readTimeoutMillis) {
    this.listener = listener;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public RtmpDataSource createDataSource() {
    return new RtmpDataSource(listener, connectTimeoutMillis, readTimeoutMillis);
  }
}

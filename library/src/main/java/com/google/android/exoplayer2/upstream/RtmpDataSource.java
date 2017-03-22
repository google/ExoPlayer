package com.google.android.exoplayer2.upstream;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.rtmp.DefaultRtmpPlayer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An RTMP {@link DataSource}.
 */
public final class RtmpDataSource implements DataSource {

  /**
   * A factory for {@link RtmpDataSource} instances.
   */
  public interface Factory extends DataSource.Factory {

    @Override
    RtmpDataSource createDataSource();

  }

  /**
   * Thrown when an error is encountered when trying to read from a {@link RtmpDataSource}.
   */
  public static final class RtmpDataSourceException extends IOException {

    public RtmpDataSourceException(String msg) {
      super(msg);
    }

    public RtmpDataSourceException(IOException cause) {
      super(cause);
    }

    public RtmpDataSourceException(String msg, IOException cause) {
      super(msg, cause);
    }

  }

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3 * 1000;

  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private final TransferListener<? super RtmpDataSource> listener;

  private Uri uri;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final static byte[] sniffHeader = new byte[] { 'R', 'T', 'M', 'P' };
  private boolean needToSniff = false;
  private boolean opened = false;
  private DefaultRtmpPlayer player = new DefaultRtmpPlayer();
  private ByteBuffer mediaData;

  /**
   * @param listener An optional listener.
   */
  public RtmpDataSource(TransferListener<? super RtmpDataSource> listener) {
    this(listener, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted
   *     as an infinite timeout.
   */
  public RtmpDataSource(TransferListener<? super RtmpDataSource> listener, int connectTimeoutMillis,
                        int readTimeoutMillis) {
    this.listener = listener;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }
    
  @Override
  public long open(DataSpec dataSpec) throws RtmpDataSourceException {
    uri = dataSpec.uri;
    try {
      player.connect(uri.toString(), connectTimeoutMillis);
    } catch (IOException ioe) {
        throw new RtmpDataSourceException("Unable to connect to " + uri.toString(), ioe);
    }

    needToSniff = true;
    opened = true;
    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }
    return C.LENGTH_UNSET;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws RtmpDataSourceException {
    int bytes = 0;

    if (needToSniff) {
      System.arraycopy(sniffHeader, 0, buffer, offset, sniffHeader.length);
      needToSniff = false;
      return sniffHeader.length;
    }

    if (mediaData == null || mediaData.remaining() == 0) {
      mediaData = player.poll();
    }

    if (mediaData != null) {
      bytes = mediaData.remaining() >= readLength ? readLength : mediaData.remaining();
      mediaData.get(buffer, offset, bytes);
    }

    if (listener != null) {
      listener.onBytesTransferred(this, 0);
    }
    return bytes;
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws RtmpDataSourceException {
    try {
      player.close();
    } catch (IllegalStateException e) {
      // Ignore illegal state.
    } catch (IOException ioe) {
      ioe.printStackTrace();
    } finally {
      if (opened) {
        opened = false;
        if (listener != null) {
          listener.onTransferEnd(this);
        }
      }
    }
  }
}

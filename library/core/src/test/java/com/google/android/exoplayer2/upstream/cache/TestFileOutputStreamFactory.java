package com.google.android.exoplayer2.upstream.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Test class for emulating device IOException when writing and flushing bytes into disk.
 */
public class TestFileOutputStreamFactory implements CacheDataSink.FileOutputStreamFactory {

  private boolean throwExceptionOnWrite;
  private boolean throwExceptionOnFlush;

  /**
   * Simulate device IOException on OutputStream.write()
   *
   * @return this factory
   */
  public TestFileOutputStreamFactory throwOnWrite() {
    throwExceptionOnWrite = true;
    return this;
  }

  /**
   * Simulate device IOException on OutputStream.flush()
   *
   * @return this factory
   */
  public TestFileOutputStreamFactory throwOnFlush() {
    throwExceptionOnFlush = true;
    return this;
  }

  /**
   * Simulate device IOException on OutputStream.write() and OutputStream.flush()
   *
   * @return this factory
   */
  public TestFileOutputStreamFactory emulateNoSpaceLeft() {
    throwExceptionOnWrite = true;
    throwExceptionOnFlush = true;
    return this;
  }

  @Override
  public OutputStream createOutputStream(File file) throws IOException {
    return new FileOutputStream(file) {
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);

        if (throwExceptionOnWrite) {
          throw new IOException("Emulate no space left on a device error while write().");
        }
      }

      @Override
      public void flush() throws IOException {
        super.flush();

        if (throwExceptionOnFlush) {
          throw new IOException("Emulate no space left on a device error while flush().");
        }
      }
    };
  }
}

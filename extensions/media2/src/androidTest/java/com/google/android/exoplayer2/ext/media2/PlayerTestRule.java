/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.rules.ExternalResource;

/** Rule for tests that use {@link SessionPlayerConnector}. */
/* package */ final class PlayerTestRule extends ExternalResource {

  /** Instrumentation to attach to {@link DataSource} instances used by the player. */
  public interface DataSourceInstrumentation {

    /** Called at the start of {@link DataSource#open}. */
    void onPreOpen(DataSpec dataSpec);
  }

  private Context context;
  private ExecutorService executor;

  private SessionPlayerConnector sessionPlayerConnector;
  private ExoPlayer exoPlayer;
  @Nullable private DataSourceInstrumentation dataSourceInstrumentation;

  @Override
  protected void before() {
    context = ApplicationProvider.getApplicationContext();
    executor = Executors.newFixedThreadPool(1);

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              // Initialize AudioManager on the main thread to workaround that
              // audio focus listener is called on the thread where the AudioManager was
              // originally initialized. [Internal: b/78617702]
              // Without posting this, audio focus listeners wouldn't be called because the
              // listeners would be posted to the test thread (here) where it waits until the
              // tests are finished.
              context.getSystemService(Context.AUDIO_SERVICE);

              DataSource.Factory dataSourceFactory = new InstrumentingDataSourceFactory(context);
              exoPlayer =
                  new ExoPlayer.Builder(context)
                      .setLooper(Looper.myLooper())
                      .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                      .build();
              sessionPlayerConnector = new SessionPlayerConnector(exoPlayer);
            });
  }

  @Override
  protected void after() {
    if (sessionPlayerConnector != null) {
      sessionPlayerConnector.close();
      sessionPlayerConnector = null;
    }
    if (exoPlayer != null) {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                exoPlayer.release();
                exoPlayer = null;
              });
    }
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }

  public void setDataSourceInstrumentation(
      @Nullable DataSourceInstrumentation dataSourceInstrumentation) {
    this.dataSourceInstrumentation = dataSourceInstrumentation;
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  public SessionPlayerConnector getSessionPlayerConnector() {
    return sessionPlayerConnector;
  }

  public ExoPlayer getExoPlayer() {
    return exoPlayer;
  }

  private final class InstrumentingDataSourceFactory implements DataSource.Factory {

    private final DefaultDataSource.Factory defaultDataSourceFactory;

    public InstrumentingDataSourceFactory(Context context) {
      defaultDataSourceFactory = new DefaultDataSource.Factory(context);
    }

    @Override
    public DataSource createDataSource() {
      DataSource dataSource = defaultDataSourceFactory.createDataSource();
      return dataSourceInstrumentation == null
          ? dataSource
          : new InstrumentedDataSource(dataSource, dataSourceInstrumentation);
    }
  }

  private static final class InstrumentedDataSource implements DataSource {

    private final DataSource wrappedDataSource;
    private final DataSourceInstrumentation instrumentation;

    public InstrumentedDataSource(
        DataSource wrappedDataSource, DataSourceInstrumentation instrumentation) {
      this.wrappedDataSource = wrappedDataSource;
      this.instrumentation = instrumentation;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
      wrappedDataSource.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
      instrumentation.onPreOpen(dataSpec);
      return wrappedDataSource.open(dataSpec);
    }

    @Nullable
    @Override
    public Uri getUri() {
      return wrappedDataSource.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return wrappedDataSource.getResponseHeaders();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      return wrappedDataSource.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
      wrappedDataSource.close();
    }
  }
}

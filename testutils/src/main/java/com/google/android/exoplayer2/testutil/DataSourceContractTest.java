/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * A collection of contract tests for {@link DataSource} implementations.
 *
 * <p>All these tests should pass for all implementations - behaviour specific to only a subset of
 * implementations should be tested elsewhere.
 *
 * <p>Subclasses should only include the logic necessary to construct the DataSource and allow it to
 * successfully read data. They shouldn't include any new {@link Test @Test} methods -
 * implementation-specific tests should be in a separate class.
 *
 * <p>If one of these tests fails for a particular {@link DataSource} implementation, that's a bug
 * in the implementation. The test should be overridden in the subclass and annotated {@link
 * Ignore}, with a link to an issue to track fixing the implementation and un-ignoring the test.
 */
@RequiresApi(19)
public abstract class DataSourceContractTest {

  @Rule public final AdditionalFailureInfo additionalFailureInfo = new AdditionalFailureInfo();

  /** Creates and returns an instance of the {@link DataSource}. */
  protected abstract DataSource createDataSource() throws Exception;

  /**
   * Returns the {@link DataSource} that will be included in the {@link TransferListener} callbacks
   * for the {@link DataSource} most recently created by {@link #createDataSource()}. If it's the
   * same {@link DataSource} then {@code null} can be returned.
   */
  @Nullable
  protected DataSource getTransferListenerDataSource() {
    return null;
  }

  /**
   * Returns whether the {@link DataSource} will continue reading indefinitely for unbounded {@link
   * DataSpec DataSpecs}.
   */
  protected boolean unboundedReadsAreIndefinite() {
    return false;
  }

  /**
   * Returns {@link TestResource} instances.
   *
   * <p>Each resource will be used to exercise the {@link DataSource} instance, allowing different
   * behaviours to be tested.
   *
   * <p>If multiple resources are returned, it's recommended to disambiguate them using {@link
   * TestResource.Builder#setName(String)}.
   */
  protected abstract ImmutableList<TestResource> getTestResources() throws Exception;

  /**
   * Returns a {@link Uri} that doesn't resolve.
   *
   * <p>This is used to test how a {@link DataSource} handles nonexistent data.
   */
  protected abstract Uri getNotFoundUri();

  @Test
  public void unboundedDataSpec_readUntilEnd() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length = dataSource.open(new DataSpec(resource.getUri()));
        byte[] data =
            unboundedReadsAreIndefinite()
                ? Util.readExactly(dataSource, resource.getExpectedBytes().length)
                : Util.readToEnd(dataSource);

        if (length != C.LENGTH_UNSET) {
          assertThat(length).isEqualTo(resource.getExpectedBytes().length);
        }
        assertThat(data).isEqualTo(resource.getExpectedBytes());
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithPosition_readUntilEnd() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length =
            dataSource.open(
                new DataSpec.Builder().setUri(resource.getUri()).setPosition(3).build());
        byte[] data =
            unboundedReadsAreIndefinite()
                ? Util.readExactly(dataSource, resource.getExpectedBytes().length - 3)
                : Util.readToEnd(dataSource);

        if (length != C.LENGTH_UNSET) {
          assertThat(length).isEqualTo(resource.getExpectedBytes().length - 3);
        }
        byte[] expectedData =
            Arrays.copyOfRange(resource.getExpectedBytes(), 3, resource.getExpectedBytes().length);
        assertThat(data).isEqualTo(expectedData);
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithLength_readExpectedRange() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length =
            dataSource.open(new DataSpec.Builder().setUri(resource.getUri()).setLength(4).build());
        byte[] data = Util.readToEnd(dataSource);

        assertThat(length).isEqualTo(4);
        byte[] expectedData = Arrays.copyOf(resource.getExpectedBytes(), 4);
        assertThat(data).isEqualTo(expectedData);
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithPositionAndLength_readExpectedRange() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length =
            dataSource.open(
                new DataSpec.Builder()
                    .setUri(resource.getUri())
                    .setPosition(2)
                    .setLength(2)
                    .build());
        byte[] data = Util.readToEnd(dataSource);

        assertThat(length).isEqualTo(2);
        byte[] expectedData = Arrays.copyOfRange(resource.getExpectedBytes(), 2, 4);
        assertThat(data).isEqualTo(expectedData);
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithPositionAtEnd_throwsPositionOutOfRangeException() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      int resourceLength = resource.getExpectedBytes().length;
      DataSource dataSource = createDataSource();
      DataSpec dataSpec =
          new DataSpec.Builder().setUri(resource.getUri()).setPosition(resourceLength).build();
      try {
        long length = dataSource.open(dataSpec);
        byte[] data =
            unboundedReadsAreIndefinite() ? Util.EMPTY_BYTE_ARRAY : Util.readToEnd(dataSource);

        // The DataSource.open() contract requires the returned length to equal the length in the
        // DataSpec if set. This is true even though the DataSource implementation may know that
        // fewer bytes will be read in this case.
        if (length != C.LENGTH_UNSET) {
          assertThat(length).isEqualTo(0);
        }
        assertThat(data).isEmpty();
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithPositionAtEndAndLength_throwsPositionOutOfRangeException()
      throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      int resourceLength = resource.getExpectedBytes().length;
      DataSource dataSource = createDataSource();
      DataSpec dataSpec =
          new DataSpec.Builder()
              .setUri(resource.getUri())
              .setPosition(resourceLength)
              .setLength(1)
              .build();
      try {
        long length = dataSource.open(dataSpec);
        byte[] data =
            unboundedReadsAreIndefinite() ? Util.EMPTY_BYTE_ARRAY : Util.readToEnd(dataSource);

        // The DataSource.open() contract requires the returned length to equal the length in the
        // DataSpec if set. This is true even though the DataSource implementation may know that
        // fewer bytes will be read in this case.
        assertThat(length).isEqualTo(1);
        assertThat(data).isEmpty();
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithPositionOutOfRange_throwsPositionOutOfRangeException() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      int resourceLength = resource.getExpectedBytes().length;
      DataSource dataSource = createDataSource();
      DataSpec dataSpec =
          new DataSpec.Builder().setUri(resource.getUri()).setPosition(resourceLength + 1).build();
      try {
        IOException exception = assertThrows(IOException.class, () -> dataSource.open(dataSpec));
        assertThat(DataSourceException.isCausedByPositionOutOfRange(exception)).isTrue();
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void dataSpecWithEndPositionOutOfRange_readsToEnd() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      int resourceLength = resource.getExpectedBytes().length;
      DataSource dataSource = createDataSource();
      DataSpec dataSpec =
          new DataSpec.Builder()
              .setUri(resource.getUri())
              .setPosition(resourceLength - 1)
              .setLength(2)
              .build();
      try {
        long length = dataSource.open(dataSpec);
        byte[] data = Util.readExactly(dataSource, /* length= */ 1);
        // TODO: Decide what the allowed behavior should be for the next read, and assert it.

        // The DataSource.open() contract requires the returned length to equal the length in the
        // DataSpec if set. This is true even though the DataSource implementation may know that
        // fewer bytes will be read in this case.
        assertThat(length).isEqualTo(2);
        byte[] expectedData =
            Arrays.copyOfRange(resource.getExpectedBytes(), resourceLength - 1, resourceLength);
        assertThat(data).isEqualTo(expectedData);
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  /**
   * {@link DataSpec#FLAG_ALLOW_GZIP} should either be ignored by {@link DataSource}
   * implementations, or correctly handled (i.e. the data is decompressed before being returned from
   * {@link DataSource#read(byte[], int, int)}).
   */
  @Test
  public void unboundedDataSpecWithGzipFlag_readUntilEnd() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length =
            dataSource.open(
                new DataSpec.Builder()
                    .setUri(resource.getUri())
                    .setFlags(DataSpec.FLAG_ALLOW_GZIP)
                    .build());
        byte[] data =
            unboundedReadsAreIndefinite()
                ? Util.readExactly(dataSource, resource.getExpectedBytes().length)
                : Util.readToEnd(dataSource);

        if (length != C.LENGTH_UNSET) {
          assertThat(length).isEqualTo(resource.getExpectedBytes().length);
        }
        assertThat(data).isEqualTo(resource.getExpectedBytes());
      } finally {
        dataSource.close();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void resourceNotFound() throws Exception {
    DataSource dataSource = createDataSource();
    assertThrows(IOException.class, () -> dataSource.open(new DataSpec(getNotFoundUri())));
    dataSource.close();
  }

  @Test
  public void transferListenerCallbacks() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      DataSource dataSource = createDataSource();
      FakeTransferListener listener = spy(new FakeTransferListener());
      dataSource.addTransferListener(listener);
      InOrder inOrder = Mockito.inOrder(listener);
      @Nullable DataSource callbackSource = getTransferListenerDataSource();
      if (callbackSource == null) {
        callbackSource = dataSource;
      }
      DataSpec reportedDataSpec = null;
      boolean reportedNetwork = false;

      TestResource resource = resources.get(i);
      DataSpec dataSpec = new DataSpec.Builder().setUri(resource.getUri()).build();
      try {
        dataSource.open(dataSpec);

        // Verify onTransferInitializing() and onTransferStart() have been called exactly from
        // DataSource.open().
        ArgumentCaptor<DataSpec> dataSpecArgumentCaptor = ArgumentCaptor.forClass(DataSpec.class);
        ArgumentCaptor<Boolean> isNetworkArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        inOrder
            .verify(listener)
            .onTransferInitializing(
                eq(callbackSource),
                dataSpecArgumentCaptor.capture(),
                isNetworkArgumentCaptor.capture());
        reportedDataSpec = dataSpecArgumentCaptor.getValue();
        reportedNetwork = isNetworkArgumentCaptor.getValue();
        inOrder
            .verify(listener)
            .onTransferStart(callbackSource, castNonNull(reportedDataSpec), reportedNetwork);
        inOrder.verifyNoMoreInteractions();

        if (unboundedReadsAreIndefinite()) {
          Util.readExactly(dataSource, resource.getExpectedBytes().length);
        } else {
          Util.readToEnd(dataSource);
        }
        // Verify sufficient onBytesTransferred() callbacks have been triggered before closing the
        // DataSource.
        assertThat(listener.bytesTransferred).isAtLeast(resource.getExpectedBytes().length);

      } finally {
        dataSource.close();
        inOrder
            .verify(listener)
            .onTransferEnd(callbackSource, castNonNull(reportedDataSpec), reportedNetwork);
        inOrder.verifyNoMoreInteractions();
      }
      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void resourceNotFound_transferListenerCallbacks() throws Exception {
    DataSource dataSource = createDataSource();
    TransferListener listener = mock(TransferListener.class);
    dataSource.addTransferListener(listener);
    @Nullable DataSource callbackSource = getTransferListenerDataSource();
    if (callbackSource == null) {
      callbackSource = dataSource;
    }

    assertThrows(IOException.class, () -> dataSource.open(new DataSpec(getNotFoundUri())));

    // Verify onTransferInitializing() has been called exactly from DataSource.open().
    verify(listener).onTransferInitializing(eq(callbackSource), any(), anyBoolean());
    verifyNoMoreInteractions(listener);

    dataSource.close();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void getUri_returnsNonNullValueOnlyWhileOpen() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        assertThat(dataSource.getUri()).isNull();

        dataSource.open(new DataSpec(resource.getUri()));

        assertThat(dataSource.getUri()).isNotNull();
      } finally {
        dataSource.close();
      }
      assertThat(dataSource.getUri()).isNull();

      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void getUri_resourceNotFound_returnsNullIfNotOpened() throws Exception {
    DataSource dataSource = createDataSource();

    assertThat(dataSource.getUri()).isNull();

    assertThrows(IOException.class, () -> dataSource.open(new DataSpec(getNotFoundUri())));
    dataSource.close();

    assertThat(dataSource.getUri()).isNull();
  }

  @Test
  public void getResponseHeaders_isEmptyWhileNotOpen() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        assertThat(dataSource.getResponseHeaders()).isEmpty();

        dataSource.open(new DataSpec(resource.getUri()));
      } finally {
        dataSource.close();
      }
      assertThat(dataSource.getResponseHeaders()).isEmpty();

      additionalFailureInfo.setInfo(null);
    }
  }

  @Test
  public void getResponseHeaders_resourceNotFound_isEmptyWhileNotOpen() throws Exception {
    DataSource dataSource = createDataSource();

    assertThat(dataSource.getResponseHeaders()).isEmpty();

    assertThrows(IOException.class, () -> dataSource.open(new DataSpec(getNotFoundUri())));
    dataSource.close();

    assertThat(dataSource.getResponseHeaders()).isEmpty();
  }

  /** Build a label to make it clear which resource caused a given test failure. */
  private static String getFailureLabel(List<TestResource> resources, int i) {
    if (resources.size() == 1) {
      return "";
    } else if (resources.get(i).getName() != null) {
      return "resource name: " + resources.get(i).getName();
    } else {
      return String.format("resource[%s]", i);
    }
  }

  /** Information about a resource that can be used to test the {@link DataSource} instance. */
  public static final class TestResource {

    @Nullable private final String name;
    private final Uri uri;
    private final byte[] expectedBytes;

    private TestResource(@Nullable String name, Uri uri, byte[] expectedBytes) {
      this.name = name;
      this.uri = uri;
      this.expectedBytes = expectedBytes;
    }

    /** Returns a human-readable name for the resource, for use in test failure messages. */
    @Nullable
    public String getName() {
      return name;
    }

    /** Returns the URI where the resource is available. */
    public Uri getUri() {
      return uri;
    }

    /** Returns the expected contents of this resource. */
    public byte[] getExpectedBytes() {
      return expectedBytes;
    }

    /** Builder for {@link TestResource} instances. */
    public static final class Builder {
      private @MonotonicNonNull String name;
      private @MonotonicNonNull Uri uri;
      private byte @MonotonicNonNull [] expectedBytes;

      /**
       * Sets a human-readable name for this resource which will be shown in test failure messages.
       */
      public Builder setName(String name) {
        this.name = name;
        return this;
      }

      /** Sets the URI where this resource is located. */
      public Builder setUri(String uri) {
        return setUri(Uri.parse(uri));
      }

      /** Sets the URI where this resource is located. */
      public Builder setUri(Uri uri) {
        this.uri = uri;
        return this;
      }

      /**
       * Sets the expected contents of this resource.
       *
       * <p>Must be at least 5 bytes.
       */
      public Builder setExpectedBytes(byte[] expectedBytes) {
        checkArgument(expectedBytes.length >= 5);
        this.expectedBytes = expectedBytes;
        return this;
      }

      public TestResource build() {
        return new TestResource(name, checkNotNull(uri), checkNotNull(expectedBytes));
      }
    }
  }

  /** A {@link TransferListener} that only keeps track of the transferred bytes. */
  public static class FakeTransferListener implements TransferListener {
    private int bytesTransferred;

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {}

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {}

    @Override
    public void onBytesTransferred(
        DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
      this.bytesTransferred += bytesTransferred;
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
  }
}

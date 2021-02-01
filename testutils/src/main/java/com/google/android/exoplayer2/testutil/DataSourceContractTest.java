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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
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
  public void unboundedDataSpec_readEverything() throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");

    for (int i = 0; i < resources.size(); i++) {
      additionalFailureInfo.setInfo(getFailureLabel(resources, i));
      TestResource resource = resources.get(i);
      DataSource dataSource = createDataSource();
      try {
        long length = dataSource.open(new DataSpec(resource.getUri()));
        byte[] data =
            resource.isEndOfInputExpected()
                ? Util.readToEnd(dataSource)
                : Util.readExactly(dataSource, resource.getExpectedBytes().length);

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
            resource.isEndOfInputExpected()
                ? Util.readToEnd(dataSource)
                : Util.readExactly(dataSource, resource.getExpectedBytes().length - 3);

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
        byte[] data =
            resource.isEndOfInputExpected()
                ? Util.readToEnd(dataSource)
                : Util.readExactly(dataSource, /* length= */ 4);

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
        byte[] data =
            resource.isEndOfInputExpected()
                ? Util.readToEnd(dataSource)
                : Util.readExactly(dataSource, /* length= */ 2);

        assertThat(length).isEqualTo(2);
        byte[] expectedData = Arrays.copyOfRange(resource.getExpectedBytes(), 2, 4);
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
  public void gzipFlagDoesntAffectReturnedData() throws Exception {
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
            resource.isEndOfInputExpected()
                ? Util.readToEnd(dataSource)
                : Util.readExactly(dataSource, resource.getExpectedBytes().length);

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
    try {
      assertThrows(IOException.class, () -> dataSource.open(new DataSpec(getNotFoundUri())));
    } finally {
      dataSource.close();
    }
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
    private final boolean endOfInputExpected;

    private TestResource(
        @Nullable String name, Uri uri, byte[] expectedBytes, boolean endOfInputExpected) {
      this.name = name;
      this.uri = uri;
      this.expectedBytes = expectedBytes;
      this.endOfInputExpected = endOfInputExpected;
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

    /**
     * Returns whether {@link DataSource#read} is expected to return {@link C#RESULT_END_OF_INPUT}
     * after all the resource data are read.
     */
    public boolean isEndOfInputExpected() {
      return endOfInputExpected;
    }

    /** Builder for {@link TestResource} instances. */
    public static final class Builder {
      private @MonotonicNonNull String name;
      private @MonotonicNonNull Uri uri;
      private byte @MonotonicNonNull [] expectedBytes;
      private boolean endOfInputExpected;

      /** Construct a new instance. */
      public Builder() {
        this.endOfInputExpected = true;
      }

      /**
       * Sets a human-readable name for this resource which will be shown in test failure messages.
       */
      public Builder setName(String name) {
        this.name = name;
        return this;
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

      /**
       * Sets whether {@link DataSource#read} is expected to return {@link C#RESULT_END_OF_INPUT}
       * after all the resource data have been read. By default, this is set to {@code true}.
       */
      public Builder setEndOfInputExpected(boolean expected) {
        this.endOfInputExpected = expected;
        return this;
      }

      public TestResource build() {
        return new TestResource(
            name, checkNotNull(uri), checkNotNull(expectedBytes), endOfInputExpected);
      }
    }
  }
}

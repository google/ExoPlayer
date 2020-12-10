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
  protected abstract DataSource createDataSource();

  /**
   * Returns {@link TestResource} instances.
   *
   * <p>Each resource will be used to exercise the {@link DataSource} instance, allowing different
   * behaviours to be tested.
   *
   * <p>If multiple resources are returned, it's recommended to disambiguate them using {@link
   * TestResource.Builder#setName(String)}.
   */
  protected abstract ImmutableList<TestResource> getTestResources();

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
        byte[] data = Util.readToEnd(dataSource);
        assertThat(length).isEqualTo(resource.getExpectedLength());
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
    private final boolean resolvesToKnownLength;

    private TestResource(
        @Nullable String name, Uri uri, byte[] expectedBytes, boolean resolvesToKnownLength) {
      this.name = name;
      this.uri = uri;
      this.expectedBytes = expectedBytes;
      this.resolvesToKnownLength = resolvesToKnownLength;
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
     * Returns the expected length of this resource.
     *
     * <p>This is either {@link #getExpectedBytes() getExpectedBytes().length} or {@link
     * C#LENGTH_UNSET}.
     */
    public long getExpectedLength() {
      return resolvesToKnownLength ? expectedBytes.length : C.LENGTH_UNSET;
    }

    /** Builder for {@link TestResource} instances. */
    public static final class Builder {
      private @MonotonicNonNull String name;
      private @MonotonicNonNull Uri uri;
      private byte @MonotonicNonNull [] expectedBytes;
      private boolean resolvesToKnownLength;

      /** Construct a new instance. */
      public Builder() {
        this.resolvesToKnownLength = true;
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

      /** Sets the expected contents of this resource. */
      public Builder setExpectedBytes(byte[] expectedBytes) {
        this.expectedBytes = expectedBytes;
        return this;
      }

      /**
       * Calling this method indicates it's expected that {@link DataSource#open(DataSpec)} will
       * return {@link C#LENGTH_UNSET} when passed the URI of this resource and a {@link DataSpec}
       * with {@code length == C.LENGTH_UNSET}.
       */
      public Builder resolvesToUnknownLength() {
        this.resolvesToKnownLength = false;
        return this;
      }

      public TestResource build() {
        return new TestResource(
            name, checkNotNull(uri), checkNotNull(expectedBytes), resolvesToKnownLength);
      }
    }
  }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.datasource;

import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * A {@link DataSource} for reading a raw resource.
 *
 * <p>URIs supported by this source are:
 *
 * <ul>
 *   <li>{@code android.resource://[package]/id}, where {@code package} is the name of the package
 *       in which the resource is located and {@code id} is the integer identifier of the resource.
 *       {@code package} is optional, its default value is the package of this application.
 *   <li>{@code android.resource://[package]/[type/]name}, where {@code package} is the name of the
 *       package in which the resource is located, {@code type} is the resource type and {@code
 *       name} is the resource name. The package and the type are optional. Their default value is
 *       the package of this application and "raw", respectively. Using the other form is more
 *       efficient.
 * </ul>
 *
 * <p>If {@code package} is specified in either of the above URI forms, it must be <a
 * href="https://developer.android.com/training/package-visibility">visible</a> to the current
 * application.
 *
 * <p>Supported {@link Uri} instances can be built as follows:
 *
 * <pre>{@code
 * Uri.Builder()
 *     .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
 *     .authority(packageName)
 *     .path(Integer.toString(resourceId))
 *     .build();
 * }</pre>
 */
@UnstableApi
public final class RawResourceDataSource extends BaseDataSource {

  /** Thrown when an {@link IOException} is encountered reading from a raw resource. */
  public static class RawResourceDataSourceException extends DataSourceException {
    /**
     * @deprecated Use {@link #RawResourceDataSourceException(String, Throwable, int)}.
     */
    @Deprecated
    public RawResourceDataSourceException(String message) {
      super(message, /* cause= */ null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /**
     * @deprecated Use {@link #RawResourceDataSourceException(String, Throwable, int)}.
     */
    @Deprecated
    public RawResourceDataSourceException(Throwable cause) {
      super(cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /** Creates a new instance. */
    public RawResourceDataSourceException(
        @Nullable String message,
        @Nullable Throwable cause,
        @PlaybackException.ErrorCode int errorCode) {
      super(message, cause, errorCode);
    }
  }

  /**
   * @deprecated Use {@code new
   *     Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).path(Integer.toString(rawResourceId)).build()}
   *     instead.
   */
  @SuppressWarnings("deprecation") // Using deprecated scheme
  @Deprecated
  public static Uri buildRawResourceUri(int rawResourceId) {
    return Uri.parse(RAW_RESOURCE_SCHEME + ":///" + rawResourceId);
  }

  /**
   * @deprecated Use {@link ContentResolver#SCHEME_ANDROID_RESOURCE} instead.
   */
  @Deprecated public static final String RAW_RESOURCE_SCHEME = "rawresource";

  private final Context applicationContext;

  @Nullable private DataSpec dataSpec;
  @Nullable private AssetFileDescriptor assetFileDescriptor;
  @Nullable private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public RawResourceDataSource(Context context) {
    super(/* isNetwork= */ false);
    this.applicationContext = context.getApplicationContext();
  }

  @Override
  public long open(DataSpec dataSpec) throws RawResourceDataSourceException {
    this.dataSpec = dataSpec;
    transferInitializing(dataSpec);
    assetFileDescriptor = openAssetFileDescriptor(applicationContext, dataSpec);

    long assetFileDescriptorLength = assetFileDescriptor.getLength();
    FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
    this.inputStream = inputStream;

    try {
      // We can't rely only on the "skipped < dataSpec.position" check below to detect whether the
      // position is beyond the end of the resource being read. This is because the file will
      // typically contain multiple resources, and there's nothing to prevent InputStream.skip()
      // from succeeding by skipping into the data of the next resource. Hence we also need to check
      // against the resource length explicitly, which is guaranteed to be set unless the resource
      // extends to the end of the file.
      if (assetFileDescriptorLength != AssetFileDescriptor.UNKNOWN_LENGTH
          && dataSpec.position > assetFileDescriptorLength) {
        throw new RawResourceDataSourceException(
            /* message= */ null,
            /* cause= */ null,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      long assetFileDescriptorOffset = assetFileDescriptor.getStartOffset();
      long skipped =
          inputStream.skip(assetFileDescriptorOffset + dataSpec.position)
              - assetFileDescriptorOffset;
      if (skipped != dataSpec.position) {
        // We expect the skip to be satisfied in full. If it isn't then we're probably trying to
        // read beyond the end of the last resource in the file.
        throw new RawResourceDataSourceException(
            /* message= */ null,
            /* cause= */ null,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      if (assetFileDescriptorLength == AssetFileDescriptor.UNKNOWN_LENGTH) {
        // The asset must extend to the end of the file. We can try and resolve the length with
        // FileInputStream.getChannel().size().
        FileChannel channel = inputStream.getChannel();
        if (channel.size() == 0) {
          bytesRemaining = C.LENGTH_UNSET;
        } else {
          bytesRemaining = channel.size() - channel.position();
          if (bytesRemaining < 0) {
            // The skip above was satisfied in full, but skipped beyond the end of the file.
            throw new RawResourceDataSourceException(
                /* message= */ null,
                /* cause= */ null,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
          }
        }
      } else {
        bytesRemaining = assetFileDescriptorLength - skipped;
        if (bytesRemaining < 0) {
          throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
      }
    } catch (RawResourceDataSourceException e) {
      throw e;
    } catch (IOException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining =
          bytesRemaining == C.LENGTH_UNSET ? dataSpec.length : min(bytesRemaining, dataSpec.length);
    }
    opened = true;
    transferStarted(dataSpec);
    return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
  }

  /** Resolves {@code dataSpec.uri} to an {@link AssetFileDescriptor}. */
  @SuppressWarnings("deprecation") // Accepting deprecated scheme
  private static AssetFileDescriptor openAssetFileDescriptor(
      Context applicationContext, DataSpec dataSpec) throws RawResourceDataSourceException {
    Uri normalizedUri = dataSpec.uri.normalizeScheme();
    Resources resources;
    int resourceId;
    if (TextUtils.equals(RAW_RESOURCE_SCHEME, normalizedUri.getScheme())) {
      resources = applicationContext.getResources();
      List<String> pathSegments = normalizedUri.getPathSegments();
      if (pathSegments.size() == 1) {
        resourceId = parseResourceId(pathSegments.get(0));
      } else {
        throw new RawResourceDataSourceException(
            RAW_RESOURCE_SCHEME
                + ":// URI must have exactly one path element, found "
                + pathSegments.size());
      }
    } else if (TextUtils.equals(
        ContentResolver.SCHEME_ANDROID_RESOURCE, normalizedUri.getScheme())) {
      String path = Assertions.checkNotNull(normalizedUri.getPath());
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      String packageName =
          TextUtils.isEmpty(normalizedUri.getHost())
              ? applicationContext.getPackageName()
              : normalizedUri.getHost();
      if (packageName.equals(applicationContext.getPackageName())) {
        resources = applicationContext.getResources();
      } else {
        try {
          resources =
              applicationContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
          throw new RawResourceDataSourceException(
              "Package in "
                  + ContentResolver.SCHEME_ANDROID_RESOURCE
                  + ":// URI not found. Check http://g.co/dev/packagevisibility.",
              e,
              PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        }
      }
      if (path.matches("\\d+")) {
        resourceId = parseResourceId(path);
      } else {
        // The javadoc of this class already discourages the URI form that requires this API call.
        @SuppressLint("DiscouragedApi")
        int resourceIdFromName =
            resources.getIdentifier(
                packageName + ":" + path, /* defType= */ "raw", /* defPackage= */ null);
        if (resourceIdFromName != 0) {
          resourceId = resourceIdFromName;
        } else {
          throw new RawResourceDataSourceException(
              "Resource not found.",
              /* cause= */ null,
              PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        }
      }
    } else {
      throw new RawResourceDataSourceException(
          "Unsupported URI scheme ("
              + normalizedUri.getScheme()
              + "). Only "
              + ContentResolver.SCHEME_ANDROID_RESOURCE
              + " is supported.",
          /* cause= */ null,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
    }

    AssetFileDescriptor assetFileDescriptor;
    try {
      assetFileDescriptor = resources.openRawResourceFd(resourceId);
    } catch (Resources.NotFoundException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
    }

    if (assetFileDescriptor == null) {
      throw new RawResourceDataSourceException(
          "Resource is compressed: " + normalizedUri,
          /* cause= */ null,
          PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
    return assetFileDescriptor;
  }

  private static int parseResourceId(String resourceId) throws RawResourceDataSourceException {
    try {
      return Integer.parseInt(resourceId);
    } catch (NumberFormatException e) {
      throw new RawResourceDataSourceException(
          "Resource identifier must be an integer.",
          /* cause= */ null,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws RawResourceDataSourceException {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesRead;
    try {
      int bytesToRead =
          bytesRemaining == C.LENGTH_UNSET ? length : (int) min(bytesRemaining, length);
      bytesRead = castNonNull(inputStream).read(buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new RawResourceDataSourceException(
            "End of stream reached having not read sufficient data.",
            new EOFException(),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      }
      return C.RESULT_END_OF_INPUT;
    }
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return dataSpec != null ? dataSpec.uri : null;
  }

  @SuppressWarnings("Finally")
  @Override
  public void close() throws RawResourceDataSourceException {
    dataSpec = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      inputStream = null;
      try {
        if (assetFileDescriptor != null) {
          assetFileDescriptor.close();
        }
      } catch (IOException e) {
        throw new RawResourceDataSourceException(
            /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      } finally {
        assetFileDescriptor = null;
        if (opened) {
          opened = false;
          transferEnded();
        }
      }
    }
  }
}

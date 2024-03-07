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
package androidx.media3.common.util;

import static androidx.media3.common.util.Util.binarySearchCeil;
import static androidx.media3.common.util.Util.binarySearchFloor;
import static androidx.media3.common.util.Util.contentEquals;
import static androidx.media3.common.util.Util.contentHashCode;
import static androidx.media3.common.util.Util.escapeFileName;
import static androidx.media3.common.util.Util.getCodecsOfType;
import static androidx.media3.common.util.Util.getStringForTime;
import static androidx.media3.common.util.Util.gzip;
import static androidx.media3.common.util.Util.maxValue;
import static androidx.media3.common.util.Util.minValue;
import static androidx.media3.common.util.Util.parseXsDateTime;
import static androidx.media3.common.util.Util.parseXsDuration;
import static androidx.media3.common.util.Util.unescapeFileName;
import static androidx.media3.test.utils.TestUtil.buildTestData;
import static androidx.media3.test.utils.TestUtil.buildTestString;
import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.SparseArray;
import android.util.SparseLongArray;
import androidx.media3.common.C;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link Util}. */
@RunWith(AndroidJUnit4.class)
public class UtilTest {

  private static final int TIMEOUT_MS = 10000;

  @Test
  public void toByteArray_fromIntArray() {
    assertThat(Util.toByteArray(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE))
        .isEqualTo(
            Bytes.concat(
                TestUtil.createByteArray(0x80, 0, 0, 0),
                TestUtil.createByteArray(0xFF, 0xFF, 0xFF, 0xFF),
                TestUtil.createByteArray(0, 0, 0, 0),
                TestUtil.createByteArray(0, 0, 0, 1),
                TestUtil.createByteArray(0x7F, 0xFF, 0xFF, 0xFF)));
  }

  @Test
  public void toByteArray_fromFloat() {
    assertThat(Util.toByteArray(Float.MAX_VALUE))
        .isEqualTo(TestUtil.createByteArray(0x7F, 0x7F, 0xFF, 0xFF));

    assertThat(Util.toByteArray(Float.MIN_VALUE))
        .isEqualTo(TestUtil.createByteArray(0x00, 0x00, 0x00, 0x01));

    assertThat(Util.toByteArray(0)).isEqualTo(TestUtil.createByteArray(0x00, 0x00, 0x00, 0x00));

    assertThat(Util.toByteArray(1.0f)).isEqualTo(TestUtil.createByteArray(0x3F, 0x80, 0x00, 0x00));

    assertThat(Util.toByteArray(-1.0f)).isEqualTo(TestUtil.createByteArray(0xBF, 0x80, 0x00, 0x00));
  }

  @Test
  public void addWithOverflowDefault_withoutOverFlow_returnsSum() {
    long res = Util.addWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(15);

    res = Util.addWithOverflowDefault(Long.MAX_VALUE - 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE + 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void addWithOverflowDefault_withOverFlow_returnsOverflowDefault() {
    long res = Util.addWithOverflowDefault(Long.MAX_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void subtrackWithOverflowDefault_withoutUnderflow_returnsSubtract() {
    long res = Util.subtractWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(-5);

    res = Util.subtractWithOverflowDefault(Long.MIN_VALUE + 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE - 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void subtrackWithOverflowDefault_withUnderflow_returnsOverflowDefault() {
    long res = Util.subtractWithOverflowDefault(Long.MIN_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void inferContentType_handlesHlsIsmUris() {
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/manifest(format=m3u8-aapl)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.ism/manifest(format=m3u8-aapl,quality=hd)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.ism/manifest(quality=hd,format=m3u8-aapl)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
  }

  @Test
  public void inferContentType_handlesHlsIsmV3Uris() {
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/manifest(format=m3u8-aapl-v3)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.ism/manifest(format=m3u8-aapl-v3,quality=hd)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.ism/manifest(quality=hd,format=m3u8-aapl-v3)")))
        .isEqualTo(C.CONTENT_TYPE_HLS);
  }

  @Test
  public void inferContentType_handlesDashIsmUris() {
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml/manifest(format=mpd-time-csf)")))
        .isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.isml/manifest(format=mpd-time-csf,quality=hd)")))
        .isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(
            Util.inferContentType(
                Uri.parse("http://a.b/c.isml/manifest(quality=hd,format=mpd-time-csf)")))
        .isEqualTo(C.CONTENT_TYPE_DASH);
  }

  @Test
  public void inferContentType_handlesSmoothStreamingIsmUris() {
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism"))).isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml"))).isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/"))).isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml/"))).isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/Manifest")))
        .isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml/manifest")))
        .isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml/manifest(filter=x)")))
        .isEqualTo(C.CONTENT_TYPE_SS);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.isml/manifest_hd")))
        .isEqualTo(C.CONTENT_TYPE_SS);
  }

  @Test
  public void inferContentType_handlesOtherIsmUris() {
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/video.mp4")))
        .isEqualTo(C.CONTENT_TYPE_OTHER);
    assertThat(Util.inferContentType(Uri.parse("http://a.b/c.ism/prefix-manifest")))
        .isEqualTo(C.CONTENT_TYPE_OTHER);
  }

  /**
   * Test that the deprecated {@link Util#inferContentType(String)} works when passed only a file
   * extension and the leading dot.
   */
  @SuppressWarnings("deprecation")
  @Test
  public void inferContentType_extensionAsPath() {
    assertThat(Util.inferContentType(".m3u8")).isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(Util.inferContentType(".mpd")).isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(Util.inferContentType(".ism")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType(".isml")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType(".mp4")).isEqualTo(C.CONTENT_TYPE_OTHER);
  }

  // Testing deprecated method.
  @SuppressWarnings("deprecation")
  @Test
  public void inferContentType_extensionOverride() {
    assertThat(
            Util.inferContentType(
                Uri.parse("file:///path/to/something.mpd"), /* overrideExtension= */ null))
        .isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(
            Util.inferContentType(
                Uri.parse("file:///path/to/something.mpd"), /* overrideExtension= */ ""))
        .isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(
            Util.inferContentType(
                Uri.parse("file:///path/to/something.mpd"), /* overrideExtension= */ "m3u8"))
        .isEqualTo(C.CONTENT_TYPE_HLS);
  }

  @Test
  public void inferContentTypeForExtension() {
    assertThat(Util.inferContentTypeForExtension("m3u8")).isEqualTo(C.CONTENT_TYPE_HLS);
    assertThat(Util.inferContentTypeForExtension("mpd")).isEqualTo(C.CONTENT_TYPE_DASH);
    assertThat(Util.inferContentTypeForExtension("ism")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentTypeForExtension("isml")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentTypeForExtension("mp4")).isEqualTo(C.CONTENT_TYPE_OTHER);
  }

  @Test
  public void fixSmoothStreamingIsmManifestUri_addsManifestSuffix() {
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.ism")))
        .isEqualTo(Uri.parse("http://a.b/c.ism/Manifest"));
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.isml")))
        .isEqualTo(Uri.parse("http://a.b/c.isml/Manifest"));

    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.ism/")))
        .isEqualTo(Uri.parse("http://a.b/c.ism/Manifest"));
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.isml/")))
        .isEqualTo(Uri.parse("http://a.b/c.isml/Manifest"));
  }

  @Test
  public void fixSmoothStreamingIsmManifestUri_doesNotAlterManifestUri() {
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.ism/Manifest")))
        .isEqualTo(Uri.parse("http://a.b/c.ism/Manifest"));
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.isml/Manifest")))
        .isEqualTo(Uri.parse("http://a.b/c.isml/Manifest"));
    assertThat(
            Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.ism/Manifest(filter=x)")))
        .isEqualTo(Uri.parse("http://a.b/c.ism/Manifest(filter=x)"));
    assertThat(Util.fixSmoothStreamingIsmManifestUri(Uri.parse("http://a.b/c.ism/Manifest_hd")))
        .isEqualTo(Uri.parse("http://a.b/c.ism/Manifest_hd"));
  }

  @Test
  public void arrayBinarySearchFloor_emptyArrayAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchFloor_emptyArrayAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetInArrayAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void longArrayBinarySearchFloor_emptyArrayAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new LongArray(), /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void longArrayBinarySearchFloor_emptyArrayAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new LongArray(), /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void longArrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void longArrayBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetInArrayAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void listBinarySearchFloor_emptyListAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchFloor_emptyListAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      listBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      listBinarySearchFloor_targetInListAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      listBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      listBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void arrayBinarySearchCeil_emptyArrayAndStayInBoundsFalse_returns0() {
    assertThat(
            binarySearchCeil(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchCeil_emptyArrayAndStayInBoundsTrue_returnsMinus1() {
    assertThat(
            binarySearchCeil(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchCeil_targetSmallerThanValues_returns0() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(3);
  }

  @Test
  public void arrayBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(7);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(6);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetInArrayAndInclusiveTrue_returnsLastIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(6);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 4,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 4,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void listBinarySearchCeil_emptyListAndStayInBoundsFalse_returns0() {
    assertThat(
            binarySearchCeil(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchCeil_emptyListAndStayInBoundsTrue_returnsMinus1() {
    assertThat(
            binarySearchCeil(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchCeil_targetSmallerThanValues_returns0() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(3);
  }

  @Test
  public void listBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(7);
  }

  @Test
  public void
      listBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(6);
  }

  @Test
  public void
      listBinarySearchCeil_targetInListAndInclusiveTrue_returnsLastIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(6);
  }

  @Test
  public void
      listBinarySearchCeil_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 4,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchCeil_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 4,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void sparseLongArrayMinValue_returnsMinValue() {
    SparseLongArray sparseLongArray = new SparseLongArray();
    sparseLongArray.put(0, 12);
    sparseLongArray.put(25, 10);
    sparseLongArray.put(42, 11);

    assertThat(minValue(sparseLongArray)).isEqualTo(10);
  }

  @Test
  public void sparseLongArrayMinValue_emptyArray_throws() {
    assertThrows(NoSuchElementException.class, () -> minValue(new SparseLongArray()));
  }

  @Test
  public void sparseLongArrayMaxValue_returnsMaxValue() {
    SparseLongArray sparseLongArray = new SparseLongArray();
    sparseLongArray.put(0, 2);
    sparseLongArray.put(25, 10);
    sparseLongArray.put(42, 1);

    assertThat(maxValue(sparseLongArray)).isEqualTo(10);
  }

  @Test
  public void sparseLongArrayMaxValue_emptyArray_throws() {
    assertThrows(NoSuchElementException.class, () -> maxValue(new SparseLongArray()));
  }

  @Test
  public void sampleCountToDuration_thenDurationToSampleCount_returnsOriginalValue() {
    // Use co-prime increments, to maximise 'discord' between sampleCount and sampleRate.
    for (long originalSampleCount = 0; originalSampleCount < 100_000; originalSampleCount += 97) {
      for (int sampleRate = 89; sampleRate < 1_000_000; sampleRate += 89) {
        long calculatedSampleCount =
            Util.durationUsToSampleCount(
                Util.sampleCountToDurationUs(originalSampleCount, sampleRate), sampleRate);
        assertWithMessage("sampleCount=%s, sampleRate=%s", originalSampleCount, sampleRate)
            .that(calculatedSampleCount)
            .isEqualTo(originalSampleCount);
      }
    }
  }

  @Test
  public void durationToSampleCount_doesntOverflowWithLargeDuration() {
    // Choose a durationUs & sampleRate that will overflow a signed 64-bit integer if they are
    // multiplied together, but not if the durationUs is converted to seconds first.
    long sampleCount =
        Util.durationUsToSampleCount(
            /* durationUs= */ Long.MAX_VALUE / 100_000, /* sampleRate= */ 192_000);
    assertThat(sampleCount).isEqualTo(17708874310762L);
  }

  @Test
  public void sampleCountToDuration_doesntOverflowWithLargeDuration() {
    // Choose a sampleCount that will overflow a signed 64-bit integer if it is multiplied directly
    // by C.MICROS_PER_SECOND, but not if it is divided by sampleRate first.
    long durationUs =
        Util.sampleCountToDurationUs(
            /* sampleCount= */ Long.MAX_VALUE / 100_000, /* sampleRate= */ 192_000);
    assertThat(durationUs).isEqualTo(480383960252848L);
  }

  @Test
  public void parseXsDuration_returnsParsedDurationInMillis() {
    assertThat(parseXsDuration("PT150.279S")).isEqualTo(150279L);
    assertThat(parseXsDuration("PT1.500S")).isEqualTo(1500L);
  }

  @Test
  public void parseXsDateTime_returnsParsedDateTimeInMillis() throws Exception {
    assertThat(parseXsDateTime("2014-06-19T23:07:42")).isEqualTo(1403219262000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00,000Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-08:00")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-800")).isEqualTo(1411161535000L);
  }

  @Test
  public void toUnsignedLong_withPositiveValue_returnsValue() {
    int x = 0x05D67F23;

    long result = Util.toUnsignedLong(x);

    assertThat(result).isEqualTo(0x05D67F23L);
  }

  @Test
  public void toUnsignedLong_withNegativeValue_returnsValue() {
    int x = 0xF5D67F23;

    long result = Util.toUnsignedLong(x);

    assertThat(result).isEqualTo(0xF5D67F23L);
  }

  @Test
  public void toLong_withZeroValue_returnsZero() {
    assertThat(Util.toLong(0, 0)).isEqualTo(0);
  }

  @Test
  public void toLong_withLongValue_returnsValue() {
    assertThat(Util.toLong(1, -4)).isEqualTo(0x1FFFFFFFCL);
  }

  @Test
  public void toLong_withBigValue_returnsValue() {
    assertThat(Util.toLong(0x7ABCDEF, 0x12345678)).isEqualTo(0x7ABCDEF_12345678L);
  }

  @Test
  public void toLong_withMaxValue_returnsValue() {
    assertThat(Util.toLong(0x0FFFFFFF, 0xFFFFFFFF)).isEqualTo(0x0FFFFFFF_FFFFFFFFL);
  }

  @Test
  public void toLong_withBigNegativeValue_returnsValue() {
    assertThat(Util.toLong(0xFEDCBA, 0x87654321)).isEqualTo(0xFEDCBA_87654321L);
  }

  @Test
  public void toHexString_returnsHexString() {
    byte[] bytes = createByteArray(0x12, 0xFC, 0x06);

    assertThat(Util.toHexString(bytes)).isEqualTo("12fc06");
  }

  @Test
  public void getCodecsOfType_withNull_returnsNull() {
    assertThat(getCodecsOfType(null, C.TRACK_TYPE_VIDEO)).isNull();
  }

  @Test
  public void getCodecsOfType_withInvalidTrackType_returnsNull() {
    assertThat(getCodecsOfType("avc1.64001e,vp9.63.1", C.TRACK_TYPE_AUDIO)).isNull();
  }

  @Test
  public void getCodecsOfType_withAudioTrack_returnsCodec() {
    assertThat(getCodecsOfType(" vp9.63.1, ec-3 ", C.TRACK_TYPE_AUDIO)).isEqualTo("ec-3");
  }

  @Test
  public void getCodecsOfType_withVideoTrack_returnsCodec() {
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
  }

  @Test
  public void getCodecsOfType_withInvalidCodec_returnsNull() {
    assertThat(getCodecsOfType("invalidCodec1, invalidCodec2 ", C.TRACK_TYPE_AUDIO)).isNull();
  }

  @Test
  public void unescapeFileName_invalidFileName_returnsNull() {
    assertThat(Util.unescapeFileName("%a")).isNull();
    assertThat(Util.unescapeFileName("%xyz")).isNull();
  }

  @Test
  public void escapeUnescapeFileName_returnsEscapedString() {
    assertEscapeUnescapeFileName("just+a regular+fileName", "just+a regular+fileName");
    assertEscapeUnescapeFileName("key:value", "key%3avalue");
    assertEscapeUnescapeFileName("<>:\"/\\|?*%", "%3c%3e%3a%22%2f%5c%7c%3f%2a%25");

    Random random = new Random(0);
    for (int i = 0; i < 1000; i++) {
      String string = buildTestString(1000, random);
      assertEscapeUnescapeFileName(string);
    }
  }

  @Test
  public void getDataUriForString_returnsCorrectDataUri() {
    assertThat(
            Util.getDataUriForString(/* mimeType= */ "text/plain", "Some Data!<>:\"/\\|?*%")
                .toString())
        .isEqualTo("data:text/plain;base64,U29tZSBEYXRhITw+OiIvXHw/KiU=");
  }

  @Test
  public void crc32_returnsUpdatedCrc32() {
    byte[] bytes = {0x5F, 0x78, 0x04, 0x7B, 0x5F};
    int start = 1;
    int end = 4;
    int initialValue = 0xFFFFFFFF;

    int result = Util.crc32(bytes, start, end, initialValue);

    assertThat(result).isEqualTo(0x67CE9747);
  }

  @Test
  public void crc8_returnsUpdatedCrc8() {
    byte[] bytes = {0x5F, 0x78, 0x04, 0x7B, 0x5F};
    int start = 1;
    int end = 4;
    int initialValue = 0;

    int result = Util.crc8(bytes, start, end, initialValue);

    assertThat(result).isEqualTo(0x4);
  }

  @Test
  public void gzip_resultInflatesBackToOriginalValue() throws Exception {
    byte[] input = buildTestData(20);

    byte[] deflated = gzip(input);

    byte[] inflated =
        ByteStreams.toByteArray(new GZIPInputStream(new ByteArrayInputStream(deflated)));
    assertThat(inflated).isEqualTo(input);
  }

  @Test
  public void getBigEndianInt_fromBigEndian() {
    byte[] bytes = {0x1F, 0x2E, 0x3D, 0x4C};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 0)).isEqualTo(0x1F2E3D4C);
  }

  @Test
  public void getBigEndianInt_fromLittleEndian() {
    byte[] bytes = {(byte) 0xC2, (byte) 0xD3, (byte) 0xE4, (byte) 0xF5};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 0)).isEqualTo(0xC2D3E4F5);
  }

  @Test
  public void getBigEndianInt_unaligned() {
    byte[] bytes = {9, 8, 7, 6, 5};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 1)).isEqualTo(0x08070605);
  }

  @Test
  public void createReadOnlyByteBuffer_fromLittleEndian_preservesByteOrder() {
    byte[] bytes = {1, 2, 3, 4};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    ByteBuffer readOnlyByteBuffer = Util.createReadOnlyByteBuffer(byteBuffer);

    assertThat(readOnlyByteBuffer.order()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    assertThat(byteBuffer.getInt()).isEqualTo(readOnlyByteBuffer.getInt());
  }

  @Test
  public void createReadOnlyByteBuffer_fromBigEndian_preservesByteOrder() {
    byte[] bytes = {1, 2, 3, 4};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    ByteBuffer readOnlyByteBuffer = Util.createReadOnlyByteBuffer(byteBuffer);

    assertThat(readOnlyByteBuffer.order()).isEqualTo(ByteOrder.BIG_ENDIAN);
    assertThat(byteBuffer.getInt()).isEqualTo(readOnlyByteBuffer.getInt());
  }

  @Test
  public void inflate_withDeflatedData_success() {
    byte[] testData = buildTestData(/*arbitrary test data size*/ 256 * 1024);
    byte[] compressedData = new byte[testData.length * 2];
    Deflater compresser = new Deflater(9);
    compresser.setInput(testData);
    compresser.finish();
    int compressedDataLength = compresser.deflate(compressedData);
    compresser.end();

    ParsableByteArray input = new ParsableByteArray(compressedData, compressedDataLength);
    ParsableByteArray output = new ParsableByteArray();
    assertThat(Util.inflate(input, output, /* inflater= */ null)).isTrue();
    assertThat(output.limit()).isEqualTo(testData.length);
    assertThat(Arrays.copyOf(output.getData(), output.limit())).isEqualTo(testData);
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void normalizeLanguageCode_keepsUndefinedTagsUnchanged() {
    assertThat(Util.normalizeLanguageCode(null)).isNull();
    assertThat(Util.normalizeLanguageCode("")).isEmpty();
    assertThat(Util.normalizeLanguageCode("und")).isEqualTo("und");
    assertThat(Util.normalizeLanguageCode("DoesNotExist")).isEqualTo("doesnotexist");
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void normalizeLanguageCode_normalizesCodeToTwoLetterISOAndLowerCase_keepingAllSubtags() {
    assertThat(Util.normalizeLanguageCode("es")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("spa")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("es-AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("SpA-ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es_AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("spa_ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es-AR-dialect")).isEqualTo("es-ar-dialect");
    // Regional subtag (South America)
    assertThat(Util.normalizeLanguageCode("ES-419")).isEqualTo("es-419");
    // Script subtag (Simplified Taiwanese)
    assertThat(Util.normalizeLanguageCode("zh-hans-tw")).isEqualTo("zh-hans-tw");
    assertThat(Util.normalizeLanguageCode("zho-hans-tw")).isEqualTo("zh-hans-tw");
    // Non-spec compliant subtags.
    assertThat(Util.normalizeLanguageCode("sv-illegalSubtag")).isEqualTo("sv-illegalsubtag");
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void normalizeLanguageCode_iso6392BibliographicalAndTextualCodes_areNormalizedToSameTag() {
    // See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
    assertThat(Util.normalizeLanguageCode("alb")).isEqualTo(Util.normalizeLanguageCode("sqi"));
    assertThat(Util.normalizeLanguageCode("arm")).isEqualTo(Util.normalizeLanguageCode("hye"));
    assertThat(Util.normalizeLanguageCode("baq")).isEqualTo(Util.normalizeLanguageCode("eus"));
    assertThat(Util.normalizeLanguageCode("bur")).isEqualTo(Util.normalizeLanguageCode("mya"));
    assertThat(Util.normalizeLanguageCode("chi")).isEqualTo(Util.normalizeLanguageCode("zho"));
    assertThat(Util.normalizeLanguageCode("cze")).isEqualTo(Util.normalizeLanguageCode("ces"));
    assertThat(Util.normalizeLanguageCode("dut")).isEqualTo(Util.normalizeLanguageCode("nld"));
    assertThat(Util.normalizeLanguageCode("fre")).isEqualTo(Util.normalizeLanguageCode("fra"));
    assertThat(Util.normalizeLanguageCode("geo")).isEqualTo(Util.normalizeLanguageCode("kat"));
    assertThat(Util.normalizeLanguageCode("ger")).isEqualTo(Util.normalizeLanguageCode("deu"));
    assertThat(Util.normalizeLanguageCode("gre")).isEqualTo(Util.normalizeLanguageCode("ell"));
    assertThat(Util.normalizeLanguageCode("ice")).isEqualTo(Util.normalizeLanguageCode("isl"));
    assertThat(Util.normalizeLanguageCode("mac")).isEqualTo(Util.normalizeLanguageCode("mkd"));
    assertThat(Util.normalizeLanguageCode("mao")).isEqualTo(Util.normalizeLanguageCode("mri"));
    assertThat(Util.normalizeLanguageCode("may")).isEqualTo(Util.normalizeLanguageCode("msa"));
    assertThat(Util.normalizeLanguageCode("per")).isEqualTo(Util.normalizeLanguageCode("fas"));
    assertThat(Util.normalizeLanguageCode("rum")).isEqualTo(Util.normalizeLanguageCode("ron"));
    assertThat(Util.normalizeLanguageCode("slo")).isEqualTo(Util.normalizeLanguageCode("slk"));
    assertThat(Util.normalizeLanguageCode("scc")).isEqualTo(Util.normalizeLanguageCode("srp"));
    assertThat(Util.normalizeLanguageCode("tib")).isEqualTo(Util.normalizeLanguageCode("bod"));
    assertThat(Util.normalizeLanguageCode("wel")).isEqualTo(Util.normalizeLanguageCode("cym"));
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void
      normalizeLanguageCode_deprecatedLanguageTagsAndModernReplacement_areNormalizedToSameTag() {
    // See https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes, "ISO 639:1988"
    assertThat(Util.normalizeLanguageCode("in")).isEqualTo(Util.normalizeLanguageCode("id"));
    assertThat(Util.normalizeLanguageCode("in")).isEqualTo(Util.normalizeLanguageCode("ind"));
    assertThat(Util.normalizeLanguageCode("iw")).isEqualTo(Util.normalizeLanguageCode("he"));
    assertThat(Util.normalizeLanguageCode("iw")).isEqualTo(Util.normalizeLanguageCode("heb"));
    assertThat(Util.normalizeLanguageCode("ji")).isEqualTo(Util.normalizeLanguageCode("yi"));
    assertThat(Util.normalizeLanguageCode("ji")).isEqualTo(Util.normalizeLanguageCode("yid"));

    // Legacy tags
    assertThat(Util.normalizeLanguageCode("i-lux")).isEqualTo(Util.normalizeLanguageCode("lb"));
    assertThat(Util.normalizeLanguageCode("i-lux")).isEqualTo(Util.normalizeLanguageCode("ltz"));
    assertThat(Util.normalizeLanguageCode("i-hak")).isEqualTo(Util.normalizeLanguageCode("hak"));
    assertThat(Util.normalizeLanguageCode("i-hak")).isEqualTo(Util.normalizeLanguageCode("zh-hak"));
    assertThat(Util.normalizeLanguageCode("i-navajo")).isEqualTo(Util.normalizeLanguageCode("nv"));
    assertThat(Util.normalizeLanguageCode("i-navajo")).isEqualTo(Util.normalizeLanguageCode("nav"));
    assertThat(Util.normalizeLanguageCode("no-bok")).isEqualTo(Util.normalizeLanguageCode("nb"));
    assertThat(Util.normalizeLanguageCode("no-bok")).isEqualTo(Util.normalizeLanguageCode("nob"));
    assertThat(Util.normalizeLanguageCode("no-nyn")).isEqualTo(Util.normalizeLanguageCode("nn"));
    assertThat(Util.normalizeLanguageCode("no-nyn")).isEqualTo(Util.normalizeLanguageCode("nno"));
    assertThat(Util.normalizeLanguageCode("zh-guoyu")).isEqualTo(Util.normalizeLanguageCode("cmn"));
    assertThat(Util.normalizeLanguageCode("zh-guoyu"))
        .isEqualTo(Util.normalizeLanguageCode("zh-cmn"));
    assertThat(Util.normalizeLanguageCode("zh-hakka")).isEqualTo(Util.normalizeLanguageCode("hak"));
    assertThat(Util.normalizeLanguageCode("zh-hakka"))
        .isEqualTo(Util.normalizeLanguageCode("zh-hak"));
    assertThat(Util.normalizeLanguageCode("zh-min-nan"))
        .isEqualTo(Util.normalizeLanguageCode("nan"));
    assertThat(Util.normalizeLanguageCode("zh-min-nan"))
        .isEqualTo(Util.normalizeLanguageCode("zh-nan"));
    assertThat(Util.normalizeLanguageCode("zh-xiang")).isEqualTo(Util.normalizeLanguageCode("hsn"));
    assertThat(Util.normalizeLanguageCode("zh-xiang"))
        .isEqualTo(Util.normalizeLanguageCode("zh-hsn"));
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void normalizeLanguageCode_macrolanguageTags_areFullyMaintained() {
    // See https://en.wikipedia.org/wiki/ISO_639_macrolanguage
    assertThat(Util.normalizeLanguageCode("zh-cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("zho-cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("ar-ayl")).isEqualTo("ar-ayl");
    assertThat(Util.normalizeLanguageCode("ara-ayl")).isEqualTo("ar-ayl");

    // Special case of short codes that are actually part of a macrolanguage.
    assertThat(Util.normalizeLanguageCode("arb")).isEqualTo("ar-arb");
    assertThat(Util.normalizeLanguageCode("nb")).isEqualTo("no-nob");
    assertThat(Util.normalizeLanguageCode("nn")).isEqualTo("no-nno");
    assertThat(Util.normalizeLanguageCode("nob")).isEqualTo("no-nob");
    assertThat(Util.normalizeLanguageCode("nno")).isEqualTo("no-nno");
    assertThat(Util.normalizeLanguageCode("tw")).isEqualTo("ak-twi");
    assertThat(Util.normalizeLanguageCode("twi")).isEqualTo("ak-twi");
    assertThat(Util.normalizeLanguageCode("bs")).isEqualTo("hbs-bos");
    assertThat(Util.normalizeLanguageCode("bos")).isEqualTo("hbs-bos");
    assertThat(Util.normalizeLanguageCode("hr")).isEqualTo("hbs-hrv");
    assertThat(Util.normalizeLanguageCode("hrv")).isEqualTo("hbs-hrv");
    assertThat(Util.normalizeLanguageCode("sr")).isEqualTo("hbs-srp");
    assertThat(Util.normalizeLanguageCode("srp")).isEqualTo("hbs-srp");
    assertThat(Util.normalizeLanguageCode("id")).isEqualTo("ms-ind");
    assertThat(Util.normalizeLanguageCode("ind")).isEqualTo("ms-ind");
    assertThat(Util.normalizeLanguageCode("cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("hak")).isEqualTo("zh-hak");
    assertThat(Util.normalizeLanguageCode("nan")).isEqualTo("zh-nan");
    assertThat(Util.normalizeLanguageCode("hsn")).isEqualTo("zh-hsn");
  }

  @Test
  public void tableExists_withExistingTable() {
    SQLiteDatabase database = getInMemorySQLiteOpenHelper().getWritableDatabase();
    database.execSQL("CREATE TABLE TestTable (ID INTEGER NOT NULL)");

    assertThat(Util.tableExists(database, "TestTable")).isTrue();
  }

  @Test
  public void tableExists_withNonExistingTable() {
    SQLiteDatabase database = getInMemorySQLiteOpenHelper().getReadableDatabase();

    assertThat(Util.tableExists(database, "table")).isFalse();
  }

  @Test
  public void getStringForTime_withNegativeTime_setsNegativePrefix() {
    assertThat(getStringForTime(new StringBuilder(), new Formatter(), /* timeMs= */ -35000))
        .isEqualTo("-00:35");
  }

  @Test
  public void getErrorCodeFromPlatformDiagnosticsInfo_withValidInput_returnsExpectedValue() {
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaDrm.error_1"))
        .isEqualTo(1);
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaDrm.error_neg_1"))
        .isEqualTo(-1);
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaCodec2.error_3"))
        .isEqualTo(3);
    assertThat(
            Util.getErrorCodeFromPlatformDiagnosticsInfo(
                "android.media.MediaCodec.weird_error_neg_10000"))
        .isEqualTo(-10000);
    assertThat(
            Util.getErrorCodeFromPlatformDiagnosticsInfo(
                "android.media.MediaCodec.weird_error_negx_10000"))
        .isEqualTo(10000);
    assertThat(
            Util.getErrorCodeFromPlatformDiagnosticsInfo(
                "android.media.MediaCodec.weird_error_xneg_10000"))
        .isEqualTo(10000);
  }

  @Test
  public void getErrorCodeFromPlatformDiagnosticsInfo_withInvalidInput_returnsZero() {
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("")).isEqualTo(0);
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaDrm.empty"))
        .isEqualTo(0);
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaDrm.error_neg_1a"))
        .isEqualTo(0);
    assertThat(Util.getErrorCodeFromPlatformDiagnosticsInfo("android.media.MediaDrm.error_a1"))
        .isEqualTo(0);
  }

  @Test
  public void postOrRun_withMatchingThread_runsInline() {
    Runnable mockRunnable = mock(Runnable.class);

    Util.postOrRun(new Handler(Looper.myLooper()), mockRunnable);

    verify(mockRunnable).run();
  }

  @Test
  public void postOrRun_fromDifferentThread_posts() throws Exception {
    Runnable mockRunnable = mock(Runnable.class);
    HandlerThread handlerThread = new HandlerThread("TestThread");
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());

    ConditionVariable postedCondition = new ConditionVariable();
    handler.post(
        () -> {
          Util.postOrRun(new Handler(Looper.getMainLooper()), mockRunnable);
          postedCondition.open();
        });
    postedCondition.block(TIMEOUT_MS);
    handlerThread.quit();

    verify(mockRunnable, never()).run();

    ShadowLooper.idleMainLooper();
    verify(mockRunnable).run();
  }

  @Test
  public void postOrRunWithCompletion_withMatchingThread_runsInline() throws Exception {
    Runnable mockRunnable = mock(Runnable.class);
    Object expectedResult = new Object();

    ListenableFuture<Object> future =
        Util.postOrRunWithCompletion(new Handler(Looper.myLooper()), mockRunnable, expectedResult);

    assertThat(future.isDone()).isTrue();
    assertThat(future.get()).isEqualTo(expectedResult);
    verify(mockRunnable).run();
  }

  @Test
  public void postOrRunWithCompletion_withException_hasException() throws Exception {
    Object expectedResult = new Object();

    ListenableFuture<Object> future =
        Util.postOrRunWithCompletion(
            new Handler(Looper.myLooper()),
            () -> {
              throw new IllegalStateException();
            },
            expectedResult);

    assertThat(future.isDone()).isTrue();
    ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
    assertThat(executionException).hasCauseThat().isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void postOrRunWithCompletion_fromDifferentThread_posts() throws Exception {
    Runnable mockRunnable = mock(Runnable.class);
    Object expectedResult = new Object();
    HandlerThread handlerThread = new HandlerThread("TestThread");
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());

    ConditionVariable postedCondition = new ConditionVariable();
    AtomicReference<ListenableFuture<Object>> futureReference = new AtomicReference<>();
    handler.post(
        () -> {
          futureReference.set(
              Util.postOrRunWithCompletion(
                  new Handler(Looper.getMainLooper()), mockRunnable, expectedResult));
          postedCondition.open();
        });
    postedCondition.block(TIMEOUT_MS);
    handlerThread.quit();

    ListenableFuture<Object> future = futureReference.get();
    verify(mockRunnable, never()).run();
    assertThat(future.isDone()).isFalse();

    ShadowLooper.idleMainLooper();
    assertThat(future.isDone()).isTrue();
    assertThat(future.get()).isEqualTo(expectedResult);
    verify(mockRunnable).run();
  }

  @Test
  public void postOrRunWithCompletion_withCancel_isNeverRun() throws Exception {
    Runnable mockRunnable = mock(Runnable.class);
    Object expectedResult = new Object();
    HandlerThread handlerThread = new HandlerThread("TestThread");
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());

    ConditionVariable postedCondition = new ConditionVariable();
    AtomicReference<ListenableFuture<Object>> futureReference = new AtomicReference<>();
    handler.post(
        () -> {
          futureReference.set(
              Util.postOrRunWithCompletion(
                  new Handler(Looper.getMainLooper()), mockRunnable, expectedResult));
          postedCondition.open();
        });
    postedCondition.block(TIMEOUT_MS);
    handlerThread.quit();
    ListenableFuture<Object> future = futureReference.get();
    future.cancel(/* mayInterruptIfRunning= */ false);

    ShadowLooper.idleMainLooper();
    verify(mockRunnable, never()).run();
  }

  @Test
  public void transformFutureAsync_withCancelledInput_isCancelled() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    inputFuture.cancel(/* mayInterruptIfRunning= */ false);

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> Futures.immediateFuture(new Object()));

    assertThat(outputFuture.isCancelled()).isTrue();
  }

  @Test
  public void transformFutureAsync_withExceptionInput_hasException() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    Exception expectedException = new Exception();
    inputFuture.setException(expectedException);

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> Futures.immediateFuture(new Object()));

    assertThat(outputFuture.isDone()).isTrue();
    ExecutionException executionException =
        assertThrows(ExecutionException.class, outputFuture::get);
    assertThat(executionException).hasCauseThat().isEqualTo(expectedException);
  }

  @Test
  public void transformFutureAsync_withCancelledTransform_isCancelled() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    SettableFuture<Object> transformFuture = SettableFuture.create();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> transformFuture);
    assertThat(outputFuture.isDone()).isFalse();
    inputFuture.set(new Object());
    assertThat(outputFuture.isDone()).isFalse();
    transformFuture.cancel(/* mayInterruptIfRunning= */ false);

    assertThat(outputFuture.isCancelled()).isTrue();
  }

  @Test
  public void transformFutureAsync_withExceptionInTransformFunction_hasException() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    Exception expectedException = new Exception();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(
            inputFuture,
            input -> {
              throw expectedException;
            });
    assertThat(outputFuture.isDone()).isFalse();
    inputFuture.set(new Object());

    assertThat(outputFuture.isDone()).isTrue();
    ExecutionException executionException =
        assertThrows(ExecutionException.class, outputFuture::get);
    assertThat(executionException).hasCauseThat().isEqualTo(expectedException);
  }

  @Test
  public void transformFutureAsync_withExceptionDuringTransform_hasException() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    Exception expectedException = new Exception();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(
            inputFuture, input -> Futures.immediateFailedFuture(expectedException));
    assertThat(outputFuture.isDone()).isFalse();
    inputFuture.set(new Object());

    assertThat(outputFuture.isDone()).isTrue();
    ExecutionException executionException =
        assertThrows(ExecutionException.class, outputFuture::get);
    assertThat(executionException).hasCauseThat().isEqualTo(expectedException);
  }

  @Test
  public void transformFutureAsync_cancelDuringInput_inputIsCancelled() {
    SettableFuture<Object> inputFuture = SettableFuture.create();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> Futures.immediateFuture(new Object()));
    assertThat(outputFuture.isDone()).isFalse();
    outputFuture.cancel(/* mayInterruptIfRunning= */ true);

    assertThat(inputFuture.isCancelled()).isTrue();
  }

  @Test
  public void transformFutureAsync_cancelDuringTransform_transformIsCancelled() {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    SettableFuture<Object> transformFuture = SettableFuture.create();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> transformFuture);
    assertThat(outputFuture.isDone()).isFalse();
    inputFuture.set(new Object());
    assertThat(outputFuture.isDone()).isFalse();
    outputFuture.cancel(/* mayInterruptIfRunning= */ true);

    assertThat(transformFuture.isCancelled()).isTrue();
  }

  @Test
  public void transformFutureAsync_withSuccessfulTransform_returnsTransformedResult()
      throws Exception {
    SettableFuture<Object> inputFuture = SettableFuture.create();
    SettableFuture<Object> transformFuture = SettableFuture.create();
    Object expectedOutput = new Object();

    ListenableFuture<Object> outputFuture =
        Util.transformFutureAsync(inputFuture, input -> transformFuture);
    assertThat(outputFuture.isDone()).isFalse();
    inputFuture.set(new Object());
    assertThat(outputFuture.isDone()).isFalse();
    transformFuture.set(expectedOutput);

    assertThat(outputFuture.isDone()).isTrue();
    assertThat(outputFuture.get()).isEqualTo(expectedOutput);
  }

  @Test
  public void getSelectionFlagStrings() {
    List<String> selectionFlags =
        Util.getSelectionFlagStrings(C.SELECTION_FLAG_AUTOSELECT | C.SELECTION_FLAG_FORCED);

    assertThat(selectionFlags).containsExactly("auto", "forced");
  }

  @Test
  public void getRoleFlagStrings() {
    List<String> roleFlags =
        Util.getRoleFlagStrings(C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND | C.ROLE_FLAG_EASY_TO_READ);

    assertThat(roleFlags).containsExactly("describes-music", "easy-read");
  }

  @Test
  public void contentEquals_twoNullSparseArrays_returnsTrue() {
    assertThat(contentEquals(null, null)).isTrue();
  }

  @Test
  public void contentEquals_oneNullSparseArrayAndOneNonNullSparseArray_returnsFalse() {
    SparseArray<Integer> sparseArray = new SparseArray<>();
    sparseArray.put(1, 2);

    assertThat(contentEquals(sparseArray, null)).isFalse();
    assertThat(contentEquals(null, sparseArray)).isFalse();
  }

  @Test
  @Config(minSdk = 16) // Specifies the minimum SDK to enforce the test to run with all API levels.
  public void contentEquals_sparseArraysWithEqualContent_returnsTrue() {
    SparseArray<Integer> sparseArray1 = new SparseArray<>();
    sparseArray1.put(1, 2);
    sparseArray1.put(3, 4);
    SparseArray<Integer> sparseArray2 = new SparseArray<>();
    sparseArray2.put(3, 4);
    sparseArray2.put(1, 2);

    assertThat(contentEquals(sparseArray1, sparseArray2)).isTrue();
  }

  @Test
  @Config(minSdk = 16) // Specifies the minimum SDK to enforce the test to run with all API levels.
  public void contentEquals_sparseArraysWithDifferentContents_returnsFalse() {
    SparseArray<Integer> sparseArray1 = new SparseArray<>();
    sparseArray1.put(1, 2);
    sparseArray1.put(3, 4);
    SparseArray<Integer> sparseArray2 = new SparseArray<>();
    sparseArray2.put(3, 4);
    SparseArray<Integer> sparseArray3 = new SparseArray<>();
    sparseArray3.put(1, 3);
    sparseArray3.put(3, 4);

    assertThat(contentEquals(sparseArray1, sparseArray2)).isFalse();
    assertThat(contentEquals(sparseArray1, sparseArray3)).isFalse();
  }

  @Test
  @Config(minSdk = 16) // Specifies the minimum SDK to enforce the test to run with all API levels.
  public void contentHashCode_sparseArraysWithEqualContent_returnsEqualContentHashCode() {
    SparseArray<Integer> sparseArray1 = new SparseArray<>();
    sparseArray1.put(1, 2);
    sparseArray1.put(3, 4);
    SparseArray<Integer> sparseArray2 = new SparseArray<>();
    sparseArray2.put(3, 4);
    sparseArray2.put(1, 2);

    assertThat(contentHashCode(sparseArray1)).isEqualTo(contentHashCode(sparseArray2));
  }

  @Test
  @Config(minSdk = 16) // Specifies the minimum SDK to enforce the test to run with all API levels.
  public void contentHashCode_sparseArraysWithDifferentContent_returnsDifferentContentHashCode() {
    // In theory this is not guaranteed though, adding this test to ensure a sensible
    // contentHashCode implementation.
    SparseArray<Integer> sparseArray1 = new SparseArray<>();
    sparseArray1.put(1, 2);
    sparseArray1.put(3, 4);
    SparseArray<Integer> sparseArray2 = new SparseArray<>();
    sparseArray2.put(3, 2);
    sparseArray2.put(1, 4);

    assertThat(contentHashCode(sparseArray1)).isNotEqualTo(contentHashCode(sparseArray2));
  }

  private static void assertEscapeUnescapeFileName(String fileName, String escapedFileName) {
    assertThat(escapeFileName(fileName)).isEqualTo(escapedFileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static void assertEscapeUnescapeFileName(String fileName) {
    String escapedFileName = Util.escapeFileName(fileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static LongArray newLongArray(long... values) {
    LongArray longArray = new LongArray();
    for (long value : values) {
      longArray.add(value);
    }
    return longArray;
  }

  /** Returns a {@link SQLiteOpenHelper} that provides an in-memory database. */
  private static SQLiteOpenHelper getInMemorySQLiteOpenHelper() {
    return new SQLiteOpenHelper(
        /* context= */ null, /* name= */ null, /* factory= */ null, /* version= */ 1) {
      @Override
      public void onCreate(SQLiteDatabase db) {}

      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    };
  }
}

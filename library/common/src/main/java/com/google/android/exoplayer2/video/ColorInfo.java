/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Stores color info.
 *
 * <p>When a {@code null} {@code ColorInfo} instance is used, this often represents a generic {@link
 * #SDR_BT709_LIMITED} instance.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ColorInfo implements Bundleable {

  /**
   * Builds {@link ColorInfo} instances.
   *
   * <p>Use {@link ColorInfo#buildUpon} to obtain a builder representing an existing {@link
   * ColorInfo}.
   */
  public static final class Builder {
    private @C.ColorSpace int colorSpace;
    private @C.ColorRange int colorRange;
    private @C.ColorTransfer int colorTransfer;
    @Nullable private byte[] hdrStaticInfo;

    /** Creates a new instance with default values. */
    public Builder() {
      colorSpace = Format.NO_VALUE;
      colorRange = Format.NO_VALUE;
      colorTransfer = Format.NO_VALUE;
    }

    /** Creates a new instance to build upon the provided {@link ColorInfo}. */
    private Builder(ColorInfo colorInfo) {
      this.colorSpace = colorInfo.colorSpace;
      this.colorRange = colorInfo.colorRange;
      this.colorTransfer = colorInfo.colorTransfer;
      this.hdrStaticInfo = colorInfo.hdrStaticInfo;
    }

    /**
     * Sets the color space.
     *
     * <p>Valid values are {@link C#COLOR_SPACE_BT601}, {@link C#COLOR_SPACE_BT709}, {@link
     * C#COLOR_SPACE_BT2020} or {@link Format#NO_VALUE} if unknown.
     *
     * @param colorSpace The color space. The default value is {@link Format#NO_VALUE}.
     * @return This {@code Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setColorSpace(@C.ColorSpace int colorSpace) {
      this.colorSpace = colorSpace;
      return this;
    }

    /**
     * Sets the color range.
     *
     * <p>Valid values are {@link C#COLOR_RANGE_LIMITED}, {@link C#COLOR_RANGE_FULL} or {@link
     * Format#NO_VALUE} if unknown.
     *
     * @param colorRange The color range. The default value is {@link Format#NO_VALUE}.
     * @return This {@code Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setColorRange(@C.ColorRange int colorRange) {
      this.colorRange = colorRange;
      return this;
    }

    /**
     * Sets the color transfer.
     *
     * <p>Valid values are {@link C#COLOR_TRANSFER_LINEAR}, {@link C#COLOR_TRANSFER_HLG}, {@link
     * C#COLOR_TRANSFER_ST2084}, {@link C#COLOR_TRANSFER_SDR} or {@link Format#NO_VALUE} if unknown.
     *
     * @param colorTransfer The color transfer. The default value is {@link Format#NO_VALUE}.
     * @return This {@code Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setColorTransfer(@C.ColorTransfer int colorTransfer) {
      this.colorTransfer = colorTransfer;
      return this;
    }

    /**
     * Sets the HdrStaticInfo as defined in CTA-861.3.
     *
     * @param hdrStaticInfo The HdrStaticInfo. The default value is {@code null}.
     * @return This {@code Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setHdrStaticInfo(@Nullable byte[] hdrStaticInfo) {
      this.hdrStaticInfo = hdrStaticInfo;
      return this;
    }

    /** Builds a new {@link ColorInfo} instance. */
    public ColorInfo build() {
      return new ColorInfo(colorSpace, colorRange, colorTransfer, hdrStaticInfo);
    }
  }

  /** Color info representing SDR BT.709 limited range, which is a common SDR video color format. */
  public static final ColorInfo SDR_BT709_LIMITED =
      new ColorInfo(
          C.COLOR_SPACE_BT709,
          C.COLOR_RANGE_LIMITED,
          C.COLOR_TRANSFER_SDR,
          /* hdrStaticInfo= */ null);

  /**
   * Color info representing SDR sRGB in accordance with {@link
   * android.hardware.DataSpace#DATASPACE_SRGB}, which is a common SDR image color format.
   */
  public static final ColorInfo SRGB_BT709_FULL =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT709)
          .setColorRange(C.COLOR_RANGE_FULL)
          .setColorTransfer(C.COLOR_TRANSFER_SRGB)
          .build();

  /**
   * Returns the {@link C.ColorSpace} corresponding to the given ISO color primary code, as per
   * table A.7.21.1 in Rec. ITU-T T.832 (03/2009), or {@link Format#NO_VALUE} if no mapping can be
   * made.
   */
  @Pure
  public static @C.ColorSpace int isoColorPrimariesToColorSpace(int isoColorPrimaries) {
    switch (isoColorPrimaries) {
      case 1:
        return C.COLOR_SPACE_BT709;
      case 4: // BT.470M.
      case 5: // BT.470BG.
      case 6: // SMPTE 170M.
      case 7: // SMPTE 240M.
        return C.COLOR_SPACE_BT601;
      case 9:
        return C.COLOR_SPACE_BT2020;
      default:
        return Format.NO_VALUE;
    }
  }

  /**
   * Returns the {@link C.ColorTransfer} corresponding to the given ISO transfer characteristics
   * code, as per table A.7.21.2 in Rec. ITU-T T.832 (03/2009), or {@link Format#NO_VALUE} if no
   * mapping can be made.
   */
  @Pure
  public static @C.ColorTransfer int isoTransferCharacteristicsToColorTransfer(
      int isoTransferCharacteristics) {
    switch (isoTransferCharacteristics) {
      case 1: // BT.709.
      case 6: // SMPTE 170M.
      case 7: // SMPTE 240M.
        return C.COLOR_TRANSFER_SDR;
      case 4:
        return C.COLOR_TRANSFER_GAMMA_2_2;
      case 13:
        return C.COLOR_TRANSFER_SRGB;
      case 16:
        return C.COLOR_TRANSFER_ST2084;
      case 18:
        return C.COLOR_TRANSFER_HLG;
      default:
        return Format.NO_VALUE;
    }
  }

  /**
   * Returns whether the {@code ColorInfo} uses an HDR {@link C.ColorTransfer}.
   *
   * <p>{@link C#COLOR_TRANSFER_LINEAR} is not considered to be an HDR {@link C.ColorTransfer},
   * because it may represent either SDR or HDR contents.
   */
  public static boolean isTransferHdr(@Nullable ColorInfo colorInfo) {
    return colorInfo != null
        && (colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
            || colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084);
  }

  /** The {@link C.ColorSpace}. */
  public final @C.ColorSpace int colorSpace;

  /** The {@link C.ColorRange}. */
  public final @C.ColorRange int colorRange;

  /** The {@link C.ColorTransfer}. */
  public final @C.ColorTransfer int colorTransfer;

  /** HdrStaticInfo as defined in CTA-861.3, or null if none specified. */
  @Nullable public final byte[] hdrStaticInfo;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * Constructs the ColorInfo.
   *
   * @param colorSpace The color space of the video.
   * @param colorRange The color range of the video.
   * @param colorTransfer The color transfer characteristics of the video.
   * @param hdrStaticInfo HdrStaticInfo as defined in CTA-861.3, or null if none specified.
   * @deprecated Use {@link Builder}.
   */
  @Deprecated
  public ColorInfo(
      @C.ColorSpace int colorSpace,
      @C.ColorRange int colorRange,
      @C.ColorTransfer int colorTransfer,
      @Nullable byte[] hdrStaticInfo) {
    this.colorSpace = colorSpace;
    this.colorRange = colorRange;
    this.colorTransfer = colorTransfer;
    this.hdrStaticInfo = hdrStaticInfo;
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * Returns whether this instance is valid.
   *
   * <p>This instance is valid if no members are {@link Format#NO_VALUE}.
   */
  public boolean isValid() {
    return colorSpace != Format.NO_VALUE
        && colorRange != Format.NO_VALUE
        && colorTransfer != Format.NO_VALUE;
  }

  /**
   * Returns a prettier {@link String} than {@link #toString()}, intended for logging.
   *
   * @see Format#toLogString(Format)
   */
  public String toLogString() {
    if (!isValid()) {
      return "NA";
    }

    return Util.formatInvariant(
        "%s/%s/%s",
        colorSpaceToString(colorSpace),
        colorRangeToString(colorRange),
        colorTransferToString(colorTransfer));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ColorInfo other = (ColorInfo) obj;
    return colorSpace == other.colorSpace
        && colorRange == other.colorRange
        && colorTransfer == other.colorTransfer
        && Arrays.equals(hdrStaticInfo, other.hdrStaticInfo);
  }

  @Override
  public String toString() {
    return "ColorInfo("
        + colorSpaceToString(colorSpace)
        + ", "
        + colorRangeToString(colorRange)
        + ", "
        + colorTransferToString(colorTransfer)
        + ", "
        + (hdrStaticInfo != null)
        + ")";
  }

  private static String colorSpaceToString(@C.ColorSpace int colorSpace) {
    // LINT.IfChange(color_space)
    switch (colorSpace) {
      case Format.NO_VALUE:
        return "Unset color space";
      case C.COLOR_SPACE_BT601:
        return "BT601";
      case C.COLOR_SPACE_BT709:
        return "BT709";
      case C.COLOR_SPACE_BT2020:
        return "BT2020";
      default:
        return "Undefined color space";
    }
  }

  private static String colorTransferToString(@C.ColorTransfer int colorTransfer) {
    // LINT.IfChange(color_transfer)
    switch (colorTransfer) {
      case Format.NO_VALUE:
        return "Unset color transfer";
      case C.COLOR_TRANSFER_LINEAR:
        return "Linear";
      case C.COLOR_TRANSFER_SDR:
        return "SDR SMPTE 170M";
      case C.COLOR_TRANSFER_SRGB:
        return "sRGB";
      case C.COLOR_TRANSFER_GAMMA_2_2:
        return "Gamma 2.2";
      case C.COLOR_TRANSFER_ST2084:
        return "ST2084 PQ";
      case C.COLOR_TRANSFER_HLG:
        return "HLG";
      default:
        return "Undefined color transfer";
    }
  }

  private static String colorRangeToString(@C.ColorRange int colorRange) {
    // LINT.IfChange(color_range)
    switch (colorRange) {
      case Format.NO_VALUE:
        return "Unset color range";
      case C.COLOR_RANGE_LIMITED:
        return "Limited range";
      case C.COLOR_RANGE_FULL:
        return "Full range";
      default:
        return "Undefined color range";
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + colorSpace;
      result = 31 * result + colorRange;
      result = 31 * result + colorTransfer;
      result = 31 * result + Arrays.hashCode(hdrStaticInfo);
      hashCode = result;
    }
    return hashCode;
  }

  // Bundleable implementation

  private static final String FIELD_COLOR_SPACE = Util.intToStringMaxRadix(0);
  private static final String FIELD_COLOR_RANGE = Util.intToStringMaxRadix(1);
  private static final String FIELD_COLOR_TRANSFER = Util.intToStringMaxRadix(2);
  private static final String FIELD_HDR_STATIC_INFO = Util.intToStringMaxRadix(3);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_COLOR_SPACE, colorSpace);
    bundle.putInt(FIELD_COLOR_RANGE, colorRange);
    bundle.putInt(FIELD_COLOR_TRANSFER, colorTransfer);
    bundle.putByteArray(FIELD_HDR_STATIC_INFO, hdrStaticInfo);
    return bundle;
  }

  public static final Creator<ColorInfo> CREATOR =
      bundle ->
          new ColorInfo(
              bundle.getInt(FIELD_COLOR_SPACE, Format.NO_VALUE),
              bundle.getInt(FIELD_COLOR_RANGE, Format.NO_VALUE),
              bundle.getInt(FIELD_COLOR_TRANSFER, Format.NO_VALUE),
              bundle.getByteArray(FIELD_HDR_STATIC_INFO));
}

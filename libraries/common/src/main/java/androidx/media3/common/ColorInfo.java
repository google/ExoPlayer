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
package androidx.media3.common;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Stores color info.
 *
 * <p>When a {@code null} {@code ColorInfo} instance is used, this often represents a generic {@link
 * #SDR_BT709_LIMITED} instance.
 */
@UnstableApi
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
    private int lumaBitdepth;
    private int chromaBitdepth;

    /** Creates a new instance with default values. */
    public Builder() {
      colorSpace = Format.NO_VALUE;
      colorRange = Format.NO_VALUE;
      colorTransfer = Format.NO_VALUE;
      lumaBitdepth = Format.NO_VALUE;
      chromaBitdepth = Format.NO_VALUE;
    }

    /** Creates a new instance to build upon the provided {@link ColorInfo}. */
    private Builder(ColorInfo colorInfo) {
      this.colorSpace = colorInfo.colorSpace;
      this.colorRange = colorInfo.colorRange;
      this.colorTransfer = colorInfo.colorTransfer;
      this.hdrStaticInfo = colorInfo.hdrStaticInfo;
      this.lumaBitdepth = colorInfo.lumaBitdepth;
      this.chromaBitdepth = colorInfo.chromaBitdepth;
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

    /**
     * Sets the luma bit depth.
     *
     * @param lumaBitdepth The lumaBitdepth. The default value is {@link Format#NO_VALUE}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setLumaBitdepth(int lumaBitdepth) {
      this.lumaBitdepth = lumaBitdepth;
      return this;
    }

    /**
     * Sets chroma bit depth.
     *
     * @param chromaBitdepth The chromaBitdepth. The default value is {@link Format#NO_VALUE}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setChromaBitdepth(int chromaBitdepth) {
      this.chromaBitdepth = chromaBitdepth;
      return this;
    }

    /** Builds a new {@link ColorInfo} instance. */
    public ColorInfo build() {
      return new ColorInfo(
          colorSpace, colorRange, colorTransfer, hdrStaticInfo, lumaBitdepth, chromaBitdepth);
    }
  }

  /** Color info representing SDR BT.709 limited range, which is a common SDR video color format. */
  public static final ColorInfo SDR_BT709_LIMITED =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT709)
          .setColorRange(C.COLOR_RANGE_LIMITED)
          .setColorTransfer(C.COLOR_TRANSFER_SDR)
          .build();

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
   * Returns whether the given color info is equivalent to values for a standard dynamic range video
   * that could generally be assumed if no further information is given.
   *
   * <p>The color info is deemed to be equivalent to SDR video if it either has unset values or
   * values matching a 8-bit (chroma+luma), BT.709 or BT.601 color space, SDR transfer and Limited
   * range color info.
   *
   * @param colorInfo The color info to evaluate.
   * @return Whether the given color info is equivalent to the assumed default SDR color info.
   */
  @EnsuresNonNullIf(result = false, expression = "#1")
  public static boolean isEquivalentToAssumedSdrDefault(@Nullable ColorInfo colorInfo) {
    if (colorInfo == null) {
      return true;
    }
    return (colorInfo.colorSpace == Format.NO_VALUE
            || colorInfo.colorSpace == C.COLOR_SPACE_BT709
            || colorInfo.colorSpace == C.COLOR_SPACE_BT601)
        && (colorInfo.colorRange == Format.NO_VALUE
            || colorInfo.colorRange == C.COLOR_RANGE_LIMITED)
        && (colorInfo.colorTransfer == Format.NO_VALUE
            || colorInfo.colorTransfer == C.COLOR_TRANSFER_SDR)
        && colorInfo.hdrStaticInfo == null
        && (colorInfo.chromaBitdepth == Format.NO_VALUE || colorInfo.chromaBitdepth == 8)
        && (colorInfo.lumaBitdepth == Format.NO_VALUE || colorInfo.lumaBitdepth == 8);
  }

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

  /** The {@link C.ColorSpace}, or {@link Format#NO_VALUE} if not set. */
  public final @C.ColorSpace int colorSpace;

  /** The {@link C.ColorRange}, or {@link Format#NO_VALUE} if not set. */
  public final @C.ColorRange int colorRange;

  /** The {@link C.ColorTransfer}, or {@link Format#NO_VALUE} if not set. */
  public final @C.ColorTransfer int colorTransfer;

  /** HdrStaticInfo as defined in CTA-861.3, or null if none specified. */
  @Nullable public final byte[] hdrStaticInfo;

  /** The bit depth of the luma samples of the video, or {@link Format#NO_VALUE} if not set. */
  public final int lumaBitdepth;

  /**
   * The bit depth of the chroma samples of the video, or {@link Format#NO_VALUE} if not set. It may
   * differ from the luma bit depth.
   */
  public final int chromaBitdepth;

  // Lazily initialized hashcode.
  private int hashCode;

  private ColorInfo(
      @C.ColorSpace int colorSpace,
      @C.ColorRange int colorRange,
      @C.ColorTransfer int colorTransfer,
      @Nullable byte[] hdrStaticInfo,
      int lumaBitdepth,
      int chromaBitdepth) {
    this.colorSpace = colorSpace;
    this.colorRange = colorRange;
    this.colorTransfer = colorTransfer;
    this.hdrStaticInfo = hdrStaticInfo;
    this.lumaBitdepth = lumaBitdepth;
    this.chromaBitdepth = chromaBitdepth;
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * Returns whether this instance is valid.
   *
   * <p>This instance is valid if at least one between bitdepths and DataSpace info are valid.
   */
  public boolean isValid() {
    return isBitdepthValid() || isDataSpaceValid();
  }

  /**
   * Returns whether this instance has valid bitdepths.
   *
   * <p>This instance has valid bitdepths if none of them is {@link Format#NO_VALUE}.
   */
  public boolean isBitdepthValid() {
    return lumaBitdepth != Format.NO_VALUE && chromaBitdepth != Format.NO_VALUE;
  }

  /**
   * Returns whether this instance has valid DataSpace members.
   *
   * <p>This instance is valid if no DataSpace members are {@link Format#NO_VALUE}.
   */
  public boolean isDataSpaceValid() {
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
    String dataspaceString =
        isDataSpaceValid()
            ? Util.formatInvariant(
                "%s/%s/%s",
                colorSpaceToString(colorSpace),
                colorRangeToString(colorRange),
                colorTransferToString(colorTransfer))
            : "NA/NA/NA";
    String bitdepthsString = isBitdepthValid() ? lumaBitdepth + "/" + chromaBitdepth : "NA/NA";
    return dataspaceString + "/" + bitdepthsString;
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
        && Arrays.equals(hdrStaticInfo, other.hdrStaticInfo)
        && lumaBitdepth == other.lumaBitdepth
        && chromaBitdepth == other.chromaBitdepth;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + colorSpace;
      result = 31 * result + colorRange;
      result = 31 * result + colorTransfer;
      result = 31 * result + Arrays.hashCode(hdrStaticInfo);
      result = 31 * result + lumaBitdepth;
      result = 31 * result + chromaBitdepth;
      hashCode = result;
    }
    return hashCode;
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
        + ", "
        + lumaBitdepthToString(lumaBitdepth)
        + ", "
        + chromaBitdepthToString(chromaBitdepth)
        + ")";
  }

  private static String lumaBitdepthToString(int val) {
    return val != Format.NO_VALUE ? val + "bit Luma" : "NA";
  }

  private static String chromaBitdepthToString(int val) {
    return val != Format.NO_VALUE ? val + "bit Chroma" : "NA";
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

  // Bundleable implementation

  private static final String FIELD_COLOR_SPACE = Util.intToStringMaxRadix(0);
  private static final String FIELD_COLOR_RANGE = Util.intToStringMaxRadix(1);
  private static final String FIELD_COLOR_TRANSFER = Util.intToStringMaxRadix(2);
  private static final String FIELD_HDR_STATIC_INFO = Util.intToStringMaxRadix(3);
  private static final String FIELD_LUMA_BITDEPTH = Util.intToStringMaxRadix(4);
  private static final String FIELD_CHROMA_BITDEPTH = Util.intToStringMaxRadix(5);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_COLOR_SPACE, colorSpace);
    bundle.putInt(FIELD_COLOR_RANGE, colorRange);
    bundle.putInt(FIELD_COLOR_TRANSFER, colorTransfer);
    bundle.putByteArray(FIELD_HDR_STATIC_INFO, hdrStaticInfo);
    bundle.putInt(FIELD_LUMA_BITDEPTH, lumaBitdepth);
    bundle.putInt(FIELD_CHROMA_BITDEPTH, chromaBitdepth);
    return bundle;
  }

  /**
   * @deprecated Use {@link #fromBundle} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<ColorInfo> CREATOR = ColorInfo::fromBundle;

  /** Restores a {@code ColorInfo} from a {@link Bundle}. */
  public static ColorInfo fromBundle(Bundle bundle) {
    return new ColorInfo(
        bundle.getInt(FIELD_COLOR_SPACE, Format.NO_VALUE),
        bundle.getInt(FIELD_COLOR_RANGE, Format.NO_VALUE),
        bundle.getInt(FIELD_COLOR_TRANSFER, Format.NO_VALUE),
        bundle.getByteArray(FIELD_HDR_STATIC_INFO),
        bundle.getInt(FIELD_LUMA_BITDEPTH, Format.NO_VALUE),
        bundle.getInt(FIELD_CHROMA_BITDEPTH, Format.NO_VALUE));
  }
}

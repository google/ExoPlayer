package com.google.android.exoplayer2

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.exoplayer2.drm.DrmInitData
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame
import com.google.android.exoplayer2.testutil.TestUtil
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.video.ColorInfo
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [Format].  */
@RunWith(AndroidJUnit4::class)
class FormatTest {
    companion object {
        private val initData: List<ByteArray> = listOf(
                byteArrayOf(1, 2, 3),
                byteArrayOf(4, 5, 6)
        )
    }

    @Test
    fun testParcelable() {
        val drmData1 = SchemeData(C.WIDEVINE_UUID, MimeTypes.VIDEO_MP4,
                TestUtil.buildTestData(128, 1 /* data seed */))
        val drmData2 = SchemeData(C.UUID_NIL, MimeTypes.VIDEO_WEBM,
                TestUtil.buildTestData(128, 1 /* data seed */))
        val drmInitData = DrmInitData(drmData1, drmData2)
        val projectionData = byteArrayOf(1, 2, 3)
        val metadata = Metadata(
                TextInformationFrame("id1", "description1", "value1"),
                TextInformationFrame("id2", "description2", "value2"))
        val colorInfo = ColorInfo(C.COLOR_SPACE_BT709,
                C.COLOR_RANGE_LIMITED, C.COLOR_TRANSFER_SDR, byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        val formatToParcel = Format(
                "id",
                "label",
                C.SELECTION_FLAG_DEFAULT,
                C.ROLE_FLAG_MAIN,
                /* bitrate= */ 1024,
                "codec",
                metadata,
                /* containerMimeType= */ MimeTypes.VIDEO_MP4,
                /* sampleMimeType= */ MimeTypes.VIDEO_H264,
                /* maxInputSize= */ 2048,
                initData,
                drmInitData,
                Format.OFFSET_SAMPLE_RELATIVE,
                /* width= */ 1920,
                /* height= */ 1080,
                /* frameRate= */ 24f,
                /* rotationDegrees= */ 90,
                /* pixelWidthHeightRatio= */ 2f,
                projectionData,
                C.STEREO_MODE_TOP_BOTTOM,
                colorInfo,
                /* channelCount= */ 6,
                /* sampleRate= */ 44100,
                C.ENCODING_PCM_24BIT,
                /* encoderDelay= */ 1001,
                /* encoderPadding= */ 1002,
                "language",
                /* accessibilityChannel= */ Format.NO_VALUE,
                /* exoMediaCryptoType= */ null
        )
        val parcel = Parcel.obtain()
        formatToParcel.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val formatFromParcel = Format.CREATOR.createFromParcel(parcel)
        Truth.assertThat(formatFromParcel).isEqualTo(formatToParcel)
        parcel.recycle()
    }
}

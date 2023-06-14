/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.muxer.Mp4Utils.MVHD_TIMEBASE;
import static java.lang.Math.max;

import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Builds the moov box structure of an MP4 file. */
/* package */ class Mp4MoovStructure {
  /** Provides track's metadata like media format, written samples. */
  public interface TrackMetadataProvider {
    Format format();

    int sortKey();

    int videoUnitTimebase();

    ImmutableList<BufferInfo> writtenSamples();

    ImmutableList<Long> writtenChunkOffsets();

    ImmutableList<Integer> writtenChunkSampleCounts();
  }

  private final MetadataCollector metadataCollector;
  private final @Mp4Muxer.LastFrameDurationBehavior int lastFrameDurationBehavior;

  public Mp4MoovStructure(
      MetadataCollector metadataCollector,
      @Mp4Muxer.LastFrameDurationBehavior int lastFrameDurationBehavior) {
    this.metadataCollector = metadataCollector;
    this.lastFrameDurationBehavior = lastFrameDurationBehavior;
  }

  /** Generates a mdat header. */
  @SuppressWarnings("InlinedApi")
  public ByteBuffer moovMetadataHeader(
      List<? extends TrackMetadataProvider> tracks, long minInputPtsUs) {
    List<ByteBuffer> trakBoxes = new ArrayList<>();

    int nextTrackId = 1;
    long videoDurationUs = 0L;
    for (int i = 0; i < tracks.size(); i++) {
      TrackMetadataProvider track = tracks.get(i);
      if (!track.writtenSamples().isEmpty()) {
        Format format = track.format();
        String languageCode = bcp47LanguageTagToIso3(format.language);

        // Generate the sample durations to calculate the total duration for tkhd box.
        List<Long> sampleDurationsVu =
            Boxes.durationsVuForStts(
                track.writtenSamples(),
                minInputPtsUs,
                track.videoUnitTimebase(),
                lastFrameDurationBehavior);

        long trackDurationInTrackUnitsVu = 0;
        for (int j = 0; j < sampleDurationsVu.size(); j++) {
          trackDurationInTrackUnitsVu += sampleDurationsVu.get(j);
        }

        long trackDurationUs =
            Mp4Utils.usFromVu(trackDurationInTrackUnitsVu, track.videoUnitTimebase());

        @C.TrackType int trackType = MimeTypes.getTrackType(format.sampleMimeType);
        ByteBuffer stts = Boxes.stts(sampleDurationsVu);
        ByteBuffer stsz = Boxes.stsz(track.writtenSamples());
        ByteBuffer stsc = Boxes.stsc(track.writtenChunkSampleCounts());
        ByteBuffer co64 = Boxes.co64(track.writtenChunkOffsets());

        String handlerType;
        String handlerName;
        ByteBuffer mhdBox;
        ByteBuffer sampleEntryBox;
        ByteBuffer stsdBox;
        ByteBuffer stblBox;

        switch (trackType) {
          case C.TRACK_TYPE_VIDEO:
            handlerType = "vide";
            handlerName = "VideoHandle";
            mhdBox = Boxes.vmhd();
            sampleEntryBox = Boxes.videoSampleEntry(format);
            stsdBox = Boxes.stsd(sampleEntryBox);
            stblBox =
                Boxes.stbl(stsdBox, stts, stsz, stsc, co64, Boxes.stss(track.writtenSamples()));
            break;
          case C.TRACK_TYPE_AUDIO:
            handlerType = "soun";
            handlerName = "SoundHandle";
            mhdBox = Boxes.smhd();
            sampleEntryBox = Boxes.audioSampleEntry(format);
            stsdBox = Boxes.stsd(sampleEntryBox);
            stblBox = Boxes.stbl(stsdBox, stts, stsz, stsc, co64);
            break;
          case C.TRACK_TYPE_METADATA:
            // TODO: (b/280443593) - Check if we can identify a metadata track type from a custom
            //  mime type.
          case C.TRACK_TYPE_UNKNOWN:
            handlerType = "meta";
            handlerName = "MetaHandle";
            mhdBox = Boxes.nmhd();
            sampleEntryBox = Boxes.textMetaDataSampleEntry(format);
            stsdBox = Boxes.stsd(sampleEntryBox);
            stblBox = Boxes.stbl(stsdBox, stts, stsz, stsc, co64);
            break;
          default:
            throw new IllegalArgumentException("Unsupported track type");
        }

        // The below statement is also a description of how a mdat box looks like, with all the
        // inner boxes and what they actually store. Although they're technically instance methods,
        // everything that is written to a box is visible in the argument list.
        ByteBuffer trakBox =
            Boxes.trak(
                Boxes.tkhd(
                    nextTrackId,
                    // Using the time base of the entire file, not that of the track; otherwise,
                    // Quicktime will stretch the audio accordingly, see b/158120042.
                    (int) Mp4Utils.vuFromUs(trackDurationUs, MVHD_TIMEBASE),
                    metadataCollector.modificationTimestampSeconds,
                    metadataCollector.orientation,
                    format),
                Boxes.mdia(
                    Boxes.mdhd(
                        trackDurationInTrackUnitsVu,
                        track.videoUnitTimebase(),
                        metadataCollector.modificationTimestampSeconds,
                        languageCode),
                    Boxes.hdlr(handlerType, handlerName),
                    Boxes.minf(mhdBox, Boxes.dinf(Boxes.dref(Boxes.localUrl())), stblBox)));

        trakBoxes.add(trakBox);
        videoDurationUs = max(videoDurationUs, trackDurationUs);
        nextTrackId++;
      }
    }

    ByteBuffer mvhdBox =
        Boxes.mvhd(nextTrackId, metadataCollector.modificationTimestampSeconds, videoDurationUs);
    ByteBuffer udtaBox = Boxes.udta(metadataCollector.location);
    ByteBuffer metaBox =
        metadataCollector.metadataPairs.isEmpty()
            ? ByteBuffer.allocate(0)
            : Boxes.meta(
                Boxes.hdlr(/* handlerType= */ "mdta", /* handlerName= */ ""),
                Boxes.keys(Lists.newArrayList(metadataCollector.metadataPairs.keySet())),
                Boxes.ilst(Lists.newArrayList(metadataCollector.metadataPairs.values())));

    ByteBuffer moovBox;
    moovBox =
        Boxes.moov(mvhdBox, udtaBox, metaBox, trakBoxes, /* mvexBox= */ ByteBuffer.allocate(0));

    // Also add XMP if needed
    if (metadataCollector.xmpData != null) {
      return BoxUtils.concatenateBuffers(
          moovBox, Boxes.uuid(Boxes.XMP_UUID, metadataCollector.xmpData.duplicate()));
    } else {
      // No need for another copy if there is no XMP to be appended.
      return moovBox;
    }
  }

  /** Returns an ISO 639-2/T (ISO3) language code for the IETF BCP 47 language tag. */
  private static @PolyNull String bcp47LanguageTagToIso3(@PolyNull String languageTag) {
    if (languageTag == null) {
      return null;
    }

    Locale locale =
        Util.SDK_INT >= 21 ? Locale.forLanguageTag(languageTag) : new Locale(languageTag);

    return locale.getISO3Language().isEmpty() ? languageTag : locale.getISO3Language();
  }
}

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
package androidx.media3.extractor;

import static androidx.media3.common.FileTypes.inferFileTypeFromResponseHeaders;
import static androidx.media3.common.FileTypes.inferFileTypeFromUri;
import static androidx.media3.extractor.mp4.Mp4Extractor.FLAG_READ_MOTION_PHOTO_METADATA;
import static androidx.media3.extractor.mp4.Mp4Extractor.FLAG_READ_SEF_DATA;

import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.amr.AmrExtractor;
import androidx.media3.extractor.avi.AviExtractor;
import androidx.media3.extractor.bmp.BmpExtractor;
import androidx.media3.extractor.flac.FlacExtractor;
import androidx.media3.extractor.flv.FlvExtractor;
import androidx.media3.extractor.heif.HeifExtractor;
import androidx.media3.extractor.jpeg.JpegExtractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.ogg.OggExtractor;
import androidx.media3.extractor.png.PngExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleTranscodingExtractor;
import androidx.media3.extractor.ts.Ac3Extractor;
import androidx.media3.extractor.ts.Ac4Extractor;
import androidx.media3.extractor.ts.AdtsExtractor;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.PsExtractor;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.TsPayloadReader;
import androidx.media3.extractor.wav.WavExtractor;
import androidx.media3.extractor.webp.WebpExtractor;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 *   <li>MP4, including M4A ({@link Mp4Extractor})
 *   <li>fMP4 ({@link FragmentedMp4Extractor})
 *   <li>Matroska and WebM ({@link MatroskaExtractor})
 *   <li>Ogg Vorbis/FLAC ({@link OggExtractor}
 *   <li>MP3 ({@link Mp3Extractor})
 *   <li>AAC ({@link AdtsExtractor})
 *   <li>MPEG TS ({@link TsExtractor})
 *   <li>MPEG PS ({@link PsExtractor})
 *   <li>FLV ({@link FlvExtractor})
 *   <li>WAV ({@link WavExtractor})
 *   <li>AC3 ({@link Ac3Extractor})
 *   <li>AC4 ({@link Ac4Extractor})
 *   <li>AMR ({@link AmrExtractor})
 *   <li>FLAC
 *       <ul>
 *         <li>If available, the FLAC extension's {@code androidx.media3.decoder.flac.FlacExtractor}
 *             is used.
 *         <li>Otherwise, the core {@link FlacExtractor} is used. Note that Android devices do not
 *             generally include a FLAC decoder before API 27. This can be worked around by using
 *             the FLAC extension or the FFmpeg extension.
 *       </ul>
 *   <li>JPEG ({@link JpegExtractor})
 *   <li>PNG ({@link PngExtractor})
 *   <li>WEBP ({@link WebpExtractor})
 *   <li>BMP ({@link BmpExtractor})
 *   <li>HEIF ({@link HeifExtractor})
 *   <li>MIDI, if available, the MIDI extension's {@code androidx.media3.decoder.midi.MidiExtractor}
 *       is used.
 * </ul>
 */
@UnstableApi
public final class DefaultExtractorsFactory implements ExtractorsFactory {

  // Extractors order is optimized according to
  // https://docs.google.com/document/d/1w2mKaWMxfz2Ei8-LdxqbPs1VLe_oudB-eryXXw9OvQQ.
  // The JPEG extractor appears after audio/video extractors because we expect audio/video input to
  // be more common.
  private static final int[] DEFAULT_EXTRACTOR_ORDER =
      new int[] {
        FileTypes.FLV,
        FileTypes.FLAC,
        FileTypes.WAV,
        FileTypes.MP4,
        FileTypes.AMR,
        FileTypes.PS,
        FileTypes.OGG,
        FileTypes.TS,
        FileTypes.MATROSKA,
        FileTypes.ADTS,
        FileTypes.AC3,
        FileTypes.AC4,
        FileTypes.MP3,
        // The following extractors are not part of the optimized ordering, and were appended
        // without further analysis.
        FileTypes.AVI,
        FileTypes.MIDI,
        FileTypes.JPEG,
        FileTypes.PNG,
        FileTypes.WEBP,
        FileTypes.BMP,
        FileTypes.HEIF
      };

  private static final ExtensionLoader FLAC_EXTENSION_LOADER =
      new ExtensionLoader(DefaultExtractorsFactory::getFlacExtractorConstructor);
  private static final ExtensionLoader MIDI_EXTENSION_LOADER =
      new ExtensionLoader(DefaultExtractorsFactory::getMidiExtractorConstructor);

  private boolean constantBitrateSeekingEnabled;
  private boolean constantBitrateSeekingAlwaysEnabled;
  private @AdtsExtractor.Flags int adtsFlags;
  private @AmrExtractor.Flags int amrFlags;
  private @FlacExtractor.Flags int flacFlags;
  private @MatroskaExtractor.Flags int matroskaFlags;
  private @Mp4Extractor.Flags int mp4Flags;
  private @FragmentedMp4Extractor.Flags int fragmentedMp4Flags;
  private @Mp3Extractor.Flags int mp3Flags;
  private @TsExtractor.Mode int tsMode;
  private @DefaultTsPayloadReaderFactory.Flags int tsFlags;
  // TODO (b/261183220): Initialize tsSubtitleFormats in constructor once shrinking bug is fixed.
  @Nullable private ImmutableList<Format> tsSubtitleFormats;
  private int tsTimestampSearchBytes;
  private boolean textTrackTranscodingEnabled;
  private SubtitleParser.Factory subtitleParserFactory;
  private @JpegExtractor.Flags int jpegFlags;

  public DefaultExtractorsFactory() {
    tsMode = TsExtractor.MODE_SINGLE_PMT;
    tsTimestampSearchBytes = TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES;
    subtitleParserFactory = new DefaultSubtitleParserFactory();
  }

  /**
   * Convenience method to set whether approximate seeking using constant bitrate assumptions should
   * be enabled for all extractors that support it. If set to true, the flags required to enable
   * this functionality will be OR'd with those passed to the setters when creating extractor
   * instances. If set to false then the flags passed to the setters will be used without
   * modification.
   *
   * @param constantBitrateSeekingEnabled Whether approximate seeking using a constant bitrate
   *     assumption should be enabled for all extractors that support it.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setConstantBitrateSeekingEnabled(
      boolean constantBitrateSeekingEnabled) {
    this.constantBitrateSeekingEnabled = constantBitrateSeekingEnabled;
    return this;
  }

  /**
   * Convenience method to set whether approximate seeking using constant bitrate assumptions should
   * be enabled for all extractors that support it, and if it should be enabled even if the content
   * length (and hence the duration of the media) is unknown. If set to true, the flags required to
   * enable this functionality will be OR'd with those passed to the setters when creating extractor
   * instances. If set to false then the flags passed to the setters will be used without
   * modification.
   *
   * <p>When seeking into content where the length is unknown, application code should ensure that
   * requested seek positions are valid, or should be ready to handle playback failures reported
   * through {@link Player.Listener#onPlayerError} with {@link PlaybackException#errorCode} set to
   * {@link PlaybackException#ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE}.
   *
   * @param constantBitrateSeekingAlwaysEnabled Whether approximate seeking using a constant bitrate
   *     assumption should be enabled for all extractors that support it, including when the content
   *     duration is unknown.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setConstantBitrateSeekingAlwaysEnabled(
      boolean constantBitrateSeekingAlwaysEnabled) {
    this.constantBitrateSeekingAlwaysEnabled = constantBitrateSeekingAlwaysEnabled;
    return this;
  }

  /**
   * Sets flags for {@link AdtsExtractor} instances created by the factory.
   *
   * @see AdtsExtractor#AdtsExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setAdtsExtractorFlags(
      @AdtsExtractor.Flags int flags) {
    this.adtsFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link AmrExtractor} instances created by the factory.
   *
   * @see AmrExtractor#AmrExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setAmrExtractorFlags(@AmrExtractor.Flags int flags) {
    this.amrFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link FlacExtractor} instances created by the factory. The flags are also used
   * by {@code androidx.media3.decoder.flac.FlacExtractor} instances if the FLAC extension is being
   * used.
   *
   * @see FlacExtractor#FlacExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setFlacExtractorFlags(
      @FlacExtractor.Flags int flags) {
    this.flacFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link MatroskaExtractor} instances created by the factory.
   *
   * @see MatroskaExtractor#MatroskaExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(
      @MatroskaExtractor.Flags int flags) {
    this.matroskaFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link Mp4Extractor} instances created by the factory.
   *
   * @see Mp4Extractor#Mp4Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMp4ExtractorFlags(@Mp4Extractor.Flags int flags) {
    this.mp4Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link FragmentedMp4Extractor} instances created by the factory.
   *
   * @see FragmentedMp4Extractor#FragmentedMp4Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setFragmentedMp4ExtractorFlags(
      @FragmentedMp4Extractor.Flags int flags) {
    this.fragmentedMp4Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link Mp3Extractor} instances created by the factory.
   *
   * @see Mp3Extractor#Mp3Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMp3ExtractorFlags(@Mp3Extractor.Flags int flags) {
    mp3Flags = flags;
    return this;
  }

  /**
   * Sets the mode for {@link TsExtractor} instances created by the factory.
   *
   * @see TsExtractor#TsExtractor(int, TimestampAdjuster, TsPayloadReader.Factory, int)
   * @param mode The mode to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setTsExtractorMode(@TsExtractor.Mode int mode) {
    tsMode = mode;
    return this;
  }

  /**
   * Sets flags for {@link DefaultTsPayloadReaderFactory}s used by {@link TsExtractor} instances
   * created by the factory.
   *
   * @see TsExtractor#TsExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setTsExtractorFlags(
      @DefaultTsPayloadReaderFactory.Flags int flags) {
    tsFlags = flags;
    return this;
  }

  /**
   * Sets a list of subtitle formats to pass to the {@link DefaultTsPayloadReaderFactory} used by
   * {@link TsExtractor} instances created by the factory.
   *
   * @see DefaultTsPayloadReaderFactory#DefaultTsPayloadReaderFactory(int, List)
   * @param subtitleFormats The subtitle formats.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setTsSubtitleFormats(List<Format> subtitleFormats) {
    tsSubtitleFormats = ImmutableList.copyOf(subtitleFormats);
    return this;
  }

  /**
   * Sets the number of bytes searched to find a timestamp for {@link TsExtractor} instances created
   * by the factory.
   *
   * @see TsExtractor#TsExtractor(int, TimestampAdjuster, TsPayloadReader.Factory, int)
   * @param timestampSearchBytes The number of search bytes to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setTsExtractorTimestampSearchBytes(
      int timestampSearchBytes) {
    tsTimestampSearchBytes = timestampSearchBytes;
    return this;
  }

  /**
   * Enables transcoding of text track samples to {@link MimeTypes#APPLICATION_MEDIA3_CUES} before
   * the data is emitted to {@link TrackOutput}.
   *
   * <p>Transcoding is disabled by default.
   *
   * @param textTrackTranscodingEnabled Whether to enable transcoding.
   * @return The factory, for convenience.
   */
  // TODO: b/289916598 - Flip this to default to enabled and deprecate it.
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setTextTrackTranscodingEnabled(
      boolean textTrackTranscodingEnabled) {
    this.textTrackTranscodingEnabled = textTrackTranscodingEnabled;
    return this;
  }

  /**
   * Sets a {@link SubtitleParser.Factory} to use when transcoding text tracks.
   *
   * <p>This is only used if {@link #setTextTrackTranscodingEnabled(boolean)} is enabled.
   *
   * <p>The default value is {@link DefaultSubtitleParserFactory}.
   *
   * @param subtitleParserFactory The factory for {@link SubtitleParser} instances.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setSubtitleParserFactory(
      SubtitleParser.Factory subtitleParserFactory) {
    this.subtitleParserFactory = subtitleParserFactory;
    return this;
  }

  /**
   * Sets flags for {@link JpegExtractor} instances created by the factory.
   *
   * @see JpegExtractor#JpegExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setJpegExtractorFlags(
      @JpegExtractor.Flags int flags) {
    this.jpegFlags = flags;
    return this;
  }

  @Override
  public synchronized Extractor[] createExtractors() {
    return createExtractors(Uri.EMPTY, new HashMap<>());
  }

  @Override
  public synchronized Extractor[] createExtractors(
      Uri uri, Map<String, List<String>> responseHeaders) {
    List<Extractor> extractors =
        new ArrayList<>(/* initialCapacity= */ DEFAULT_EXTRACTOR_ORDER.length);

    @FileTypes.Type
    int responseHeadersInferredFileType = inferFileTypeFromResponseHeaders(responseHeaders);
    if (responseHeadersInferredFileType != FileTypes.UNKNOWN) {
      addExtractorsForFileType(responseHeadersInferredFileType, extractors);
    }

    @FileTypes.Type int uriInferredFileType = inferFileTypeFromUri(uri);
    if (uriInferredFileType != FileTypes.UNKNOWN
        && uriInferredFileType != responseHeadersInferredFileType) {
      addExtractorsForFileType(uriInferredFileType, extractors);
    }

    for (int fileType : DEFAULT_EXTRACTOR_ORDER) {
      if (fileType != responseHeadersInferredFileType && fileType != uriInferredFileType) {
        addExtractorsForFileType(fileType, extractors);
      }
    }
    Extractor[] result = new Extractor[extractors.size()];
    for (int i = 0; i < extractors.size(); i++) {
      result[i] =
          textTrackTranscodingEnabled
              ? new SubtitleTranscodingExtractor(extractors.get(i), subtitleParserFactory)
              : extractors.get(i);
    }
    return result;
  }

  private void addExtractorsForFileType(@FileTypes.Type int fileType, List<Extractor> extractors) {
    switch (fileType) {
      case FileTypes.AC3:
        extractors.add(new Ac3Extractor());
        break;
      case FileTypes.AC4:
        extractors.add(new Ac4Extractor());
        break;
      case FileTypes.ADTS:
        extractors.add(
            new AdtsExtractor(
                adtsFlags
                    | (constantBitrateSeekingEnabled
                        ? AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                        : 0)
                    | (constantBitrateSeekingAlwaysEnabled
                        ? AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                        : 0)));
        break;
      case FileTypes.AMR:
        extractors.add(
            new AmrExtractor(
                amrFlags
                    | (constantBitrateSeekingEnabled
                        ? AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                        : 0)
                    | (constantBitrateSeekingAlwaysEnabled
                        ? AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                        : 0)));
        break;
      case FileTypes.FLAC:
        @Nullable Extractor flacExtractor = FLAC_EXTENSION_LOADER.getExtractor(flacFlags);
        if (flacExtractor != null) {
          extractors.add(flacExtractor);
        } else {
          extractors.add(new FlacExtractor(flacFlags));
        }
        break;
      case FileTypes.FLV:
        extractors.add(new FlvExtractor());
        break;
      case FileTypes.MATROSKA:
        extractors.add(new MatroskaExtractor(matroskaFlags));
        break;
      case FileTypes.MP3:
        extractors.add(
            new Mp3Extractor(
                mp3Flags
                    | (constantBitrateSeekingEnabled
                        ? Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                        : 0)
                    | (constantBitrateSeekingAlwaysEnabled
                        ? Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                        : 0)));
        break;
      case FileTypes.MP4:
        extractors.add(new FragmentedMp4Extractor(fragmentedMp4Flags));
        extractors.add(new Mp4Extractor(mp4Flags));
        break;
      case FileTypes.OGG:
        extractors.add(new OggExtractor());
        break;
      case FileTypes.PS:
        extractors.add(new PsExtractor());
        break;
      case FileTypes.TS:
        if (tsSubtitleFormats == null) {
          tsSubtitleFormats = ImmutableList.of();
        }
        extractors.add(
            new TsExtractor(
                tsMode,
                new TimestampAdjuster(0),
                new DefaultTsPayloadReaderFactory(tsFlags, tsSubtitleFormats),
                tsTimestampSearchBytes));
        break;
      case FileTypes.WAV:
        extractors.add(new WavExtractor());
        break;
      case FileTypes.JPEG:
        extractors.add(new JpegExtractor(jpegFlags));
        break;
      case FileTypes.MIDI:
        @Nullable Extractor midiExtractor = MIDI_EXTENSION_LOADER.getExtractor();
        if (midiExtractor != null) {
          extractors.add(midiExtractor);
        }
        break;
      case FileTypes.AVI:
        extractors.add(new AviExtractor());
        break;
      case FileTypes.PNG:
        extractors.add(new PngExtractor());
        break;
      case FileTypes.WEBP:
        extractors.add(new WebpExtractor());
        break;
      case FileTypes.BMP:
        extractors.add(new BmpExtractor());
        break;
      case FileTypes.HEIF:
        if ((mp4Flags & FLAG_READ_MOTION_PHOTO_METADATA) == 0
            && (mp4Flags & FLAG_READ_SEF_DATA) == 0) {
          extractors.add(new HeifExtractor());
        }
        break;
      case FileTypes.WEBVTT:
      case FileTypes.UNKNOWN:
      default:
        break;
    }
  }

  private static Constructor<? extends Extractor> getMidiExtractorConstructor()
      throws ClassNotFoundException, NoSuchMethodException {
    return Class.forName("androidx.media3.decoder.midi.MidiExtractor")
        .asSubclass(Extractor.class)
        .getConstructor();
  }

  @Nullable
  private static Constructor<? extends Extractor> getFlacExtractorConstructor()
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException {
    @SuppressWarnings("nullness:argument")
    boolean isFlacNativeLibraryAvailable =
        Boolean.TRUE.equals(
            Class.forName("androidx.media3.decoder.flac.FlacLibrary")
                .getMethod("isAvailable")
                .invoke(/* obj= */ null));
    if (isFlacNativeLibraryAvailable) {
      return Class.forName("androidx.media3.decoder.flac.FlacExtractor")
          .asSubclass(Extractor.class)
          .getConstructor(int.class);
    }
    return null;
  }

  private static final class ExtensionLoader {

    public interface ConstructorSupplier {
      @Nullable
      Constructor<? extends Extractor> getConstructor()
          throws InvocationTargetException,
              IllegalAccessException,
              NoSuchMethodException,
              ClassNotFoundException;
    }

    private final ConstructorSupplier constructorSupplier;
    private final AtomicBoolean extensionLoaded;

    @GuardedBy("extensionLoaded")
    @Nullable
    private Constructor<? extends Extractor> extractorConstructor;

    public ExtensionLoader(ConstructorSupplier constructorSupplier) {
      this.constructorSupplier = constructorSupplier;
      extensionLoaded = new AtomicBoolean(false);
    }

    @Nullable
    public Extractor getExtractor(Object... constructorParams) {
      @Nullable
      Constructor<? extends Extractor> extractorConstructor = maybeLoadExtractorConstructor();
      if (extractorConstructor == null) {
        return null;
      }
      try {
        return extractorConstructor.newInstance(constructorParams);
      } catch (Exception e) {
        throw new IllegalStateException("Unexpected error creating extractor", e);
      }
    }

    @Nullable
    private Constructor<? extends Extractor> maybeLoadExtractorConstructor() {
      synchronized (extensionLoaded) {
        if (extensionLoaded.get()) {
          return extractorConstructor;
        }
        try {
          return constructorSupplier.getConstructor();
        } catch (ClassNotFoundException e) {
          // Expected if the app was built without the extension.
        } catch (Exception e) {
          // The extension is present, but instantiation failed.
          throw new RuntimeException("Error instantiating extension", e);
        }
        extensionLoaded.set(true);
        return extractorConstructor;
      }
    }
  }
}

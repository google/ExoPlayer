package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES;
import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_HANDLED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.TrackSelector.InvalidationListener;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link DefaultTrackSelector}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class DefaultTrackSelectorTest {

  private static final Parameters DEFAULT_PARAMETERS = new Parameters();
  private static final RendererCapabilities ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO, FORMAT_EXCEEDS_CAPABILITIES);

  @Mock
  private InvalidationListener invalidationListener;

  private DefaultTrackSelector trackSelector;

  @Before
  public void setUp() {
    initMocks(this);
    trackSelector = new DefaultTrackSelector();
  }

  /**
   * Tests that track selector will not call
   * {@link InvalidationListener#onTrackSelectionsInvalidated()} when it's set with default
   * values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithDefaultParametersDoesNotNotifyInvalidationListener()
      throws Exception {
    trackSelector.init(invalidationListener);
    trackSelector.setParameters(DEFAULT_PARAMETERS);

    verify(invalidationListener, never()).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will call {@link InvalidationListener#onTrackSelectionsInvalidated()}
   * when it's set with non-default values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithNonDefaultParameterNotifyInvalidationListener()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.init(invalidationListener);
    trackSelector.setParameters(parameters);

    verify(invalidationListener).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will not call
   * {@link InvalidationListener#onTrackSelectionsInvalidated()} again when it's set with
   * the same values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithSameParametersDoesNotNotifyInvalidationListenerAgain()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.init(invalidationListener);
    trackSelector.setParameters(parameters);
    trackSelector.setParameters(parameters);

    verify(invalidationListener, times(1)).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will select audio track with {@link C#SELECTION_FLAG_DEFAULT}
   * given default values of {@link Parameters}.
   */
  @Test
  public void testSelectTracksSelectTrackWithSelectionFlag() throws Exception {
    Format audioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format formatWithSelectionFlag =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, C.SELECTION_FLAG_DEFAULT, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(formatWithSelectionFlag, audioFormat));
    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(formatWithSelectionFlag);
  }

  /**
   * Tests that track selector will select audio track with language that match preferred language
   * given by {@link Parameters}.
   */
  @Test
  public void testSelectTracksSelectPreferredAudioLanguage()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.setParameters(parameters);

    Format frAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "fr");
    Format enAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "en");

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(frAudioFormat, enAudioFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(enAudioFormat);
  }

  /**
   * Tests that track selector will prefer selecting audio track with language that match preferred
   * language given by {@link Parameters} over track with {@link C#SELECTION_FLAG_DEFAULT}.
   */
  @Test
  public void testSelectTracksSelectPreferredAudioLanguageOverSelectionFlag()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.setParameters(parameters);

    Format frAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, C.SELECTION_FLAG_DEFAULT, "fr");
    Format enAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "en");

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(frAudioFormat, enAudioFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(enAudioFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilities()
      throws Exception {
    Format supportedFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format exceededFormat =
        Format.createAudioSampleFormat("exceededFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(supportedFormat.id, FORMAT_HANDLED);
    mappedCapabilities.put(exceededFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {mappedAudioRendererCapabilities},
        singleTrackGroup(exceededFormat, supportedFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(supportedFormat);
  }

  /**
   * Tests that track selector will select a track that exceeds the renderer's capabilities when
   * there are no other choice, given the default {@link Parameters}.
   */
  @Test
  public void testSelectTracksWithNoTrackWithinCapabilitiesSelectExceededCapabilityTrack()
      throws Exception {

    Format audioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(audioFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(audioFormat);
  }

  /**
   * Tests that track selector will return a null track selection for a renderer when
   * all tracks exceed that renderer's capabilities when {@link Parameters} does not allow
   * exceeding-capabilities tracks.
   */
  @Test
  public void testSelectTracksWithNoTrackWithinCapabilitiesAndSetByParamsReturnNoSelection()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withExceedRendererCapabilitiesIfNecessary(false);
    trackSelector.setParameters(parameters);

    Format audioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(audioFormat));

    assertThat(result.selections.get(0)).isNull();
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * tracks that have {@link C#SELECTION_FLAG_DEFAULT} but exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverSelectionFlag()
      throws Exception {
    Format supportedFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format exceededWithSelectionFlagFormat =
        Format.createAudioSampleFormat("exceededFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, C.SELECTION_FLAG_DEFAULT, null);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(supportedFormat.id, FORMAT_HANDLED);
    mappedCapabilities.put(exceededWithSelectionFlagFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {mappedAudioRendererCapabilities},
        singleTrackGroup(exceededWithSelectionFlagFormat, supportedFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(supportedFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that have language matching preferred audio given by {@link Parameters} but exceed
   * renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverPreferredLanguage()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.setParameters(parameters);

    Format supportedFrFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, "fr");
    Format exceededEnFormat =
        Format.createAudioSampleFormat("exceededFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, "en");

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(exceededEnFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    mappedCapabilities.put(supportedFrFormat.id, FORMAT_HANDLED);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {mappedAudioRendererCapabilities},
        singleTrackGroup(exceededEnFormat, supportedFrFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(supportedFrFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that have both language matching preferred audio given by {@link Parameters} and
   * {@link C#SELECTION_FLAG_DEFAULT}, but exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverSelectionFlagAndPreferredLanguage()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withPreferredAudioLanguage("en");
    trackSelector.setParameters(parameters);

    Format supportedFrFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, "fr");
    Format exceededDefaultSelectionEnFormat =
        Format.createAudioSampleFormat("exceededFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, C.SELECTION_FLAG_DEFAULT, "en");

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(exceededDefaultSelectionEnFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    mappedCapabilities.put(supportedFrFormat.id, FORMAT_HANDLED);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {mappedAudioRendererCapabilities},
        singleTrackGroup(exceededDefaultSelectionEnFormat, supportedFrFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(supportedFrFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher num channel when other factors
   * are the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherNumChannel()
      throws Exception {
    Format lowerChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 6, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherChannelFormat, lowerChannelFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(higherChannelFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher sample rate when other factors
   * are the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherSampleRate()
      throws Exception {
    Format higherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherSampleRateFormat, lowerSampleRateFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(higherSampleRateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher bit-rate when other factors
   * are the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherBitrate()
      throws Exception {
    Format lowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(lowerBitrateFormat, higherBitrateFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(higherBitrateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with higher channel count over tracks with
   * higher sample rate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksPreferHigherNumChannelBeforeSampleRate()
      throws Exception {
    Format lowerChannelHigherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherChannelLowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 6, 22050, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherChannelLowerSampleRateFormat, lowerChannelHigherSampleRateFormat));

    assertThat(result.selections.get(0).getSelectedFormat())
        .isEqualTo(higherChannelLowerSampleRateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with higher sample rate over tracks with
   * higher bitrate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksPreferHigherSampleRateBeforeBitrate()
      throws Exception {
    Format higherSampleRateLowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateHigherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherSampleRateLowerBitrateFormat, lowerSampleRateHigherBitrateFormat));

    assertThat(result.selections.get(0).getSelectedFormat())
        .isEqualTo(higherSampleRateLowerBitrateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower num channel when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerNumChannel()
      throws Exception {
    Format lowerChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 6, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherChannelFormat, lowerChannelFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(lowerChannelFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower sample rate when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerSampleRate()
      throws Exception {
    Format lowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);
    Format higherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherSampleRateFormat, lowerSampleRateFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(lowerSampleRateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower bit-rate when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerBitrate()
      throws Exception {
    Format lowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(lowerBitrateFormat, higherBitrateFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(lowerBitrateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with lower channel count over tracks with
   * lower sample rate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesPreferLowerNumChannelBeforeSampleRate()
      throws Exception {
    Format lowerChannelHigherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherChannelLowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 6, 22050, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherChannelLowerSampleRateFormat, lowerChannelHigherSampleRateFormat));

    assertThat(result.selections.get(0).getSelectedFormat())
        .isEqualTo(lowerChannelHigherSampleRateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with lower sample rate over tracks with
   * lower bitrate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesPreferLowerSampleRateBeforeBitrate()
      throws Exception {
    Format higherSampleRateLowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateHigherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
        singleTrackGroup(higherSampleRateLowerBitrateFormat, lowerSampleRateHigherBitrateFormat));

    assertThat(result.selections.get(0).getSelectedFormat())
        .isEqualTo(lowerSampleRateHigherBitrateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower bitrate when {@link Parameters}
   * indicate lowest bitrate preference, even when tracks are within capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesAndForceLowestBitrateSelectLowerBitrate()
      throws Exception {
    Parameters parameters = DEFAULT_PARAMETERS.withForceLowestBitrate(true);
    trackSelector.setParameters(parameters);

    Format lowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);

    TrackSelectorResult result = trackSelector.selectTracks(
        new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
        singleTrackGroup(lowerBitrateFormat, higherBitrateFormat));

    assertThat(result.selections.get(0).getSelectedFormat()).isEqualTo(lowerBitrateFormat);
  }

  private static TrackGroupArray singleTrackGroup(Format... formats) {
    return new TrackGroupArray(new TrackGroup(formats));
  }

  /**
   * A {@link RendererCapabilities} that advertises support for all formats of a given type using
   * a provided support value. For any format that does not have the given track type,
   * {@link #supportsFormat(Format)} will return {@link #FORMAT_UNSUPPORTED_TYPE}.
   */
  private static final class FakeRendererCapabilities implements RendererCapabilities {

    private final int trackType;
    private final int supportValue;

    /**
     * Returns {@link FakeRendererCapabilities} that advertises adaptive support for all
     * tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     * support for.
     */
    FakeRendererCapabilities(int trackType) {
      this(trackType, FORMAT_HANDLED | ADAPTIVE_SEAMLESS);
    }

    /**
     * Returns {@link FakeRendererCapabilities} that advertises support level using given value
     * for all tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     * support for.
     * @param supportValue the support level value that will be returned for formats with
     * the given type.
     */
    FakeRendererCapabilities(int trackType, int supportValue) {
      this.trackType = trackType;
      this.supportValue = supportValue;
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? (supportValue) : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

  /**
   * A {@link RendererCapabilities} that advertises support for different formats using a mapping
   * between format ID and format-support value.
   */
  private static final class FakeMappedRendererCapabilities implements RendererCapabilities {

    private final int trackType;
    private final Map<String, Integer> formatToCapability;

    /**
     * Returns {@link FakeRendererCapabilities} that advertises support level using the given
     * mapping between format ID and format-support value.
     *
     * @param trackType the track type to be returned for {@link #getTrackType()}
     * @param formatToCapability a map of (format id, support level) that will be used to return
     * support level for any given format. For any format that's not in the map,
     * {@link #supportsFormat(Format)} will return {@link #FORMAT_UNSUPPORTED_TYPE}.
     */
    FakeMappedRendererCapabilities(int trackType, Map<String, Integer> formatToCapability) {
      this.trackType = trackType;
      this.formatToCapability = new HashMap<>(formatToCapability);
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return format.id != null && formatToCapability.containsKey(format.id)
          ? formatToCapability.get(format.id)
          : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

}

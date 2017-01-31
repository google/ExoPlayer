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

package com.google.android.exoplayer2.drm;

import android.media.MediaDrm;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager.EventListener;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Mode;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.HashMap;

/**
 * Helper class to download, renew and release offline licenses. It utilizes {@link
 * DefaultDrmSessionManager}.
 */
public final class OfflineLicenseHelper<T extends ExoMediaCrypto> {

  private final ConditionVariable conditionVariable;
  private final DefaultDrmSessionManager<T> drmSessionManager;
  private final HandlerThread handlerThread;

  /**
   * Helper method to download a DASH manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which the manifest should be read.
   * @param manifestUriString The URI of the manifest to be read.
   * @return An instance of {@link DashManifest}.
   * @throws IOException If an error occurs reading data from the stream.
   * @see DashManifestParser
   */
  public static DashManifest downloadManifest(HttpDataSource dataSource, String manifestUriString)
      throws IOException {
    DataSourceInputStream inputStream = new DataSourceInputStream(
        dataSource, new DataSpec(Uri.parse(manifestUriString)));
    try {
      inputStream.open();
      DashManifestParser parser = new DashManifestParser();
      return parser.parse(dataSource.getUri(), inputStream);
    } finally {
      inputStream.close();
    }
  }

  /**
   * Instantiates a new instance which uses Widevine CDM. Call {@link #releaseResources()} when
   * you're done with the helper instance.
   *
   * @param licenseUrl The default license URL.
   * @param httpDataSourceFactory A factory from which to obtain {@link HttpDataSource} instances.
   * @return A new instance which uses Widevine CDM.
   * @throws UnsupportedDrmException If the Widevine DRM scheme is unsupported or cannot be
   *     instantiated.
   */
  public static OfflineLicenseHelper<FrameworkMediaCrypto> newWidevineInstance(
      String licenseUrl, Factory httpDataSourceFactory) throws UnsupportedDrmException {
    return newWidevineInstance(
        new HttpMediaDrmCallback(licenseUrl, httpDataSourceFactory, null), null);
  }

  /**
   * Instantiates a new instance which uses Widevine CDM. Call {@link #releaseResources()} when
   * you're done with the helper instance.
   *
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @return A new instance which uses Widevine CDM.
   * @throws UnsupportedDrmException If the Widevine DRM scheme is unsupported or cannot be
   *     instantiated.
   * @see DefaultDrmSessionManager#DefaultDrmSessionManager(java.util.UUID, ExoMediaDrm,
   *     MediaDrmCallback, HashMap, Handler, EventListener)
   */
  public static OfflineLicenseHelper<FrameworkMediaCrypto> newWidevineInstance(
      MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters)
      throws UnsupportedDrmException {
    return new OfflineLicenseHelper<>(FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID), callback,
        optionalKeyRequestParameters);
  }

  /**
   * Constructs an instance. Call {@link #releaseResources()} when you're done with it.
   *
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @see DefaultDrmSessionManager#DefaultDrmSessionManager(java.util.UUID, ExoMediaDrm,
   *     MediaDrmCallback, HashMap, Handler, EventListener)
   */
  public OfflineLicenseHelper(ExoMediaDrm<T> mediaDrm, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters) {
    handlerThread = new HandlerThread("OfflineLicenseHelper");
    handlerThread.start();

    conditionVariable = new ConditionVariable();
    EventListener eventListener = new EventListener() {
      @Override
      public void onDrmKeysLoaded() {
        conditionVariable.open();
      }

      @Override
      public void onDrmSessionManagerError(Exception e) {
        conditionVariable.open();
      }

      @Override
      public void onDrmKeysRestored() {
        conditionVariable.open();
      }

      @Override
      public void onDrmKeysRemoved() {
        conditionVariable.open();
      }
    };
    drmSessionManager = new DefaultDrmSessionManager<>(C.WIDEVINE_UUID, mediaDrm, callback,
        optionalKeyRequestParameters, new Handler(handlerThread.getLooper()), eventListener);
  }

  /** Releases the used resources. */
  public void releaseResources() {
    handlerThread.quit();
  }

  /**
   * Downloads an offline license.
   *
   * @param dataSource The {@link HttpDataSource} to be used for download.
   * @param manifestUriString The URI of the manifest to be read.
   * @return The downloaded offline license key set id.
   * @throws IOException If an error occurs reading data from the stream.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws DrmSessionException Thrown when there is an error during DRM session.
   */
  public byte[] download(HttpDataSource dataSource, String manifestUriString)
      throws IOException, InterruptedException, DrmSessionException {
    return download(dataSource, downloadManifest(dataSource, manifestUriString));
  }

  /**
   * Downloads an offline license.
   *
   * @param dataSource The {@link HttpDataSource} to be used for download.
   * @param dashManifest The {@link DashManifest} of the DASH content.
   * @return The downloaded offline license key set id.
   * @throws IOException If an error occurs reading data from the stream.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws DrmSessionException Thrown when there is an error during DRM session.
   */
  public byte[] download(HttpDataSource dataSource, DashManifest dashManifest)
      throws IOException, InterruptedException, DrmSessionException {
    // Get DrmInitData
    // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
    // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
    if (dashManifest.getPeriodCount() < 1) {
      return null;
    }
    Period period = dashManifest.getPeriod(0);
    int adaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
    if (adaptationSetIndex == C.INDEX_UNSET) {
      adaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_AUDIO);
      if (adaptationSetIndex == C.INDEX_UNSET) {
        return null;
      }
    }
    AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
    if (adaptationSet.representations.isEmpty()) {
      return null;
    }
    Representation representation = adaptationSet.representations.get(0);
    DrmInitData drmInitData = representation.format.drmInitData;
    if (drmInitData == null) {
      InitializationChunk initializationChunk = loadInitializationChunk(dataSource, representation);
      if (initializationChunk == null) {
        return null;
      }
      Format sampleFormat = initializationChunk.getSampleFormat();
      if (sampleFormat != null) {
        drmInitData = sampleFormat.drmInitData;
      }
      if (drmInitData == null) {
        return null;
      }
    }
    blockingKeyRequest(DefaultDrmSessionManager.MODE_DOWNLOAD, null, drmInitData);
    return drmSessionManager.getOfflineLicenseKeySetId();
  }

  /**
   * Renews an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be renewed.
   * @return Renewed offline license key set id.
   * @throws DrmSessionException Thrown when there is an error during DRM session.
   */
  public byte[] renew(byte[] offlineLicenseKeySetId) throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    blockingKeyRequest(DefaultDrmSessionManager.MODE_DOWNLOAD, offlineLicenseKeySetId, null);
    return drmSessionManager.getOfflineLicenseKeySetId();
  }

  /**
   * Releases an offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license to be released.
   * @throws DrmSessionException Thrown when there is an error during DRM session.
   */
  public void release(byte[] offlineLicenseKeySetId) throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    blockingKeyRequest(DefaultDrmSessionManager.MODE_RELEASE, offlineLicenseKeySetId, null);
  }

  /**
   * Returns license and playback durations remaining in seconds of the given offline license.
   *
   * @param offlineLicenseKeySetId The key set id of the license.
   */
  public Pair<Long, Long> getLicenseDurationRemainingSec(byte[] offlineLicenseKeySetId)
      throws DrmSessionException {
    Assertions.checkNotNull(offlineLicenseKeySetId);
    DrmSession<T> session = openBlockingKeyRequest(DefaultDrmSessionManager.MODE_QUERY,
        offlineLicenseKeySetId, null);
    Pair<Long, Long> licenseDurationRemainingSec =
        WidevineUtil.getLicenseDurationRemainingSec(drmSessionManager);
    drmSessionManager.releaseSession(session);
    return licenseDurationRemainingSec;
  }

  private void blockingKeyRequest(@Mode int licenseMode, byte[] offlineLicenseKeySetId,
      DrmInitData drmInitData) throws DrmSessionException {
    DrmSession<T> session = openBlockingKeyRequest(licenseMode, offlineLicenseKeySetId,
        drmInitData);
    DrmSessionException error = session.getError();
    if (error != null) {
      throw error;
    }
    drmSessionManager.releaseSession(session);
  }

  private DrmSession<T> openBlockingKeyRequest(@Mode int licenseMode, byte[] offlineLicenseKeySetId,
      DrmInitData drmInitData) {
    drmSessionManager.setMode(licenseMode, offlineLicenseKeySetId);
    conditionVariable.close();
    DrmSession<T> session = drmSessionManager.acquireSession(handlerThread.getLooper(),
        drmInitData);
    // Block current thread until key loading is finished
    conditionVariable.block();
    return session;
  }

  private static InitializationChunk loadInitializationChunk(final DataSource dataSource,
      final Representation representation) throws IOException, InterruptedException {
    RangedUri rangedUri = representation.getInitializationUri();
    if (rangedUri == null) {
      return null;
    }
    DataSpec dataSpec = new DataSpec(rangedUri.resolveUri(representation.baseUrl), rangedUri.start,
        rangedUri.length, representation.getCacheKey());
    InitializationChunk initializationChunk = new InitializationChunk(dataSource, dataSpec,
        representation.format, C.SELECTION_REASON_UNKNOWN, null /* trackSelectionData */,
        newWrappedExtractor(representation.format));
    initializationChunk.load();
    return initializationChunk;
  }

  private static ChunkExtractorWrapper newWrappedExtractor(final Format format) {
    final String mimeType = format.containerMimeType;
    final boolean isWebm = mimeType.startsWith(MimeTypes.VIDEO_WEBM)
        || mimeType.startsWith(MimeTypes.AUDIO_WEBM);
    final Extractor extractor = isWebm ? new MatroskaExtractor() : new FragmentedMp4Extractor();
    return new ChunkExtractorWrapper(extractor, format, false /* preferManifestDrmInitData */,
        false /* resendFormatOnInit */);
  }

}

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

package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.drm.ExoMediaDrm;
import androidx.media3.exoplayer.drm.MediaDrmCallback;
import androidx.media3.exoplayer.drm.MediaDrmCallbackException;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake implementation of {@link ExoMediaDrm} for use in tests.
 *
 * <p>{@link LicenseServer} can be used to respond to interactions stemming from {@link
 * #getKeyRequest(byte[], List, int, HashMap)} and {@link #provideKeyResponse(byte[], byte[])}.
 *
 * <p>Currently only supports streaming key requests.
 */
// TODO: Consider replacing this with a Robolectric ShadowMediaDrm so we can use a real
//  FrameworkMediaDrm.
@RequiresApi(29)
@UnstableApi
public final class FakeExoMediaDrm implements ExoMediaDrm {

  /** Builder for {@link FakeExoMediaDrm} instances. */
  public static class Builder {
    private boolean enforceValidKeyResponses;
    private int provisionsRequired;
    private boolean throwNotProvisionedExceptionFromGetKeyRequest;
    private int maxConcurrentSessions;
    private boolean throwNoSuchMethodErrorForProvisioningAndResourceBusy;

    /** Constructs an instance. */
    public Builder() {
      enforceValidKeyResponses = true;
      provisionsRequired = 0;
      maxConcurrentSessions = Integer.MAX_VALUE;
    }

    /**
     * Sets whether key responses passed to {@link #provideKeyResponse(byte[], byte[])} should be
     * checked for validity (i.e. that they came from a {@link LicenseServer}).
     *
     * <p>Defaults to true.
     */
    @CanIgnoreReturnValue
    public Builder setEnforceValidKeyResponses(boolean enforceValidKeyResponses) {
      this.enforceValidKeyResponses = enforceValidKeyResponses;
      return this;
    }

    /**
     * Sets how many successful provisioning round trips are needed for the {@link FakeExoMediaDrm}
     * to be provisioned.
     *
     * <p>An unprovisioned {@link FakeExoMediaDrm} will throw {@link NotProvisionedException} from
     * methods that declare it until enough valid provisioning responses are passed to {@link
     * FakeExoMediaDrm#provideProvisionResponse(byte[])}.
     *
     * <p>Defaults to 0 (i.e. device is already provisioned).
     */
    @CanIgnoreReturnValue
    public Builder setProvisionsRequired(int provisionsRequired) {
      this.provisionsRequired = provisionsRequired;
      return this;
    }

    /**
     * Configures the {@link FakeExoMediaDrm} to throw any {@link NotProvisionedException} from
     * {@link #getKeyRequest(byte[], List, int, HashMap)} instead of the default behaviour of
     * throwing from {@link #openSession()}.
     */
    @CanIgnoreReturnValue
    public Builder throwNotProvisionedExceptionFromGetKeyRequest() {
      this.throwNotProvisionedExceptionFromGetKeyRequest = true;
      return this;
    }

    /**
     * Configures the {@link FakeExoMediaDrm} to throw {@link NoSuchMethodError} instead of {@link
     * NotProvisionedException} or {@link ResourceBusyException}.
     *
     * <p>This simulates a framework bug (b/291440132) introduced in API 34 and resolved by
     * http://r.android.com/2770659, allowing us to test workarounds for the bug.
     *
     * <p>The default is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder throwNoSuchMethodErrorForProvisioningAndResourceBusy(
        boolean throwNoSuchMethodErrorForProvisioningAndResourceBusy) {
      checkState(
          !throwNoSuchMethodErrorForProvisioningAndResourceBusy || Util.SDK_INT == 34,
          "The framework bug recreated by this method only exists on API 34.");
      this.throwNoSuchMethodErrorForProvisioningAndResourceBusy =
          throwNoSuchMethodErrorForProvisioningAndResourceBusy;
      return this;
    }

    /**
     * Sets the maximum number of concurrent sessions the {@link FakeExoMediaDrm} will support.
     *
     * <p>If this is exceeded then subsequent calls to {@link FakeExoMediaDrm#openSession()} will
     * throw {@link ResourceBusyException}.
     *
     * <p>Defaults to {@link Integer#MAX_VALUE}.
     */
    @CanIgnoreReturnValue
    public Builder setMaxConcurrentSessions(int maxConcurrentSessions) {
      this.maxConcurrentSessions = maxConcurrentSessions;
      return this;
    }

    /**
     * Returns a {@link FakeExoMediaDrm} instance with an initial reference count of 1. The caller
     * is responsible for calling {@link FakeExoMediaDrm#release()} when they no longer need the
     * instance.
     */
    public FakeExoMediaDrm build() {
      return new FakeExoMediaDrm(
          enforceValidKeyResponses,
          provisionsRequired,
          throwNotProvisionedExceptionFromGetKeyRequest,
          throwNoSuchMethodErrorForProvisioningAndResourceBusy,
          maxConcurrentSessions);
    }
  }

  public static final ProvisionRequest FAKE_PROVISION_REQUEST =
      new ProvisionRequest(TestUtil.createByteArray(7, 8, 9), "bar.test");
  public static final ImmutableList<Byte> VALID_PROVISION_RESPONSE =
      TestUtil.createByteList(4, 5, 6);

  /** Key for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_KEY = "KEY_STATUS";

  /** Value for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_AVAILABLE = "AVAILABLE";

  /** Value for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_UNAVAILABLE = "UNAVAILABLE";

  private static final ImmutableList<Byte> VALID_KEY_RESPONSE = TestUtil.createByteList(1, 2, 3);
  private static final ImmutableList<Byte> KEY_DENIED_RESPONSE = TestUtil.createByteList(9, 8, 7);
  private static final ImmutableList<Byte> PROVISIONING_REQUIRED_RESPONSE =
      TestUtil.createByteList(4, 5, 6);

  private final boolean enforceValidKeyResponses;
  private final int provisionsRequired;
  private final int maxConcurrentSessions;
  private final boolean throwNotProvisionedExceptionFromGetKeyRequest;
  private final boolean throwNoSuchMethodErrorForProvisioningAndResourceBusy;
  private final Map<String, byte[]> byteProperties;
  private final Map<String, String> stringProperties;
  private final Set<List<Byte>> openSessionIds;
  private final Set<List<Byte>> sessionIdsWithValidKeys;
  private final AtomicInteger sessionIdGenerator;

  private int provisionsReceived;
  private int referenceCount;
  @Nullable private OnEventListener onEventListener;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation") // Using deprecated constructor to reduce duplication.
  public FakeExoMediaDrm() {
    this(/* maxConcurrentSessions= */ Integer.MAX_VALUE);
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public FakeExoMediaDrm(int maxConcurrentSessions) {
    this(
        /* enforceValidKeyResponses= */ true,
        /* provisionsRequired= */ 0,
        /* throwNotProvisionedExceptionFromGetKeyRequest= */ false,
        /* throwNoSuchMethodErrorForProvisioningAndResourceBusy= */ false,
        maxConcurrentSessions);
  }

  private FakeExoMediaDrm(
      boolean enforceValidKeyResponses,
      int provisionsRequired,
      boolean throwNotProvisionedExceptionFromGetKeyRequest,
      boolean throwNoSuchMethodErrorForProvisioningAndResourceBusy,
      int maxConcurrentSessions) {
    this.enforceValidKeyResponses = enforceValidKeyResponses;
    this.provisionsRequired = provisionsRequired;
    this.maxConcurrentSessions = maxConcurrentSessions;
    this.throwNotProvisionedExceptionFromGetKeyRequest =
        throwNotProvisionedExceptionFromGetKeyRequest;
    this.throwNoSuchMethodErrorForProvisioningAndResourceBusy =
        throwNoSuchMethodErrorForProvisioningAndResourceBusy;
    byteProperties = new HashMap<>();
    stringProperties = new HashMap<>();
    openSessionIds = new HashSet<>();
    sessionIdsWithValidKeys = new HashSet<>();
    sessionIdGenerator = new AtomicInteger();

    referenceCount = 1;
  }

  // ExoMediaDrm implementation

  @Override
  public void setOnEventListener(@Nullable OnEventListener listener) {
    this.onEventListener = listener;
  }

  @Override
  public void setOnKeyStatusChangeListener(@Nullable OnKeyStatusChangeListener listener) {
    // Do nothing.
  }

  @Override
  public void setOnExpirationUpdateListener(@Nullable OnExpirationUpdateListener listener) {
    // Do nothing.
  }

  @Override
  public byte[] openSession() throws MediaDrmException {
    checkState(referenceCount > 0);
    if (!throwNotProvisionedExceptionFromGetKeyRequest && provisionsReceived < provisionsRequired) {
      throwNotProvisionedException();
    }
    if (openSessionIds.size() >= maxConcurrentSessions) {
      if (throwNoSuchMethodErrorForProvisioningAndResourceBusy) {
        throw new NoSuchMethodError(
            "no non-static method"
                + " \"Landroid/media/ResourceBusyException;.<init>(Ljava/lang/String;III)V\"");
      } else {
        throw new ResourceBusyException("Too many sessions open. max=" + maxConcurrentSessions);
      }
    }
    byte[] sessionId =
        TestUtil.buildTestData(/* length= */ 10, sessionIdGenerator.incrementAndGet());
    if (!openSessionIds.add(toByteList(sessionId))) {
      throw new MediaDrmException(
          Util.formatInvariant(
              "Generated sessionId[%s] clashes with already-open session",
              sessionIdGenerator.get()));
    }
    return sessionId;
  }

  @Override
  public void closeSession(byte[] sessionId) {
    checkState(referenceCount > 0);
    // TODO: Store closed session IDs too?
    checkState(openSessionIds.remove(toByteList(sessionId)));
  }

  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<DrmInitData.SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters)
      throws NotProvisionedException {
    checkState(referenceCount > 0);
    if (keyType == KEY_TYPE_OFFLINE || keyType == KEY_TYPE_RELEASE) {
      throw new UnsupportedOperationException("Offline key requests are not supported.");
    }
    checkArgument(keyType == KEY_TYPE_STREAMING, "Unrecognised keyType: " + keyType);
    if (throwNotProvisionedExceptionFromGetKeyRequest && provisionsReceived < provisionsRequired) {
      throwNotProvisionedException();
    }
    checkState(openSessionIds.contains(toByteList(scope)));
    checkNotNull(schemeDatas);
    KeyRequestData requestData =
        new KeyRequestData(
            schemeDatas,
            keyType,
            optionalParameters != null ? optionalParameters : ImmutableMap.of());
    @KeyRequest.RequestType
    int requestType =
        sessionIdsWithValidKeys.contains(toByteList(scope))
            ? KeyRequest.REQUEST_TYPE_RENEWAL
            : KeyRequest.REQUEST_TYPE_INITIAL;
    return new KeyRequest(requestData.toByteArray(), /* licenseServerUrl= */ "", requestType);
  }

  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    checkState(referenceCount > 0);
    List<Byte> responseAsList = Bytes.asList(response);
    if (responseAsList.equals(KEY_DENIED_RESPONSE)) {
      throw new DeniedByServerException("Key request denied");
    }
    if (responseAsList.equals(PROVISIONING_REQUIRED_RESPONSE)) {
      throwNotProvisionedException();
    }
    if (enforceValidKeyResponses && !responseAsList.equals(VALID_KEY_RESPONSE)) {
      throw new IllegalArgumentException(
          "Unrecognised response. scope="
              + Util.toHexString(scope)
              + ", response="
              + Util.toHexString(response));
    }
    sessionIdsWithValidKeys.add(Bytes.asList(scope));
    return Util.EMPTY_BYTE_ARRAY;
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    checkState(referenceCount > 0);
    return FAKE_PROVISION_REQUEST;
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    checkState(referenceCount > 0);
    if (Bytes.asList(response).equals(VALID_PROVISION_RESPONSE)) {
      provisionsReceived++;
    }
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    checkState(referenceCount > 0);
    checkState(openSessionIds.contains(toByteList(sessionId)));
    return ImmutableMap.of(
        KEY_STATUS_KEY,
        sessionIdsWithValidKeys.contains(toByteList(sessionId))
            ? KEY_STATUS_AVAILABLE
            : KEY_STATUS_UNAVAILABLE);
  }

  @Override
  public boolean requiresSecureDecoder(byte[] sessionId, String mimeType) {
    return false;
  }

  @Override
  public void acquire() {
    checkState(referenceCount > 0);
    referenceCount++;
  }

  @Override
  public void release() {
    referenceCount--;
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public PersistableBundle getMetrics() {
    checkState(referenceCount > 0);

    return null;
  }

  @Override
  public String getPropertyString(String propertyName) {
    checkState(referenceCount > 0);
    @Nullable String value = stringProperties.get(propertyName);
    if (value == null) {
      throw new IllegalArgumentException("Unrecognized propertyName: " + propertyName);
    }
    return value;
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    checkState(referenceCount > 0);
    @Nullable byte[] value = byteProperties.get(propertyName);
    if (value == null) {
      throw new IllegalArgumentException("Unrecognized propertyName: " + propertyName);
    }
    return value;
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    checkState(referenceCount > 0);
    stringProperties.put(propertyName, value);
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    checkState(referenceCount > 0);
    byteProperties.put(propertyName, value);
  }

  @Override
  public CryptoConfig createCryptoConfig(byte[] sessionId) throws MediaCryptoException {
    checkState(referenceCount > 0);
    checkState(openSessionIds.contains(toByteList(sessionId)));
    return new FakeCryptoConfig();
  }

  @Override
  public @C.CryptoType int getCryptoType() {
    return FakeCryptoConfig.TYPE;
  }

  // Methods to facilitate testing

  public int getReferenceCount() {
    return referenceCount;
  }

  /**
   * Calls {@link OnEventListener#onEvent(ExoMediaDrm, byte[], int, int, byte[])} on the attached
   * listener (if present) once for each open session ID which passes {@code sessionIdPredicate},
   * passing the provided values for {@code event}, {@code extra} and {@code data}.
   */
  public void triggerEvent(
      Predicate<byte[]> sessionIdPredicate, int event, int extra, @Nullable byte[] data) {
    @Nullable OnEventListener onEventListener = this.onEventListener;
    if (onEventListener == null) {
      return;
    }
    for (List<Byte> sessionId : openSessionIds) {
      byte[] sessionIdArray = Bytes.toArray(sessionId);
      if (sessionIdPredicate.apply(sessionIdArray)) {
        onEventListener.onEvent(this, sessionIdArray, event, extra, data);
      }
    }
  }

  /**
   * Resets the provisioning state of this instance, so it requires {@link
   * Builder#setProvisionsRequired(int) provisionsRequired} (possibly zero) provision operations
   * before it's operational again.
   */
  public void resetProvisioning() {
    provisionsReceived = 0;
  }

  private void throwNotProvisionedException() throws NotProvisionedException {
    if (throwNoSuchMethodErrorForProvisioningAndResourceBusy) {
      throw new NoSuchMethodError(
          "no non-static method"
              + " \"Landroid/media/NotProvisionedException;.<init>(Ljava/lang/String;III)V\"");
    } else {
      throw new NotProvisionedException("Not provisioned.");
    }
  }

  private static ImmutableList<Byte> toByteList(byte[] byteArray) {
    return ImmutableList.copyOf(Bytes.asList(byteArray));
  }

  /** An license server implementation to interact with {@link FakeExoMediaDrm}. */
  public static class LicenseServer implements MediaDrmCallback {

    private final ImmutableSet<ImmutableList<DrmInitData.SchemeData>> allowedSchemeDatas;

    private final List<ImmutableList<Byte>> receivedProvisionRequests;
    private final List<ImmutableList<DrmInitData.SchemeData>> receivedSchemeDatas;

    private boolean nextResponseIndicatesProvisioningRequired;

    @SafeVarargs
    public static LicenseServer allowingSchemeDatas(List<DrmInitData.SchemeData>... schemeDatas) {
      ImmutableSet.Builder<ImmutableList<DrmInitData.SchemeData>> schemeDatasBuilder =
          ImmutableSet.builder();
      for (List<DrmInitData.SchemeData> schemeData : schemeDatas) {
        schemeDatasBuilder.add(ImmutableList.copyOf(schemeData));
      }
      return new LicenseServer(schemeDatasBuilder.build());
    }

    @SafeVarargs
    public static LicenseServer requiringProvisioningThenAllowingSchemeDatas(
        List<DrmInitData.SchemeData>... schemeDatas) {
      ImmutableSet.Builder<ImmutableList<DrmInitData.SchemeData>> schemeDatasBuilder =
          ImmutableSet.builder();
      for (List<DrmInitData.SchemeData> schemeData : schemeDatas) {
        schemeDatasBuilder.add(ImmutableList.copyOf(schemeData));
      }
      LicenseServer licenseServer = new LicenseServer(schemeDatasBuilder.build());
      licenseServer.nextResponseIndicatesProvisioningRequired = true;
      return licenseServer;
    }

    private LicenseServer(ImmutableSet<ImmutableList<DrmInitData.SchemeData>> allowedSchemeDatas) {
      this.allowedSchemeDatas = allowedSchemeDatas;

      receivedProvisionRequests = new ArrayList<>();
      receivedSchemeDatas = new ArrayList<>();
    }

    public ImmutableList<ImmutableList<Byte>> getReceivedProvisionRequests() {
      return ImmutableList.copyOf(receivedProvisionRequests);
    }

    public ImmutableList<ImmutableList<DrmInitData.SchemeData>> getReceivedSchemeDatas() {
      return ImmutableList.copyOf(receivedSchemeDatas);
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request)
        throws MediaDrmCallbackException {
      receivedProvisionRequests.add(ImmutableList.copyOf(Bytes.asList(request.getData())));
      if (Arrays.equals(request.getData(), FAKE_PROVISION_REQUEST.getData())) {
        return Bytes.toArray(VALID_PROVISION_RESPONSE);
      } else {
        return Util.EMPTY_BYTE_ARRAY;
      }
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request)
        throws MediaDrmCallbackException {
      ImmutableList<DrmInitData.SchemeData> schemeDatas =
          KeyRequestData.fromByteArray(request.getData()).schemeDatas;
      receivedSchemeDatas.add(schemeDatas);

      ImmutableList<Byte> response;
      if (nextResponseIndicatesProvisioningRequired) {
        nextResponseIndicatesProvisioningRequired = false;
        response = PROVISIONING_REQUIRED_RESPONSE;
      } else if (allowedSchemeDatas.contains(schemeDatas)) {
        response = VALID_KEY_RESPONSE;
      } else {
        response = KEY_DENIED_RESPONSE;
      }
      return Bytes.toArray(response);
    }
  }

  /**
   * A structured set of key request fields that can be serialized into bytes by {@link
   * #getKeyRequest(byte[], List, int, HashMap)} and then deserialized by {@link
   * LicenseServer#executeKeyRequest(UUID, KeyRequest)}.
   */
  private static class KeyRequestData implements Parcelable {
    public final ImmutableList<DrmInitData.SchemeData> schemeDatas;
    public final int type;
    public final ImmutableMap<String, String> optionalParameters;

    public KeyRequestData(
        List<DrmInitData.SchemeData> schemeDatas,
        int type,
        Map<String, String> optionalParameters) {
      this.schemeDatas = ImmutableList.copyOf(schemeDatas);
      this.type = type;
      this.optionalParameters = ImmutableMap.copyOf(optionalParameters);
    }

    public KeyRequestData(Parcel in) {
      this.schemeDatas =
          ImmutableList.copyOf(
              in.readParcelableList(
                  new ArrayList<>(), DrmInitData.SchemeData.class.getClassLoader()));
      this.type = in.readInt();

      ImmutableMap.Builder<String, String> optionalParameters = new ImmutableMap.Builder<>();
      List<String> optionalParameterKeys = checkNotNull(in.createStringArrayList());
      List<String> optionalParameterValues = checkNotNull(in.createStringArrayList());
      checkArgument(optionalParameterKeys.size() == optionalParameterValues.size());
      for (int i = 0; i < optionalParameterKeys.size(); i++) {
        optionalParameters.put(optionalParameterKeys.get(i), optionalParameterValues.get(i));
      }

      this.optionalParameters = optionalParameters.buildOrThrow();
    }

    public byte[] toByteArray() {
      Parcel parcel = Parcel.obtain();
      try {
        writeToParcel(parcel, /* flags= */ 0);
        return parcel.marshall();
      } finally {
        parcel.recycle();
      }
    }

    public static KeyRequestData fromByteArray(byte[] bytes) {
      Parcel parcel = Parcel.obtain();
      try {
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        return CREATOR.createFromParcel(parcel);
      } finally {
        parcel.recycle();
      }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof KeyRequestData)) {
        return false;
      }

      KeyRequestData that = (KeyRequestData) obj;
      return Objects.equals(this.schemeDatas, that.schemeDatas)
          && this.type == that.type
          && Objects.equals(this.optionalParameters, that.optionalParameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(schemeDatas, type, optionalParameters);
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelableList(schemeDatas, flags);
      dest.writeInt(type);
      dest.writeStringList(optionalParameters.keySet().asList());
      dest.writeStringList(optionalParameters.values().asList());
    }

    public static final Parcelable.Creator<KeyRequestData> CREATOR =
        new Parcelable.Creator<KeyRequestData>() {

          @Override
          public KeyRequestData createFromParcel(Parcel in) {
            return new KeyRequestData(in);
          }

          @Override
          public KeyRequestData[] newArray(int size) {
            return new KeyRequestData[size];
          }
        };
  }
}

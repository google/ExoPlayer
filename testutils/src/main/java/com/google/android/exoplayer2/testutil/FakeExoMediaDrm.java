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

package com.google.android.exoplayer2.testutil;

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
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallbackException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;
import java.util.ArrayList;
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
public final class FakeExoMediaDrm implements ExoMediaDrm {

  public static final ProvisionRequest FAKE_PROVISION_REQUEST =
      new ProvisionRequest(TestUtil.createByteArray(7, 8, 9), "bar.test");

  /** Key for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_KEY = "KEY_STATUS";
  /** Value for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_AVAILABLE = "AVAILABLE";
  /** Value for use with the Map returned from {@link FakeExoMediaDrm#queryKeyStatus(byte[])}. */
  public static final String KEY_STATUS_UNAVAILABLE = "UNAVAILABLE";

  private static final ImmutableList<Byte> VALID_KEY_RESPONSE = TestUtil.createByteList(1, 2, 3);
  private static final ImmutableList<Byte> KEY_DENIED_RESPONSE = TestUtil.createByteList(9, 8, 7);

  private final int maxConcurrentSessions;
  private final Map<String, byte[]> byteProperties;
  private final Map<String, String> stringProperties;
  private final Set<List<Byte>> openSessionIds;
  private final Set<List<Byte>> sessionIdsWithValidKeys;
  private final AtomicInteger sessionIdGenerator;

  private int referenceCount;

  /**
   * Constructs an instance that returns random and unique {@code sessionIds} for subsequent calls
   * to {@link #openSession()} with no limit on the number of concurrent open sessions.
   */
  public FakeExoMediaDrm() {
    this(/* maxConcurrentSessions= */ Integer.MAX_VALUE);
  }

  /**
   * Constructs an instance that returns random and unique {@code sessionIds} for subsequent calls
   * to {@link #openSession()} with a limit on the number of concurrent open sessions.
   *
   * @param maxConcurrentSessions The max number of sessions allowed to be open simultaneously.
   */
  public FakeExoMediaDrm(int maxConcurrentSessions) {
    this.maxConcurrentSessions = maxConcurrentSessions;
    byteProperties = new HashMap<>();
    stringProperties = new HashMap<>();
    openSessionIds = new HashSet<>();
    sessionIdsWithValidKeys = new HashSet<>();
    sessionIdGenerator = new AtomicInteger();

    referenceCount = 1;
  }

  // ExoMediaCrypto implementation

  @Override
  public void setOnEventListener(@Nullable OnEventListener listener) {
    // Do nothing.
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
    Assertions.checkState(referenceCount > 0);
    if (openSessionIds.size() >= maxConcurrentSessions) {
      throw new ResourceBusyException("Too many sessions open. max=" + maxConcurrentSessions);
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
    Assertions.checkState(referenceCount > 0);
    // TODO: Store closed session IDs too?
    Assertions.checkState(openSessionIds.remove(toByteList(sessionId)));
  }

  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<DrmInitData.SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters)
      throws NotProvisionedException {
    Assertions.checkState(referenceCount > 0);
    if (keyType == KEY_TYPE_OFFLINE || keyType == KEY_TYPE_RELEASE) {
      throw new UnsupportedOperationException("Offline key requests are not supported.");
    }
    Assertions.checkArgument(keyType == KEY_TYPE_STREAMING, "Unrecognised keyType: " + keyType);
    Assertions.checkState(openSessionIds.contains(toByteList(scope)));
    Assertions.checkNotNull(schemeDatas);
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

  @Nullable
  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    Assertions.checkState(referenceCount > 0);
    List<Byte> responseAsList = Bytes.asList(response);
    if (responseAsList.equals(VALID_KEY_RESPONSE)) {
      sessionIdsWithValidKeys.add(Bytes.asList(scope));
    } else if (responseAsList.equals(KEY_DENIED_RESPONSE)) {
      throw new DeniedByServerException("Key request denied");
    }
    return Util.EMPTY_BYTE_ARRAY;
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    Assertions.checkState(referenceCount > 0);
    return FAKE_PROVISION_REQUEST;
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    Assertions.checkState(referenceCount > 0);
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    Assertions.checkState(referenceCount > 0);
    Assertions.checkState(openSessionIds.contains(toByteList(sessionId)));
    return ImmutableMap.of(
        KEY_STATUS_KEY,
        sessionIdsWithValidKeys.contains(toByteList(sessionId))
            ? KEY_STATUS_AVAILABLE
            : KEY_STATUS_UNAVAILABLE);
  }

  @Override
  public void acquire() {
    Assertions.checkState(referenceCount > 0);
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
    Assertions.checkState(referenceCount > 0);

    return null;
  }

  @Override
  public String getPropertyString(String propertyName) {
    Assertions.checkState(referenceCount > 0);
    @Nullable String value = stringProperties.get(propertyName);
    if (value == null) {
      throw new IllegalArgumentException("Unrecognized propertyName: " + propertyName);
    }
    return value;
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    Assertions.checkState(referenceCount > 0);
    @Nullable byte[] value = byteProperties.get(propertyName);
    if (value == null) {
      throw new IllegalArgumentException("Unrecognized propertyName: " + propertyName);
    }
    return value;
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    Assertions.checkState(referenceCount > 0);
    stringProperties.put(propertyName, value);
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    Assertions.checkState(referenceCount > 0);
    byteProperties.put(propertyName, value);
  }

  @Override
  public ExoMediaCrypto createMediaCrypto(byte[] sessionId) throws MediaCryptoException {
    Assertions.checkState(referenceCount > 0);
    Assertions.checkState(openSessionIds.contains(toByteList(sessionId)));
    return new FakeExoMediaCrypto();
  }

  @Override
  public Class<FakeExoMediaCrypto> getExoMediaCryptoType() {
    return FakeExoMediaCrypto.class;
  }

  private static ImmutableList<Byte> toByteList(byte[] byteArray) {
    return ImmutableList.copyOf(Bytes.asList(byteArray));
  }

  private static class FakeExoMediaCrypto implements ExoMediaCrypto {}

  /** An license server implementation to interact with {@link FakeExoMediaDrm}. */
  public static class LicenseServer implements MediaDrmCallback {

    private final List<ImmutableList<DrmInitData.SchemeData>> receivedSchemeDatas;
    private final ImmutableSet<ImmutableList<DrmInitData.SchemeData>> allowedSchemeDatas;

    @SafeVarargs
    public static LicenseServer allowingSchemeDatas(List<DrmInitData.SchemeData>... schemeDatas) {
      ImmutableSet.Builder<ImmutableList<DrmInitData.SchemeData>> schemeDatasBuilder =
          ImmutableSet.builder();
      for (List<DrmInitData.SchemeData> schemeData : schemeDatas) {
        schemeDatasBuilder.add(ImmutableList.copyOf(schemeData));
      }
      return new LicenseServer(schemeDatasBuilder.build());
    }

    private LicenseServer(ImmutableSet<ImmutableList<DrmInitData.SchemeData>> allowedSchemeDatas) {
      receivedSchemeDatas = new ArrayList<>();
      this.allowedSchemeDatas = allowedSchemeDatas;
    }

    public ImmutableList<ImmutableList<DrmInitData.SchemeData>> getReceivedSchemeDatas() {
      return ImmutableList.copyOf(receivedSchemeDatas);
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request)
        throws MediaDrmCallbackException {
      return new byte[0];
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request)
        throws MediaDrmCallbackException {
      ImmutableList<DrmInitData.SchemeData> schemeDatas =
          KeyRequestData.fromByteArray(request.getData()).schemeDatas;
      receivedSchemeDatas.add(schemeDatas);
      return Bytes.toArray(
          allowedSchemeDatas.contains(schemeDatas) ? VALID_KEY_RESPONSE : KEY_DENIED_RESPONSE);
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
      List<String> optionalParameterKeys = Assertions.checkNotNull(in.createStringArrayList());
      List<String> optionalParameterValues = Assertions.checkNotNull(in.createStringArrayList());
      Assertions.checkArgument(optionalParameterKeys.size() == optionalParameterValues.size());
      for (int i = 0; i < optionalParameterKeys.size(); i++) {
        optionalParameters.put(optionalParameterKeys.get(i), optionalParameterValues.get(i));
      }

      this.optionalParameters = optionalParameters.build();
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

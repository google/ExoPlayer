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
import android.os.PersistableBundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** A fake implementation of {@link ExoMediaDrm} for use in tests. */
@RequiresApi(18)
public class FakeExoMediaDrm implements ExoMediaDrm {

  private static final KeyRequest DUMMY_KEY_REQUEST =
      new KeyRequest(TestUtil.createByteArray(4, 5, 6), "foo.test");

  private static final ProvisionRequest DUMMY_PROVISION_REQUEST =
      new ProvisionRequest(TestUtil.createByteArray(7, 8, 9), "bar.test");

  private final Map<String, byte[]> byteProperties;
  private final Map<String, String> stringProperties;
  private final Set<List<Byte>> openSessionIds;
  private final AtomicInteger sessionIdGenerator;

  private int referenceCount;

  /**
   * Constructs an instance that returns random and unique {@code sessionIds} for subsequent calls
   * to {@link #openSession()}.
   */
  public FakeExoMediaDrm() {
    byteProperties = new HashMap<>();
    stringProperties = new HashMap<>();
    openSessionIds = new HashSet<>();
    sessionIdGenerator = new AtomicInteger();

    referenceCount = 1;
  }

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
    return DUMMY_KEY_REQUEST;
  }

  @Nullable
  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    Assertions.checkState(referenceCount > 0);
    return null;
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    Assertions.checkState(referenceCount > 0);
    return DUMMY_PROVISION_REQUEST;
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    Assertions.checkState(referenceCount > 0);
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    Assertions.checkState(referenceCount > 0);
    return Collections.emptyMap();
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

  @Nullable
  @Override
  public Class<? extends ExoMediaCrypto> getExoMediaCryptoType() {
    return FakeExoMediaCrypto.class;
  }

  private static List<Byte> toByteList(byte[] byteArray) {
    List<Byte> result = new ArrayList<>(byteArray.length);
    for (byte b : byteArray) {
      result.add(b);
    }
    return result;
  }

  private static class FakeExoMediaCrypto implements ExoMediaCrypto {}
}

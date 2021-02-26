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

package com.google.android.exoplayer2.upstream;

import static java.lang.Math.min;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.DataSourceContractTest;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link UdpDataSource}. */
@RunWith(AndroidJUnit4.class)
public class UdpDataSourceContractTest extends DataSourceContractTest {

  private UdpDataSource udpDataSource;
  private byte[] data;

  @Before
  public void setUp() {
    udpDataSource = new UdpDataSource();
    data = TestUtil.buildTestData(/* length= */ 256);
    PacketTrasmitterTransferListener transferListener = new PacketTrasmitterTransferListener(data);
    udpDataSource.addTransferListener(transferListener);
  }

  @Override
  protected DataSource createDataSource() {
    return udpDataSource;
  }

  @Override
  protected boolean unboundedReadsAreIndefinite() {
    return true;
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("local-udp-unicast-socket")
            .setUri("udp://localhost:" + findFreeUdpPort())
            .setExpectedBytes(data)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("udp://notfound.invalid:12345");
  }

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithPosition_readUntilEnd() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithLength_readExpectedRange() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithPositionAndLength_readExpectedRange() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithPositionAtEnd_throwsPositionOutOfRangeException() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithPositionAtEndAndLength_throwsPositionOutOfRangeException() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithPositionOutOfRange_throwsPositionOutOfRangeException() {}

  @Test
  @Ignore("UdpDataSource doesn't support DataSpec's position or length [internal: b/175856954]")
  @Override
  public void dataSpecWithEndPositionOutOfRange_readsToEnd() {}

  /**
   * Finds a free UDP port in the range of unreserved ports 50000-60000 that can be used from the
   * test or throws an {@link IllegalStateException} if no port is available.
   *
   * <p>There is no guarantee that the port returned will still be available as another process may
   * occupy it in the mean time.
   */
  private static int findFreeUdpPort() {
    for (int i = 50000; i <= 60000; i++) {
      try {
        new DatagramSocket(i).close();
        return i;
      } catch (SocketException e) {
        // Port is occupied, continue to next port.
      }
    }
    throw new IllegalStateException();
  }

  /**
   * A {@link TransferListener} that triggers UDP packet transmissions back to the UDP data source.
   */
  private static class PacketTrasmitterTransferListener implements TransferListener {
    private final byte[] data;

    public PacketTrasmitterTransferListener(byte[] data) {
      this.data = data;
    }

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {}

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
      String host = dataSpec.uri.getHost();
      int port = dataSpec.uri.getPort();
      try (DatagramSocket socket = new DatagramSocket()) {
        // Split data in packets of up to 64 bytes: UDP is unreliable, it may lose, duplicate or
        // re-order packets. However, we want to transmit more than one UDP packets to thoroughly
        // test the UDP data source. We assume that UDP delivery within the same host is reliable.
        for (int offset = 0; offset < data.length; offset += 64) {
          int packetLength = min(64, data.length - offset);
          DatagramPacket packet =
              new DatagramPacket(data, offset, packetLength, InetAddress.getByName(host), port);
          socket.send(packet);
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void onBytesTransferred(
        DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {}

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
  }
}

/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RtpPacketReorderingQueue}. */
@RunWith(AndroidJUnit4.class)
public class RtpPacketReorderingQueueTest {

  private final RtpPacketReorderingQueue reorderingQueue = new RtpPacketReorderingQueue();

  @Test
  public void poll_emptyQueue_returnsNull() {
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isNull();
  }

  @Test
  public void poll_onlyOnePacketInQueue_returns() {
    RtpPacket packet = makePacket(/* sequenceNumber= */ 1);

    // Queue after offering: [1].
    reorderingQueue.offer(packet, /* receivedTimestampMs= */ 0);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet);
  }

  @Test
  public void poll_withPacketsEnqueuedInOrder_returnsCorrectPacket() {
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packet2 = makePacket(/* sequenceNumber= */ 2);
    RtpPacket packet3 = makePacket(/* sequenceNumber= */ 3);

    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 0);
    reorderingQueue.offer(packet2, /* receivedTimestampMs= */ 0);
    reorderingQueue.offer(packet3, /* receivedTimestampMs= */ 0);

    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet1);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet2);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet3);
  }

  @Test
  public void reset_nonEmptyQueue_resetsQueue() {
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packet2 = makePacket(/* sequenceNumber= */ 2);

    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 0);
    reorderingQueue.offer(packet2, /* receivedTimestampMs= */ 0);
    reorderingQueue.reset();

    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isNull();
  }

  @Test
  public void reorder_withPacketArriveOutOfOrderButInTime_returnsPacketsInCorrectOrder() {
    // The packets arrive in the order of 1, 4, 5, 2, 3, 6. The mis-positioned packets (2, 3)
    // arrive in time, so the polling order is: 1, null, 2, 3, 4, 5, 6.
    List<RtpPacket> polledPackets = new ArrayList<>();
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packet2 = makePacket(/* sequenceNumber= */ 2);
    RtpPacket packet3 = makePacket(/* sequenceNumber= */ 3);
    RtpPacket packet4 = makePacket(/* sequenceNumber= */ 4);
    RtpPacket packet5 = makePacket(/* sequenceNumber= */ 5);
    RtpPacket packet6 = makePacket(/* sequenceNumber= */ 6);

    // Offering 1, queue after offering: [1].
    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 1);
    // Offering 4, queue after offering: [1, 4].
    reorderingQueue.offer(packet4, /* receivedTimestampMs= */ 2);
    // polling 1, queue after polling: [4].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 0));
    // Offering 5, queue after offering: [4, 5].
    reorderingQueue.offer(packet5, /* receivedTimestampMs= */ 3);
    // Should not poll: still waiting for packet 2, since packet 4 is received at 2, after cutoff.
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 1));
    // Offering 2, queue after offering: [2, 4, 5].
    reorderingQueue.offer(packet2, /* receivedTimestampMs= */ 4);
    // polling 2, queue after polling: [4, 5].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 2));
    // Offering 3, queue after offering: [3, 4, 5].
    reorderingQueue.offer(packet3, /* receivedTimestampMs= */ 5);
    // polling 3, queue after polling: [4, 5].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 3));
    // Offering 6, queue after offering: [4, 5, 6].
    reorderingQueue.offer(packet6, /* receivedTimestampMs= */ 6);
    // polling 4, queue after polling: [5, 6].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 4));
    // polling 5, queue after polling: [6].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 5));
    // polling 6, queue after polling: [].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 6));

    assertThat(polledPackets)
        .containsExactly(packet1, null, packet2, packet3, packet4, packet5, packet6)
        .inOrder();
  }

  @Test
  public void
      reorder_withPacketArriveOutOfOrderMissedDeadline_returnsPacketsWithSequenceNumberJump() {
    // Packets arrive in order 1, 3, 4, 2. Packet 2 arrives after packet 3 is dequeued, so packet 2
    // is discarded.
    List<RtpPacket> polledPackets = new ArrayList<>();
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packet2 = makePacket(/* sequenceNumber= */ 2);
    RtpPacket packet3 = makePacket(/* sequenceNumber= */ 3);
    RtpPacket packet4 = makePacket(/* sequenceNumber= */ 4);

    // Offering 1, queue after offering: [1].
    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 1);
    // Offering 3, queue after offering: [1, 3].
    reorderingQueue.offer(packet3, /* receivedTimestampMs= */ 2);
    // polling 1, queue after polling: [3].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 1));
    // Queue after offering: [3, 4].
    reorderingQueue.offer(packet4, /* receivedTimestampMs= */ 3);
    // Should poll packet 3 (receivedTimestampMs = 2), because 2 hasn't come after the wait.
    // polling 3, queue after polling: [4].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 2));
    // Offering 2, queue after offering: [4]. Should not add packet 2: packet 3 is already dequeued.
    reorderingQueue.offer(packet2, /* receivedTimestampMs= */ 4);
    // polling 4, queue after polling: [].
    polledPackets.add(reorderingQueue.poll(/* cutoffTimestampMs= */ 3));

    assertThat(polledPackets).containsExactly(packet1, packet3, packet4).inOrder();
  }

  @Test
  public void reorder_withLargerThanAllowedJumpForwardInSequenceNumber_resetsQueue() {
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packetWithSequenceNumberJump =
        makePacket(/* sequenceNumber= */ 10 + RtpPacketReorderingQueue.MAX_SEQUENCE_LEAP_ALLOWED);

    // Offering 1, queue after offering: [1].
    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 1);

    // Queueing a packet with a sequence number that creates a shift larger than the allowed maximum
    // will force reset the queue.
    reorderingQueue.offer(packetWithSequenceNumberJump, /* receivedTimestampMs= */ 2);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0))
        .isEqualTo(packetWithSequenceNumberJump);
  }

  @Test
  public void reorder_withLargerThanAllowedJumpInSequenceNumberAndWrapAround_resetsQueue() {
    RtpPacket packet1 = makePacket(/* sequenceNumber= */ 1);
    RtpPacket packetWithSequenceNumberJump =
        makePacket(
            /* sequenceNumber= */ RtpPacket.MAX_SEQUENCE_NUMBER
                - RtpPacketReorderingQueue.MAX_SEQUENCE_LEAP_ALLOWED
                - 10);

    // Offering 1, queue after offering: [1].
    reorderingQueue.offer(packet1, /* receivedTimestampMs= */ 1);
    // Queueing a packet with a sequence number that creates a shift larger than the allowed maximum
    // will force reset the queue.
    reorderingQueue.offer(packetWithSequenceNumberJump, /* receivedTimestampMs= */ 2);

    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0))
        .isEqualTo(packetWithSequenceNumberJump);
  }

  @Test
  public void reorder_receivingOutOfOrderPacketWithWrapAround_returnsPacketsInCorrectOrder() {
    RtpPacket packet2 = makePacket(/* sequenceNumber= */ 2);
    RtpPacket packet3 = makePacket(/* sequenceNumber= */ 3);
    RtpPacket packet65000 = makePacket(/* sequenceNumber= */ 65000);
    RtpPacket packet65001 = makePacket(/* sequenceNumber= */ 65001);
    RtpPacket packet65002 = makePacket(/* sequenceNumber= */ 65002);
    RtpPacket packet65003 = makePacket(/* sequenceNumber= */ 65003);
    RtpPacket packet65004 = makePacket(/* sequenceNumber= */ 65004);

    reorderingQueue.offer(packet65000, /* receivedTimestampMs= */ 1);
    reorderingQueue.offer(packet65001, /* receivedTimestampMs= */ 2);
    reorderingQueue.offer(packet65002, /* receivedTimestampMs= */ 3);
    reorderingQueue.offer(packet65003, /* receivedTimestampMs= */ 4);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet65000);
    reorderingQueue.offer(packet2, /* receivedTimestampMs= */ 5);
    reorderingQueue.offer(packet3, /* receivedTimestampMs= */ 6);
    reorderingQueue.offer(packet65004, /* receivedTimestampMs= */ 7);

    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet65001);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet65002);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet65003);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet65004);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 6)).isEqualTo(packet2);
    assertThat(reorderingQueue.poll(/* cutoffTimestampMs= */ 0)).isEqualTo(packet3);
  }

  private static RtpPacket makePacket(int sequenceNumber) {
    return new RtpPacket.Builder().setSequenceNumber(sequenceNumber).build();
  }
}

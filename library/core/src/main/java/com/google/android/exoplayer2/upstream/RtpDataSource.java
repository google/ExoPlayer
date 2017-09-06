/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.Uri;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.rtp.DefaultRtpDistributionFeedbackFactory;
import com.google.android.exoplayer2.util.rtp.AluRtpDistributionFeedbackFactory;
import com.google.android.exoplayer2.util.rtp.RtpDistributionFeedback;
import com.google.android.exoplayer2.util.rtp.RtpPacket;
import com.google.android.exoplayer2.util.rtp.RtpPacketQueue;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpSessionUtils;

import java.io.IOException;

/**
 * A RTP {@link DataSource}.
 */
public final class RtpDataSource implements DataSource {

  /**
   * Thrown when an error is encountered when trying to read from a {@link RtpDataSource}.
   */
  public static final class RtpDataSourceException extends IOException {

    public RtpDataSourceException(String message) {
      super(message);
    }

    public RtpDataSourceException(Exception cause) {
      super(cause);
    }

  }

  /**
   * The maximum transfer unit, in bytes.
   */
  public static final int MTU_SIZE = 1500;

  private final TransferListener<? super RtpDataSource> listener;

  /**
   * The RTP distribution and feedback scheme implementation
   */
  private RtpDistributionFeedback distributionFeedback;

  /**
   * The RTP feedback properties
   */
  private final RtpDistributionFeedback.RtpFeedbackProperties feedbackProperties;

  /**
   * The RTP source holders
   */
  private RtpBurstSourceHolder burstSourceHolder;
  private RtpAuthTokenSourceHolder authTokenSourceHolder;
  private RtpDistributionSourceHolder distributionSourceHolder;
  private RtpRetransmissionSourceHolder retransmissionSourceHolder;

  private long lastTimeStampBytesReaded;

  private DataSpec dataSpec;
  private boolean opened;

  /**
   * @param listener An optional listener.
   */
  public RtpDataSource(TransferListener<? super RtpDataSource> listener) {
    this(listener, new RtpDistributionFeedback.RtpFeedbackProperties());
  }

  /**
   * @param listener An optional listener.
   * @param feedbackProperties The feedback properties to be set to the data source.
   */
  public RtpDataSource(TransferListener<? super RtpDataSource> listener,
                       RtpDistributionFeedback.RtpFeedbackProperties feedbackProperties) {
    this.listener = listener;
    this.feedbackProperties = (feedbackProperties == null) ?
            new RtpDistributionFeedback.RtpFeedbackProperties() : feedbackProperties;
  }

  /**
   * Sets the value of a feedback property. The value will be used to establish a specific a
   * feedback scheme and model.
   *
   * @param property The name of the feedback property.
   * @param value The value of the feedback property.
   */

  public void setFeedbackProperty(int property, Object value) {
    feedbackProperties.set(property, value);
  }

  /**
   * Builds specific distribution and feedback implementation from vendor.
   * A default distribution and feedback implementation will be created whether no vendor model
   * was given from feedback properties.
   *
   * @return The {@link RtpDistributionFeedback}.
   */

  private RtpDistributionFeedback buildRtpDistributionFeedback() {
    if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.FB_VENDOR)) {

      int fbVendor = (int) feedbackProperties.getSnapshot().get(RtpDistributionFeedback.Properties.
              FB_VENDOR);

      switch (fbVendor) {
        case RtpDistributionFeedback.Providers.ALU:
          return new AluRtpDistributionFeedbackFactory(RtcpSessionUtils.SSRC(),
                  RtcpSessionUtils.CNAME()).createDistributionFeedback();

        default:
          return new DefaultRtpDistributionFeedbackFactory(RtcpSessionUtils.SSRC(),
                  RtcpSessionUtils.CNAME()).createDistributionFeedback();
      }

    } else {
      return new DefaultRtpDistributionFeedbackFactory(RtcpSessionUtils.SSRC(),
              RtcpSessionUtils.CNAME()).createDistributionFeedback();
    }
  }

  @Override
  public long open(DataSpec dataSpec) throws RtpDataSourceException {
    this.dataSpec = dataSpec;

    distributionFeedback = buildRtpDistributionFeedback();
    distributionSourceHolder = new RtpDistributionSourceHolder(distributionFeedback);

    if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.
            FB_EVENTS_CALLBACK)) {

      distributionFeedback.setFeedbackEventListener(
              (RtpDistributionFeedback.RtpFeedbackEventListener)
              feedbackProperties.getSnapshot().get(RtpDistributionFeedback.Properties.
                      FB_EVENTS_CALLBACK));
    }

    if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.FB_SCHEME)) {

      int fbScheme = (int) feedbackProperties.getSnapshot().get(RtpDistributionFeedback.
              Properties.FB_SCHEME);

      if ((fbScheme & RtpDistributionFeedback.Schemes.FB_PORT_MAPPING) ==
              RtpDistributionFeedback.Schemes.FB_PORT_MAPPING) {

          authTokenSourceHolder = new RtpAuthTokenSourceHolder(distributionFeedback);

          if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.
                  FB_PORT_MAPPING_URI)) {
            try {

              authTokenSourceHolder.open(Uri.parse((String)feedbackProperties.getSnapshot().
                      get(RtpDistributionFeedback.Properties.FB_PORT_MAPPING_URI)));

            } catch (IOException ex) {
              // ....
            }
          }
      }

      if ((fbScheme & RtpDistributionFeedback.Schemes.FB_RAMS) ==
              RtpDistributionFeedback.Schemes.FB_RAMS) {

        burstSourceHolder = new RtpBurstSourceHolder(distributionFeedback);

        if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.
                FB_RAMS_URI)) {

          try {

            if ((authTokenSourceHolder == null) || (!authTokenSourceHolder.isOpened())) {
              burstSourceHolder.open(Uri.parse((String) feedbackProperties.getSnapshot().
                      get(RtpDistributionFeedback.Properties.FB_RAMS_URI)));
            }

          } catch (IOException ex) {

            try {

              distributionSourceHolder.open(dataSpec.uri);

            } catch (IOException ex2) {
              throw new RtpDataSourceException(ex2);
            }

          }
        }
      }

      if ((burstSourceHolder == null) || !burstSourceHolder.isOpened()) {
        try {

          if (!distributionSourceHolder.isOpened()) {
            distributionSourceHolder.open(dataSpec.uri);
          }

        } catch (IOException ex2) {
          throw new RtpDataSourceException(ex2);
        }
      }

      if ((fbScheme & RtpDistributionFeedback.Schemes.FB_CONGESTION_CONTROL) ==
              RtpDistributionFeedback.Schemes.FB_CONGESTION_CONTROL) {

        retransmissionSourceHolder = new RtpRetransmissionSourceHolder(distributionFeedback);

        if (feedbackProperties.getSnapshot().containsKey(RtpDistributionFeedback.Properties.
                FB_CONGESTION_CONTROL_URI)) {

          try {

            retransmissionSourceHolder.open(Uri.parse((String) feedbackProperties.getSnapshot().
                    get(RtpDistributionFeedback.Properties.FB_CONGESTION_CONTROL_URI)));

          } catch (IOException ex) {
            // Do nothing
          }
        }
      }

    } else {

      try {

        distributionSourceHolder.open(dataSpec.uri);

      } catch (IOException ex) {
        throw new RtpDataSourceException(ex);
      }
    }

    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    opened = true;
    return C.LENGTH_UNSET;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws RtpDataSourceException {
    int length = 0;

    if (distributionSourceHolder.isOpened()) {

      if ((burstSourceHolder != null) &&
              (burstSourceHolder.isOpened() || burstSourceHolder.isDataAvailable())) {
        length = burstSourceHolder.read(buffer, offset, readLength);

      } else {

        if ((retransmissionSourceHolder != null)) {
          if (retransmissionSourceHolder.isDataAvailable()) {
            if (retransmissionSourceHolder.getFirstSequenceNumberAvailable() <
                    distributionSourceHolder.getFirstSequenceNumberAvailable()) {

              if (retransmissionSourceHolder.getFirstTimeStampAvailable() <=
                      distributionSourceHolder.getFirstTimeStampAvailable()) {
                length = retransmissionSourceHolder.read(buffer, offset, readLength);

              } else {
                length = distributionSourceHolder.read(buffer, offset, readLength);
              }

            } else if (retransmissionSourceHolder.getFirstTimeStampAvailable() <
                    distributionSourceHolder.getFirstTimeStampAvailable()) {
              length = retransmissionSourceHolder.read(buffer, offset, readLength);

            } else {
              length = distributionSourceHolder.read(buffer, offset, readLength);
            }

          } else if (retransmissionSourceHolder.isDataPending()) {
            long delay = (int) (System.currentTimeMillis() - lastTimeStampBytesReaded);

            if (delay > retransmissionSourceHolder.getMaxDelayTimeForPending()) {
              retransmissionSourceHolder.resetAllPacketsRecoveryPending(
                      distributionSourceHolder.getFirstTimeStampAvailable());

              length = distributionSourceHolder.read(buffer, offset, readLength);
            }

          } else {
            length = distributionSourceHolder.read(buffer, offset, readLength);
          }
        } else {
          length = distributionSourceHolder.read(buffer, offset, readLength);
        }
      }

    } else {

      if (burstSourceHolder != null) {
        length = burstSourceHolder.read(buffer, offset, readLength);
      }
    }

    if (length > 0) {
      listener.onBytesTransferred(this, length);
      lastTimeStampBytesReaded = System.currentTimeMillis();
    }

    return length;
  }

  @Override
  public Uri getUri() {
    return dataSpec.uri;
  }

  @Override
  public void close() {
    if ((burstSourceHolder != null) && (burstSourceHolder.isOpened())) {
      burstSourceHolder.close();
    }

    if ((retransmissionSourceHolder != null) && (retransmissionSourceHolder.isOpened())) {
      retransmissionSourceHolder.close();
    }

    if ((distributionSourceHolder != null) && (distributionSourceHolder.isOpened())) {
      distributionSourceHolder.close();
    }

    if (opened) {
      opened = false;

      if (listener != null) {
        listener.onTransferEnd(this);
      }

    }
  }


  private class RtpAuthTokenSourceHolder implements
          RtpDistributionFeedback.RtpFeedbackTargetSource.AuthTokenEventListener,
          Handler.Callback {

    private RtpDistributionFeedback.RtpAuthTokenSource authTokenSource;

    private final Handler mediaHandler;
    private final HandlerThread mediaThread;
    private final Loader mediaLoader;

    private boolean opened;
    private boolean released;

    private static final int MSG_SOURCE_RELEASE = 4;

    public RtpAuthTokenSourceHolder(RtpDistributionFeedback distributionFeedback) {
      mediaThread = new HandlerThread("Handler:RtpBurstSource", Process.THREAD_PRIORITY_AUDIO);
      mediaThread.start();

      mediaHandler = new Handler(mediaThread.getLooper(), this);
      mediaLoader = new Loader("Loader:RtpBurstSource");

      opened = false;
      released = true;

      try {

        authTokenSource = distributionFeedback.createAuthTokenSource(this);

      } catch (RtpDistributionFeedback.UnsupportedRtpDistributionFeedbackSourceException ex) {
        mediaHandler.sendEmptyMessage(MSG_SOURCE_RELEASE);
      }
    }

    void open(Uri uri) throws IOException {
      if (!opened) {

        authTokenSource.open(uri);

        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.startLoading(authTokenSource, authTokenSource, 0);
          }
        };

        mediaHandler.post(currentThreadTask);

        authTokenSource.sendAuthTokenRequest();

        opened = true;
        released = false;
      }
    }

    void close() {
      if (opened) {
        if (!authTokenSource.isLoadCanceled()) {
          authTokenSource.cancelLoad();
          authTokenSource.close();
        }

        opened = false;
      }

      release();
    }

    void release() {
      if (!released) {
        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.release();
          }
        };

        mediaHandler.post(currentThreadTask);
        mediaThread.quit();

        released = true;
      }
    }

    public boolean isOpened() {
      return opened;
    }


    // RtpDistributionFeedback.RtpFeedbackEventListener.AuthTokenEventListener implementation
    @Override
    public void onAuthTokenResponse() {
      try {

        if (burstSourceHolder != null) {
          burstSourceHolder.open(Uri.parse((String) feedbackProperties.getSnapshot().
                  get(RtpDistributionFeedback.Properties.FB_RAMS_URI)));
        }

      } catch (IOException ex) {}
    }

    @Override
    public void onAuthTokenResponseBeforeTimeout() {
      close();
    }

    @Override
    public void onAuthTokenResponseBeforeError() {
      close();
    }

    @Override
    public void onAuthTokenResponseUnexpected() {
      close();
    }

    @Override
    public void onRtpAuthTokenSourceError() {
      close();
    }

    @Override
    public void onRtpAuthTokenSourceCanceled() {
      close();
    }

    // Handler.Callback implementation
    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {

        case MSG_SOURCE_RELEASE: {
          release();
          return true;
        }

        default:
          // Should never happen.
          throw new IllegalStateException();
      }
    }
  }

  private class RtpBurstSourceHolder implements
          RtpDistributionFeedback.RtpFeedbackTargetSource.BurstEventListener,
          Handler.Callback {

    private RtpPacketQueue packetQueue;
    private RtpDistributionFeedback.RtpBurstSource burstSource;

    private boolean opened;
    private boolean error;
    private boolean released;

    private final Handler mediaHandler;
    private final HandlerThread mediaThread;
    private final Loader mediaLoader;

    private final ConditionVariable loadCondition;

    // Internal messages
    private static final int MSG_SOURCE_RELEASE = 1;

    public RtpBurstSourceHolder(RtpDistributionFeedback distributionFeedback) {

      mediaThread = new HandlerThread("Handler:RtpBurstSource", Process.THREAD_PRIORITY_AUDIO);
      mediaThread.start();

      mediaHandler = new Handler(mediaThread.getLooper(), this);
      mediaLoader = new Loader("Loader:RtpBurstSource");

      loadCondition = new ConditionVariable();

      opened = false;
      error = false;
      released = true;

      try {

        burstSource = distributionFeedback.createBurstSource(this);
        packetQueue = new RtpPacketQueue(burstSource.getMaxBufferCapacity());

      } catch (RtpDistributionFeedback.UnsupportedRtpDistributionFeedbackSourceException ex) {
        mediaHandler.sendEmptyMessage(MSG_SOURCE_RELEASE);
      }
    }

    void open(Uri uri) throws IOException {
      if (!opened) {

        burstSource.open(uri);

        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.startLoading(burstSource, burstSource, 0);
          }
        };

        mediaHandler.post(currentThreadTask);

        burstSource.sendBurstRapidAcquisitionRequest(dataSpec.uri);

        opened = true;
        released = false;
      }
    }

    public int read(byte[] buffer, int offset, int readLength) throws RtpDataSourceException {
      int rbytes;

      if (error && !packetQueue.isDataAvailable()) {
        throw new RtpDataSourceException("RtpBurstSource is closed");
      }

      if (!packetQueue.isDataAvailable()) {
        loadCondition.block();
      }

      rbytes = packetQueue.get(buffer, offset, readLength);

      loadCondition.close();

      return rbytes;
    }

    void close() {
      if (opened) {
        if (!burstSource.isLoadCanceled()) {
          burstSource.cancelLoad();
          burstSource.close();
        }

        opened = false;
        loadCondition.open();
      }

      release();
    }

    void release() {
      if (!released) {
        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.release();
          }
        };

        mediaHandler.post(currentThreadTask);
        mediaThread.quit();

        released = true;
      }
    }

    boolean isDataAvailable() {
      return (packetQueue != null) && (packetQueue.isDataAvailable());
    }

    public boolean isOpened() {
      return opened;
    }

    public boolean isError() {
      return error;
    }

    // RtpDistributionFeedback.RtpFeedbackEventListener.BurstEventListener implementation
    @Override
    public void onBurstRapidAcquisitionAccepted() {
    }

    @Override
    public void onBurstRapidAcquisitionRejected() {
      try {

        burstSource.sendBurstTerminationRequest();

      } catch (IOException ex) {
        // Do nothing
      } finally {
        close();
      }
    }

    @Override
    public void oBurstRapidAcquisitionResponseBeforeTimeout() {
      close();
    }

    @Override
    public void onMulticastJoinSignal() {
      try {

        distributionSourceHolder.open(dataSpec.uri);

      } catch (IOException ex) {
        // Do nothing
        close();
      }
    }

    @Override
    public void onBurstRapidAcquisitionCompleted() {
      try {

        burstSource.sendBurstTerminationRequest();

      } catch (IOException ex) {
        // Do nothing
      } finally {
        close();
      }
    }

    @Override
    public void onInvalidToken() {
      close();
    }

    @Override
    public void onRtpPacketBurstReceived(RtpPacket packet) {
      packetQueue.push(packet);
      loadCondition.open();
    }

    @Override
    public void onRtpBurstSourceError() {

      try {

        if (!distributionSourceHolder.isOpened()) {
          distributionSourceHolder.open(dataSpec.uri);
        }

      } catch (IOException ex) {
        // Do nothing
      } finally {
        error = true;
        close();
      }
    }

    @Override
    public void onRtpBurstSourceCanceled() {
      close();
    }


    // Handler.Callback implementation
    @Override
    public boolean handleMessage(Message msg) {

      switch (msg.what) {

        case MSG_SOURCE_RELEASE: {
          release();
          return true;
        }

        default:
          // Should never happen.
          throw new IllegalStateException();
      }
    }
  }

  private class RtpRetransmissionSourceHolder implements
          RtpDistributionFeedback.RtpFeedbackTargetSource.RetransmissionEventListener,
          Handler.Callback {

    private RtpPacketQueue packetQueue;
    private RtpDistributionFeedback.RtpRetransmissionSource retransmissionSource;

    private boolean opened;
    private boolean error;
    private boolean released;

    private final Handler mediaHandler;
    private final HandlerThread mediaThread;
    private final Loader mediaLoader;

    private final ConditionVariable loadCondition;

    // Internal messages
    private static final int MSG_SOURCE_RELEASE = 1;

    public RtpRetransmissionSourceHolder(RtpDistributionFeedback distributionFeedback) {

      mediaThread = new HandlerThread("Handler:RtpRetransmissionSource", Process.THREAD_PRIORITY_AUDIO);
      mediaThread.start();

      mediaHandler = new Handler(mediaThread.getLooper(), this);
      mediaLoader = new Loader("Loader:RtpRetransmissionSource");

      loadCondition = new ConditionVariable();

      opened = false;
      released = true;

      try {

        retransmissionSource = distributionFeedback.createRetransmissionSource(this);
        packetQueue = new RtpPacketQueue(retransmissionSource.getMaxBufferCapacity());

      } catch (RtpDistributionFeedback.UnsupportedRtpDistributionFeedbackSourceException ex) {
        mediaHandler.sendEmptyMessage(MSG_SOURCE_RELEASE);
      }
    }

    void open(Uri uri) throws IOException {
      if (!opened) {

        retransmissionSource.open(uri);

        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.startLoading(retransmissionSource, retransmissionSource, 0);
          }
        };

        mediaHandler.post(currentThreadTask);

        opened = true;
        released = false;
      }
    }

    public int read(byte[] buffer, int offset, int readLength) throws RtpDataSourceException {
      int rbytes;

      if (error && !packetQueue.isDataAvailable()) {
        throw new RtpDataSourceException("RtpRetransmissionSource is closed");
      }

      if (!packetQueue.isDataAvailable()) {
        loadCondition.block();
      }

      rbytes = packetQueue.get(buffer, offset, readLength);

      loadCondition.close();

      return rbytes;
    }

    void close() {
      if (opened) {
        if (!retransmissionSource.isLoadCanceled()) {

          try {

            retransmissionSource.sendRetransmissionTerminationRequest();

          } catch (IOException ex) { }

          retransmissionSource.cancelLoad();
          retransmissionSource.close();
        }

        opened = false;
        loadCondition.open();
      }

      release();
    }

    void release() {
      if (!released) {
        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.release();
          }
        };

        mediaHandler.post(currentThreadTask);
        mediaThread.quit();

        released = true;
      }
    }

    void resetAllPacketsRecoveryPending(long timestamp) {
      retransmissionSource.resetAllPacketsRecoveryPending(timestamp);
    }

    long getMaxDelayTimeForPending() {
      return retransmissionSource.getMaxDelayTimeForPending();
    }

    void lostPacketEvent(int lastSequenceReceived, int numLostPackets) {
      if ((retransmissionSource.getPacketsRecoveryPending() + numLostPackets) <
              retransmissionSource.getMaxPacketsRecoveryPending()) {
        try {

          retransmissionSource.sendRetransmissionPacketRequest(lastSequenceReceived, numLostPackets);

        } catch (IOException ex) {
          // Do nothing
        }
      } else {
        retransmissionSource.resetAllPacketsRecoveryPending(0);
      }
    }

    public boolean isOpened() {
      return opened;
    }

    public boolean isError() {
      return error;
    }

    boolean isDataAvailable() {
      return (packetQueue != null) && (packetQueue.isDataAvailable());
    }

    int getFirstSequenceNumberAvailable() {
      return packetQueue.front().getSequenceNumber();
    }

    long getFirstTimeStampAvailable() {
      return packetQueue.front().getTimeStamp();
    }

    public boolean isDataPending() { return retransmissionSource.getPacketsRecoveryPending() > 0; }

    // RtpDistributionFeedback.RtpFeedbackEventListener.RetransmissionEventListener implementation
    @Override
    public void onInvalidToken() {
      close();
    }

    @Override
    public void onRtpPacketLossReceived(RtpPacket packet) {
      packetQueue.push(packet);
      loadCondition.open();
    }

    @Override
    public void onRtpRetransmissionSourceError() {
      error = true;
      close();
    }

    @Override
    public void onRtpRetransmissionSourceCanceled() {
      close();
    }

    // Handler.Callback implementation
    @Override
    public boolean handleMessage(Message msg) {

      switch (msg.what) {

        case MSG_SOURCE_RELEASE: {
          release();
          return true;
        }

        default:
          // Should never happen.
          throw new IllegalStateException();
      }
    }
  }


  private class RtpDistributionSourceHolder implements
          RtpDistributionFeedback.RtpDistributionEventListener,
          Handler.Callback {

    private RtpPacketQueue packetQueue;
    private RtpDistributionFeedback.RtpDistributionSource distributionSource;

    private boolean opened;
    private boolean error;
    private boolean released;

    private final Handler mediaHandler;
    private final HandlerThread mediaThread;
    private final Loader mediaLoader;

    private final ConditionVariable loadCondition;

    // Internal messages
    private static final int MSG_SOURCE_RELEASE = 1;

    public RtpDistributionSourceHolder(RtpDistributionFeedback distributionFeedback) {
      mediaThread = new HandlerThread("Handler:RtpDistributionSource", Process.THREAD_PRIORITY_AUDIO);
      mediaThread.start();

      mediaHandler = new Handler(mediaThread.getLooper(), this);
      mediaLoader = new Loader("Loader:RtpDistributionSource");

      loadCondition = new ConditionVariable();

      opened = false;
      released = true;

      try {

        distributionSource = distributionFeedback.createDistributionSource(this);
        packetQueue = new RtpPacketQueue(distributionSource.getMaxBufferCapacity());

      } catch (RtpDistributionFeedback.UnsupportedRtpDistributionFeedbackSourceException ex) {
        mediaHandler.sendEmptyMessage(MSG_SOURCE_RELEASE);
      }
    }

    void open(Uri uri) throws IOException {
      if (!opened) {
        distributionSource.open(uri);

        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.startLoading(distributionSource, distributionSource, 0);
          }
        };

        opened = true;
        released = false;

        mediaHandler.post(currentThreadTask);
      }
    }

    public int read(byte[] buffer, int offset, int readLength) throws RtpDataSourceException {
      int rbytes;

      if (!opened || error) {
        throw new RtpDataSourceException("RtpDistributionSource is closed");
      }

      if (!packetQueue.isDataAvailable()) {
        loadCondition.block();
      }

      rbytes = packetQueue.get(buffer, offset, readLength);

      loadCondition.close();

      return rbytes;
    }

    void close() {
      if (opened) {
        if (!distributionSource.isLoadCanceled()) {

          distributionSource.cancelLoad();
          distributionSource.close();
        }

        opened = false;
        loadCondition.open();
      }

      release();
    }

    void release() {
      if (!released) {
        Runnable currentThreadTask = new Runnable() {
          @Override
          public void run() {
            mediaLoader.release();
          }
        };

        mediaHandler.post(currentThreadTask);
        mediaThread.quit();

        released = true;
      }
    }

    int getFirstSequenceNumberAvailable() {
      return packetQueue.front().getSequenceNumber();
    }

    long getFirstTimeStampAvailable() {
      return packetQueue.front().getTimeStamp();
    }

    public boolean isOpened() {
      return opened;
    }

    public boolean isError() {
      return error;
    }

    // RtpDistributionFeedback.RtpDistributionEventListener implementation
    @Override
    public void onRtpPacketReceived(RtpPacket packet) {
      packetQueue.push(packet);
      loadCondition.open();
    }

    @Override
    public void onRtpLostPacketDetected(int lastSequenceReceived, int numLostPackets) {
      if ((retransmissionSourceHolder != null) && retransmissionSourceHolder.isOpened()) {
        retransmissionSourceHolder.lostPacketEvent(lastSequenceReceived, numLostPackets);
      }
    }

    @Override
    public void onRtpDistributionSourceError() {
      error = true;
      close();
    }

    @Override
    public void onRtpDistributionSourceCanceled() {
      close();
    }

    // Handler.Callback implementation
    @Override
    public boolean handleMessage(Message msg) {

      switch (msg.what) {

        case MSG_SOURCE_RELEASE: {
          release();
          return true;
        }

        default:
          // Should never happen.
          throw new IllegalStateException();
      }
    }
  }

}

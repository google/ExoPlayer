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
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.EGL14;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.effect.GlTextureProcessor;
import androidx.media3.effect.TextureInfo;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.framework.AppTextureFrame;
import com.google.mediapipe.framework.TextureFrame;
import com.google.mediapipe.glutil.EglManager;
import java.util.concurrent.ConcurrentHashMap;

/** Runs a MediaPipe graph on input frames. */
/* package */ final class MediaPipeProcessor implements GlTextureProcessor {

  private static final LibraryLoader LOADER =
      new LibraryLoader("mediapipe_jni") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  static {
    // Not all build configurations require OpenCV to be loaded separately, so attempt to load the
    // library but ignore the error if it's not present.
    try {
      System.loadLibrary("opencv_java3");
    } catch (UnsatisfiedLinkError e) {
      // Do nothing.
    }
  }

  private final FrameProcessor frameProcessor;
  private final ConcurrentHashMap<TextureInfo, TextureFrame> outputFrames;

  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private boolean acceptedFrame;

  /**
   * Creates a new texture processor that wraps a MediaPipe graph.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in gamma RGB BT.709.
   * @param graphName Name of a MediaPipe graph asset to load.
   * @param inputStreamName Name of the input video stream in the graph.
   * @param outputStreamName Name of the input video stream in the graph.
   */
  public MediaPipeProcessor(
      Context context,
      boolean useHdr,
      String graphName,
      String inputStreamName,
      String outputStreamName) {
    checkState(LOADER.isAvailable());
    // TODO(b/227624622): Confirm whether MediaPipeProcessor could support HDR colors.
    checkArgument(!useHdr, "MediaPipeProcessor does not support HDR colors.");
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    EglManager eglManager = new EglManager(EGL14.eglGetCurrentContext());
    frameProcessor =
        new FrameProcessor(
            context, eglManager.getNativeContext(), graphName, inputStreamName, outputStreamName);
    outputFrames = new ConcurrentHashMap<>();
    // OnWillAddFrameListener is called on the same thread as frameProcessor.onNewFrame(...), so no
    // synchronization is needed for acceptedFrame.
    frameProcessor.setOnWillAddFrameListener((long timestamp) -> acceptedFrame = true);
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
    frameProcessor.setConsumer(
        frame -> {
          TextureInfo texture =
              new TextureInfo(
                  frame.getTextureName(),
                  /* fboId= */ C.INDEX_UNSET,
                  frame.getWidth(),
                  frame.getHeight());
          outputFrames.put(texture, frame);
          outputListener.onOutputFrameAvailable(texture, frame.getTimestamp());
        });
  }

  @Override
  public void setErrorListener(ErrorListener errorListener) {
    this.errorListener = errorListener;
    frameProcessor.setAsynchronousErrorListener(
        error -> errorListener.onFrameProcessingError(new FrameProcessingException(error)));
  }

  @Override
  public boolean maybeQueueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    acceptedFrame = false;
    AppTextureFrame appTextureFrame =
        new AppTextureFrame(inputTexture.texId, inputTexture.width, inputTexture.height);
    // TODO(b/238302213): Handle timestamps restarting from 0 when applying effects to a playlist.
    //  MediaPipe will fail if the timestamps are not monotonically increasing.
    appTextureFrame.setTimestamp(presentationTimeUs);
    frameProcessor.onNewFrame(appTextureFrame);
    try {
      appTextureFrame.waitUntilReleasedWithGpuSync();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      errorListener.onFrameProcessingError(new FrameProcessingException(e));
    }
    inputListener.onInputFrameProcessed(inputTexture);
    return acceptedFrame;
  }

  @Override
  public void releaseOutputFrame(TextureInfo outputTexture) {
    checkStateNotNull(outputFrames.get(outputTexture)).release();
  }

  @Override
  public void release() {
    frameProcessor.close();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    frameProcessor.waitUntilIdle();
    outputListener.onCurrentOutputStreamEnded();
  }
}

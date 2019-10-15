/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.gvr;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.Surface;
import androidx.annotation.BinderThread;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.spherical.GlViewGroup;
import com.google.android.exoplayer2.ui.spherical.PointerRenderer;
import com.google.android.exoplayer2.ui.spherical.SceneRenderer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.vr.ndk.base.DaydreamApi;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;
import com.google.vr.sdk.controller.Orientation;
import javax.microedition.khronos.egl.EGLConfig;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Base activity for VR 360 video playback. */
public abstract class GvrPlayerActivity extends GvrActivity {

  private static final int EXIT_FROM_VR_REQUEST_CODE = 42;

  @Nullable private Player player;
  private @MonotonicNonNull ControllerManager controllerManager;
  private @MonotonicNonNull SurfaceTexture surfaceTexture;
  private @MonotonicNonNull Surface surface;
  private @MonotonicNonNull SceneRenderer sceneRenderer;
  private @MonotonicNonNull PlayerControlView playerControlView;

  @CallSuper
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setScreenAlwaysOn(true);

    GvrView gvrView = new GvrView(/* context= */ this);
    gvrView.setRenderTargetScale(getRenderTargetScale());

    // If a custom theme isn't specified, the Context's theme is used. For VR Activities, this is
    // the old Android default theme rather than a modern theme. Override this with a custom theme.
    Context theme = new ContextThemeWrapper(this, R.style.ExoVrTheme);
    GlViewGroup glViewGroup = new GlViewGroup(theme, R.layout.exo_vr_ui);

    playerControlView = Assertions.checkNotNull(glViewGroup.findViewById(R.id.controller));
    playerControlView.setShowVrButton(true);
    playerControlView.setVrButtonListener(v -> exit());

    sceneRenderer = new SceneRenderer();
    PointerRenderer pointerRenderer = new PointerRenderer();
    Renderer renderer = new Renderer(sceneRenderer, pointerRenderer, glViewGroup);

    // Attach glViewGroup to gvrView in order to properly handle UI events.
    gvrView.addView(glViewGroup);
    // Standard GvrView configuration
    gvrView.setEGLConfigChooser(
        8, 8, 8, 8, // RGBA bits.
        16, // Depth bits.
        0); // Stencil bits.
    gvrView.setRenderer(renderer);
    setContentView(gvrView);

    if (gvrView.setAsyncReprojectionEnabled(true)) {
      AndroidCompat.setSustainedPerformanceMode(/* activity= */ this, true);
    }

    // Handle the user clicking on the 'X' in the top left corner. Since this is done when the user
    // has taken the headset out of VR, it should launch the app's exit flow directly rather than
    // using Daydream's exit transition.
    gvrView.setOnCloseButtonListener(this::finish);

    controllerManager =
        new ControllerManager(/* context= */ this, new ControllerManagerEventListener());
    Controller controller = controllerManager.getController();
    ControllerEventListener controllerEventListener =
        new ControllerEventListener(controller, pointerRenderer, glViewGroup);
    controller.setEventListener(controllerEventListener);
  }

  @CallSuper
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent unused) {
    if (requestCode == EXIT_FROM_VR_REQUEST_CODE && resultCode == RESULT_OK) {
      finish();
    }
  }

  @CallSuper
  @Override
  protected void onResume() {
    super.onResume();
    player = createPlayer();
    Player.VideoComponent videoComponent = player.getVideoComponent();
    if (videoComponent != null) {
      videoComponent.setVideoFrameMetadataListener(Assertions.checkNotNull(sceneRenderer));
      videoComponent.setCameraMotionListener(sceneRenderer);
      videoComponent.setVideoSurface(surface);
    }
    Assertions.checkNotNull(playerControlView).setPlayer(player);
    Assertions.checkNotNull(controllerManager).start();
  }

  @CallSuper
  @Override
  protected void onPause() {
    Assertions.checkNotNull(controllerManager).stop();
    Assertions.checkNotNull(playerControlView).setPlayer(null);
    Assertions.checkNotNull(player).release();
    player = null;
    super.onPause();
  }

  @CallSuper
  @Override
  protected void onDestroy() {
    releaseSurface(surfaceTexture, surface);
    super.onDestroy();
  }

  /**
   * Called by {@link #onCreate(Bundle)} to get the render target scale value that will be passed to
   * {@link GvrView#setRenderTargetScale(float)}. Since videos typically have fewer pixels per
   * degree than the phone displays, the target can normally be lower than 1 to reduce the amount of
   * work required to render the scene. The default value is 0.5.
   *
   * @return The render target scale value that will be passed to {@link
   *     GvrView#setRenderTargetScale(float)}.
   */
  protected float getRenderTargetScale() {
    return 0.5f;
  }

  /** Called by {@link #onResume()} to create a player instance for this activity to use. */
  protected abstract Player createPlayer();

  /**
   * Sets the stereo mode that will be used for video content that does not specify its own mode.
   *
   * @param stereoMode The default {@link C.StereoMode}.
   */
  protected void setDefaultStereoMode(@C.StereoMode int stereoMode) {
    Assertions.checkNotNull(sceneRenderer).setDefaultStereoMode(stereoMode);
  }

  /** Tries to exit gracefully from VR using a VR transition dialog. */
  @SuppressWarnings("nullness:argument.type.incompatible")
  protected void exit() {
    DaydreamApi daydreamApi = DaydreamApi.create(this);
    if (daydreamApi != null) {
      // Use Daydream's exit transition to avoid disorienting the user. This will cause
      // onActivityResult to be called.
      daydreamApi.exitFromVr(/* activity= */ this, EXIT_FROM_VR_REQUEST_CODE, /* data= */ null);
      daydreamApi.close();
    } else {
      finish();
    }
  }

  /** Toggles PlayerControl visibility. */
  @UiThread
  protected void togglePlayerControlVisibility() {
    if (Assertions.checkNotNull(playerControlView).isVisible()) {
      playerControlView.hide();
    } else {
      playerControlView.show();
    }
  }

  private void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
    // Called on the GL thread. Post to the main thread.
    runOnUiThread(
        () -> {
          SurfaceTexture oldSurfaceTexture = this.surfaceTexture;
          Surface oldSurface = this.surface;
          this.surfaceTexture = surfaceTexture;
          this.surface = new Surface(surfaceTexture);
          if (player != null) {
            Player.VideoComponent videoComponent = player.getVideoComponent();
            if (videoComponent != null) {
              videoComponent.setVideoSurface(surface);
            }
          }
          releaseSurface(oldSurfaceTexture, oldSurface);
        });
  }

  private static void releaseSurface(
      @Nullable SurfaceTexture oldSurfaceTexture, @Nullable Surface oldSurface) {
    if (oldSurfaceTexture != null) {
      oldSurfaceTexture.release();
    }
    if (oldSurface != null) {
      oldSurface.release();
    }
  }

  private class Renderer implements GvrView.StereoRenderer {
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100;

    private final SceneRenderer sceneRenderer;
    private final PointerRenderer pointerRenderer;
    private final GlViewGroup glViewGroup;
    private final float[] viewProjectionMatrix;

    public Renderer(
        SceneRenderer sceneRenderer, PointerRenderer pointerRenderer, GlViewGroup glViewGroup) {
      this.sceneRenderer = sceneRenderer;
      this.pointerRenderer = pointerRenderer;
      this.glViewGroup = glViewGroup;
      viewProjectionMatrix = new float[16];
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {}

    @Override
    public void onDrawEye(Eye eye) {
      Matrix.multiplyMM(
          viewProjectionMatrix, 0, eye.getPerspective(Z_NEAR, Z_FAR), 0, eye.getEyeView(), 0);
      sceneRenderer.drawFrame(viewProjectionMatrix, eye.getType() == Eye.Type.RIGHT);
      if (glViewGroup.isVisible()) {
        glViewGroup.getRenderer().draw(viewProjectionMatrix);
        pointerRenderer.draw(viewProjectionMatrix);
      }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {}

    @Override
    public void onSurfaceCreated(EGLConfig config) {
      onSurfaceTextureAvailable(sceneRenderer.init());
      glViewGroup.getRenderer().init();
      pointerRenderer.init();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {}

    @Override
    public void onRendererShutdown() {
      glViewGroup.getRenderer().shutdown();
      pointerRenderer.shutdown();
      sceneRenderer.shutdown();
    }
  }

  private class ControllerEventListener extends Controller.EventListener {

    private final Controller controller;
    private final PointerRenderer pointerRenderer;
    private final GlViewGroup glViewGroup;
    private final float[] controllerOrientationMatrix;
    private boolean clickButtonDown;
    private boolean appButtonDown;

    public ControllerEventListener(
        Controller controller, PointerRenderer pointerRenderer, GlViewGroup glViewGroup) {
      this.controller = controller;
      this.pointerRenderer = pointerRenderer;
      this.glViewGroup = glViewGroup;
      controllerOrientationMatrix = new float[16];
    }

    @Override
    @BinderThread
    public void onUpdate() {
      controller.update();
      Orientation orientation = controller.orientation;
      orientation.toRotationMatrix(controllerOrientationMatrix);
      pointerRenderer.setControllerOrientation(controllerOrientationMatrix);

      if (clickButtonDown || controller.clickButtonState) {
        int action;
        if (clickButtonDown != controller.clickButtonState) {
          clickButtonDown = controller.clickButtonState;
          action = clickButtonDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
        } else {
          action = MotionEvent.ACTION_MOVE;
        }
        float[] yawPitchRoll = orientation.toYawPitchRollRadians(new float[3]);
        runOnUiThread(() -> dispatchClick(action, yawPitchRoll[0], yawPitchRoll[1]));
      } else if (!appButtonDown && controller.appButtonState) {
        runOnUiThread(GvrPlayerActivity.this::togglePlayerControlVisibility);
      }
      appButtonDown = controller.appButtonState;
    }

    private void dispatchClick(int action, float yaw, float pitch) {
      boolean clickedOnView = glViewGroup.simulateClick(action, yaw, pitch);
      if (action == MotionEvent.ACTION_DOWN && !clickedOnView) {
        togglePlayerControlVisibility();
      }
    }
  }

  private final class ControllerManagerEventListener implements ControllerManager.EventListener {

    @Override
    public void onApiStatusChanged(int status) {
      // Do nothing.
    }

    @Override
    public void onRecentered() {
      // TODO: If in cardboard mode call gvrView.recenterHeadTracker().
      runOnUiThread(() -> Assertions.checkNotNull(playerControlView).show());
    }
  }
}

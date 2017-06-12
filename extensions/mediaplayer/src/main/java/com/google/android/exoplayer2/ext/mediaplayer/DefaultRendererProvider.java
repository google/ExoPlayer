package com.google.android.exoplayer2.ext.mediaplayer;

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
 *
 * @author michalliu@tencent.com
 */

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataDecoderFactory;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;
import java.util.List;

public class DefaultRendererProvider implements RendererProvider {
    protected Context context;
    protected Handler handler;

    protected TextRenderer.Output captionListener;
    protected MetadataRenderer.Output metadataListener;
    protected AudioRendererEventListener audioRendererEventListener;
    protected VideoRendererEventListener videoRendererEventListener;

    protected DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
    protected int droppedFrameNotificationAmount = 50;
    protected int allowedJoiningTimeMs = 5000;

    public DefaultRendererProvider(Context context, Handler handler, TextRenderer.Output captionListener, MetadataRenderer.Output metadataListener,
                                   AudioRendererEventListener audioRendererEventListener, VideoRendererEventListener videoRendererEventListener) {
        this.context = context;
        this.handler = handler;
        this.captionListener = captionListener;
        this.metadataListener = metadataListener;
        this.audioRendererEventListener = audioRendererEventListener;
        this.videoRendererEventListener = videoRendererEventListener;
    }

    public void setDrmSessionManager(DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        this.drmSessionManager = drmSessionManager;
    }

    public void setDroppedFrameNotificationAmount(int droppedFrameNotificationAmount) {
        this.droppedFrameNotificationAmount = droppedFrameNotificationAmount;
    }

    public void setAllowedVideoJoiningTimeMs(int videoJoiningTimeMs) {
        this.allowedJoiningTimeMs = videoJoiningTimeMs;
    }

    @Override
    public List<Renderer> generate() {
        List<Renderer> renderers = new ArrayList<>();

        renderers.addAll(buildAudioRenderers());
        renderers.addAll(buildVideoRenderers());
        renderers.addAll(buildCaptionRenderers());
        renderers.addAll(buildMetadataRenderers());

        return renderers;
    }

    protected List<Renderer> buildAudioRenderers() {
        List<Renderer> renderers = new ArrayList<>();

        renderers.add(new MediaCodecAudioRenderer(MediaCodecSelector.DEFAULT, drmSessionManager, true, handler, audioRendererEventListener, AudioCapabilities.getCapabilities(context)));

        return renderers;
    }

    protected List<Renderer> buildVideoRenderers() {
        List<Renderer> renderers = new ArrayList<>();

        renderers.add(new MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT, allowedJoiningTimeMs, drmSessionManager, false, handler, videoRendererEventListener, droppedFrameNotificationAmount));

        return renderers;
    }

    protected List<Renderer> buildCaptionRenderers() {
        List<Renderer> renderers = new ArrayList<>();

        renderers.add(new TextRenderer(captionListener, handler.getLooper()));

        return renderers;
    }

    protected List<Renderer> buildMetadataRenderers() {
        List<Renderer> renderers = new ArrayList<>();

        renderers.add(new MetadataRenderer(metadataListener, handler.getLooper(), MetadataDecoderFactory.DEFAULT));

        return renderers;
    }
}
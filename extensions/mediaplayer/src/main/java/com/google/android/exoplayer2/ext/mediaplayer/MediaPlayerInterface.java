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
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface MediaPlayerInterface {
    int MEDIA_INFO_VIDEO_RENDERING_START = 3; // MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
    int MEDIA_INFO_BUFFERING_START = 701;
    int MEDIA_INFO_BUFFERING_END = 702;
    int MEDIA_INFO_VIDEO_ROTATION_CHANGED = 10001;

    int EXO_MEDIA_ERROR_WHAT_IO = -4000;
    int EXO_MEDIA_ERROR_WHAT_EXTRACTOR = -4001; // UnrecognizedInputFormatException
    int EXO_MEIDA_ERROR_ILLEGAL_STATE = -4002; // IllegalStateException
    int EXO_MEIDA_ERROR_MEDIACODEC_DECODER_INIT = -4003; //  MediaCodecRenderer.DecoderInitializationException
    int EXO_MEDIA_ERROR_WHAT_UNKNOWN = -4999;
    int EXO_MEDIA_ERROR_EXTRA_UNKNOWN = -1;
    int EXO_MEDIA_ERROR_EXTRA_NETWORK = -2;
    int EXO_MEDIA_ERROR_EXTRA_CONN = -3;
    int EXO_MEDIA_ERROR_RESPONSE_403 = -10;
    int EXO_MEDIA_ERROR_RESPONSE_404 = -11;
    int EXO_MEDIA_ERROR_RESPONSE_500 = -12;
    int EXO_MEDIA_ERROR_RESPONSE_502 = -13;
    int EXO_MEDIA_ERROR_RESPONSE_OTHER = -30;

    void setDataSource(Context context, Uri uri) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    void setDataSource(Context context, List<String> pathList) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    void setDataSource(String path) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    void setDataSource(List<String> path) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    void prepareAsync() throws IllegalStateException;

    void start() throws IllegalStateException;

    void stop() throws IllegalStateException;

    void pause() throws IllegalStateException;

    void seekTo(int msec) throws IllegalStateException;

    void reset();

    void release();

    long getDuration();

    long getCurrentPosition();

    int getVideoWidth();

    int getVideoHeight();

    boolean isPlaying();

    void setVolume(float leftVolume, float rightVolume);

    void setRate(float rate);

    boolean isLooping();

    void setLooping(boolean looping);

    void setDisplay(SurfaceHolder sh);

    void setSurface(Surface surface);

    void setScreenOnWhilePlaying(boolean screenOn);

    @Deprecated
    void setWakeMode(Context context, int mode);

    void setNextMediaPlayer(MediaPlayerInterface nextMediaPlayer) throws UnsupportedOperationException;

    void setOnPreparedListener(OnPreparedListener listener);

    void setOnCompletionListener(OnCompletionListener listener);

    void setOnBufferingUpdateListener(
            OnBufferingUpdateListener listener);

    void setOnSeekCompleteListener(
            OnSeekCompleteListener listener);

    void setOnVideoSizeChangedListener(
            OnVideoSizeChangedListener listener);

    void setOnErrorListener(OnErrorListener listener);

    void setOnInfoListener(OnInfoListener listener);

    interface OnPreparedListener {
        void onPrepared(MediaPlayerInterface mp);
    }

    interface OnCompletionListener {
        void onCompletion(MediaPlayerInterface mp);
    }

    interface OnBufferingUpdateListener {
        void onBufferingUpdate(MediaPlayerInterface mp, int percent);
    }

    interface OnSeekCompleteListener {
        void onSeekComplete(MediaPlayerInterface mp);
    }

    interface OnVideoSizeChangedListener {
        void onVideoSizeChanged(MediaPlayerInterface mp, int width, int height);
    }

    interface OnErrorListener {
        boolean onError(MediaPlayerInterface mp, int what, int extra);
    }

    interface OnInfoListener {
        boolean onInfo(MediaPlayerInterface mp, int what, int extra);
    }
}

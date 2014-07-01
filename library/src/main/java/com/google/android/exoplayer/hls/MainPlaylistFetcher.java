package com.google.android.exoplayer.hls;
/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.google.android.exoplayer.ParserException;

import java.io.IOException;
import java.io.InputStream;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.ManifestFetcher;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

/**
 * A concrete implementation of {@link ManifestFetcher} for loading SmoothStreaming
 * manifests.
 * <p>
 * This class is provided for convenience, however it is expected that most applications will
 * contain their own mechanisms for making asynchronous network requests and parsing the response.
 * In such cases it is recommended that application developers use their existing solution rather
 * than this one.
 */
public final class MainPlaylistFetcher extends ManifestFetcher<MainPlaylist> {

    private String url;

    /**
     * @param callback The callback to provide with the parsed manifest (or error).
     */
    public MainPlaylistFetcher(String url, ManifestCallback<MainPlaylist> callback) {
        super(callback);
        this.url = url;
    }

    /**
     * @param callback The callback to provide with the parsed manifest (or error).
     * @param timeoutMillis The timeout in milliseconds for the connection used to load the data.
     */
    public MainPlaylistFetcher(ManifestCallback<MainPlaylist> callback,
                                          int timeoutMillis) {
        super(callback, timeoutMillis);
    }

    @Override
    protected MainPlaylist parse(InputStream stream, String inputEncoding,
                                            String contentId) throws IOException, ParserException {
        return MainPlaylist.parse(url, stream, inputEncoding);
    }

}

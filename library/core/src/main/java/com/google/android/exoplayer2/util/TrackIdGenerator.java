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
package com.google.android.exoplayer2.util;

/**
 * Generates track ids for initializing payload readers
 */
public final class TrackIdGenerator {

    private static final int ID_UNSET = Integer.MIN_VALUE;

    private final String formatIdPrefix;
    private final int firstTrackId;
    private final int trackIdIncrement;
    private int trackId;
    private String formatId;

    public TrackIdGenerator(int firstTrackId, int trackIdIncrement) {
        this(ID_UNSET, firstTrackId, trackIdIncrement);
    }

    public TrackIdGenerator(int programNumber, int firstTrackId, int trackIdIncrement) {
        this.formatIdPrefix = programNumber != ID_UNSET ? programNumber + "/" : "";
        this.firstTrackId = firstTrackId;
        this.trackIdIncrement = trackIdIncrement;
        trackId = ID_UNSET;
    }

    /**
     * Generates a new set of track and track format ids. Must be called before {@code get*}
     * methods.
     */
    public void generateNewId() {
        trackId = trackId == ID_UNSET ? firstTrackId : trackId + trackIdIncrement;
        formatId = formatIdPrefix + trackId;
    }

    /**
     * Returns the last generated track id. Must be called after the first {@link #generateNewId()}
     * call.
     *
     * @return The last generated track id.
     */
    public int getTrackId() {
        maybeThrowUninitializedError();
        return trackId;
    }

    /**
     * Returns the last generated format id, with the format {@code "programNumber/trackId"}. If no
     * {@code programNumber} was provided, the {@code trackId} alone is used as format id. Must be
     * called after the first {@link #generateNewId()} call.
     *
     * @return The last generated format id, with the format {@code "programNumber/trackId"}. If no
     *     {@code programNumber} was provided, the {@code trackId} alone is used as
     *     format id.
     */
    public String getFormatId() {
        maybeThrowUninitializedError();
        return formatId;
    }

    private void maybeThrowUninitializedError() {
        if (trackId == ID_UNSET) {
            throw new IllegalStateException("generateNewId() must be called before retrieving ids.");
        }
    }

}
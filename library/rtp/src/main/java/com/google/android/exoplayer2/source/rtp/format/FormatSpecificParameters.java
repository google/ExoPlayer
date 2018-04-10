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
package com.google.android.exoplayer2.source.rtp.format;

import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FormatSpecificParameters {
    private final Map<String, FormatSpecificParameter> parameters;
    private Map<String, FormatSpecificParameter> headersSnapshot;

    public FormatSpecificParameters() {
        parameters = new LinkedHashMap<>();
    }

    /**
     * Added a specified {@code FormatSpecificParameter} parameter. If a parameter previously existed,
     * the old value is replaced by the specified value.
     *
     * @param parameter The {@code FormatSpecificParameter} parameter.
     */
    public synchronized void add(FormatSpecificParameter parameter) {
        headersSnapshot = null;
        parameters.put(parameter.name(), parameter);
    }

    /**
     * Gets the {@code FormatSpecificParameter} given by parameter name.
     *
     * @return The {@code FormatSpecificParameter} given by parameter name.
     */
    @Nullable
    public String value(String parameter) {
        if (parameters.containsKey(parameter)) {
            return parameters.get(parameter).value();
        }

        return null;
    }

    public synchronized boolean contains(String parameterName) {
        return parameters.containsKey(parameterName);
    }

    public synchronized boolean contains(FormatSpecificParameter parameter) {
        return parameters.containsKey(parameter.name());
    }

    public synchronized void clear() {
        parameters.clear();
    }

    /**
     * Gets the number of media codec parameters.
     *
     * @return The number of the media codec parameters.
     */
    public int size() {
        return parameters.size();
    }

    /**
     * Gets a snapshot of the {@code FormatSpecificParameter} parameters.
     *
     * @return A snapshot of the {@code FormatSpecificParameter} parameters.
     */
    public synchronized Map<String, FormatSpecificParameter> getSnapshot() {
        if (headersSnapshot == null) {
            headersSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
        return headersSnapshot;
    }
}

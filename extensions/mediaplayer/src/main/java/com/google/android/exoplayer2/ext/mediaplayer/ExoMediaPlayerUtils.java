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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ExoMediaPlayerUtils {

    public static String join(Iterator<String> iterator, String delimiter) {
        String ret = "";
        if (iterator != null) {
            while (iterator.hasNext()) {
                ret += iterator.next();
                if (iterator.hasNext()) {
                    ret += delimiter;
                }
            }
        }
        return ret;
    }

    public static String join(List<String> elements, String delimiter) {
        if (elements != null) {
            return join(elements.iterator(), delimiter);
        }
        return "";
    }

    public static String join(List<String> elements) {
        return join(elements, "|");
    }

    public static String getPrintableStackTrace(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnected();
    }

    public static String getLogcatContent() {
        return getLogcatContent(0, null, 10);
    }


    public static String getLogcatContent(int maxSize, String tag, int contextSeconds) {
        long endTimeRange = System.currentTimeMillis();
        long startTimeRange = endTimeRange - contextSeconds * 1000;
        int year = Calendar.getInstance().get(Calendar.YEAR);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS", Locale.getDefault());
        boolean flagStart = false;
        String threadtime="^\\d\\d-\\d\\d\\s\\d\\d:.*";

        String[] cmd;
        if (tag == null) {
            cmd = new String[] {"logcat", "-d", "-v", "threadtime"};
        } else {
            cmd = new String[] {"logcat", "-d", "-v", "threadtime", "-s", tag};
        }

        Process process = null;
        StringBuilder sb = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec(cmd);
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.matches(threadtime)) {
                    String s = line.substring(0, 18);
                    Date d = f.parse(year + "-" + s.substring(0, 18));
                    long t = d.getTime();
                    if (t > endTimeRange) {
                        break;
                    }
                    if (t > startTimeRange) {
                        if (!flagStart) {
                            sb.append(">>>>>> start logcat log <<<<<<\n");
                            flagStart = true;
                        }
                        sb.append(line).append("\n");
                    }
                    if (maxSize > 0 && sb.length() > maxSize) {
                        sb.delete(0, sb.length() - maxSize);
                    }
                }
            }
            sb.append(">>>>>> end logcat log <<<<<<");
            return sb.toString();
        } catch (Throwable thr) {
            return sb.append("\n[error:" + thr.toString() + "]").toString();
        } finally {
            if (process != null) {
                try {
                    process.getOutputStream().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    process.getInputStream().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    process.getErrorStream().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

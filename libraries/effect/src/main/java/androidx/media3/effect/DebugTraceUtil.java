/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** A debugging tracing utility. */
@UnstableApi
public final class DebugTraceUtil {
  /** Events logged by {@link #logEvent}. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    EVENT_VIDEO_INPUT_FORMAT,
    EVENT_DECODER_DECODED_FRAME,
    EVENT_VFP_REGISTER_NEW_INPUT_STREAM,
    EVENT_VFP_SURFACE_TEXTURE_INPUT,
    EVENT_VFP_QUEUE_FRAME,
    EVENT_VFP_QUEUE_BITMAP,
    EVENT_VFP_QUEUE_TEXTURE,
    EVENT_VFP_RENDERED_TO_OUTPUT_SURFACE,
    EVENT_VFP_OUTPUT_TEXTURE_RENDERED,
    EVENT_VFP_FINISH_PROCESSING_INPUT_STREAM,
    EVENT_COMPOSITOR_OUTPUT_TEXTURE_RENDERED,
    EVENT_ENCODER_ENCODED_FRAME,
    EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO,
    EVENT_MUXER_WRITE_SAMPLE_VIDEO,
    EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO,
    EVENT_MUXER_WRITE_SAMPLE_AUDIO,
    EVENT_DECODER_RECEIVE_EOS,
    EVENT_DECODER_SIGNAL_EOS,
    EVENT_VFP_RECEIVE_END_OF_INPUT,
    EVENT_EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOS,
    EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS,
    EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS,
    EVENT_VFP_SIGNAL_ENDED,
    EVENT_ENCODER_RECEIVE_EOS,
    EVENT_MUXER_TRACK_ENDED_AUDIO,
    EVENT_MUXER_TRACK_ENDED_VIDEO
  })
  @Documented
  @Target(TYPE_USE)
  public @interface DebugTraceEvent {}

  public static final String EVENT_VIDEO_INPUT_FORMAT = "VideoInputFormat";
  public static final String EVENT_DECODER_DECODED_FRAME = "Decoder-DecodedFrame";
  public static final String EVENT_VFP_REGISTER_NEW_INPUT_STREAM = "VFP-RegisterNewInputStream";
  public static final String EVENT_VFP_SURFACE_TEXTURE_INPUT = "VFP-SurfaceTextureInput";
  public static final String EVENT_VFP_QUEUE_FRAME = "VFP-QueueFrame";
  public static final String EVENT_VFP_QUEUE_BITMAP = "VFP-QueueBitmap";
  public static final String EVENT_VFP_QUEUE_TEXTURE = "VFP-QueueTexture";
  public static final String EVENT_VFP_RENDERED_TO_OUTPUT_SURFACE = "VFP-RenderedToOutputSurface";
  public static final String EVENT_VFP_OUTPUT_TEXTURE_RENDERED = "VFP-OutputTextureRendered";
  public static final String EVENT_VFP_FINISH_PROCESSING_INPUT_STREAM = "VFP-FinishOneInputStream";
  public static final String EVENT_COMPOSITOR_OUTPUT_TEXTURE_RENDERED =
      "COMP-OutputTextureRendered";
  public static final String EVENT_ENCODER_ENCODED_FRAME = "Encoder-EncodedFrame";
  public static final String EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO = "Muxer-CanWriteSample_Video";
  public static final String EVENT_MUXER_WRITE_SAMPLE_VIDEO = "Muxer-WriteSample_Video";
  public static final String EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO = "Muxer-CanWriteSample_Audio";
  public static final String EVENT_MUXER_WRITE_SAMPLE_AUDIO = "Muxer-WriteSample_Audio";
  public static final String EVENT_DECODER_RECEIVE_EOS = "Decoder-ReceiveEOS";
  public static final String EVENT_DECODER_SIGNAL_EOS = "Decoder-SignalEOS";
  public static final String EVENT_VFP_RECEIVE_END_OF_INPUT = "VFP-ReceiveEndOfAllInput";
  public static final String EVENT_EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOS =
      "ExternalTextureManager-SignalEOS";
  public static final String EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS =
      "BitmapTextureManager-SignalEOS";
  public static final String EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS =
      "TexIdTextureManager-SignalEOS";
  public static final String EVENT_VFP_SIGNAL_ENDED = "VFP-SignalEnded";
  public static final String EVENT_ENCODER_RECEIVE_EOS = "Encoder-ReceiveEOS";
  public static final String EVENT_MUXER_TRACK_ENDED_AUDIO = "Muxer-TrackEnded_Audio";
  public static final String EVENT_MUXER_TRACK_ENDED_VIDEO = "Muxer-TrackEnded_Video";

  /** List ordered based on expected event ordering. */
  private static final ImmutableList<String> EVENT_TYPES =
      ImmutableList.of(
          EVENT_VIDEO_INPUT_FORMAT,
          EVENT_DECODER_DECODED_FRAME,
          EVENT_VFP_REGISTER_NEW_INPUT_STREAM,
          EVENT_VFP_SURFACE_TEXTURE_INPUT,
          EVENT_VFP_QUEUE_FRAME,
          EVENT_VFP_QUEUE_BITMAP,
          EVENT_VFP_QUEUE_TEXTURE,
          EVENT_VFP_RENDERED_TO_OUTPUT_SURFACE,
          EVENT_VFP_OUTPUT_TEXTURE_RENDERED,
          EVENT_VFP_FINISH_PROCESSING_INPUT_STREAM,
          EVENT_COMPOSITOR_OUTPUT_TEXTURE_RENDERED,
          EVENT_ENCODER_ENCODED_FRAME,
          EVENT_MUXER_CAN_WRITE_SAMPLE_VIDEO,
          EVENT_MUXER_WRITE_SAMPLE_VIDEO,
          EVENT_MUXER_CAN_WRITE_SAMPLE_AUDIO,
          EVENT_MUXER_WRITE_SAMPLE_AUDIO,
          EVENT_DECODER_RECEIVE_EOS,
          EVENT_DECODER_SIGNAL_EOS,
          EVENT_VFP_RECEIVE_END_OF_INPUT,
          EVENT_EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOS,
          EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS,
          EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS,
          EVENT_VFP_SIGNAL_ENDED,
          EVENT_ENCODER_RECEIVE_EOS,
          EVENT_MUXER_TRACK_ENDED_AUDIO,
          EVENT_MUXER_TRACK_ENDED_VIDEO);

  private static final int MAX_FIRST_LAST_LOGS = 10;

  @GuardedBy("DebugTraceUtil.class")
  private static final Map<String, EventLogger> events = new LinkedHashMap<>();

  @GuardedBy("DebugTraceUtil.class")
  private static long startTimeMs = SystemClock.DEFAULT.elapsedRealtime();

  public static synchronized void reset() {
    events.clear();
    startTimeMs = SystemClock.DEFAULT.elapsedRealtime();
  }

  /**
   * Logs a new event.
   *
   * @param eventName The {@linkplain DebugTraceEvent event name} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   * @param extra Optional extra info about the event being logged.
   */
  public static synchronized void logEvent(
      @DebugTraceEvent String eventName, long presentationTimeUs, @Nullable String extra) {
    long eventTimeMs = SystemClock.DEFAULT.elapsedRealtime() - startTimeMs;
    if (!events.containsKey(eventName)) {
      events.put(eventName, new EventLogger());
    }
    EventLogger logger = events.get(eventName);
    logger.addLog(new EventLog(presentationTimeUs, eventTimeMs, extra));
  }

  /**
   * Logs a new event.
   *
   * @param eventName The {@linkplain DebugTraceEvent event name} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   */
  public static synchronized void logEvent(
      @DebugTraceEvent String eventName, long presentationTimeUs) {
    logEvent(eventName, presentationTimeUs, /* extra= */ null);
  }

  /**
   * Generate a summary of the traced events, containing the total number of times an event happened
   * and the detailed log on the first and last {@link #MAX_FIRST_LAST_LOGS} times.
   */
  public static synchronized String generateTraceSummary() {
    StringBuilder stringBuilder = new StringBuilder().append('{');
    for (int i = 0; i < EVENT_TYPES.size(); i++) {
      String eventType = EVENT_TYPES.get(i);
      if (!events.containsKey(eventType)) {
        stringBuilder.append(Util.formatInvariant("\"%s\": \"No events logged\",", eventType));
        continue;
      }
      stringBuilder
          .append(Util.formatInvariant("\"%s\":{", eventType))
          .append(checkNotNull(events.get(eventType)))
          .append("},");
    }
    stringBuilder.append('}');
    return stringBuilder.toString();
  }

  /** Dumps all the stored events to a tsv file. */
  public static synchronized void dumpTsv(Writer writer) throws IOException {
    writer.write("event\ttimestamp\tpresentation\textra\n");
    for (Map.Entry<String, EventLogger> entry : events.entrySet()) {
      ImmutableList<EventLog> eventLogs = entry.getValue().getLogs();
      for (int i = 0; i < eventLogs.size(); i++) {
        EventLog eventLog = eventLogs.get(i);
        writer.write(
            Util.formatInvariant(
                "%s\t%d\t%s\t%s\n",
                entry.getKey(),
                eventLog.eventTimeMs,
                presentationTimeToString(eventLog.presentationTimeUs),
                Strings.nullToEmpty(eventLog.extra)));
      }
    }
  }

  private static String presentationTimeToString(long presentationTimeUs) {
    if (presentationTimeUs == C.TIME_UNSET) {
      return "UNSET";
    } else if (presentationTimeUs == C.TIME_END_OF_SOURCE) {
      return "EOS";
    } else {
      return String.valueOf(presentationTimeUs);
    }
  }

  private static final class EventLog {
    public final long presentationTimeUs;
    public final long eventTimeMs;
    @Nullable public final String extra;

    private EventLog(long presentationTimeUs, long eventTimeMs, @Nullable String extra) {
      this.presentationTimeUs = presentationTimeUs;
      this.eventTimeMs = eventTimeMs;
      this.extra = extra;
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(
          Util.formatInvariant(
              "\"%s@%d", presentationTimeToString(presentationTimeUs), eventTimeMs));
      if (extra != null) {
        stringBuilder.append(Util.formatInvariant("(%s)", extra));
      }
      return stringBuilder.append('"').toString();
    }
  }

  private static final class EventLogger {
    private final List<EventLog> firstLogs;
    private final Queue<EventLog> lastLogs;
    private int totalCount;

    public EventLogger() {
      firstLogs = new ArrayList<>(MAX_FIRST_LAST_LOGS);
      lastLogs = new ArrayDeque<>(MAX_FIRST_LAST_LOGS);
      totalCount = 0;
    }

    public void addLog(EventLog log) {
      if (firstLogs.size() < MAX_FIRST_LAST_LOGS) {
        firstLogs.add(log);
      } else {
        lastLogs.add(log);
        if (lastLogs.size() > MAX_FIRST_LAST_LOGS) {
          lastLogs.remove();
        }
      }
      totalCount++;
    }

    public ImmutableList<EventLog> getLogs() {
      return new ImmutableList.Builder<EventLog>().addAll(firstLogs).addAll(lastLogs).build();
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder =
          new StringBuilder().append("\"Count\": ").append(totalCount).append(", \"first\":[");
      for (int i = 0; i < firstLogs.size(); i++) {
        stringBuilder.append(firstLogs.get(i)).append(",");
      }
      stringBuilder.append("],");
      if (lastLogs.isEmpty()) {
        return stringBuilder.toString();
      }
      ImmutableList<EventLog> lastLogsList = ImmutableList.copyOf(lastLogs);
      stringBuilder.append("\"last\":[");
      for (int i = 0; i < lastLogsList.size(); i++) {
        stringBuilder.append(lastLogsList.get(i)).append(",");
      }
      stringBuilder.append(']');
      return stringBuilder.toString();
    }
  }
}

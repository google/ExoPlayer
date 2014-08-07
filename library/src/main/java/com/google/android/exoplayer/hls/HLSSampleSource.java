package com.google.android.exoplayer.hls;

import android.media.MediaExtractor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.upstream.AESDataSource;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by martin on 31/07/14.
 */
public class HLSSampleSource implements SampleSource {
  private static final String TAG = "HLSSampleSource";

  private String url;
  private MainPlaylist mainPlaylist;
  private final ArrayList<HLSTrack> trackList = new ArrayList<HLSTrack>();
  boolean prepared;

  private int initialBps;
  private int estimatedBps;
  private int forcedBps;
  private double bpsFraction;
  private int lowThresholdMsec;
  private int hightThresholdMsec;
  private MainPlaylist.Entry currentEntry;

  private ArrayList<LinkedList<Object>> list;
  int track2type[] = new int[2];

  private int maxBufferSize;
  private int bufferSize;
  private int bufferMsec;

  private final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
  private AtomicLong bufferedPositionUs;

  private int sequence;
  private ChunkTask chunkTask;
  private String userAgent;
  private long ptsOffset;

  private boolean endOfStream;
  private Handler eventHandler;
  private EventListener eventListener;
  private int videoStreamType;
  private int audioStreamType;
  private boolean gotStreamTypes;
  private int maxBps;

  public static class Quality {
      public int width;
      public int height;
      public int bps;
  }

  public interface EventListener {
    void onQualitiesParsed(Quality qualities[]);
    void onChunkStart(Quality quality);
  }

  static class ChunkSentinel {
    MediaFormat mediaFormat;
    MainPlaylist.Entry entry;
  };

  static class HLSTrack {
    public int type;
    public TrackInfo trackInfo;
    public boolean discontinuity;
    public MainPlaylist.Entry readEntry;
    public long wrapOffset;
    public long lastPts;
  }

  public HLSSampleSource(String url, Handler eventHandler, EventListener listener) {
    this.url = url;
    maxBufferSize = 30 * 1024 * 1024;
    bpsFraction = 0.75;
    initialBps = 0;
    forcedBps = 0;
    lowThresholdMsec = 10000;
    list = new ArrayList<LinkedList<Object>>();
    list.add(new LinkedList<Object>());
    list.add(new LinkedList<Object>());
    userAgent = "HLS Player";
    bufferedPositionUs = new AtomicLong();
    this.eventHandler = eventHandler;
    this.eventListener = listener;
  }

  public HLSSampleSource(String url) {
      this(url, null, null);
  }

  public void setForcedBps(int bps) {

    this.forcedBps = bps;
  }

  public void setMaxBps(int bps) {

    this.maxBps = bps;
  }

  public void setInitialBps(int bps) {

    this.initialBps = bps;
  }

  private MainPlaylist.Entry getEntryBelow(int bps) {
    for (int i = mainPlaylist.entries.size() - 1; i >= 0; i--) {
      MainPlaylist.Entry entry = mainPlaylist.entries.get(i);
      if (entry.bps < bps) {
        return entry;
      }
    }

    return mainPlaylist.entries.get(0);
  }

  private MainPlaylist.Entry evaluateNextEntry() {
    if (forcedBps >= 0) {
      // manually set
      return getEntryBelow(forcedBps);
    }
    if (estimatedBps <= 0) {
      // first time
      return getEntryBelow(initialBps);
    }
    MainPlaylist.Entry idealEntry = getEntryBelow((int)((double)estimatedBps * bpsFraction));

    if (idealEntry.bps > currentEntry.bps) {
      if (bufferMsec < hightThresholdMsec) {
        // The ideal format is a higher quality, but we have insufficient buffer to
        // safely switch up. Defer switching up for now.
        idealEntry = currentEntry;
      }
    } else {
      // The ideal format is a lower quality, but we have sufficient buffer to defer switching
      // down for now.
      if (bufferMsec > lowThresholdMsec) {
        idealEntry = currentEntry;
      }
    }

    if(maxBps >= 0 && idealEntry.bps > maxBps) {
      idealEntry = getEntryBelow(maxBps);
    }

    return idealEntry;
  }

  @Override
  public boolean prepare() throws IOException {
    int i = 0;
    if (prepared)
      return true;
    try {
      mainPlaylist = MainPlaylist.parse(this.url);
    } catch (Exception e) {
      Log.d(TAG, "cannot parse main playlist");
      e.printStackTrace();

    }
    if (mainPlaylist == null || mainPlaylist.entries.size() == 0) {
      // no main playlist: we fake one
      mainPlaylist = MainPlaylist.createFakeMainPlaylist(this.url);
    }

    // compute durationSec
    currentEntry = evaluateNextEntry();
    VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
    long durationUs = (long)variantPlaylist.duration * 1000 * 1000;

    sequence = variantPlaylist.mediaSequence;

    prepared = true;

    // start downloading, we need to get some information from the first chunks
    continueBuffering(0);

    if (eventListener != null && eventHandler != null) {
        final Quality qualities[] = new Quality[mainPlaylist.entries.size()];
        i = 0;
        for (MainPlaylist.Entry e : mainPlaylist.entries) {
            qualities[i] = new Quality();
            qualities[i].width = e.width;
            qualities[i].height = e.height;
            qualities[i].bps = e.bps;
            i++;
        }
        eventHandler.post(new Runnable() {
            @Override
            public void run() {
                eventListener.onQualitiesParsed(qualities);
            }
        });
    }

    boolean found = false;

    // see if there is a pts offset
    while (true) {
      boolean empty = true;
      synchronized (list) {
        for (LinkedList<Object> l : list) {
          for (Object o : l) {
            if (o instanceof HLSExtractor.Sample) {
              HLSExtractor.Sample sample =  (HLSExtractor.Sample)o;
              if (found == false) {
                ptsOffset = sample.pts;
                Log.d(TAG, "found ptsOffset=" + ptsOffset);
                found = true;
              }
            }
          }
          if (!l.isEmpty()) {
            empty = false;
          }
        }
      }

      if (empty && chunkTask == null) {
        break;
      }
      if (found) {
        break;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (!found) {
      return false;
    }

    if (audioStreamType != HLSExtractor.STREAM_TYPE_NONE) {
      HLSTrack track = new HLSTrack();
      track.type = HLSExtractor.TYPE_AUDIO;
      String mime = (audioStreamType == HLSExtractor.STREAM_TYPE_AAC_ADTS) ? MimeTypes.AUDIO_AAC : MimeTypes.AUDIO_MPEG;
      track.trackInfo = new TrackInfo(mime, durationUs);
      trackList.add(track);
      track2type[i++] = HLSExtractor.TYPE_AUDIO;
    }
    if (videoStreamType != HLSExtractor.STREAM_TYPE_NONE) {
      HLSTrack track = new HLSTrack();
      track.type = HLSExtractor.TYPE_VIDEO;
      track.trackInfo = new TrackInfo(MimeTypes.VIDEO_H264, durationUs);
      trackList.add(track);
      track2type[i++] = HLSExtractor.TYPE_VIDEO;
    }

    return true;
  }

  @Override
  public int getTrackCount() {
    return trackList.size();
  }

  @Override
  public TrackInfo getTrackInfo(int track) {
    return trackList.get(track).trackInfo;
  }

  @Override
  public void enable(int track, long timeUs) {

  }

  @Override
  public void disable(int track) {

  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (chunkTask != null) {
      // we are already loading something
      return;
    }

    if (bufferSize >= maxBufferSize) {
      // we don't want to waste too much memory
      return;
    }

    if (endOfStream) {
      return;
    }

    estimatedBps = (int)bandwidthMeter.getEstimate() * 8;
    bufferMsec = (int)(bufferedPositionUs.get() - playbackPositionUs);

    currentEntry = evaluateNextEntry();
    if (eventListener != null) {
      Quality quality = new Quality();
      quality.width = currentEntry.width;
      quality.height = currentEntry.height;
      quality.bps = currentEntry.height;
      eventListener.onChunkStart(quality);
    }
    VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
    VariantPlaylist.Entry variantEntry = variantPlaylist.entries.get(sequence - variantPlaylist.mediaSequence);

    Chunk chunk = new Chunk();
    chunk.variantEntry = variantEntry;
    chunk.mainEntry = currentEntry;
    chunk.variantPlaylist = variantPlaylist;
    chunk.videoMediaFormat = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
                    currentEntry.width, currentEntry.height, null);
    chunkTask = new ChunkTask(chunk);
    chunkTask.execute();
  }

  @Override
  public int readData(int track, long playbackPositionUs, FormatHolder formatHolder, SampleHolder sampleHolder, boolean onlyReadDiscontinuity) throws IOException {
    if (onlyReadDiscontinuity) {
      if (trackList.get(track).discontinuity) {
        trackList.get(track).discontinuity = false;
        return DISCONTINUITY_READ;
      } else {
        return NOTHING_READ;
      }
    }

    synchronized(list) {
      Object o;
      HLSTrack hlsTrack = trackList.get(track);
      try {
        o = list.get(track2type[track]).removeFirst();
        if (o instanceof ChunkSentinel) {
          ChunkSentinel sentinel = (ChunkSentinel)o;
          if (sentinel.entry != trackList.get(track).readEntry) {
            formatHolder.format = sentinel.mediaFormat;
            hlsTrack.readEntry = sentinel.entry;
            return FORMAT_READ;
          } else {
            return NOTHING_READ;
          }
        } else {
          HLSExtractor.Sample sample = (HLSExtractor.Sample)o;
          sample.data.limit(sample.data.position());
          sample.data.position(0);
          sampleHolder.data.put(sample.data);
          sampleHolder.size = sample.data.limit();
          if (sample.pts < hlsTrack.lastPts && (hlsTrack.lastPts - sample.pts) > Math.pow(2,31)) {
            Log.d(TAG, "wrap detected");
            hlsTrack.wrapOffset += Math.pow(2,32);
          }
          hlsTrack.lastPts = sample.pts;
          sampleHolder.timeUs = (sample.pts - ptsOffset + hlsTrack.wrapOffset) * 1000 / 45;
          sampleHolder.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
          bufferSize -= sampleHolder.size;
          //Log.d(TAG, (sample.type == HLSExtractor.TYPE_AUDIO ? "AUDIO" : "VIDEO") + " timeUS=" + (sampleHolder.pts/1000));
          return SAMPLE_READ;
        }
      } catch (NoSuchElementException e) {
        if (endOfStream == true) {
          return END_OF_STREAM;
        } else {
          return NOTHING_READ;
        }
      }
    }
  }

  @Override
  public void seekToUs(long timeUs) {
    if (chunkTask != null) {
      chunkTask.abort();
    }
    synchronized(list) {
      for (LinkedList<Object> l : list) {
        l.clear();
      }
      bufferSize = 0;
      bufferMsec = 0;
      bufferedPositionUs.set(timeUs);
      // XXX: try to find the appropriate wrapOffset
      for (HLSTrack t : trackList) {
        t.lastPts = 0;
        t.wrapOffset = 0;
      }
    }

    VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
    long acc = 0;
    sequence = variantPlaylist.mediaSequence;
    for (VariantPlaylist.Entry e : variantPlaylist.entries) {
      acc += (long)(e.extinf * 1000000);
      if (acc > timeUs) {
        break;
      }
      sequence++;
    }

    Log.d(TAG, "seekTo " + timeUs/1000 + " => " + sequence);

    for (HLSTrack t : trackList) {
      t.discontinuity = true;
    }

    endOfStream = false;
  }

  @Override
  public long getBufferedPositionUs() {
    return bufferedPositionUs.get();
  }

  @Override
  public void release() {
    if (chunkTask != null) {
      chunkTask.abort();
    }
    for (LinkedList<Object> l : list) {
      l.clear();
    }
    list.clear();
  }

  static class Chunk {
    VariantPlaylist variantPlaylist;
    VariantPlaylist.Entry variantEntry;
    MediaFormat videoMediaFormat;
    MainPlaylist.Entry mainEntry;
  }

  class ChunkTask extends AsyncTask<Void, Void, Void>  {
    private final Chunk chunk;
    private Exception exception;
    private boolean aborted;

    public ChunkTask(Chunk chunk) {
      this.chunk = chunk;
    }

    @Override
    protected Void doInBackground(Void... params) {
      HLSSampleSource source = HLSSampleSource.this;
      String variantPlaylistUrl = chunk.variantPlaylist.url;
      VariantPlaylist.Entry variantEntry = chunk.variantEntry;
      String chunkUrl = Util.makeAbsoluteUrl(variantPlaylistUrl, variantEntry.url);
      Log.d(TAG, "opening " + chunkUrl);
      Uri uri = null;
      MediaFormat audioMediaFormat = null;

      if (variantEntry.keyEntry != null) {
        String dataUrl = null;
        String keyUrl = null;
        try {
          dataUrl = URLEncoder.encode(chunkUrl, "utf-8");
          keyUrl = URLEncoder.encode(variantEntry.keyEntry.uri, "utf-8");
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }

        String iv = variantEntry.keyEntry.IV;
        if (iv == null) {
          // XXX: is this nextChunkIndex or nextChunkIndex + 1 ?
          iv = Integer.toHexString(sequence);
        }
        uri = Uri.parse("aes://dummy?dataUrl=" + dataUrl + "&keyUrl=" + keyUrl + "&iv=" + iv);
      } else {
        uri = Uri.parse(chunkUrl);
      }

      synchronized (source.list) {
        ChunkSentinel sentinel = new ChunkSentinel();
        sentinel.mediaFormat = chunk.videoMediaFormat;
        sentinel.entry = chunk.mainEntry;
        list.get(HLSExtractor.TYPE_VIDEO).add(sentinel);
      }

      DataSpec dataSpec = new DataSpec(uri, variantEntry.offset, variantEntry.length, null);
      DataSource HTTPDataSource = new HttpDataSource(userAgent, null, bandwidthMeter);
      DataSource dataSource = new AESDataSource(userAgent, HTTPDataSource);
      try {
        dataSource.open(dataSpec);
      } catch (IOException e) {
        e.printStackTrace();
        exception = e;
        return null;
      }

      HLSExtractor extractor = null;
      /*
        try {
          extractor = new TSExtractorNative(dataSource);
        } catch (UnsatisfiedLinkError e) {
          Log.e(TAG, "cannot load TSExtractorNative");
        }
      }*/
      if (extractor == null) {
        extractor = new TSExtractor(dataSource);
      }

      HLSExtractor.Sample sample;
      while (aborted == false) {
        try {
          sample = extractor.read();
        } catch (ParserException e) {
          Log.e(TAG, "extractor read error");
          e.printStackTrace();
          break;
        }
        if (sample == null) {
          break;
        }
        synchronized (source.list) {
          if (!aborted) {
            if (!gotStreamTypes) {
              audioStreamType = extractor.getStreamType(HLSExtractor.TYPE_AUDIO);
              videoStreamType = extractor.getStreamType(HLSExtractor.TYPE_VIDEO);
              gotStreamTypes = true;
            }
            if (audioMediaFormat == null && sample.type == HLSExtractor.TYPE_AUDIO) {
              if (audioStreamType == HLSExtractor.STREAM_TYPE_AAC_ADTS) {
                AACExtractor.ADTSHeader h = new AACExtractor.ADTSHeader();
                byte header[] = new byte[7];
                int oldPosition = sample.data.position();
                sample.data.position(0);
                sample.data.get(header, 0, 7);
                sample.data.position(oldPosition);
                h.update(new HLSExtractor.UnsignedByteArray(header), 0);
                audioMediaFormat = h.toMediaFormat();
              } else {
                // XX: do not hardcode
                audioMediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_MPEG, -1, 2, 44100, null);
              }
              ChunkSentinel sentinel = new ChunkSentinel();
              sentinel.mediaFormat = audioMediaFormat;
              sentinel.entry = chunk.mainEntry;

              list.get(sample.type).add(sentinel);
            }
            list.get(sample.type).add(sample);
          }
          bufferSize += sample.data.limit();
        }
        source.bufferedPositionUs.set(sample.pts);
      }

      extractor.release();
      try {
        dataSource.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return null;
    }

    public void abort()  {
      synchronized (HLSSampleSource.this.list) {
        aborted = true;
        exception = new Exception("aborted");
      }
    }

    @Override
    protected void onPostExecute(Void dummy) {
      HLSSampleSource source = HLSSampleSource.this;
      if (exception == null) {
        source.sequence++;
      }

      VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
      if (sequence == variantPlaylist.mediaSequence + variantPlaylist.entries.size()) {
        endOfStream = true;
      }

      source.chunkTask = null;
    }
  }
}

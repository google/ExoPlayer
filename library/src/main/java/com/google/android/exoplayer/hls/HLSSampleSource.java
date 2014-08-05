package com.google.android.exoplayer.hls;

import android.media.MediaExtractor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.parser.ts.TSExtractor;
import com.google.android.exoplayer.parser.ts.TSExtractorNative;
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
  private long ptsOffsetUs;

  private boolean endOfStream;

  static class HLSTrack {
    public int type;
    public TrackInfo trackInfo;
    public boolean discontinuity;
  }

  public HLSSampleSource(String url) {
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
  }

  public void setForcedBps(int bps) {
    this.forcedBps = bps;
  }

  public void setInitialBps(int initialBps) {
    this.initialBps = initialBps;
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
    if (forcedBps > 0) {
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

    return idealEntry;
  }

  @Override
  public boolean prepare() throws IOException {
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
      mainPlaylist = MainPlaylist.createVideoMainPlaylist(this.url);
    }

    // compute durationSec
    currentEntry = evaluateNextEntry();
    VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
    long durationUs = (long)variantPlaylist.duration * 1000 * 1000;

    sequence = variantPlaylist.mediaSequence;

    boolean hasVideo = false;
    boolean hasAudio = false;

    for (MainPlaylist.Entry entry: mainPlaylist.entries) {
      if (entry.codecs.contains("mp4a")) {
        hasAudio = true;
      }
      if (entry.codecs.contains("avc1")) {
        hasVideo = true;
      }
    }

    int i = 0;
    if (hasAudio) {
      HLSTrack track = new HLSTrack();
      track.type = HLSExtractor.TYPE_AUDIO;
      track.trackInfo = new TrackInfo(MimeTypes.AUDIO_AAC, durationUs);
      trackList.add(track);
      track2type[i++] = HLSExtractor.TYPE_AUDIO;
    }
    if (hasVideo) {
      HLSTrack track = new HLSTrack();
      track.type = HLSExtractor.TYPE_VIDEO;
      track.trackInfo = new TrackInfo(MimeTypes.VIDEO_H264, durationUs);
      trackList.add(track);
      track2type[i++] = HLSExtractor.TYPE_VIDEO;
    }

    prepared = true;

    continueBuffering(0);

    // see if there is a pts offset
    long start = System.currentTimeMillis();
    while (start - System.currentTimeMillis() < 1000) {
      boolean found = false;
      synchronized (list) {
        for (LinkedList<Object> l : list) {
          for (Object o : l) {
            if (o instanceof HLSExtractor.Sample) {
              HLSExtractor.Sample sample =  (HLSExtractor.Sample)o;
              if (found == false) {
                ptsOffsetUs = sample.timeUs;
                Log.d(TAG, "found ptsOffsetUs=" + ptsOffsetUs);
                found = true;
              }
            }
          }
        }
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
    VariantPlaylist variantPlaylist = currentEntry.getVariantPlaylist();
    VariantPlaylist.Entry variantEntry = variantPlaylist.entries.get(sequence - variantPlaylist.mediaSequence);

    Chunk chunk = new Chunk();
    chunk.variantEntry = variantEntry;
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
      try {
        o = list.get(track2type[track]).removeFirst();
        if (o instanceof MediaFormat) {
          formatHolder.format = (MediaFormat)o;
          return FORMAT_READ;
        } else {
          HLSExtractor.Sample sample = (HLSExtractor.Sample)o;
          sample.data.limit(sample.data.position());
          sample.data.position(0);
          sampleHolder.data.put(sample.data);
          sampleHolder.size = sample.data.limit();
          sampleHolder.timeUs = sample.timeUs - ptsOffsetUs;
          sampleHolder.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
          bufferSize -= sampleHolder.size;
          //Log.d(TAG, (sample.type == HLSExtractor.TYPE_AUDIO ? "AUDIO" : "VIDEO") + " timeUS=" + (sampleHolder.timeUs/1000));
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
        list.get(HLSExtractor.TYPE_VIDEO).add(chunk.videoMediaFormat);
      }

      DataSpec dataSpec = new DataSpec(uri, variantEntry.offset, variantEntry.length, null);
      DataSource dataSource = new HttpDataSource(userAgent, null, bandwidthMeter);
      try {
        dataSource.open(dataSpec);
      } catch (IOException e) {
        e.printStackTrace();
        exception = e;
        return null;
      }

      HLSExtractor extractor = null;
      try {
        extractor = new TSExtractorNative(dataSource);
      } catch (UnsatisfiedLinkError e) {
        Log.e(TAG, "cannot load TSExtractorNative");
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
            if (audioMediaFormat == null && sample.type == HLSExtractor.TYPE_AUDIO) {
              AACExtractor.ADTSHeader h = new AACExtractor.ADTSHeader();
              byte header[] = new byte[7];
              int oldPosition = sample.data.position();
              sample.data.position(0);
              sample.data.get(header, 0, 7);
              sample.data.position(oldPosition);
              h.update(new HLSExtractor.UnsignedByteArray(header), 0);
              audioMediaFormat = h.toMediaFormat();
              list.get(sample.type).add(audioMediaFormat);
            }
            list.get(sample.type).add(sample);
          }
          bufferSize += sample.data.limit();
        }
        source.bufferedPositionUs.set(sample.timeUs);
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

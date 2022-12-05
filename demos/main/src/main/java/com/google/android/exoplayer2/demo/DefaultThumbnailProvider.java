package com.google.android.exoplayer2.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.thumbnail.ThumbnailDescription;
import com.google.android.exoplayer2.util.Log;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class DefaultThumbnailProvider implements ThumbnailProvider {

  private static final String TAG_DEBUG = DefaultThumbnailProvider.class.getSimpleName();

  private LruCache<String, Bitmap> bitmapCache;
  private View parent;

  //dummy bitmap to indicate that a download is already triggered but not finished yet
  private final Bitmap dummyBitmap = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
  @Nullable ExoPlayer exoPlayer;

  public DefaultThumbnailProvider(ExoPlayer exoPlayer, View view) {
    this.exoPlayer = exoPlayer;

    this.parent = view;

    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    final int cacheSize = maxMemory / 4;
    bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
      @Override
      protected int sizeOf(String key, Bitmap bitmap) {
        return bitmap.getByteCount() / 1024;
      }
    };
  }

  public Bitmap getThumbnail(long position) {
    return getThumbnail(position, true);
  }

  private Bitmap getThumbnail(long position, boolean retrigger) {
    if (exoPlayer != null) {
      Object manifest = exoPlayer.getCurrentManifest();

      ThumbnailDescription thumbnailDescription = null;
      if (manifest instanceof DashManifest) {
        DashManifest dashManifest = (DashManifest) manifest;
        List<ThumbnailDescription> thumbnailDescs = dashManifest.getThumbnailDescriptions(position);
        //selected thumbnail description with lowest bitrate
        for (ThumbnailDescription desc : thumbnailDescs) {
          if (thumbnailDescription == null || thumbnailDescription.getBitrate() > desc.getBitrate()) {
            thumbnailDescription = desc;
          }
        }
        if (bitmapNotAvailableOrDownloadNotTriggeredYet(thumbnailDescription.getUri())) {
          this.initThumbnailSource(thumbnailDescription);
          return null;
        }
      }

      if (retrigger) {
        //also download next and prev thumbnails to have a nicer UI user experience
        getThumbnail(thumbnailDescription.getStartTimeMs() + thumbnailDescription.getDurationMs(), false);
        getThumbnail(thumbnailDescription.getStartTimeMs() - thumbnailDescription.getDurationMs(), false);
      }

      return getThumbnailInternal(position, thumbnailDescription);
    }
    return null;
  }

  private boolean bitmapNotAvailableOrDownloadNotTriggeredYet(Uri uri) {
    Bitmap tmp = bitmapCache.get(uri.toString());
    if (tmp != null) return false;
    return true;
  }

  private Bitmap getThumbnailInternal(long position, ThumbnailDescription thumbnailDescription) {
    if (thumbnailDescription == null) return null;

    Bitmap thumbnailSource =  bitmapCache.get(thumbnailDescription.getUri().toString());

    if (thumbnailSource == null || thumbnailSource.getWidth() == 1) return null;

    if (position < thumbnailDescription.getStartTimeMs() || position > thumbnailDescription.getStartTimeMs() + thumbnailDescription.getDurationMs())  return null;

    int count = thumbnailDescription.getTileCountHorizontal() * thumbnailDescription.getTileCountVertical();

    int durationPerImage = (int)(thumbnailDescription.getDurationMs() / count);

    int imageNumberToUseWithinTile = (int)((position - thumbnailDescription.getStartTimeMs()) / durationPerImage);

    //handle special case if position == duration
    if (imageNumberToUseWithinTile > count-1) imageNumberToUseWithinTile = count-1;

    int intRowToUse = (int)(imageNumberToUseWithinTile / thumbnailDescription.getTileCountHorizontal());

    int intColToUse = imageNumberToUseWithinTile - intRowToUse * thumbnailDescription.getTileCountHorizontal();

    double thumbnailWidth = (double) thumbnailDescription.getImageWidth() / thumbnailDescription.getTileCountHorizontal();
    double thumbnailHeight = (double) thumbnailDescription.getImageHeight() / thumbnailDescription.getTileCountVertical();

    int cropXLeft = (int)Math.round(intColToUse * thumbnailWidth);
    int cropYTop = (int)Math.round(intRowToUse * thumbnailHeight);

    if (cropXLeft + thumbnailWidth <= thumbnailSource.getWidth() && cropYTop + thumbnailHeight <= thumbnailSource.getHeight()) {
      return Bitmap.createBitmap(thumbnailSource
          , cropXLeft, cropYTop, (int) thumbnailWidth, (int) thumbnailHeight);
    }
    else {
      Log.d(TAG_DEBUG, "Image does not have expected (" + thumbnailDescription.getImageWidth() + "x" + thumbnailDescription.getImageHeight() + ") dimensions to crop. Source " + thumbnailDescription.getUri());
      return null;
    }
  }

  private synchronized void initThumbnailSource(ThumbnailDescription thumbnailDescription){
    String path = thumbnailDescription.getUri().toString();
    if (path == null) return;

    if (bitmapCache.get(path) != null) return;
    bitmapCache.put(path, dummyBitmap);

    RetrieveThumbnailImageTask currentTask = new RetrieveThumbnailImageTask();
    currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, path);
  }

  class RetrieveThumbnailImageTask extends AsyncTask<String, Integer, Bitmap> {

    String downloadedUrl;

    RetrieveThumbnailImageTask() {

    }

    @Override
    protected void onCancelled() {
      super.onCancelled();
      if (downloadedUrl != null) bitmapCache.remove(downloadedUrl);
    }

    protected Bitmap doInBackground(String... urls) {
      downloadedUrl = urls[0];
      InputStream in =null;
      Bitmap thumbnailToDownload=null;
      int responseCode = -1;

      try{
        URL url = new URL(downloadedUrl);
        if (!isCancelled()) {
          HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();
          httpURLConnection.setDoInput(true);
          httpURLConnection.connect();
          responseCode = httpURLConnection.getResponseCode();
          if(responseCode == HttpURLConnection.HTTP_OK)
          {
            if (!isCancelled()) {
              in = httpURLConnection.getInputStream();
              if (!isCancelled()) {
                thumbnailToDownload = BitmapFactory.decodeStream(in);
              }
              in.close();
            }
          }
        }

      }
      catch(Exception ex){
        bitmapCache.remove(downloadedUrl);
        System.out.println(ex);
      }

      return thumbnailToDownload;
    }

    protected void onPostExecute(Bitmap downloadedThumbnail) {
      if (downloadedThumbnail != null) {
        bitmapCache.put(downloadedUrl, downloadedThumbnail);
        if (parent != null) parent.invalidate();
      }
      else {
        bitmapCache.remove(downloadedUrl);
      }
    }
  }

}

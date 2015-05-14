package com.google.android.exoplayer.upstream.supercache;

import com.google.android.exoplayer.upstream.supercache.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * High-performance persistent cache for media (should be)
 */
public final class SuperCache {
    private static final int INDEX_MEDIA = 1;
    private static final int INDEX_META = 2;

    private static DiskLruCache diskLruCache;

    public SuperCache(File file, int count, long size) throws IOException {
        diskLruCache = DiskLruCache.open(file, 1, count, size);
    }

    public static void flush() throws IOException {
        diskLruCache.flush();
    }

    public static void close() throws IOException {
        diskLruCache.close();
    }

    public MediaUnit get(String key) throws IOException {
        return new MediaUnit(key);
    }

    public MediaUnit put(String key) throws IOException {
        return new MediaUnit(key);
    }

    public final class MediaUnit {
        private final static String STATUS_DONE = "RDY";

        private boolean isFinished;
        private InputStream inputStream;
        private OutputStream outputStream;
        private String key;
        private DiskLruCache.Editor editor;
        private DiskLruCache.Snapshot snapshot;

        public MediaUnit(String key) throws IOException {
            this.key = key;
            snapshot = diskLruCache.get(key);
            if (snapshot == null) {
                //Entry doesn't exist
                //Create new one
                editor = diskLruCache.edit(key);
                isFinished = false;
                outputStream = editor.newOutputStream(INDEX_MEDIA);
            } else {
                String status = snapshot.getString(INDEX_META);
                isFinished = status.equals(STATUS_DONE);
                if (!isFinished) {
                    inputStream = null;
                    diskLruCache.remove(key);
                } else {
                    inputStream = snapshot.getInputStream(INDEX_MEDIA);
                }
            }
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public boolean isFinished() {
            return isFinished;
        }

        /**
         * Call after all data is written to cache
         */
        public void setFinished() {
            if (!isFinished){
                isFinished = true;
                try {
                    snapshot = diskLruCache.get(key);
                    inputStream = snapshot.getInputStream(INDEX_MEDIA);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                catch (NullPointerException e){
                    //Invalid operation?
                }
            }
        }
    }

    public interface SuperCacheable{
        void setSuperCache(SuperCache superCache);
    }
}
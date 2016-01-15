package net.butterflytv.rtmp_client;

/**
 * Created by faraklit on 01.01.2016.
 */
public class RtmpClient {

    static {
        System.loadLibrary("rtmp-jni");
    }

    public native int open(String url, boolean isPublishMode);

    public native int read(byte[] data, int offset, int size);

    public native int write(byte[] data);

    public native int seek(int seekTime);

    public native int pause(int pause);

    public native int close();

}

#include <string.h>
#include <jni.h>
#include "libmpg123/src/mpg123.h"
#include <stdio.h>
#include <android/log.h>
#include <stdlib.h>

#define LOG(x...) __android_log_print(ANDROID_LOG_DEBUG, "mpg123JNI", x)
#define LOGE(x...) __android_log_print(ANDROID_LOG_ERROR, "mpg123JNI", x)

#define FUNC(RETURN_TYPE, NAME, ...) \
JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer_ext_libmpg123_Mpg123Decoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer_ext_libmpg123_Mpg123Decoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

mpg123_handle *handle;
int rate, channels, encoding;

FUNC(void, init) {
    mpg123_init();
}

FUNC(jint, newDecoder) {
    handle = mpg123_new(NULL, NULL);
    mpg123_open_feed(handle);
    if (handle == NULL) {
        LOGE("error opening feed");
        return -1;
    }
    return 1;
}

FUNC(jint, mpgdecode, jobject inBuffer, jint inSize, jint outBuffer, jint outSize) {
    size_t done = 0;
    size_t cumulative_bytes = 0;
    unsigned char* audio;
    int err;
    int frame_offset;
    int ret;
    char *in = (char *) (*env)->GetDirectBufferAddress(env, inBuffer);
    char *out = (char *) (*env)->GetDirectBufferAddress(env, outBuffer);

    mpg123_feed(handle, (const unsigned char*) in, inSize);
    do {
        err = mpg123_decode_frame(handle, &frame_offset, &audio, &done);
        switch(err) {
            case MPG123_NEW_FORMAT:
                mpg123_getformat(handle, &rate, &channels, &encoding);
                break;
            case MPG123_OK:
                if (done < outSize) {
		    memcpy(out, audio, done);
                    cumulative_bytes += done;
                } else {
                    memcpy(out, audio, outSize);
                    cumulative_bytes += outSize;
                }
                break;
            case MPG123_NEED_MORE:
                break;
            default:
                break;
        }
    } while(done > 0);
    return cumulative_bytes;
}

FUNC(void, close) {
    mpg123_close(handle);
}

FUNC(void, exit) {
    mpg123_exit();
}

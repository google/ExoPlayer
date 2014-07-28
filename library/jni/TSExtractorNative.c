#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <inttypes.h>
#include <unistd.h>
#include <stdlib.h>

#define TAG "TSExtractorNativeJNI"

#define RESULT_NEED_MORE_DATA 1
#define RESULT_END_OF_STREAM 2
#define RESULT_READ_SAMPLE_FULL 3

#define TYPE_VIDEO 0
#define TYPE_AUDIO 1
#define TYPE_COUNT 2

#define SAMPLE_FLAG_SYNC 1

// has to be a multiple of 188
#define BUFFER_SIZE (100*188)

typedef struct Sample Sample;
typedef struct PayloadHandler PayloadHandler;
typedef struct SectionHandler SectionHandler;
typedef struct TSParser TSParser;

struct PayloadHandler {
    void (*handlePayload)(PayloadHandler *ph, uint8_t *packet, int payload_offset, int unit_start);
    int pid;
    PayloadHandler *next;
    int isPES;
    TSParser *tsp;
    void (*destroy)(PayloadHandler *ph);
    int cc_counter;
};

struct SectionHandler {
    PayloadHandler payloadHandler;
    int tableID;
    uint8_t *section;
    int sectionMaxSize;
    int sectionLength;
    int sectionWriteOffset;
    void (*handleSection)(SectionHandler *sh, uint8_t *section, int sectionLength);
};

struct Sample {
    uint8_t *data;
    int size;
    int64_t timeUs;
    Sample *next;
};

struct TSParser{
    jbyteArray dataByteArray;
    uint8_t *data;
    int dataSize;
    int dataPosition;
    int dataIncompletePosition;
    Sample *sampleHead[TYPE_COUNT];
    Sample **sampleLastNext[TYPE_COUNT];
    PayloadHandler *activePayloadHandlerHead;
    PayloadHandler **activePayloadHandlerLastNext;

    int audioConfigFound;
    int sampleRateIndex;
    int channelConfigIndex;

    int pmt_pid;

    jobject inputStream;
    int inputStreamFinished;
    int incompleteReadPosition;
};

static inline void *_malloc(int size)
{
    void *ptr = malloc(size);
    if (ptr == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot Allocate Memory");
        exit(1);
    }
    return ptr;
}

static inline void *_mallocz(int size)
{
    void *ptr = _malloc(size);
    memset(ptr, 0, size);
    return ptr;
}

static inline void _free(void *ptr)
{
    free(ptr);
}

static void payload_handler_init(PayloadHandler *ph, TSParser *tsp, int pid)
{
    ph->pid = pid;
    ph->tsp = tsp;

    ph->next = tsp->activePayloadHandlerHead;
    tsp->activePayloadHandlerHead = ph;
}

static void payload_handler_exit(PayloadHandler *ph)
{
    TSParser *tsp = ph->tsp;
    PayloadHandler *cur = tsp->activePayloadHandlerHead;
    PayloadHandler *prev = NULL;

    while(cur) {
        if (cur == ph) {
            if (prev) {
                prev->next = cur->next;
            } else {
                tsp->activePayloadHandlerHead = cur->next;
            }
            if (cur->next == NULL) {
                if (prev) {
                    tsp->activePayloadHandlerLastNext = &prev->next;
                } else {
                    tsp->activePayloadHandlerLastNext = &tsp->activePayloadHandlerHead;
                }
            }
            break;
        }
        prev = cur;
        cur = cur->next;
    }

    _free(ph);
}

static void section_handler_handle_payload(PayloadHandler *ph, uint8_t *packet, int offset, int unitStart)
{
    SectionHandler *sh = (SectionHandler*)ph;
    if (sh->sectionLength == 0) {
        // pointer_field (what if pointer_field is != 0 ?)
        offset++;
        int tableID = packet[offset++];
        if (sh->tableID != tableID) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "unexepected tableID: %d != %d", tableID, sh->tableID);
        }
        sh->sectionLength = ((packet[offset] & 0xf) << 8) | packet[offset+1];
        offset += 2;
        if (sh->sectionLength > sh->sectionMaxSize) {
            if (sh->section)
                _free(sh->section);
            sh->section = _malloc(sh->sectionLength * 2);
            sh->sectionMaxSize = sh->sectionLength * 2;
        }
        sh->sectionWriteOffset = 0;
    }
    int copy = 188 - offset;
    if (sh->sectionLength - sh->sectionWriteOffset < copy) {
        copy = sh->sectionLength - sh->sectionWriteOffset;
    }

    memcpy(sh->section + sh->sectionWriteOffset, packet + offset, copy);
    sh->sectionWriteOffset += copy;
    if (sh->sectionWriteOffset == sh->sectionLength) {
        int sectionLength = sh->sectionLength;
        sh->sectionLength = 0;
        sh->handleSection(sh, sh->section, sectionLength);
    }

}

static void section_handler_init(SectionHandler *sh, TSParser *tsp, int pid)
{
    PayloadHandler *ph = (PayloadHandler*)sh;
    payload_handler_init(ph, tsp, pid);
    ph->handlePayload = section_handler_handle_payload;
}

static void section_handler_exit(SectionHandler*sh)
{
    if (sh->section)
        _free(sh->section);
    payload_handler_exit((PayloadHandler*)sh);
}

static void pmt_handler_handle_section(SectionHandler *sh, uint8_t *data, int dataLength)
{

}

static void pmt_handler_destroy(PayloadHandler*ph)
{
    SectionHandler *sh = (SectionHandler*)ph;
    section_handler_exit(sh);
}
static SectionHandler *pmt_handler_create(TSParser *tsp, int pid)
{
    SectionHandler *sh = _mallocz(sizeof(*sh));
    section_handler_init(sh, tsp, pid);
    sh->handleSection = pmt_handler_handle_section;
    ((PayloadHandler*)sh)->destroy = pmt_handler_destroy;
    sh->tableID = 2;
    return sh;
}

static void pat_handler_handle_section(SectionHandler *sh, uint8_t *data, int dataLength)
{
    int i;
    TSParser *tsp = ((PayloadHandler*)sh)->tsp;
    // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
    // stop at length - 4 to skip crc
    for (i = 5; i < dataLength - 4; ){
        // XXX
        //int programNumber = (data[i] << 8) - data[i+1];
        i += 2;
        tsp->pmt_pid = ((data[i] & 0x1f) << 8) | data[i+1];
        i += 2;
        // we just take the first program
        __android_log_print(ANDROID_LOG_ERROR, TAG, "found PMT pid: %04x", tsp->pmt_pid);
        pmt_handler_create(tsp, tsp->pmt_pid);
    }

    // do not listen to PAT updates
    ((PayloadHandler*)sh)->destroy((PayloadHandler*)sh);
}

static void pat_handler_destroy(PayloadHandler*ph)
{
    SectionHandler *sh = (SectionHandler*)ph;
    section_handler_exit(sh);
}
static SectionHandler *pat_handler_create(TSParser *tsp, int pid)
{
    SectionHandler *sh = _mallocz(sizeof(*sh));
    section_handler_init(sh, tsp, pid);
    sh->handleSection = pat_handler_handle_section;
    ((PayloadHandler*)sh)->destroy = pat_handler_destroy;
    sh->tableID = 0;
    return sh;
}

static TSParser *tsparser_create(JNIEnv *env, jobject thiz)
{
    TSParser *tsp = _mallocz(sizeof(*tsp));
    jfieldID fid;
    jclass cls;
    int i;

    cls = (*env)->FindClass(env, "com/google/android/exoplayer/parser/ts/TSExtractorNative");
    fid = (*env)->GetFieldID(env, cls, "inputStream", "Lcom/google/android/exoplayer/upstream/NonBlockingInputStream;");

    tsp->dataByteArray = (*env)->NewGlobalRef(env, (*env)->NewByteArray(env, BUFFER_SIZE));
    tsp->data = (uint8_t*)(*env)->GetByteArrayElements(env, tsp->dataByteArray, NULL);

    for (i = 0; i < TYPE_COUNT; i++) {
        tsp->sampleHead[i] = NULL;
        tsp->sampleLastNext[i] = &tsp->sampleHead[i];
    }
    tsp->activePayloadHandlerHead = NULL;
    tsp->activePayloadHandlerLastNext = &tsp->activePayloadHandlerHead;

    tsp->inputStream = (*env)->NewGlobalRef(env, (*env)->GetObjectField(env, thiz, fid));

    pat_handler_create(tsp, 0);
    return tsp;
}

static void tsparser_destroy(JNIEnv *env, TSParser*tsp)
{
    (*env)->ReleaseByteArrayElements(env, tsp->dataByteArray, (jbyte*)tsp->data, JNI_ABORT);
    (*env)->DeleteGlobalRef(env, tsp->dataByteArray);
    (*env)->DeleteGlobalRef(env, tsp->inputStream);
}

static void _refill_data(JNIEnv *env, TSParser *tsp)
{
    jint offset = 0;
    jint length = BUFFER_SIZE;

    if (tsp->dataIncompletePosition) {
        // we need multiple of 188 bytes
        offset = tsp->dataIncompletePosition;
        length = BUFFER_SIZE - tsp->dataIncompletePosition;
    }

    jclass cls = (*env)->FindClass(env,  "com/google/android/exoplayer/upstream/NonBlockingInputStream");
    jmethodID mid = (*env)->GetMethodID(env, cls, "read", "([BII)I");

    jint ret = (*env)->CallIntMethod(env, tsp->inputStream, mid, tsp->dataByteArray, offset, length);
    if (ret == -1) {
        int padding;
        tsp->inputStreamFinished = 1;
        padding = 188 - tsp->dataSize % 188;
        if (padding) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "TS file is not a multiple of 188 bytes ?");
        }
        tsp->dataSize = 188 * ((tsp->dataSize + 187) / 188);
    } else {
        offset += ret;
        if (offset % 188) {
            tsp->dataIncompletePosition = offset;
        } else {
            tsp->dataSize = offset;
            tsp->dataIncompletePosition = 0;
            tsp->dataPosition = 0;
        }
    }
}

#define PARSE_ONE_PACKET_AGAIN 0
#define PARSE_ONE_PACKET_WAIT 1
#define PARSE_ONE_PACKET_FINISHED 2

static int tsparser_parse_one_packet(JNIEnv *env, TSParser*tsp)
{
    uint8_t *packet;
    if (tsp->dataPosition == tsp->dataSize || tsp->incompleteReadPosition) {
        _refill_data(env, tsp);
        if (tsp->inputStreamFinished) {
            return PARSE_ONE_PACKET_FINISHED;
        } else if (tsp->dataPosition == tsp->dataSize || tsp->incompleteReadPosition) {
            return PARSE_ONE_PACKET_WAIT;
        }
    }

    packet = &tsp->data[tsp->dataPosition];
    tsp->dataPosition += 188;

    int unitStart = (packet[1] & 0x40) != 0;
    int pid = (packet[1] & 0x1f) << 8;
    pid |= packet[2];

    int cc_counter = packet[3] & 0xf;

    PayloadHandler *payloadHandler = tsp->activePayloadHandlerHead;
    while (payloadHandler) {
        if (payloadHandler->pid == pid) {
            break;
        }
    }
    if (!payloadHandler) {
        return PARSE_ONE_PACKET_AGAIN;
    }

    int expected_cc_counter = (payloadHandler->cc_counter + 1) & 0xf;
    if (expected_cc_counter != cc_counter) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "cc_error: %d -> %d", payloadHandler->cc_counter, cc_counter);
    }
    payloadHandler->cc_counter = cc_counter;

    int adaptation_field_exists = (packet[3] & 0x20) != 0;

    int payload_offset = 4;

    if (adaptation_field_exists) {
        payload_offset += packet[4] + 1;
    }

    payloadHandler->handlePayload(payloadHandler, packet, payload_offset, unitStart);

    return PARSE_ONE_PACKET_AGAIN;

}

static TSParser *_retrieve_tsp(JNIEnv *env, jobject thiz)
{
    jfieldID fid;
    jclass cls;

    cls = (*env)->FindClass(env, "com/google/android/exoplayer/parser/ts/TSExtractorNative");
    fid = (*env)->GetFieldID(env, cls, "nativeHandle", "J");
    return (TSParser*)(*env)->GetLongField(env, thiz, fid);
}

static void _set_tsp(JNIEnv *env, jobject thiz, TSParser *tsp)
{
    jfieldID fid;
    jclass cls;

    cls = (*env)->FindClass(env, "com/google/android/exoplayer/parser/ts/TSExtractorNative");
    fid = (*env)->GetFieldID(env, cls, "nativeHandle", "J");
    (*env)->SetLongField(env, thiz, fid, (jlong)tsp);
}

void Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeInit(JNIEnv* env, jobject thiz)
{
    TSParser *tsp;

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    tsp = tsparser_create(env, thiz);

    _set_tsp(env, thiz, tsp);
}

static void _fill_holder(JNIEnv *env, jobject holder, Sample *sample)
{
    jfieldID fid;
    jclass cls;
    void *ptr;
    jobject data;
    jlong capacity;

    cls = (*env)->FindClass(env, "com/google/android/exoplayer/SampleHolder");
    fid = (*env)->GetFieldID(env, cls, "data", "Ljava/nio/ByteBuffer;");
    data = (*env)->GetObjectField(env, holder, fid);
    if (!data) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "holder has no ByteBuffer");
        return;
    }

    ptr = (*env)->GetDirectBufferAddress(env, data);
    if (!ptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ByteBuffer is not direct");
        return;
    }

    capacity = (*env)->GetDirectBufferCapacity(env, data);
    if (capacity < sample->size) {
        memcpy(ptr, sample->data, capacity);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ByteBuffer capacity is too small %d < %d", (int)capacity, sample->size);
    } else {
        memcpy(ptr, sample->data, sample->size);
    }
    fid = (*env)->GetFieldID(env, cls, "timeUs", "J");
    (*env)->SetLongField(env, holder, fid, sample->timeUs);
#define SAMPLE_FLAG_SYNC 1
    fid = (*env)->GetFieldID(env, cls, "flags", "I");
    (*env)->SetIntField(env, holder, fid, SAMPLE_FLAG_SYNC);

}

jint Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeRead(JNIEnv* env, jobject thiz, jint type, jobject holder)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return RESULT_END_OF_STREAM;
    }

    while (tsp->sampleHead[type] == NULL) {
        if (tsparser_parse_one_packet(env, tsp) != PARSE_ONE_PACKET_AGAIN) {
            break;
        };
    }

    if (tsp->sampleHead[type]) {
        _fill_holder(env, holder, tsp->sampleHead[type]);

        // remove from list
        *tsp->sampleLastNext[type] = tsp->sampleHead[type]->next;
        return RESULT_READ_SAMPLE_FULL;
    } else {
        if (tsp->inputStreamFinished) {
            return RESULT_END_OF_STREAM;
        } else {
            return RESULT_NEED_MORE_DATA;
        }
    }

    // never reached
    return RESULT_NEED_MORE_DATA;
}

jint Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeGetSampleRateIndex(JNIEnv* env, jobject thiz)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return 0;
    }

    while (!tsp->audioConfigFound) {
        if (tsparser_parse_one_packet(env, tsp) == PARSE_ONE_PACKET_FINISHED)
            break;
    }

    return tsp->sampleRateIndex;
}

jint Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeGetChannelConfigIndex(JNIEnv* env, jobject thiz)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return 0;
    }

    while (!tsp->audioConfigFound) {
        if (tsparser_parse_one_packet(env, tsp) == PARSE_ONE_PACKET_FINISHED)
            break;
    }

    return tsp->channelConfigIndex;
}

jboolean Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeIsReadFinished(JNIEnv* env, jobject thiz)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}


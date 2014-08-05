#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <inttypes.h>
#include <unistd.h>
#include <pthread.h>
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
#define BUFFER_SIZE (200*188)

typedef struct PayloadHandler PayloadHandler;
typedef struct SectionHandler SectionHandler;
typedef struct PESHandler PESHandler;
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

struct PESHandler {
    PayloadHandler payloadHandler;
    int type;
    int length;
    jobject sample;
    uint8_t *data;
    int size;
    int position;
    int capacity;
};

struct TSParser{
    jbyteArray dataByteArray;
    uint8_t *data;
    int dataSize;
    int dataPosition;
    int dataIncompletePosition;
    PayloadHandler *activePayloadHandlerHead;

    jobject outSample;

    int pmt_pid;

    jobject dataSource;
    int dataSourceFinished;

    JNIEnv *env;
};

static int total_size;
static int counter;

static inline void *_malloc(int size)
{
    void *ptr = malloc(size + sizeof(int));
    if (ptr == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot Allocate Memory");
        exit(1);
    }

    int *intptr = (int*)ptr;
    *intptr = size;
    total_size += size;
    if (!(counter & 0x3f)) {
      __android_log_print(ANDROID_LOG_DEBUG, TAG, "total_size: %d", total_size);
    }

    return intptr + 1;
}

static inline void *_mallocz(int size)
{
    void *ptr = _malloc(size);
    memset(ptr, 0, size);
    return ptr;
}

static inline void _free(void *ptr)
{
    int *intptr = (int*)ptr;
    intptr--;
    total_size -= *intptr;
    free(intptr);
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

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "payload_handler_exit: %x\n", ph);

    while(cur) {
        if (cur == ph) {
            if (prev) {
                prev->next = cur->next;
            } else {
                tsp->activePayloadHandlerHead = cur->next;
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

static void get_sample(JNIEnv *env, PESHandler *pesh, int64_t timeUs)
{
  jclass cls;
  jmethodID mid;
  jfieldID fid;
  jobject data;

  cls = (*env)->FindClass(env, "com/google/android/exoplayer/hls/HLSExtractor");
  mid = (*env)->GetStaticMethodID(env, cls, "getSample", "(I)Lcom/google/android/exoplayer/hls/HLSExtractor$Sample;");

  pesh->sample = (*env)->CallStaticObjectMethod(env, cls, mid, pesh->type);

  (*env)->DeleteLocalRef(env, cls);

  cls = (*env)->FindClass(env, "com/google/android/exoplayer/hls/HLSExtractor$Sample");
  fid = (*env)->GetFieldID(env, cls, "data", "Ljava/nio/ByteBuffer;");
  data = (*env)->GetObjectField(env, pesh->sample, fid);

  fid = (*env)->GetFieldID(env, cls, "timeUs", "J");
  (*env)->SetLongField(env, pesh->sample, fid, (jlong)timeUs);

  pesh->data = (uint8_t*)(*env)->GetDirectBufferAddress(env, data);
  pesh->position = 0;
  pesh->capacity = (*env)->GetDirectBufferCapacity(env, data);

  (*env)->DeleteLocalRef(env, cls);

  pesh->sample = (*env)->NewGlobalRef(env, pesh->sample);
  //__android_log_print(ANDROID_LOG_DEBUG, TAG, "newRef %x", (int)pesh->sample);
}

static void set_position(JNIEnv *env, PESHandler *pesh)
{
  jclass cls = (*env)->FindClass(env, "com/google/android/exoplayer/hls/HLSExtractor$Sample");
  jfieldID fid = (*env)->GetFieldID(env, cls, "data", "Ljava/nio/ByteBuffer;");
  jobject data = (*env)->GetObjectField(env, pesh->sample, fid);
  jmethodID mid;

  (*env)->DeleteLocalRef(env, cls);

  cls = (*env)->FindClass(env, "java/nio/Buffer");
  mid = (*env)->GetMethodID(env, cls,"position", "(I)Ljava/nio/Buffer;");
  (*env)->CallObjectMethod(env, data, mid, pesh->position);

  (*env)->DeleteLocalRef(env, cls);
}


static void sample_resize(JNIEnv *env, PESHandler *pesh, int newSize)
{
  jclass cls;
  jmethodID mid;
  jfieldID fid;
  jobject data;

  set_position(env, pesh);

  cls = (*env)->FindClass(env, "com/google/android/exoplayer/hls/HLSExtractor");
  mid = (*env)->GetStaticMethodID(env, cls, "resizeSample", "(Lcom/google/android/exoplayer/hls/HLSExtractor$Sample;I)V");

  (*env)->CallStaticObjectMethod(env, cls, mid, pesh->sample, newSize);
  (*env)->DeleteLocalRef(env, cls);

  cls = (*env)->FindClass(env, "com/google/android/exoplayer/hls/HLSExtractor$Sample");
  fid = (*env)->GetFieldID(env, cls, "data", "Ljava/nio/ByteBuffer;");
  data = (*env)->GetObjectField(env, pesh->sample, fid);

  pesh->data = (uint8_t*)(*env)->GetDirectBufferAddress(env, data);
  pesh->capacity = (*env)->GetDirectBufferCapacity(env, data);


  (*env)->DeleteLocalRef(env, cls);
  (*env)->DeleteLocalRef(env, data);
}

static void pes_handler_handle_payload(PayloadHandler *ph, uint8_t *packet, int offset, int unitStart)
{
    PESHandler *pesh = (PESHandler*)ph;
    JNIEnv *env = ph->tsp->env;

    if (unitStart) {
        uint64_t pts = 0;

        // output previous packet
        if (pesh->sample != NULL) {
            //__android_log_print(ANDROID_LOG_DEBUG, TAG, "got %s packet size %d", pesh->type == TYPE_AUDIO ? "audio" : "video", pesh->position);

            set_position(env, pesh);
            ph->tsp->outSample = pesh->sample;

            if (pesh->length != 0 && pesh->length != pesh->position) {
                __android_log_print(ANDROID_LOG_DEBUG, TAG, "PES length %d != %d", pesh->position, pesh->length);
            }

            pesh->sample = NULL;
        }

        if (packet[offset] != 0 || packet[offset + 1] != 0 || packet[offset + 2] != 1 ) {
            __android_log_print(ANDROID_LOG_DEBUG, TAG, "bad start code: 0x%02x%02x%02x", packet[offset], packet[offset+1], packet[offset+2]);
        }
        offset += 3;
        // skip stream id
        offset++;
        pesh->length = ((int)packet[offset]<<8) | packet[offset + 1];
        offset += 2;

        // skip some stuff
        offset++;
        int flags = packet[offset++];
        int headerDataLength = packet[offset++];
        int fixedOffset = offset;

        if ((flags & 0x80) == 0x80) {
            pts = (long)(packet[offset++] & 0x0e) << 28;
            pts |= (packet[offset++]) << 21;
            pts |= (packet[offset++] & 0xfe) << 13;
            pts |= (packet[offset++]) << 6;
            pts |= (packet[offset++] & 0xfe) >> 2;

        }
        if ((flags & 0x40) == 0x40) {
            // DTS
            offset += 5;
        }

        get_sample(env, pesh, pts  * 1000 / 45);

        offset = fixedOffset + headerDataLength;
        if (pesh->length > 0)
            pesh->length -= headerDataLength + 3;

        memcpy(pesh->data, packet + offset, 188 - offset);
        pesh->position = 188 - offset;
        return;
    }

    if (!pesh->sample)
      return;

    if (pesh->position + 188 > pesh->capacity) {
        sample_resize(env, pesh, 2*(pesh->position + 188));
    }
    memcpy(pesh->data + pesh->position, packet + offset, 188 - offset);
    pesh->position += 188 - offset;
}

static void pes_handler_destroy(PayloadHandler *ph)
{
    payload_handler_exit(ph);
}

static PESHandler *pes_handler_create(TSParser *tsp, int pid, int type)
{
    PESHandler *pesh = _mallocz(sizeof(*pesh));
    PayloadHandler *ph = (PayloadHandler*)pesh;
    payload_handler_init(ph, tsp, pid);
    pesh->type = type;
    ph->handlePayload = pes_handler_handle_payload;
    ph->destroy = pes_handler_destroy;
    ph->isPES = 1;

    return pesh;
}

static void pmt_handler_handle_section(SectionHandler *sh, uint8_t *data, int dataLength)
{
#define STREAM_TYPE_AAC_ADTS 0xf
#define STREAM_TYPE_H264 0x1b
    PESHandler *audio_handler = NULL;
    PESHandler *video_handler = NULL;
    PayloadHandler *ph = (PayloadHandler*)sh;

    // start from 5 to skip transport_stream_id, version_number, current_next_indicator, etc.
    int i = 7;
    int program_info_length = ((data[i] & 0xf) << 8) | data[i+1];
    i += 2 + program_info_length;
    while (i < dataLength - 4) {
        int streamType = data[i];
        i++;
        int streamPID = ((data[i] & 0x1f) << 8) | data[i+1];
        i+=2;
        int ES_info_length = ((data[i] & 0xf) << 8) | data[i+1];
        i+=2;
        i += ES_info_length;

        if (audio_handler == NULL && (streamType == STREAM_TYPE_AAC_ADTS)) {
            audio_handler = pes_handler_create(ph->tsp, streamPID, TYPE_AUDIO);
             __android_log_print(ANDROID_LOG_DEBUG, TAG, "audio found on pid %04x", streamPID);
        } else if (video_handler == NULL && streamType == STREAM_TYPE_H264) {
            video_handler = pes_handler_create(ph->tsp, streamPID, TYPE_VIDEO);
             __android_log_print(ANDROID_LOG_DEBUG, TAG, "video found on pid %04x", streamPID);
        }
    }

    // do not listen to future PMT updates
    ((PayloadHandler*)sh)->destroy((PayloadHandler*)sh);
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
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "pat_handler_handle_section: %d bytes", dataLength);

    for (i = 5; i < dataLength - 4; ){
        // XXX
        //int programNumber = (data[i] << 8) - data[i+1];
        i += 2;
        tsp->pmt_pid = ((data[i] & 0x1f) << 8) | data[i+1];
        i += 2;
        // we just take the first program
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "found PMT pid: 0x%04x", tsp->pmt_pid);
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

    cls = (*env)->FindClass(env, "com/google/android/exoplayer/parser/ts/TSExtractorNative");
    fid = (*env)->GetFieldID(env, cls, "dataSource", "Lcom/google/android/exoplayer/upstream/DataSource;");

    tsp->dataByteArray = (*env)->NewGlobalRef(env, (*env)->NewByteArray(env, BUFFER_SIZE));
    tsp->data = (uint8_t*)(*env)->GetByteArrayElements(env, tsp->dataByteArray, NULL);

    tsp->activePayloadHandlerHead = NULL;

    tsp->dataSource = (*env)->NewGlobalRef(env, (*env)->GetObjectField(env, thiz, fid));

    pat_handler_create(tsp, 0);
    return tsp;
}

static void tsparser_destroy(TSParser*tsp)
{
    PayloadHandler *ph;
    JNIEnv *env = tsp->env;

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    ph = tsp->activePayloadHandlerHead;
    while (ph) {
        // flush
        if (ph->isPES) {
            PESHandler *pesh = (PESHandler*)ph;
            if (pesh->sample) {
                (*env)->DeleteGlobalRef(env, pesh->sample);
                //__android_log_print(ANDROID_LOG_ERROR, TAG, "flush type=%d (%p)", pesh->type, pesh);
                pesh->sample = NULL;
            }
        }
        ph = ph->next;
    }

    if (tsp->outSample) {
      (*env)->DeleteGlobalRef(env, tsp->outSample);
      tsp->outSample = NULL;
    }

    (*env)->ReleaseByteArrayElements(env, tsp->dataByteArray, (jbyte*)tsp->data, JNI_ABORT);
    (*env)->DeleteGlobalRef(env, tsp->dataByteArray);
    (*env)->DeleteGlobalRef(env, tsp->dataSource);

    while (tsp->activePayloadHandlerHead) {
      tsp->activePayloadHandlerHead->destroy(tsp->activePayloadHandlerHead);
    }

    _free(tsp);
}

static void _refill_data(TSParser *tsp)
{
    jint offset = 0;
    jint length = BUFFER_SIZE;
    JNIEnv *env = tsp->env;

    if (tsp->dataIncompletePosition) {
        // we need multiple of 188 bytes
        offset = tsp->dataIncompletePosition;
        length = BUFFER_SIZE - tsp->dataIncompletePosition;
    }

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "_refill_data");

    jclass cls = (*env)->FindClass(env,  "com/google/android/exoplayer/upstream/DataSource");
    jmethodID mid = (*env)->GetMethodID(env, cls, "read", "([BII)I");
    // make sure we don't overflow the local reference table
    (*env)->DeleteLocalRef(env, cls);

    jint ret = (*env)->CallIntMethod(env, tsp->dataSource, mid, tsp->dataByteArray, offset, length);
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "_refill_data: read returned %d", ret);

    if (ret == -1) {
        tsp->dataSourceFinished = 1;
        if (tsp->dataSize % 188) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "TS file is not a multiple of 188 bytes (%d)?", tsp->dataSize);
            tsp->dataSize = 188 * ((tsp->dataSize + 187) / 188);
        }
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

static int tsparser_parse_one_packet(TSParser*tsp)
{
    uint8_t *packet;
    //__android_log_print(ANDROID_LOG_ERROR, TAG, "tsparser_parse_one_packet");

    if (tsp->dataPosition == tsp->dataSize || tsp->dataIncompletePosition) {
        _refill_data(tsp);
        if (tsp->dataSourceFinished) {
            return PARSE_ONE_PACKET_FINISHED;
        } else if (tsp->dataPosition == tsp->dataSize || tsp->dataIncompletePosition) {
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
        payloadHandler = payloadHandler->next;
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

    TSParser *tsp = (TSParser*)(*env)->GetLongField(env, thiz, fid);
    tsp->env = env;
    return tsp;
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

jobject Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeRead(JNIEnv* env, jobject thiz)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);
    jobject out = NULL;

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return NULL;
    }

    tsp->env = env;

    while (tsp->outSample == NULL) {
        tsparser_parse_one_packet(tsp);
        if (tsp->dataSourceFinished) {
          break;
        }
    }
    if (tsp->outSample == NULL) {
      PayloadHandler *ph = tsp->activePayloadHandlerHead;
      while(ph) {
        if (ph->isPES) {
          PESHandler *pesh = (PESHandler*)ph;
          if (pesh->sample) {
            set_position(env, pesh);
            out = pesh->sample;
            pesh->sample = NULL;
            return out;
          }
          ph = ph->next;
        }
      }
    } else {
      out = tsp->outSample;
      //__android_log_print(ANDROID_LOG_DEBUG, TAG, "output %x", tsp->outSample);
      // not needed
      // (*env)->DeleteGlobalRef(env, tsp->outSample);
      tsp->outSample = NULL;
    }

    return out;
}

void Java_com_google_android_exoplayer_parser_ts_TSExtractorNative_nativeRelease(JNIEnv* env, jobject thiz)
{
    TSParser *tsp = _retrieve_tsp(env, thiz);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", __FUNCTION__);

    if (!tsp) {
        return;
    }

    tsparser_destroy(tsp);
    _set_tsp(env, thiz, NULL);
}

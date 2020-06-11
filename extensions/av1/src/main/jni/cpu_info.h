#ifndef EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_
#define EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_

namespace gav1_jni {

// Returns the number of performance cores that are available for AV1 decoding.
// This is a heuristic that works on most common android devices. Returns 0 on
// error or if the number of performance cores cannot be determined.
int GetNumberOfPerformanceCoresOnline();

}  // namespace gav1_jni

#endif  // EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_

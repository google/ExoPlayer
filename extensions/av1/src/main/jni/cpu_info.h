/*
 * Copyright 2021 The Android Open Source Project
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
#ifndef EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_
#define EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_

namespace gav1_jni {

// Returns the number of performance cores that are available for AV1 decoding.
// This is a heuristic that works on most common android devices. Returns 0 on
// error or if the number of performance cores cannot be determined.
int GetNumberOfPerformanceCoresOnline();

}  // namespace gav1_jni

#endif  // EXOPLAYER_V2_EXTENSIONS_AV1_SRC_MAIN_JNI_CPU_INFO_H_

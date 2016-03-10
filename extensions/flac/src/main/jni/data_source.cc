/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "include/data_source.h"
#include <android/log.h>
#include <string.h>

#define LOG_TAG "DataSource"
#define ALOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

void DataSource::setBuffer(const void *data, const size_t size) {
  this->data = data;
  this->size = size;
}

ssize_t DataSource::readAt(off64_t /*offset*/, void *const data, size_t size) {
  if (size > this->size) {
    size = this->size;
  }
  memcpy(data, this->data, size);
  this->data = reinterpret_cast<const char *>(this->data) + size;
  this->size -= size;
  return size;
}

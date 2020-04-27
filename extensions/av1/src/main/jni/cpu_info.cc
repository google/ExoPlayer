#include "cpu_info.h"  // NOLINT

#include <unistd.h>

#include <cerrno>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace gav1_jni {
namespace {

// Note: The code in this file needs to use the 'long' type because it is the
// return type of the Standard C Library function strtol(). The linter warnings
// are suppressed with NOLINT comments since they are integers at runtime.

// Returns the number of online processor cores.
int GetNumberOfProcessorsOnline() {
  // See https://developer.android.com/ndk/guides/cpu-features.
  long num_cpus = sysconf(_SC_NPROCESSORS_ONLN);  // NOLINT
  if (num_cpus < 0) {
    return 0;
  }
  // It is safe to cast num_cpus to int. sysconf(_SC_NPROCESSORS_ONLN) returns
  // the return value of get_nprocs(), which is an int.
  return static_cast<int>(num_cpus);
}

}  // namespace

// These CPUs support heterogeneous multiprocessing.
#if defined(__arm__) || defined(__aarch64__)

// A helper function used by GetNumberOfPerformanceCoresOnline().
//
// Returns the cpuinfo_max_freq value (in kHz) of the given CPU. Returns 0 on
// failure.
long GetCpuinfoMaxFreq(int cpu_index) {  // NOLINT
  char buffer[128];
  const int rv = snprintf(
      buffer, sizeof(buffer),
      "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu_index);
  if (rv < 0 || rv >= sizeof(buffer)) {
    return 0;
  }
  FILE* file = fopen(buffer, "r");
  if (file == nullptr) {
    return 0;
  }
  char* const str = fgets(buffer, sizeof(buffer), file);
  fclose(file);
  if (str == nullptr) {
    return 0;
  }
  const long freq = strtol(str, nullptr, 10);  // NOLINT
  if (freq <= 0 || freq == LONG_MAX) {
    return 0;
  }
  return freq;
}

// Returns the number of performance CPU cores that are online. The number of
// efficiency CPU cores is subtracted from the total number of CPU cores. Uses
// cpuinfo_max_freq to determine whether a CPU is a performance core or an
// efficiency core.
//
// This function is not perfect. For example, the Snapdragon 632 SoC used in
// Motorola Moto G7 has performance and efficiency cores with the same
// cpuinfo_max_freq but different cpuinfo_min_freq. This function fails to
// differentiate the two kinds of cores and reports all the cores as
// performance cores.
int GetNumberOfPerformanceCoresOnline() {
  // Get the online CPU list. Some examples of the online CPU list are:
  //   "0-7"
  //   "0"
  //   "0-1,2,3,4-7"
  FILE* file = fopen("/sys/devices/system/cpu/online", "r");
  if (file == nullptr) {
    return 0;
  }
  char online[512];
  char* const str = fgets(online, sizeof(online), file);
  fclose(file);
  file = nullptr;
  if (str == nullptr) {
    return 0;
  }

  // Count the number of the slowest CPUs. Some SoCs such as Snapdragon 855
  // have performance cores with different max frequencies, so only the slowest
  // CPUs are efficiency cores. If we count the number of the fastest CPUs, we
  // will fail to count the second fastest performance cores.
  long slowest_cpu_freq = LONG_MAX;  // NOLINT
  int num_slowest_cpus = 0;
  int num_cpus = 0;
  const char* cp = online;
  int range_begin = -1;
  while (true) {
    char* str_end;
    const int cpu = static_cast<int>(strtol(cp, &str_end, 10));  // NOLINT
    if (str_end == cp) {
      break;
    }
    cp = str_end;
    if (*cp == '-') {
      range_begin = cpu;
    } else {
      if (range_begin == -1) {
        range_begin = cpu;
      }

      num_cpus += cpu - range_begin + 1;
      for (int i = range_begin; i <= cpu; ++i) {
        const long freq = GetCpuinfoMaxFreq(i);  // NOLINT
        if (freq <= 0) {
          return 0;
        }
        if (freq < slowest_cpu_freq) {
          slowest_cpu_freq = freq;
          num_slowest_cpus = 0;
        }
        if (freq == slowest_cpu_freq) {
          ++num_slowest_cpus;
        }
      }

      range_begin = -1;
    }
    if (*cp == '\0') {
      break;
    }
    ++cp;
  }

  // If there are faster CPU cores than the slowest CPU cores, exclude the
  // slowest CPU cores.
  if (num_slowest_cpus < num_cpus) {
    num_cpus -= num_slowest_cpus;
  }
  return num_cpus;
}

#else

// Assume symmetric multiprocessing.
int GetNumberOfPerformanceCoresOnline() {
  return GetNumberOfProcessorsOnline();
}

#endif

}  // namespace gav1_jni

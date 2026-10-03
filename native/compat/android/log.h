#ifndef CLASSIC_L2TP_ANDROID_LOG_H
#define CLASSIC_L2TP_ANDROID_LOG_H

/* Host stand-in for <android/log.h> so the same engine builds with the system compiler. */
#include <stdio.h>

#define ANDROID_LOG_UNKNOWN 0
#define ANDROID_LOG_DEFAULT 1
#define ANDROID_LOG_VERBOSE 2
#define ANDROID_LOG_DEBUG 3
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6
#define ANDROID_LOG_FATAL 7
#define ANDROID_LOG_SILENT 8

#define __android_log_print(prio, tag, fmt, ...) \
  do {                                           \
    fprintf(stderr, "%s: " fmt "\n", tag, ##__VA_ARGS__); \
    fflush(stderr);                              \
  } while (0)

#endif

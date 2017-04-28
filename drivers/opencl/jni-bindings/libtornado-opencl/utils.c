/*
 * Copyright 2012 James Clarkson.
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

#include "utils.h"
#include <stdio.h>
#include <string.h>


#define NSECS_PER_SEC 1000000000ULL
#include <time.h>
static unsigned long long start, stop;

void resetAndStartTimer() {
    //        struct timespec t;
    //        clock_gettime(CLOCK_REALTIME,&t);
    //        start = t.tv_sec*NSECS_PER_SEC +  t.tv_nsec;
}

unsigned long long getElapsedTime() {
    //        struct timespec t;
    //        clock_gettime(CLOCK_REALTIME,&t);
    //        stop = t.tv_sec*NSECS_PER_SEC +  t.tv_nsec;
    //        return (stop-start);
    return 0;
}

char *getOpenCLError(char *func, cl_int code) {
    char *str;
    char *msg = malloc(sizeof (char)*128);
    memset(msg, '\0', 128);
    switch (code) {
        case CL_SUCCESS:
            str = "Operation completed successfully.";
            break;
        case CL_INVALID_VALUE:
            str = "CL_INVALID_VALUE";
            break;
        case CL_INVALID_DEVICE:
            str = "CL_INVALID_DEVICE";
            break;
        case CL_DEVICE_NOT_AVAILABLE:
            str = "CL_DEVICE_NOT_AVAILABLE";
            break;
        case CL_OUT_OF_HOST_MEMORY:
            str = "CL_OUT_OF_HOST_MEMORY";
            break;
        case CL_INVALID_CONTEXT:
            str = "CL_INVALID_CONTEXT";
            break;
        case CL_INVALID_MEM_OBJECT:
            str = "CL_INVALID_MEM_OBJECT";
            break;
        default:
            str = "Unknown OpenCL Error";
    }

    sprintf(msg, "%s(%d) %s", func, (int) code, str);
    return msg;

}



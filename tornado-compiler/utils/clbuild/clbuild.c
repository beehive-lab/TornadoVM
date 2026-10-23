/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
/*
 * clbuild: compiles an OpenCL C file with clBuildProgram on every GPU of every platform (or the one
 * whose name contains argv[2]) and prints the build log on failure. Used by the TC lit tests the way
 * nvcc checks the CUDA ones. A device without cl_khr_fp64 is skipped for a source that uses double, as
 * TC's legality step refuses such kernels there. Usage: clbuild <file.cl | -> [device-name-substring]
 */
#define CL_TARGET_OPENCL_VERSION 120
#include <CL/cl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *slurp(const char *path) {
    FILE *f = strcmp(path, "-") == 0 ? stdin : fopen(path, "rb");
    if (!f) { perror(path); exit(2); }
    size_t cap = 1 << 16, len = 0; char *buf = malloc(cap);
    for (size_t n; (n = fread(buf + len, 1, cap - len - 1, f)) > 0;) { len += n; if (len + 1 >= cap) buf = realloc(buf, cap *= 2); }
    buf[len] = 0;
    return buf;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: clbuild <file.cl | -> [device-substring]\n"); return 2; }
    const char *src = slurp(argv[1]);
    cl_platform_id platforms[8]; cl_uint np = 0;
    clGetPlatformIDs(8, platforms, &np);
    int built = 0, failed = 0, skipped = 0;
    int usesDouble = strstr(src, "double") != NULL;
    for (cl_uint p = 0; p < np; p++) {
        cl_device_id devices[8]; cl_uint nd = 0;
        if (clGetDeviceIDs(platforms[p], CL_DEVICE_TYPE_GPU, 8, devices, &nd) != CL_SUCCESS) continue;
        for (cl_uint d = 0; d < nd; d++) {
            char name[256]; clGetDeviceInfo(devices[d], CL_DEVICE_NAME, sizeof name, name, NULL);
            if (argc > 2 && !strstr(name, argv[2])) continue;
            char ext[8192]; clGetDeviceInfo(devices[d], CL_DEVICE_EXTENSIONS, sizeof ext, ext, NULL);
            if (usesDouble && !strstr(ext, "cl_khr_fp64")) { skipped++; continue; }
            cl_int err; cl_context ctx = clCreateContext(NULL, 1, &devices[d], NULL, NULL, &err);
            cl_program prog = clCreateProgramWithSource(ctx, 1, &src, NULL, &err);
            err = clBuildProgram(prog, 1, &devices[d], "", NULL, NULL);
            if (err != CL_SUCCESS) {
                size_t n; clGetProgramBuildInfo(prog, devices[d], CL_PROGRAM_BUILD_LOG, 0, NULL, &n);
                char *log = malloc(n + 1); clGetProgramBuildInfo(prog, devices[d], CL_PROGRAM_BUILD_LOG, n, log, NULL); log[n] = 0;
                fprintf(stderr, "clbuild: %s: build failed (%d)\n%s\n", name, err, log);
                failed++;
            } else {
                built++;
            }
            clReleaseProgram(prog); clReleaseContext(ctx);
        }
    }
    if (built + failed == 0) { fprintf(stderr, "clbuild: no OpenCL GPU that can build this source\n"); return 3; }
    printf("clbuild: built on %d device(s), failed on %d, skipped %d without fp64\n", built, failed, skipped);
    return failed ? 1 : 0;
}

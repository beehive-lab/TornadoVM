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
 * hiprtccheck: compiles a HIP C++ file with hipRTC, as the HIP backend does, for each AMD GPU
 * architecture given (default: gfx942, a 64-wide CDNA GPU, and gfx1100, a 32-wide RDNA GPU) and prints
 * the log on failure. No GPU is needed. Used by the TC lit tests the way nvcc checks the CUDA ones.
 * Build: gcc -O2 -D__HIP_PLATFORM_AMD__ -I$ROCM_PATH/include -o hiprtccheck hiprtccheck.c -L$ROCM_PATH/lib -lhiprtc
 * Usage: hiprtccheck <file.hip | -> [gfx...]
 */
#include <hip/hiprtc.h>
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

static int compile(const char *src, const char *arch) {
    char option[64];
    snprintf(option, sizeof option, "--offload-arch=%s", arch);
    const char *options[] = { option };
    hiprtcProgram prog;
    if (hiprtcCreateProgram(&prog, src, "tc.hip", 0, NULL, NULL) != HIPRTC_SUCCESS) { fprintf(stderr, "hiprtccheck: hiprtcCreateProgram failed\n"); return 1; }
    hiprtcResult r = hiprtcCompileProgram(prog, 1, options);
    if (r != HIPRTC_SUCCESS) {
        size_t n = 0; hiprtcGetProgramLogSize(prog, &n);
        char *log = malloc(n + 1); hiprtcGetProgramLog(prog, log); log[n] = 0;
        fprintf(stderr, "hiprtccheck: %s: %s\n%s\n", arch, hiprtcGetErrorString(r), log);
        free(log);
    }
    hiprtcDestroyProgram(&prog);
    return r != HIPRTC_SUCCESS;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: hiprtccheck <file.hip | -> [gfx...]\n"); return 2; }
    const char *src = slurp(argv[1]);
    static const char *defaults[] = { "gfx942", "gfx1100" };
    int failed = 0;
    if (argc > 2) {
        for (int i = 2; i < argc; i++) failed += compile(src, argv[i]);
    } else {
        for (size_t i = 0; i < sizeof defaults / sizeof defaults[0]; i++) failed += compile(src, defaults[i]);
    }
    return failed ? 1 : 0;
}

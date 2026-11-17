/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.codegen;

import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * Small device functions that give Java semantics where the target's built-ins differ. The emitter
 * adds a helper to the kernel preamble only when the kernel uses it, so simple kernels stay free of
 * boilerplate. Each helper is written from the Java Language Specification.
 */
final class Helpers {

    private Helpers() {
    }

    // Math.min/max (JLS, java.lang.Math): NaN if either argument is NaN; -0.0 is smaller than +0.0.
    /** The names of all CUDA helpers (they live in the tc_ and java_ namespaces, which Naming reserves). */
    static java.util.Set<String> cudaNames() {
        return CUDA.keySet();
    }

    private static final Map<String, String> CUDA = Map.ofEntries(
            // Java integer division (JLS 15.17.2): MIN_VALUE / -1 is MIN_VALUE and MIN_VALUE % -1 is 0;
            // C leaves both undefined, and some devices trap or return another value.
            Map.entry("java_idiv", """
                    __device__ static inline int java_idiv(int a, int b) {
                      return b == -1 ? (int) ((unsigned int) 0 - (unsigned int) a) : a / b;
                    }
                    """),
            Map.entry("java_irem", """
                    __device__ static inline int java_irem(int a, int b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            Map.entry("java_ldiv", """
                    __device__ static inline long long java_ldiv(long long a, long long b) {
                      return b == -1 ? (long long) ((unsigned long long) 0 - (unsigned long long) a) : a / b;
                    }
                    """),
            Map.entry("java_lrem", """
                    __device__ static inline long long java_lrem(long long a, long long b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            Map.entry("java_fminf", """
                    __device__ static inline float java_fminf(float a, float b) {
                      if (a != a || b != b) return __int_as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return __int_as_float(__float_as_int(a) | __float_as_int(b));
                      return a < b ? a : b;
                    }
                    """),
            Map.entry("java_fmaxf", """
                    __device__ static inline float java_fmaxf(float a, float b) {
                      if (a != a || b != b) return __int_as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return __int_as_float(__float_as_int(a) & __float_as_int(b));
                      return a > b ? a : b;
                    }
                    """),
            Map.entry("java_fmin", """
                    __device__ static inline double java_fmin(double a, double b) {
                      if (a != a || b != b) return __longlong_as_double(0x7ff8000000000000LL);
                      if (a == 0.0 && b == 0.0) return __longlong_as_double(__double_as_longlong(a) | __double_as_longlong(b));
                      return a < b ? a : b;
                    }
                    """),
            // A vector value with one lane replaced (Java vector types are values here; setX makes a new one).
            Map.entry("tc_with", """
                    template <typename V, typename S> __device__ static inline V tc_with_x(V v, S s) { v.x = s; return v; }
                    template <typename V, typename S> __device__ static inline V tc_with_y(V v, S s) { v.y = s; return v; }
                    template <typename V, typename S> __device__ static inline V tc_with_z(V v, S s) { v.z = s; return v; }
                    template <typename V, typename S> __device__ static inline V tc_with_w(V v, S s) { v.w = s; return v; }
                    """),
            Map.entry("tc_warp_sum", """
                    __device__ static inline float tc_warp_sum(float v) {
                      for (int lane = 16; lane > 0; lane >>= 1) v += __shfl_xor_sync(0xffffffffu, v, lane);
                      return v;
                    }
                    """),
            Map.entry("tc_mma_fragment", """
                    __device__ static inline float4 tc_mma_fragment(float v) {
                      return make_float4(v, v, v, v);
                    }
                    """),
            // A: a 16 x k row-major tile of elementBytes-wide elements (rows of k * elementBytes bytes);
            // ldmatrix .x4 gives the four A registers (f16: k = 16, 2 bytes; s8: k = 32, 1 byte).
            Map.entry("tc_mma_load_a", """
                    __device__ static inline int4 tc_mma_load_a(const int *tile, int k, int elementBytes) {
                      unsigned lane = threadIdx.x & 31u, group = lane >> 3;
                      unsigned row = ((group & 1u) << 3) + (lane & 7u);
                      unsigned offset = row * (unsigned) (k * elementBytes) + ((group >> 1) << 4);
                      unsigned address = (unsigned) __cvta_generic_to_shared((const char *) tile + offset);
                      int4 a;
                      asm volatile("ldmatrix.sync.aligned.m8n8.x4.shared.b16 {%0,%1,%2,%3}, [%4];"
                                   : "=r"(a.x), "=r"(a.y), "=r"(a.z), "=r"(a.w) : "r"(address));
                      return a;
                    }
                    """),
            // A from global memory: lane l, register i holds row l/4 + 8(i%2), columns 2(l%4) + 8(i/2)
            // and the next one of the 16 x 16 tile at a[row * ld + col]: one 32-bit load per register.
            Map.entry("tc_mma_load_a_global", """
                    __device__ static inline int4 tc_mma_load_a_global(const unsigned short *a, int row, int col, int ld) {
                      unsigned lane = threadIdx.x & 31u;
                      const unsigned short *p = a + (long long) (row + (int) (lane >> 2)) * ld + col + (int) ((lane & 3u) << 1);
                      long long down = 8LL * ld;
                      int4 r;
                      r.x = *(const int *) p;
                      r.y = *(const int *) (p + down);
                      r.z = *(const int *) (p + 8);
                      r.w = *(const int *) (p + down + 8);
                      return r;
                    }
                    """),
            // B from global memory: each lane loads a half pair along n of the two 8 x 8 halves
            // (rows k and k + 8), and movmatrix transposes them into the column-major B registers.
            Map.entry("tc_mma_load_b_global", """
                    __device__ static inline int2 tc_mma_load_b_global(const unsigned short *b, int row, int col, int ld) {
                      unsigned lane = threadIdx.x & 31u;
                      const unsigned short *p = b + (long long) (row + (int) (lane >> 2)) * ld + col + (int) ((lane & 3u) << 1);
                      int lo = *(const int *) p, hi = *(const int *) (p + 8LL * ld);
                      int2 r;
                      asm volatile("movmatrix.sync.aligned.m8n8.trans.b16 %0, %1;" : "=r"(r.x) : "r"(lo));
                      asm volatile("movmatrix.sync.aligned.m8n8.trans.b16 %0, %1;" : "=r"(r.y) : "r"(hi));
                      return r;
                    }
                    """),
            // B: a k x 8 row-major half tile (rows of 16 bytes), transposed by ldmatrix into the two B registers.
            Map.entry("tc_mma_load_b", """
                    __device__ static inline int2 tc_mma_load_b(const int *tile, int k) {
                      unsigned lane = threadIdx.x & 31u;
                      unsigned row = (lane & 7u) + (((lane >> 3) & 1u) << 3);
                      unsigned address = (unsigned) __cvta_generic_to_shared((const char *) tile + (row << 4));
                      int2 b;
                      asm volatile("ldmatrix.sync.aligned.m8n8.x2.trans.shared.b16 {%0,%1}, [%2];"
                                   : "=r"(b.x), "=r"(b.y) : "r"(address));
                      return b;
                    }
                    """),
            Map.entry("tc_mma_load_b_swizzled", """
                    __device__ static inline int2 tc_mma_load_b_swizzled(const void *tile, int k, int byteOffset) {
                      unsigned lane = threadIdx.x & 31u;
                      unsigned offset = ((lane & 7u) << 4) + (((lane >> 3) & 1u) << 7);
                      offset ^= ((offset >> 7) & 7u) << 4;
                      unsigned address = (unsigned) __cvta_generic_to_shared((const char *) tile + byteOffset + offset);
                      int2 b;
                      asm volatile("ldmatrix.sync.aligned.m8n8.x2.trans.shared.b16 {%0,%1}, [%2];"
                                   : "=r"(b.x), "=r"(b.y) : "r"(address));
                      return b;
                    }
                    """),
            Map.entry("tc_mma_store_b_swizzled", """
                    __device__ static inline void tc_mma_store_b_swizzled(void *tile, int row, int col, int stride, half value, int byteOffset) {
                      unsigned offset = (unsigned) (row * stride + col) * 2u;
                      offset ^= ((offset >> 7) & 7u) << 4;
                      *((half *) ((char *) tile + byteOffset + offset)) = value;
                    }
                    """),
            Map.entry("tc_mma_fragment_int", """
                    __device__ static inline int4 tc_mma_fragment_int(int v) {
                      return make_int4(v, v, v, v);
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_s8", """
                    __device__ static inline int4 tc_mma_m16n8k32_s8(int4 a, int2 b, int4 c) {
                      int4 d;
                      asm volatile("mma.sync.aligned.m16n8k32.row.col.s32.s8.s8.s32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};"
                                   : "=r"(d.x), "=r"(d.y), "=r"(d.z), "=r"(d.w)
                                   : "r"(a.x), "r"(a.y), "r"(a.z), "r"(a.w), "r"(b.x), "r"(b.y), "r"(c.x), "r"(c.y), "r"(c.z), "r"(c.w));
                      return d;
                    }
                    """),
            Map.entry("tc_mma_store_int", """
                    __device__ static inline void tc_mma_store_int(int4 d, int *c, int row, int col, int ld) {
                      unsigned lane = threadIdx.x & 31u;
                      int r = row + (int) (lane >> 2), k = col + (int) ((lane & 3u) << 1);
                      c[r * ld + k] = d.x;
                      c[r * ld + k + 1] = d.y;
                      c[(r + 8) * ld + k] = d.z;
                      c[(r + 8) * ld + k + 1] = d.w;
                    }
                    """),
            // Same fragments, other input formats: bf16 (m16n8k16) and FP8 E4M3/E5M2 (m16n8k32, sm_89+).
            Map.entry("tc_mma_m16n8k16_bf16", """
                    __device__ static inline float4 tc_mma_m16n8k16_bf16(int4 a, int2 b, float4 c) {
                      float4 d;
                      asm volatile("mma.sync.aligned.m16n8k16.row.col.f32.bf16.bf16.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};"
                                   : "=f"(d.x), "=f"(d.y), "=f"(d.z), "=f"(d.w)
                                   : "r"(a.x), "r"(a.y), "r"(a.z), "r"(a.w), "r"(b.x), "r"(b.y), "f"(c.x), "f"(c.y), "f"(c.z), "f"(c.w));
                      return d;
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_e4m3", """
                    __device__ static inline float4 tc_mma_m16n8k32_e4m3(int4 a, int2 b, float4 c) {
                      float4 d;
                      asm volatile("mma.sync.aligned.m16n8k32.row.col.f32.e4m3.e4m3.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};"
                                   : "=f"(d.x), "=f"(d.y), "=f"(d.z), "=f"(d.w)
                                   : "r"(a.x), "r"(a.y), "r"(a.z), "r"(a.w), "r"(b.x), "r"(b.y), "f"(c.x), "f"(c.y), "f"(c.z), "f"(c.w));
                      return d;
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_e5m2", """
                    __device__ static inline float4 tc_mma_m16n8k32_e5m2(int4 a, int2 b, float4 c) {
                      float4 d;
                      asm volatile("mma.sync.aligned.m16n8k32.row.col.f32.e5m2.e5m2.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};"
                                   : "=f"(d.x), "=f"(d.y), "=f"(d.z), "=f"(d.w)
                                   : "r"(a.x), "r"(a.y), "r"(a.z), "r"(a.w), "r"(b.x), "r"(b.y), "f"(c.x), "f"(c.y), "f"(c.z), "f"(c.w));
                      return d;
                    }
                    """),
            Map.entry("tc_mma_m16n8k16", """
                    __device__ static inline float4 tc_mma_m16n8k16(int4 a, int2 b, float4 c) {
                      float4 d;
                      asm volatile("mma.sync.aligned.m16n8k16.row.col.f32.f16.f16.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%10,%11,%12,%13};"
                                   : "=f"(d.x), "=f"(d.y), "=f"(d.z), "=f"(d.w)
                                   : "r"(a.x), "r"(a.y), "r"(a.z), "r"(a.w), "r"(b.x), "r"(b.y), "f"(c.x), "f"(c.y), "f"(c.z), "f"(c.w));
                      return d;
                    }
                    """),
            // C/D: lane l holds rows l/4 and l/4 + 8, columns 2(l%4) and 2(l%4) + 1 of the 16 x 8 tile.
            Map.entry("tc_mma_store", """
                    __device__ static inline void tc_mma_store(float4 d, float *c, int row, int col, int ld) {
                      unsigned lane = threadIdx.x & 31u;
                      int r = row + (int) (lane >> 2), k = col + (int) ((lane & 3u) << 1);
                      c[r * ld + k] = d.x;
                      c[r * ld + k + 1] = d.y;
                      c[(r + 8) * ld + k] = d.z;
                      c[(r + 8) * ld + k + 1] = d.w;
                    }
                    """),
            // cp.async (PTX ISA 7.0, sm_80): .ca caches the 4-byte copy in L1, .cg (16 bytes only) bypasses it.
            Map.entry("tc_cp_async_4", """
                    __device__ static inline void tc_cp_async_4(int *dst, const char *src) {
                      unsigned s = (unsigned) __cvta_generic_to_shared(dst);
                      asm volatile("cp.async.ca.shared.global [%0], [%1], 4;" :: "r"(s), "l"(src) : "memory");
                    }
                    """),
            Map.entry("tc_cp_async_16", """
                    __device__ static inline void tc_cp_async_16(int *dst, const char *src) {
                      unsigned s = (unsigned) __cvta_generic_to_shared(dst);
                      asm volatile("cp.async.cg.shared.global [%0], [%1], 16;" :: "r"(s), "l"(src) : "memory");
                    }
                    """),
            Map.entry("tc_cp_async_commit", """
                    __device__ static inline void tc_cp_async_commit() {
                      asm volatile("cp.async.commit_group;" ::: "memory");
                    }
                    """),
            // wait_group takes an immediate: the switch folds away for a constant argument.
            Map.entry("tc_cp_async_wait", """
                    __device__ static inline void tc_cp_async_wait(int n) {
                      switch (n) {
                        case 0: asm volatile("cp.async.wait_group 0;" ::: "memory"); break;
                        case 1: asm volatile("cp.async.wait_group 1;" ::: "memory"); break;
                        case 2: asm volatile("cp.async.wait_group 2;" ::: "memory"); break;
                        case 3: asm volatile("cp.async.wait_group 3;" ::: "memory"); break;
                        default: asm volatile("cp.async.wait_all;" ::: "memory"); break;
                      }
                    }
                    """),
            // After a device-side launch: report a failed launch, and the launch itself with --threadInfo.
            Map.entry("tc_device_launch_check", """
                    __device__ static inline void tc_device_launch_check(const long long *context, const char *child, const char *label, unsigned gx, unsigned gy, unsigned gz, dim3 grid, dim3 block) {
                      cudaError_t error = cudaGetLastError();
                      if (error != cudaSuccess) printf("[TornadoVM-CUDA] device launch of %s failed: %s\\n", child, cudaGetErrorString(error));
                      if (context[3]) printf("Task info: %s\\n\\tLaunched by       : block [%u, %u, %u] thread [%u, %u, %u]\\n\\tGlobal work size  : [%u, %u, %u]\\n"
                                             "\\tLocal  work size  : [%u, %u, %u]\\n\\tNumber of workgroups  : [%u, %u, %u]\\n\\n", label,
                                             blockIdx.x, blockIdx.y, blockIdx.z, threadIdx.x, threadIdx.y, threadIdx.z, gx, gy, gz, block.x, block.y, block.z, grid.x, grid.y, grid.z);
                    }
                    """),
            Map.entry("java_fmax", """
                    __device__ static inline double java_fmax(double a, double b) {
                      if (a != a || b != b) return __longlong_as_double(0x7ff8000000000000LL);
                      if (a == 0.0 && b == 0.0) return __longlong_as_double(__double_as_longlong(a) & __double_as_longlong(b));
                      return a > b ? a : b;
                    }
                    """));

    /**
     * What every HIP MMA helper builds on: the matrix-core family of the target (from the architecture
     * macros hipRTC defines), the operand vector types, fetching a k-word of the A and B fragments from
     * the lane that holds it, and moving a result into the PTX C/D layout. Guarded, so that a kernel with
     * several MMA helpers defines it once.
     */
    private static final String MMA_COMMON = """
                    #ifndef TC_MMA_COMMON
                    #define TC_MMA_COMMON
                    #if defined(__gfx908__) || defined(__gfx90a__) || defined(__gfx940__) || defined(__gfx941__) || defined(__gfx942__) || defined(__gfx950__)
                    #define TC_MFMA 1
                    #elif defined(__GFX11__)
                    #define TC_WMMA11 1
                    #elif defined(__gfx1200__) || defined(__gfx1201__)
                    #define TC_WMMA12 1
                    #elif defined(__gfx1250__)
                    #define TC_WMMA1250 1
                    #else
                    #error "KernelContext MMA: no matrix-core lowering for this GPU architecture"
                    #endif
                    typedef _Float16 tc_h4_t __attribute__((ext_vector_type(4)));
                    typedef _Float16 tc_h8_t __attribute__((ext_vector_type(8)));
                    typedef _Float16 tc_h16_t __attribute__((ext_vector_type(16)));
                    typedef short tc_s2_t __attribute__((ext_vector_type(2)));
                    typedef short tc_s4_t __attribute__((ext_vector_type(4)));
                    typedef short tc_s8_t __attribute__((ext_vector_type(8)));
                    typedef short tc_s16_t __attribute__((ext_vector_type(16)));
                    typedef float tc_f4_t __attribute__((ext_vector_type(4)));
                    typedef float tc_f8_t __attribute__((ext_vector_type(8)));
                    typedef int tc_i2_t __attribute__((ext_vector_type(2)));
                    typedef int tc_i4_t __attribute__((ext_vector_type(4)));
                    typedef int tc_i8_t __attribute__((ext_vector_type(8)));
                    #if defined(TC_WMMA1250)
                    typedef __bf16 tc_b16_t __attribute__((ext_vector_type(16)));
                    #endif
                    // A fragment lives in the registers of a 32-lane warp in the PTX m16n8 layouts: register i of lane
                    // 4g + t holds k-word 4(i/2) + t of row g + 8(i%2) of A; register j holds k-word 4j + t of column g of
                    // B. A k-word is 2 halves (k16 shapes) or 4 bytes (k32 shapes). These fetch k-word w = 4wq + wl of a
                    // row of A or a column of B from the lane of the calling warp that holds it.
                    __device__ static inline int tc_mma_lane(int l) {
                      return (int) (__lane_id() & ~31u) + l;
                    }
                    __device__ static inline int tc_mma_a_word(int4 a, int r, int wq, int wl) {
                      int src = tc_mma_lane(4 * (r & 7) + wl);
                      int lo = __shfl(wq ? a.z : a.x, src), hi = __shfl(wq ? a.w : a.y, src);
                      return r < 8 ? lo : hi;
                    }
                    __device__ static inline int tc_mma_a_word_any(int4 a, int r, int wq, int wl) {
                      int src = tc_mma_lane(4 * (r & 7) + wl);
                      int x = __shfl(a.x, src), y = __shfl(a.y, src), z = __shfl(a.z, src), w = __shfl(a.w, src);
                      return wq ? (r < 8 ? z : w) : (r < 8 ? x : y);
                    }
                    __device__ static inline int tc_mma_b_word(int2 b, int n, int wq, int wl) {
                      return __shfl(wq ? b.y : b.x, tc_mma_lane(4 * (n & 7) + wl));
                    }
                    __device__ static inline int tc_mma_b_word_any(int2 b, int n, int wq, int wl) {
                      int src = tc_mma_lane(4 * (n & 7) + wl);
                      int x = __shfl(b.x, src), y = __shfl(b.y, src);
                      return wq ? y : x;
                    }
                    // Element e of the calling lane's C/D fragment is row g + 8(e/2), column 2t + e%2 of the 16 x 8 tile.
                    __device__ static inline int tc_mma_row(int e) {
                      return (int) ((__lane_id() & 31u) >> 2) + 8 * (e >> 1);
                    }
                    __device__ static inline int tc_mma_col(int e) {
                      return 2 * (int) (__lane_id() & 3u) + (e & 1);
                    }
                    // Four FP8 values (one k-word) as halves, exactly (every E4M3 and E5M2 value is a half).
                    __device__ static inline _Float16 tc_mma_e4m3(unsigned b) {
                      unsigned x = (b >> 3) & 15u, m = b & 7u;
                      float v = x == 0u ? (float) m * 0x1p-9f : (x == 15u && m == 7u) ? __int_as_float(0x7fc00000) : __uint_as_float(((x + 120u) << 23) | (m << 20));
                      return (_Float16) ((b & 0x80u) ? -v : v);
                    }
                    __device__ static inline _Float16 tc_mma_e5m2(unsigned b) {
                      return __builtin_bit_cast(_Float16, (unsigned short) (b << 8));
                    }
                    template <bool E4M3> __device__ static inline tc_h4_t tc_mma_fp8_word(int w) {
                      unsigned u = (unsigned) w;
                      tc_h4_t h;
                      for (int e = 0; e < 4; e++) {
                        unsigned b = (u >> (8 * e)) & 0xffu;
                        h[e] = E4M3 ? tc_mma_e4m3(b) : tc_mma_e5m2(b);
                      }
                      return h;
                    }
                    #if defined(TC_MFMA)
                    // Each 32-lane warp computes its 16 x 8 tile as eight 4 x 4 blocks of the 4x4 MFMA instructions, which
                    // keep every block within four lanes: a warp's data never meets another warp's, so the wavefront's
                    // other half may be idle. Lane 4(2bm + bn) + x supplies row 4bm + x of A and column 4bn + x of B for
                    // block (bm, bn), and receives column 4bn + x of its result.
                    __device__ static inline int tc_mfma_row() {
                      int l = (int) (__lane_id() & 31u);
                      return 4 * (l >> 3) + (l & 3);
                    }
                    __device__ static inline int tc_mfma_col() {
                      int l = (int) (__lane_id() & 31u);
                      return 4 * ((l >> 2) & 1) + (l & 3);
                    }
                    template <typename V, typename T> __device__ static inline T tc_mfma_out(V d, int e) {
                      int row = tc_mma_row(e), col = tc_mma_col(e);
                      int src = tc_mma_lane(4 * (2 * (row >> 2) + (col >> 2)) + (col & 3));
                      T x = __shfl(d[0], src), y = __shfl(d[1], src), z = __shfl(d[2], src), w = __shfl(d[3], src);
                      int i = row & 3;
                      return i == 0 ? x : i == 1 ? y : i == 2 ? z : w;
                    }
                    #elif defined(TC_WMMA11)
                    // One warp is the whole wavefront and computes a 16 x 16 tile with one WMMA instruction; B is the
                    // warp's 16 x 8 tile padded with zero columns. Lane l supplies row l % 16 of A and column l % 16 of B
                    // (every k), and receives column l % 16 of rows 2i + l / 16 of the result.
                    template <typename V, typename T> __device__ static inline T tc_wmma_out(V d, int e) {
                      int row = tc_mma_row(e), col = tc_mma_col(e), src = col + 16 * (row & 1), hi = 4 * (e >> 1);
                      T x = __shfl(d[hi], src), y = __shfl(d[hi + 1], src), z = __shfl(d[hi + 2], src), w = __shfl(d[hi + 3], src);
                      int i = (row >> 1) & 3;
                      return i == 0 ? x : i == 1 ? y : i == 2 ? z : w;
                    }
                    #elif defined(TC_WMMA12) || defined(TC_WMMA1250)
                    // As on RDNA3, but lane l supplies k = 8(l / 16) .. 8(l / 16) + 7 of row l % 16 of A and of column
                    // l % 16 of B, and receives column l % 16 of rows 8(l / 16) + i of the result. gfx1250's longer
                    // forms repeat that every 16 k: lane l holds k = 16j + 8(l / 16) .. 16j + 8(l / 16) + 7 for each j.
                    template <typename V, typename T> __device__ static inline T tc_wmma_out(V d, int e) {
                      int row = tc_mma_row(e), col = tc_mma_col(e), src = col + 16 * (row >> 3);
                      T v[8];
                      for (int i = 0; i < 8; i++) {
                        v[i] = __shfl(d[i], src);
                      }
                      T r = v[0];
                      for (int i = 1; i < 8; i++) {
                        r = (row & 7) == i ? v[i] : r;
                      }
                      return r;
                    }
                    #endif
                    #if defined(TC_WMMA1250)
                    // The A and B operands of gfx1250's 16x16x64 8-bit forms for a k32 fragment pair (k 32-63 zero): lane
                    // l holds k-words 2(l / 16), 2(l / 16) + 1, 4 + 2(l / 16) and 5 + 2(l / 16) of row l % 16 of A and of
                    // column l % 16 of B, the columns past the warp's 8 being zero.
                    __device__ static inline void tc_wmma1250_k32(int4 a, int2 b, tc_i8_t &av, tc_i8_t &bv) {
                      int l = (int) __lane_id(), m = l & 15, half = l >> 4;
                      av = {};
                      bv = {};
                      for (int kb = 0; kb < 2; kb++) {
                        for (int h = 0; h < 2; h++) {
                          av[2 * kb + h] = tc_mma_a_word(a, m, kb, 2 * half + h);
                          int bword = tc_mma_b_word(b, m, kb, 2 * half + h);
                          bv[2 * kb + h] = m < 8 ? bword : 0;
                        }
                      }
                    }
                    #endif
                    // D = A x B + C for 16-bit inputs (m16n8k16): CVT names the half or bfloat16 vector types.
                    template <bool BF16> __device__ static inline float4 tc_mma_k16(int4 a, int2 b, float4 c) {
                    #if defined(TC_MFMA)
                      int ra = tc_mfma_row(), cb = tc_mfma_col();
                      tc_f4_t d = {0.0f, 0.0f, 0.0f, 0.0f};
                    #if defined(__gfx908__)
                      if (BF16) {
                        for (int w = 0; w < 8; w++) {
                          tc_s2_t av = __builtin_bit_cast(tc_s2_t, tc_mma_a_word(a, ra, w >> 2, w & 3));
                          tc_s2_t bv = __builtin_bit_cast(tc_s2_t, tc_mma_b_word(b, cb, w >> 2, w & 3));
                          d = __builtin_amdgcn_mfma_f32_4x4x2bf16(av, bv, d, 0, 0, 0);
                        }
                      } else
                    #endif
                      for (int s = 0; s < 4; s++) {
                        tc_i2_t av = {tc_mma_a_word(a, ra, s >> 1, (2 * s) & 3), tc_mma_a_word(a, ra, s >> 1, (2 * s + 1) & 3)};
                        tc_i2_t bv = {tc_mma_b_word(b, cb, s >> 1, (2 * s) & 3), tc_mma_b_word(b, cb, s >> 1, (2 * s + 1) & 3)};
                    #if !defined(__gfx908__)
                        if (BF16) {
                          d = __builtin_amdgcn_mfma_f32_4x4x4bf16_1k(__builtin_bit_cast(tc_s4_t, av), __builtin_bit_cast(tc_s4_t, bv), d, 0, 0, 0);
                        } else
                    #endif
                        {
                          d = __builtin_amdgcn_mfma_f32_4x4x4f16(__builtin_bit_cast(tc_h4_t, av), __builtin_bit_cast(tc_h4_t, bv), d, 0, 0, 0);
                        }
                      }
                      return make_float4(c.x + tc_mfma_out<tc_f4_t, float>(d, 0), c.y + tc_mfma_out<tc_f4_t, float>(d, 1), c.z + tc_mfma_out<tc_f4_t, float>(d, 2),
                          c.w + tc_mfma_out<tc_f4_t, float>(d, 3));
                    #elif defined(TC_WMMA11)
                      int l = (int) __lane_id(), m = l & 15;
                      tc_i8_t aw, bw;
                      for (int w = 0; w < 8; w++) {
                        aw[w] = tc_mma_a_word(a, m, w >> 2, w & 3);
                        int bword = tc_mma_b_word(b, m, w >> 2, w & 3);
                        bw[w] = m < 8 ? bword : 0;
                      }
                      tc_f8_t z = {};
                      tc_f8_t d = BF16 ? __builtin_amdgcn_wmma_f32_16x16x16_bf16_w32(__builtin_bit_cast(tc_s16_t, aw), __builtin_bit_cast(tc_s16_t, bw), z)
                                       : __builtin_amdgcn_wmma_f32_16x16x16_f16_w32(__builtin_bit_cast(tc_h16_t, aw), __builtin_bit_cast(tc_h16_t, bw), z);
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #elif defined(TC_WMMA12)
                      int l = (int) __lane_id(), m = l & 15, wq = l >> 4;
                      tc_i4_t aw, bw;
                      for (int j = 0; j < 4; j++) {
                        aw[j] = tc_mma_a_word_any(a, m, wq, j);
                        int bword = tc_mma_b_word_any(b, m, wq, j);
                        bw[j] = m < 8 ? bword : 0;
                      }
                      tc_f8_t z = {};
                      tc_f8_t d = BF16 ? __builtin_amdgcn_wmma_f32_16x16x16_bf16_w32_gfx12(__builtin_bit_cast(tc_s8_t, aw), __builtin_bit_cast(tc_s8_t, bw), z)
                                       : __builtin_amdgcn_wmma_f32_16x16x16_f16_w32_gfx12(__builtin_bit_cast(tc_h8_t, aw), __builtin_bit_cast(tc_h8_t, bw), z);
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #elif defined(TC_WMMA1250)
                      // 16x16x32 with k 16-31 zero: the first 8 values of each lane are the RDNA4 operand.
                      int l = (int) __lane_id(), m = l & 15, wq = l >> 4;
                      tc_i8_t aw = {}, bw = {};
                      for (int j = 0; j < 4; j++) {
                        aw[j] = tc_mma_a_word_any(a, m, wq, j);
                        int bword = tc_mma_b_word_any(b, m, wq, j);
                        bw[j] = m < 8 ? bword : 0;
                      }
                      tc_f8_t z = {};
                      tc_f8_t d = BF16 ? __builtin_amdgcn_wmma_f32_16x16x32_bf16(false, __builtin_bit_cast(tc_b16_t, aw), false, __builtin_bit_cast(tc_b16_t, bw), (short) 0, z, false, false)
                                       : __builtin_amdgcn_wmma_f32_16x16x32_f16(false, __builtin_bit_cast(tc_h16_t, aw), false, __builtin_bit_cast(tc_h16_t, bw), (short) 0, z, false, false);
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #endif
                    }
                    // D = A x B + C for FP8 inputs (m16n8k32), widened exactly to halves and multiplied as halves.
                    template <bool E4M3> __device__ static inline float4 tc_mma_k32_fp8(int4 a, int2 b, float4 c) {
                    #if defined(TC_MFMA)
                      int ra = tc_mfma_row(), cb = tc_mfma_col();
                      tc_f4_t d = {0.0f, 0.0f, 0.0f, 0.0f};
                      for (int w = 0; w < 8; w++) {
                        tc_h4_t av = tc_mma_fp8_word<E4M3>(tc_mma_a_word(a, ra, w >> 2, w & 3));
                        tc_h4_t bv = tc_mma_fp8_word<E4M3>(tc_mma_b_word(b, cb, w >> 2, w & 3));
                        d = __builtin_amdgcn_mfma_f32_4x4x4f16(av, bv, d, 0, 0, 0);
                      }
                      return make_float4(c.x + tc_mfma_out<tc_f4_t, float>(d, 0), c.y + tc_mfma_out<tc_f4_t, float>(d, 1), c.z + tc_mfma_out<tc_f4_t, float>(d, 2),
                          c.w + tc_mfma_out<tc_f4_t, float>(d, 3));
                    #elif defined(TC_WMMA11)
                      int l = (int) __lane_id(), m = l & 15;
                      tc_f8_t d = {};
                      for (int kb = 0; kb < 2; kb++) {
                        tc_h16_t av, bv;
                        for (int j = 0; j < 4; j++) {
                          tc_h4_t ah = tc_mma_fp8_word<E4M3>(tc_mma_a_word(a, m, kb, j));
                          tc_h4_t bh = tc_mma_fp8_word<E4M3>(tc_mma_b_word(b, m, kb, j));
                          for (int e = 0; e < 4; e++) {
                            av[4 * j + e] = ah[e];
                            bv[4 * j + e] = m < 8 ? bh[e] : (_Float16) 0.0f;
                          }
                        }
                        d = __builtin_amdgcn_wmma_f32_16x16x16_f16_w32(av, bv, d);
                      }
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #elif defined(TC_WMMA12)
                      int l = (int) __lane_id(), m = l & 15, half = l >> 4;
                      tc_f8_t d = {};
                      for (int kb = 0; kb < 2; kb++) {
                        tc_h8_t av, bv;
                        for (int h = 0; h < 2; h++) {
                          tc_h4_t ah = tc_mma_fp8_word<E4M3>(tc_mma_a_word(a, m, kb, 2 * half + h));
                          tc_h4_t bh = tc_mma_fp8_word<E4M3>(tc_mma_b_word(b, m, kb, 2 * half + h));
                          for (int e = 0; e < 4; e++) {
                            av[4 * h + e] = ah[e];
                            bv[4 * h + e] = m < 8 ? bh[e] : (_Float16) 0.0f;
                          }
                        }
                        d = __builtin_amdgcn_wmma_f32_16x16x16_f16_w32_gfx12(av, bv, d);
                      }
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #elif defined(TC_WMMA1250)
                      // gfx1250 multiplies E4M3 (fp8) and E5M2 (bf8) natively.
                      tc_i8_t av, bv;
                      tc_wmma1250_k32(a, b, av, bv);
                      tc_f8_t z = {};
                      tc_f8_t d = E4M3 ? __builtin_amdgcn_wmma_f32_16x16x64_fp8_fp8(av, bv, (short) 0, z, false, false)
                                       : __builtin_amdgcn_wmma_f32_16x16x64_bf8_bf8(av, bv, (short) 0, z, false, false);
                      return make_float4(c.x + tc_wmma_out<tc_f8_t, float>(d, 0), c.y + tc_wmma_out<tc_f8_t, float>(d, 1), c.z + tc_wmma_out<tc_f8_t, float>(d, 2),
                          c.w + tc_wmma_out<tc_f8_t, float>(d, 3));
                    #endif
                    }
                    #endif
                    """;

    /**
     * HIP helpers where the CUDA one does not apply; every other helper is the CUDA one, since HIP C++
     * accepts it unchanged. The warp operations name their width: a KernelContext warp is 32 lanes.
     */
    private static final Map<String, String> HIP = Map.ofEntries(
            Map.entry("tc_warp_sum", """
                    __device__ static inline float tc_warp_sum(float v) {
                      for (int lane = 16; lane > 0; lane >>= 1) v += __shfl_xor(v, lane, 32);
                      return v;
                    }
                    """),
            // The 32 ballot bits of the lane's warp: the low half of a 64-wide wavefront's ballot for
            // lanes 0-31, the high half for lanes 32-63 (all of it on a 32-wide wavefront).
            Map.entry("tc_warp_bits", """
                    __device__ static inline unsigned tc_warp_bits(unsigned long long ballot) {
                      return (unsigned) (ballot >> (__lane_id() & 32u));
                    }
                    """),
            // Signed int8 dot product of the four bytes of a and b, plus c (CUDA's __dp4a).
            Map.entry("tc_dp4a", """
                    __device__ static inline int tc_dp4a(int a, int b, int c) {
                      return c + (int) (signed char) a * (int) (signed char) b + (int) (signed char) (a >> 8) * (int) (signed char) (b >> 8)
                          + (int) (signed char) (a >> 16) * (int) (signed char) (b >> 16) + (int) (a >> 24) * (int) (b >> 24);
                    }
                    """),
            // cp.async as a plain copy: the slot is written before the copying thread reaches its wait, so
            // commit and wait have nothing to do (the barrier after the wait publishes it to the block).
            Map.entry("tc_cp_async_4", """
                    __device__ static inline void tc_cp_async_4(int *dst, const char *src) {
                      *dst = *(const int *) src;
                    }
                    """),
            Map.entry("tc_cp_async_16", """
                    __device__ static inline void tc_cp_async_16(int *dst, const char *src) {
                      *(int4 *) dst = *(const int4 *) src;
                    }
                    """),
            Map.entry("tc_cp_async_commit", """
                    __device__ static inline void tc_cp_async_commit() {
                    }
                    """),
            Map.entry("tc_cp_async_wait", """
                    __device__ static inline void tc_cp_async_wait(int n) {
                    }
                    """),
            // Tensor-core MMA on AMD matrix cores. Fragments keep the PTX m16n8 layouts KernelContext documents
            // (the loads read what ldmatrix and movmatrix would); mma moves them into the operand layouts of
            // MFMA (CDNA, 4x4 blocks within each warp) or WMMA (RDNA3, RDNA4, gfx1250) and the result back.
            Map.entry("tc_mma_load_a", """
                    // A: a 16 x k row-major tile of elementBytes-wide elements, as ldmatrix .x4 reads it: register j holds
                    // row 8(j%2) + g, bytes 16(j/2) + 4t of the tile.
                    __device__ static inline int4 tc_mma_load_a(const int *tile, int k, int elementBytes) {
                      unsigned lane = threadIdx.x & 31u, g = lane >> 2, t = lane & 3u, row = (unsigned) (k * elementBytes);
                      const char *p = (const char *) tile + 4u * t;
                      int4 a;
                      a.x = *(const int *) (p + g * row);
                      a.y = *(const int *) (p + (g + 8u) * row);
                      a.z = *(const int *) (p + g * row + 16u);
                      a.w = *(const int *) (p + (g + 8u) * row + 16u);
                      return a;
                    }
                    """),
            Map.entry("tc_mma_load_b", """
                    // B: a k x 8 row-major tile of 16-bit pairs (rows of 16 bytes), as ldmatrix .x2 .trans reads it: register
                    // j holds column g of rows 8j + 2t and 8j + 2t + 1.
                    __device__ static inline int2 tc_mma_load_b(const int *tile, int k) {
                      unsigned lane = threadIdx.x & 31u, g = lane >> 2, t = lane & 3u;
                      const unsigned short *p = (const unsigned short *) tile + g;
                      int2 b;
                      b.x = (int) (p[16u * t] | ((unsigned) p[16u * t + 8u] << 16));
                      b.y = (int) (p[16u * t + 64u] | ((unsigned) p[16u * t + 72u] << 16));
                      return b;
                    }
                    """),
            Map.entry("tc_mma_load_b_swizzled", """
                    // As tc_mma_load_b from a tile whose rows were stored with the stride-32 swizzle (row r of the second
                    // 8-row half at bytes 128 + 16(r ^ 1)).
                    __device__ static inline int2 tc_mma_load_b_swizzled(const void *tile, int k, int byteOffset) {
                      unsigned lane = threadIdx.x & 31u, g = lane >> 2, t = lane & 3u;
                      const unsigned char *p = (const unsigned char *) tile + byteOffset + 2u * g;
                      int2 b;
                      b.x = (int) (*(const unsigned short *) (p + 32u * t) | ((unsigned) *(const unsigned short *) (p + 32u * t + 16u) << 16));
                      b.y = (int) (*(const unsigned short *) (p + 128u + ((32u * t) ^ 16u)) | ((unsigned) *(const unsigned short *) (p + 128u + ((32u * t + 16u) ^ 16u)) << 16));
                      return b;
                    }
                    """),
            Map.entry("tc_mma_load_b_global", """
                    // B from global memory: register i holds column col + g of rows row + 8i + 2t and the next one.
                    __device__ static inline int2 tc_mma_load_b_global(const unsigned short *b, int row, int col, int ld) {
                      unsigned lane = threadIdx.x & 31u;
                      long long l = ld;
                      const unsigned short *p = b + (long long) (row + 2 * (int) (lane & 3u)) * l + col + (int) (lane >> 2);
                      int2 r;
                      r.x = (int) (p[0] | ((unsigned) p[l] << 16));
                      r.y = (int) (p[8 * l] | ((unsigned) p[9 * l] << 16));
                      return r;
                    }
                    """),
            Map.entry("tc_mma_m16n8k16", MMA_COMMON + """
                    __device__ static inline float4 tc_mma_m16n8k16(int4 a, int2 b, float4 c) {
                      return tc_mma_k16<false>(a, b, c);
                    }
                    """),
            Map.entry("tc_mma_m16n8k16_bf16", MMA_COMMON + """
                    __device__ static inline float4 tc_mma_m16n8k16_bf16(int4 a, int2 b, float4 c) {
                      return tc_mma_k16<true>(a, b, c);
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_s8", MMA_COMMON + """
                    __device__ static inline int4 tc_mma_m16n8k32_s8(int4 a, int2 b, int4 c) {
                    #if defined(TC_MFMA)
                      int ra = tc_mfma_row(), cb = tc_mfma_col();
                      tc_i4_t d = {0, 0, 0, 0};
                      for (int w = 0; w < 8; w++) {
                        d = __builtin_amdgcn_mfma_i32_4x4x4i8(tc_mma_a_word(a, ra, w >> 2, w & 3), tc_mma_b_word(b, cb, w >> 2, w & 3), d, 0, 0, 0);
                      }
                      return make_int4(c.x + tc_mfma_out<tc_i4_t, int>(d, 0), c.y + tc_mfma_out<tc_i4_t, int>(d, 1), c.z + tc_mfma_out<tc_i4_t, int>(d, 2), c.w + tc_mfma_out<tc_i4_t, int>(d, 3));
                    #elif defined(TC_WMMA11)
                      int l = (int) __lane_id(), m = l & 15;
                      tc_i8_t d = {};
                      for (int kb = 0; kb < 2; kb++) {
                        tc_i4_t av, bv;
                        for (int j = 0; j < 4; j++) {
                          av[j] = tc_mma_a_word(a, m, kb, j);
                          int bword = tc_mma_b_word(b, m, kb, j);
                          bv[j] = m < 8 ? bword : 0;
                        }
                        d = __builtin_amdgcn_wmma_i32_16x16x16_iu8_w32(true, av, true, bv, d, false);
                      }
                      return make_int4(c.x + tc_wmma_out<tc_i8_t, int>(d, 0), c.y + tc_wmma_out<tc_i8_t, int>(d, 1), c.z + tc_wmma_out<tc_i8_t, int>(d, 2), c.w + tc_wmma_out<tc_i8_t, int>(d, 3));
                    #elif defined(TC_WMMA12)
                      int l = (int) __lane_id(), m = l & 15, half = l >> 4;
                      tc_i8_t d = {};
                      for (int kb = 0; kb < 2; kb++) {
                        tc_i2_t av, bv;
                        for (int h = 0; h < 2; h++) {
                          av[h] = tc_mma_a_word(a, m, kb, 2 * half + h);
                          int bword = tc_mma_b_word(b, m, kb, 2 * half + h);
                          bv[h] = m < 8 ? bword : 0;
                        }
                        d = __builtin_amdgcn_wmma_i32_16x16x16_iu8_w32_gfx12(true, av, true, bv, d, false);
                      }
                      return make_int4(c.x + tc_wmma_out<tc_i8_t, int>(d, 0), c.y + tc_wmma_out<tc_i8_t, int>(d, 1), c.z + tc_wmma_out<tc_i8_t, int>(d, 2), c.w + tc_wmma_out<tc_i8_t, int>(d, 3));
                    #elif defined(TC_WMMA1250)
                      tc_i8_t av, bv;
                      tc_wmma1250_k32(a, b, av, bv);
                      tc_i8_t z = {};
                      tc_i8_t d = __builtin_amdgcn_wmma_i32_16x16x64_iu8(true, av, true, bv, z, false, false);
                      return make_int4(c.x + tc_wmma_out<tc_i8_t, int>(d, 0), c.y + tc_wmma_out<tc_i8_t, int>(d, 1), c.z + tc_wmma_out<tc_i8_t, int>(d, 2), c.w + tc_wmma_out<tc_i8_t, int>(d, 3));
                    #endif
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_e4m3", MMA_COMMON + """
                    __device__ static inline float4 tc_mma_m16n8k32_e4m3(int4 a, int2 b, float4 c) {
                      return tc_mma_k32_fp8<true>(a, b, c);
                    }
                    """),
            Map.entry("tc_mma_m16n8k32_e5m2", MMA_COMMON + """
                    __device__ static inline float4 tc_mma_m16n8k32_e5m2(int4 a, int2 b, float4 c) {
                      return tc_mma_k32_fp8<false>(a, b, c);
                    }
                    """),
            Map.entry("tc_llmin", """
                    __device__ static inline long long tc_llmin(long long a, long long b) {
                      return a < b ? a : b;
                    }
                    """),
            Map.entry("tc_llmax", """
                    __device__ static inline long long tc_llmax(long long a, long long b) {
                      return a > b ? a : b;
                    }
                    """));

    /**
     * OpenCL C helpers, from the JLS like the CUDA ones. OpenCL 1.2 has no float atomics: they are a
     * compare-and-swap loop on the bits (global memory).
     */
    private static final Map<String, String> OPENCL = Map.ofEntries(
            // Java integer division (JLS 15.17.2): MIN_VALUE / -1 is MIN_VALUE and MIN_VALUE % -1 is 0;
            // C leaves both undefined, and some devices trap or return another value.
            Map.entry("java_idiv", """
                    static inline int java_idiv(int a, int b) {
                      return b == -1 ? (int) ((uint) 0 - (uint) a) : a / b;
                    }
                    """),
            Map.entry("java_irem", """
                    static inline int java_irem(int a, int b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            Map.entry("java_ldiv", """
                    static inline long java_ldiv(long a, long b) {
                      return b == -1 ? (long) ((ulong) 0 - (ulong) a) : a / b;
                    }
                    """),
            Map.entry("java_lrem", """
                    static inline long java_lrem(long a, long b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            // NaN tests on the bits: TornadoVM builds OpenCL with -cl-fast-relaxed-math, which lets the
            // compiler fold isnan(x) and x != x to false.
            Map.entry("java_fminf", """
                    static inline float java_fminf(float a, float b) {
                      if ((as_uint(a) & 0x7fffffffu) > 0x7f800000u || (as_uint(b) & 0x7fffffffu) > 0x7f800000u) return as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return as_float(as_int(a) | as_int(b));
                      return a < b ? a : b;
                    }
                    """),
            Map.entry("java_fmaxf", """
                    static inline float java_fmaxf(float a, float b) {
                      if ((as_uint(a) & 0x7fffffffu) > 0x7f800000u || (as_uint(b) & 0x7fffffffu) > 0x7f800000u) return as_float(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return as_float(as_int(a) & as_int(b));
                      return a > b ? a : b;
                    }
                    """),
            Map.entry("java_fmin", """
                    static inline double java_fmin(double a, double b) {
                      if ((as_ulong(a) & 0x7fffffffffffffffUL) > 0x7ff0000000000000UL || (as_ulong(b) & 0x7fffffffffffffffUL) > 0x7ff0000000000000UL) return as_double(0x7ff8000000000000L);
                      if (a == 0.0 && b == 0.0) return as_double(as_long(a) | as_long(b));
                      return a < b ? a : b;
                    }
                    """),
            Map.entry("java_fmax", """
                    static inline double java_fmax(double a, double b) {
                      if ((as_ulong(a) & 0x7fffffffffffffffUL) > 0x7ff0000000000000UL || (as_ulong(b) & 0x7fffffffffffffffUL) > 0x7ff0000000000000UL) return as_double(0x7ff8000000000000L);
                      if (a == 0.0 && b == 0.0) return as_double(as_long(a) & as_long(b));
                      return a > b ? a : b;
                    }
                    """),
            Map.entry("tc_atomic_add_f32", """
                    static inline float tc_atomic_add_f32(volatile __global float *p, float v) {
                      uint old = as_uint(*p), seen;
                      do {
                        seen = old;
                        old = atomic_cmpxchg((volatile __global uint *) p, seen, as_uint(as_float(seen) + v));
                      } while (old != seen);
                      return as_float(old);
                    }
                    """),
            Map.entry("tc_atomic_add_f64", """
                    static inline double tc_atomic_add_f64(volatile __global double *p, double v) {
                      ulong old = as_ulong(*p), seen;
                      do {
                        seen = old;
                        old = atom_cmpxchg((volatile __global ulong *) p, seen, as_ulong(as_double(seen) + v));
                      } while (old != seen);
                      return as_double(old);
                    }
                    """));

    /**
     * Metal helpers. MSL atomics need an {@code atomic_*} object: the helpers take the element's
     * address and are overloaded on its address space (device, threadgroup). Float add is a
     * compare-and-swap loop on the bits, as on OpenCL.
     */
    private static final Map<String, String> METAL = Map.ofEntries(
            // Java integer division (JLS 15.17.2): MIN_VALUE / -1 is MIN_VALUE and MIN_VALUE % -1 is 0;
            // C leaves both undefined, and some devices trap or return another value.
            Map.entry("java_idiv", """
                    static inline int java_idiv(int a, int b) {
                      return b == -1 ? (int) ((uint) 0 - (uint) a) : a / b;
                    }
                    """),
            Map.entry("java_irem", """
                    static inline int java_irem(int a, int b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            Map.entry("java_ldiv", """
                    static inline long java_ldiv(long a, long b) {
                      return b == -1 ? (long) ((ulong) 0 - (ulong) a) : a / b;
                    }
                    """),
            Map.entry("java_lrem", """
                    static inline long java_lrem(long a, long b) {
                      return b == -1 ? 0 : a % b;
                    }
                    """),
            Map.entry("java_fminf", """
                    static inline float java_fminf(float a, float b) {
                      if (isnan(a) || isnan(b)) return as_type<float>(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return as_type<float>(as_type<int>(a) | as_type<int>(b));
                      return a < b ? a : b;
                    }
                    """),
            Map.entry("java_fmaxf", """
                    static inline float java_fmaxf(float a, float b) {
                      if (isnan(a) || isnan(b)) return as_type<float>(0x7fc00000);
                      if (a == 0.0f && b == 0.0f) return as_type<float>(as_type<int>(a) & as_type<int>(b));
                      return a > b ? a : b;
                    }
                    """),
            Map.entry("tc_atomic_add_i32", atomicOverloads("int", "add", "int v", "atomic_fetch_add_explicit(a, v, memory_order_relaxed)")),
            Map.entry("tc_atomic_exchange_i32", atomicOverloads("int", "exchange", "int v", "atomic_exchange_explicit(a, v, memory_order_relaxed)")),
            Map.entry("tc_atomic_min_i32", atomicOverloads("int", "min", "int v", "atomic_fetch_min_explicit(a, v, memory_order_relaxed)")),
            Map.entry("tc_atomic_max_i32", atomicOverloads("int", "max", "int v", "atomic_fetch_max_explicit(a, v, memory_order_relaxed)")),
            Map.entry("tc_atomic_cas_i32", """
                    static inline int tc_atomic_cas(device int *p, int expected, int desired) {
                      atomic_compare_exchange_weak_explicit((device atomic_int *) p, &expected, desired, memory_order_relaxed, memory_order_relaxed);
                      return expected;
                    }
                    static inline int tc_atomic_cas(threadgroup int *p, int expected, int desired) {
                      atomic_compare_exchange_weak_explicit((threadgroup atomic_int *) p, &expected, desired, memory_order_relaxed, memory_order_relaxed);
                      return expected;
                    }
                    """),
            // No 64-bit add in MSL: add the low words, then the high word plus the carry. The sum is
            // exact; the previous value returned pairs the two words' old values, each read atomically.
            Map.entry("tc_atomic_add_i64", atomicAdd64("device") + atomicAdd64("threadgroup")),
            // Signed int8 dot products: (a + ao) and (b + bo) are byte addresses in any address space.
            Map.entry("tc_dp4a", """
                    template <typename P, typename Q>
                    static inline int tc_dp4a(P a, long ao, Q b, long bo, int c) {
                      return c + (int) (char) a[ao] * (int) (char) b[bo] + (int) (char) a[ao + 1] * (int) (char) b[bo + 1]
                          + (int) (char) a[ao + 2] * (int) (char) b[bo + 2] + (int) (char) a[ao + 3] * (int) (char) b[bo + 3];
                    }
                    """),
            Map.entry("tc_dp4a_packed", """
                    static inline int tc_dp4a_packed(int a, int b, int c) {
                      char4 x = as_type<char4>(a), y = as_type<char4>(b);
                      return c + (int) x.x * (int) y.x + (int) x.y * (int) y.y + (int) x.z * (int) y.z + (int) x.w * (int) y.w;
                    }
                    """),
            // Matrix8x8Float/Matrix8x8Half values are simdgroup_float8x8/simdgroup_half8x8. A local array
            // (float[] or HalfFloat[]) arrives as a byte pointer in its own address space.
            Map.entry("tc_simdgroup_matrix", simdgroupLoads("float", "tc_simdgroup_load") + simdgroupLoads("half", "tc_simdgroup_load_half") + """
                    template <typename A, typename B>
                    static inline simdgroup_float8x8 tc_simdgroup_mma(A a, B b, simdgroup_float8x8 c) {
                      simdgroup_float8x8 d;
                      simdgroup_multiply_accumulate(d, a, b, c);
                      return d;
                    }
                    static inline void tc_simdgroup_store(simdgroup_float8x8 m, device float *p, int base, int stride) {
                      simdgroup_store(m, p + base, (ulong) stride);
                    }
                    static inline void tc_simdgroup_store(simdgroup_float8x8 m, threadgroup float *p, int base, int stride) {
                      simdgroup_store(m, p + base, (ulong) stride);
                    }
                    static inline void tc_simdgroup_store(simdgroup_float8x8 m, device unsigned char *p, int base, int stride) {
                      tc_simdgroup_store(m, (device float *) p, base, stride);
                    }
                    static inline void tc_simdgroup_store(simdgroup_float8x8 m, threadgroup unsigned char *p, int base, int stride) {
                      tc_simdgroup_store(m, (threadgroup float *) p, base, stride);
                    }
                    """),
            Map.entry("tc_atomic_add_f32", """
                    static inline float tc_atomic_add(device float *p, float v) {
                      device atomic_uint *a = (device atomic_uint *) p;
                      uint old = atomic_load_explicit(a, memory_order_relaxed);
                      while (!atomic_compare_exchange_weak_explicit(a, &old, as_type<uint>(as_type<float>(old) + v), memory_order_relaxed, memory_order_relaxed)) {
                      }
                      return as_type<float>(old);
                    }
                    static inline float tc_atomic_add(threadgroup float *p, float v) {
                      threadgroup atomic_uint *a = (threadgroup atomic_uint *) p;
                      uint old = atomic_load_explicit(a, memory_order_relaxed);
                      while (!atomic_compare_exchange_weak_explicit(a, &old, as_type<uint>(as_type<float>(old) + v), memory_order_relaxed, memory_order_relaxed)) {
                      }
                      return as_type<float>(old);
                    }
                    """));

    /** {@code name}: loads of a simdgroup matrix of {@code element} from device or threadgroup memory, optionally transposed. */
    private static String simdgroupLoads(String element, String name) {
        var sb = new StringBuilder();
        for (var space : List.of("device", "threadgroup")) {
            sb.append("static inline simdgroup_").append(element).append("8x8 ").append(name).append('(').append(space).append(" const ").append(element)
                    .append(" *p, int base, int stride, bool transpose) {\n");
            sb.append("  simdgroup_").append(element).append("8x8 m;\n");
            sb.append("  simdgroup_load(m, p + base, (ulong) stride, ulong2(0, 0), transpose);\n");
            sb.append("  return m;\n}\n");
        }
        for (var space : List.of("device", "threadgroup")) {
            sb.append("static inline simdgroup_").append(element).append("8x8 ").append(name).append('(').append(space)
                    .append(" const unsigned char *p, int base, int stride, bool transpose) {\n");
            sb.append("  return ").append(name).append("((").append(space).append(" const ").append(element).append(" *) p, base, stride, transpose);\n}\n");
        }
        return sb.toString();
    }

    private static String atomicAdd64(String space) {
        return """
                static inline long tc_atomic_add(SPACE long *p, long v) {
                  SPACE atomic_uint *w = (SPACE atomic_uint *) p;
                  uint lo = (uint) v;
                  uint oldLo = atomic_fetch_add_explicit(&w[0], lo, memory_order_relaxed);
                  uint hi = (uint) (v >> 32) + (oldLo + lo < oldLo ? 1u : 0u);
                  uint oldHi = hi != 0u ? atomic_fetch_add_explicit(&w[1], hi, memory_order_relaxed) : atomic_load_explicit(&w[1], memory_order_relaxed);
                  return (long) (((ulong) oldHi << 32) | oldLo);
                }
                """.replace("SPACE", space);
    }

    /** One read-modify-write atomic for device and threadgroup addresses ({@code a}: the atomic object). */
    private static String atomicOverloads(String type, String op, String params, String body) {
        var sb = new StringBuilder();
        for (var space : List.of("device", "threadgroup")) {
            sb.append("static inline ").append(type).append(" tc_atomic_").append(op).append('(').append(space).append(' ').append(type).append(" *p, ").append(params).append(") {\n");
            sb.append("  ").append(space).append(" atomic_").append(type).append(" *a = (").append(space).append(" atomic_").append(type).append(" *) p;\n");
            sb.append("  return ").append(body).append(";\n}\n");
        }
        return sb.toString();
    }

    /** The source of every helper of {@code dialect} (what {@link Naming} scans for the names it calls). */
    static java.util.Collection<String> sources(Dialect dialect) {
        return switch (dialect) {
            case CUDA -> CUDA.values();
            case OPENCL -> OPENCL.values();
            case METAL -> METAL.values();
            case HIP -> {
                var all = new java.util.HashMap<>(CUDA);
                all.putAll(HIP);
                yield all.values();
            }
        };
    }

    static String source(String helper, Dialect dialect) {
        var text = switch (dialect) {
            case CUDA -> CUDA.get(helper);
            case OPENCL -> OPENCL.get(helper);
            case METAL -> METAL.get(helper);
            case HIP -> HIP.getOrDefault(helper, CUDA.get(helper));
        };
        if (text == null) {
            throw new IllegalArgumentException("no " + dialect + " definition for helper " + helper);
        }
        return text;
    }
}

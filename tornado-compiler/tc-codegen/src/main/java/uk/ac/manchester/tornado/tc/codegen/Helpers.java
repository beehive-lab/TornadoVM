/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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

    static String source(String helper, Dialect dialect) {
        var text = switch (dialect) {
            case CUDA -> CUDA.get(helper);
            case OPENCL -> OPENCL.get(helper);
            case METAL -> METAL.get(helper);
        };
        if (text == null) {
            throw new IllegalArgumentException("no " + dialect + " definition for helper " + helper);
        }
        return text;
    }
}

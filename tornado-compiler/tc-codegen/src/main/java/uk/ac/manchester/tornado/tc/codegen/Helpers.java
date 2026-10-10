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
    private static final Map<String, String> CUDA = Map.ofEntries(
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
                    __device__ static inline void tc_device_launch_check(const long long *context, const char *child, dim3 grid, dim3 block) {
                      cudaError_t error = cudaGetLastError();
                      if (error != cudaSuccess) printf("[TornadoVM-CUDA] device launch of %s failed: %s\\n", child, cudaGetErrorString(error));
                      if (context[3]) printf("Device launch: %s, grid [%u, %u, %u], block [%u, %u, %u], from block [%u, %u, %u] thread [%u, %u, %u]\\n", child,
                                             grid.x, grid.y, grid.z, block.x, block.y, block.z, blockIdx.x, blockIdx.y, blockIdx.z, threadIdx.x, threadIdx.y, threadIdx.z);
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
     * Work-group reductions for @Reduce kernels: each thread's value into shared memory, a tree over
     * the (power-of-two) block, the result in s[0] for every thread. Combines with Java semantics.
     */
    private static String blockReduce(String helper) {
        var parts = helper.split("_"); // tc_reduce_<op>_<type>
        String op = parts[2], t = parts[3];
        String c = switch (t) {
            case "f32" -> "float";
            case "f64" -> "double";
            case "i32" -> "int";
            default -> "long long";
        };
        String combine = switch (op) {
            case "add" -> "a + b";
            case "mul" -> "a * b";
            case "max" -> t.equals("f32") ? "java_fmaxf(a, b)" : t.equals("f64") ? "java_fmax(a, b)" : "(a > b ? a : b)";
            default -> t.equals("f32") ? "java_fminf(a, b)" : t.equals("f64") ? "java_fmin(a, b)" : "(a < b ? a : b)";
        };
        return ("__device__ static inline %1$s %2$s(%1$s v) {\n"
                + "  __shared__ %1$s s[1024];\n"
                + "  unsigned t = threadIdx.x;\n"
                + "  __syncthreads();\n"
                + "  s[t] = v;\n"
                + "  __syncthreads();\n"
                + "  for (unsigned stride = blockDim.x / 2; stride > 0; stride >>= 1) {\n"
                + "    if (t < stride) { %1$s a = s[t], b = s[t + stride]; s[t] = %3$s; }\n"
                + "    __syncthreads();\n"
                + "  }\n"
                + "  return s[0];\n"
                + "}\n").formatted(c, helper, combine);
    }

    /** Helpers another helper calls (emitted before it). */
    static java.util.List<String> dependencies(String helper) {
        if (helper.startsWith("tc_reduce_max_f") || helper.startsWith("tc_reduce_min_f")) {
            var t = helper.endsWith("f32") ? "f" : "";
            return java.util.List.of((helper.contains("max") ? "java_fmax" : "java_fmin") + t);
        }
        return java.util.List.of();
    }

    static String source(String helper, Dialect dialect) {
        if (helper.startsWith("tc_reduce_") && dialect == Dialect.CUDA) {
            return blockReduce(helper);
        }
        var text = switch (dialect) {
            case CUDA -> CUDA.get(helper);
            case OPENCL, METAL -> null;
        };
        if (text == null) {
            throw new IllegalArgumentException("no " + dialect + " definition for helper " + helper);
        }
        return text;
    }
}

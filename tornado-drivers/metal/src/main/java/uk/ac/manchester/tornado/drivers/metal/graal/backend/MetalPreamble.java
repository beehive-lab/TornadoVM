/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.backend;

/**
 * Metal Shading Language (MSL) preamble emitted before every compiled kernel.
 *
 * <p>MSL is a strict subset of C++17 and does NOT include OpenCL C built-ins.
 * TornadoVM's LIR layer emits OpenCL-style names (vload2/vstore4, isequal,
 * float8, …), so this preamble provides compatible inline definitions.
 *
 * <p>Why each section is needed:
 * <ul>
 *   <li><b>tornado_ptr_t</b>: {@code device uchar*} enables byte-level pointer
 *       arithmetic on device buffers; all address-computation variables (ul_N)
 *       use this type.</li>
 *   <li><b>isequal / isnotequal / isgreater / isless</b>: MSL Table 6.3
 *       (Relational Functions) does not define scalar-float variants of these
 *       OpenCL functions. MSL only provides {@code isfinite}, {@code isinf},
 *       {@code isnan}, etc. The shims return {@code int} matching OpenCL
 *       semantics.</li>
 *   <li><b>signum_f</b>: Java {@code Math.signum(NaN)} returns NaN; MSL
 *       {@code sign(NaN)} returns 0. The wrapper preserves Java semantics.</li>
 *   <li><b>tornado_atomic_add_float</b>: {@code atomic_float} was introduced
 *       only in Metal 3.0. The CAS-loop implementation is portable across all
 *       Apple Silicon targets.</li>
 *   <li><b>vload2/3/4 and vstore2/3/4</b>: These are OpenCL C built-ins; MSL
 *       has no equivalent. Separate overloads for {@code device} and
 *       {@code thread} address spaces are required because MSL enforces strict
 *       address-space segregation — a function taking {@code device T*} cannot
 *       accept a {@code thread T*}.</li>
 *   <li><b>float8 / float16 / int8 / int16 / half8 / half16</b>: MSL 2.2
 *       defines vectors only for n=2,3,4. The names float8 etc. are reserved
 *       as incomplete types; the structs complete them. Arithmetic operators
 *       are then added so the types behave like native vector types.</li>
 *   <li><b>vload8 / vload16 and vstore8 / vstore16</b>: Implemented on top of
 *       the float4/int4/half4 native types and the struct types above.</li>
 *   <li><b>atomicMul_Tornado_Int</b>: MSL has no native atomic-multiply
 *       intrinsic; CAS loop required.</li>
 * </ul>
 */
public final class MetalPreamble {

    private MetalPreamble() {
    }

    // @formatter:off
    public static final String PREAMBLE =
        "#include <metal_stdlib>\n" +
        "using namespace metal;\n" +
        "\n" +
        // tornado_ptr_t — byte-addressed device pointer for pointer arithmetic
        "typedef device uchar* tornado_ptr_t;\n" +
        "\n" +
        // Scalar relational shims (MSL has no isequal/isgreater/isless for float scalars)
        "inline int isequal   (float a, float b) { return (int)(a == b); }\n" +
        "inline int isnotequal(float a, float b) { return (int)(a != b); }\n" +
        "inline int isgreater (float a, float b) { return (int)(a >  b); }\n" +
        "inline int isless    (float a, float b) { return (int)(a <  b); }\n" +
        // signum: Java Math.signum(NaN)==NaN but MSL sign(NaN)==0
        "inline float signum_f(float x) { return isnan(x) ? x : sign(x); }\n" +
        // Float atomic-add via CAS loop (portable pre-Metal-3.0; no atomic_float needed)
        "inline float tornado_atomic_add_float(device atomic_uint* p, float delta) {\n" +
        "    uint exp = atomic_load_explicit(p, memory_order_relaxed);\n" +
        "    while (!atomic_compare_exchange_weak_explicit(p, &exp,\n" +
        "           as_type<uint>(as_type<float>(exp) + delta),\n" +
        "           memory_order_relaxed, memory_order_relaxed)) {}\n" +
        "    return as_type<float>(exp);\n" +
        "}\n" +
        "\n" +
        // ── vload / vstore: device address space ──────────────────────────────
        // float
        "inline float2  vload2 (uint n, const device float*  p) { return ((const device float2* )p)[n]; }\n" +
        "inline float3  vload3 (uint n, const device float*  p) { return ((const device float3* )p)[n]; }\n" +
        "inline float4  vload4 (uint n, const device float*  p) { return ((const device float4* )p)[n]; }\n" +
        "inline void    vstore2(float2  v, uint n, device float*  p) { ((device float2* )p)[n] = v; }\n" +
        "inline void    vstore3(float3  v, uint n, device float*  p) { ((device float3* )p)[n] = v; }\n" +
        "inline void    vstore4(float4  v, uint n, device float*  p) { ((device float4* )p)[n] = v; }\n" +
        // int
        "inline int2    vload2 (uint n, const device int*    p) { return ((const device int2*  )p)[n]; }\n" +
        "inline int3    vload3 (uint n, const device int*    p) { return ((const device int3*  )p)[n]; }\n" +
        "inline int4    vload4 (uint n, const device int*    p) { return ((const device int4*  )p)[n]; }\n" +
        "inline void    vstore2(int2    v, uint n, device int*    p) { ((device int2*  )p)[n] = v; }\n" +
        "inline void    vstore3(int3    v, uint n, device int*    p) { ((device int3*  )p)[n] = v; }\n" +
        "inline void    vstore4(int4    v, uint n, device int*    p) { ((device int4*  )p)[n] = v; }\n" +
        // half
        "inline half2   vload2 (uint n, const device half*   p) { return ((const device half2*  )p)[n]; }\n" +
        "inline half3   vload3 (uint n, const device half*   p) { return ((const device half3*  )p)[n]; }\n" +
        "inline half4   vload4 (uint n, const device half*   p) { return ((const device half4*  )p)[n]; }\n" +
        "inline void    vstore2(half2   v, uint n, device half*   p) { ((device half2*  )p)[n] = v; }\n" +
        "inline void    vstore3(half3   v, uint n, device half*   p) { ((device half3*  )p)[n] = v; }\n" +
        "inline void    vstore4(half4   v, uint n, device half*   p) { ((device half4*  )p)[n] = v; }\n" +
        // uchar (unsigned byte — ByteArray buffers)
        "inline uchar2  vload2 (uint n, const device uchar*  p) { return ((const device uchar2* )p)[n]; }\n" +
        "inline uchar3  vload3 (uint n, const device uchar*  p) { return ((const device uchar3* )p)[n]; }\n" +
        "inline uchar4  vload4 (uint n, const device uchar*  p) { return ((const device uchar4* )p)[n]; }\n" +
        "inline void    vstore2(uchar2  v, uint n, device uchar*  p) { ((device uchar2* )p)[n] = v; }\n" +
        "inline void    vstore3(uchar3  v, uint n, device uchar*  p) { ((device uchar3* )p)[n] = v; }\n" +
        "inline void    vstore4(uchar4  v, uint n, device uchar*  p) { ((device uchar4* )p)[n] = v; }\n" +
        // char (signed byte — Int8Array / ImageByte kernels)
        "inline char2   vload2 (uint n, const device char*   p) { return ((const device char2*  )p)[n]; }\n" +
        "inline char3   vload3 (uint n, const device char*   p) { return ((const device char3*  )p)[n]; }\n" +
        "inline char4   vload4 (uint n, const device char*   p) { return ((const device char4*  )p)[n]; }\n" +
        "inline void    vstore2(char2   v, uint n, device char*   p) { ((device char2*  )p)[n] = v; }\n" +
        "inline void    vstore3(char3   v, uint n, device char*   p) { ((device char3*  )p)[n] = v; }\n" +
        "inline void    vstore4(char4   v, uint n, device char*   p) { ((device char4*  )p)[n] = v; }\n" +
        // short (HalfFloatArray storage)
        "inline short2  vload2 (uint n, const device short*  p) { return ((const device short2* )p)[n]; }\n" +
        "inline short4  vload4 (uint n, const device short*  p) { return ((const device short4* )p)[n]; }\n" +
        "inline void    vstore2(short2  v, uint n, device short*  p) { ((device short2* )p)[n] = v; }\n" +
        "inline void    vstore4(short4  v, uint n, device short*  p) { ((device short4* )p)[n] = v; }\n" +
        "\n" +
        // ── vload / vstore: thread address space (private / stack arrays) ─────
        // MSL enforces strict address-space segregation: device* ≠ thread*
        // float
        "inline float2  vload2 (uint n, const thread float*  p) { return ((const thread float2* )p)[n]; }\n" +
        "inline float3  vload3 (uint n, const thread float*  p) { return ((const thread float3* )p)[n]; }\n" +
        "inline float4  vload4 (uint n, const thread float*  p) { return ((const thread float4* )p)[n]; }\n" +
        "inline void    vstore2(float2  v, uint n, thread float*  p) { ((thread float2* )p)[n] = v; }\n" +
        "inline void    vstore3(float3  v, uint n, thread float*  p) { ((thread float3* )p)[n] = v; }\n" +
        "inline void    vstore4(float4  v, uint n, thread float*  p) { ((thread float4* )p)[n] = v; }\n" +
        // int
        "inline int2    vload2 (uint n, const thread int*    p) { return ((const thread int2*  )p)[n]; }\n" +
        "inline int3    vload3 (uint n, const thread int*    p) { return ((const thread int3*  )p)[n]; }\n" +
        "inline int4    vload4 (uint n, const thread int*    p) { return ((const thread int4*  )p)[n]; }\n" +
        "inline void    vstore2(int2    v, uint n, thread int*    p) { ((thread int2*  )p)[n] = v; }\n" +
        "inline void    vstore3(int3    v, uint n, thread int*    p) { ((thread int3*  )p)[n] = v; }\n" +
        "inline void    vstore4(int4    v, uint n, thread int*    p) { ((thread int4*  )p)[n] = v; }\n" +
        // half
        "inline half2   vload2 (uint n, const thread half*   p) { return ((const thread half2*  )p)[n]; }\n" +
        "inline half3   vload3 (uint n, const thread half*   p) { return ((const thread half3*  )p)[n]; }\n" +
        "inline half4   vload4 (uint n, const thread half*   p) { return ((const thread half4*  )p)[n]; }\n" +
        "inline void    vstore2(half2   v, uint n, thread half*   p) { ((thread half2*  )p)[n] = v; }\n" +
        "inline void    vstore3(half3   v, uint n, thread half*   p) { ((thread half3*  )p)[n] = v; }\n" +
        "inline void    vstore4(half4   v, uint n, thread half*   p) { ((thread half4*  )p)[n] = v; }\n" +
        // short
        "inline short2  vload2 (uint n, const thread short*  p) { return ((const thread short2* )p)[n]; }\n" +
        "inline short4  vload4 (uint n, const thread short*  p) { return ((const thread short4* )p)[n]; }\n" +
        "inline void    vstore2(short2  v, uint n, thread short*  p) { ((thread short2* )p)[n] = v; }\n" +
        "inline void    vstore4(short4  v, uint n, thread short*  p) { ((thread short4* )p)[n] = v; }\n" +
        "\n" +
        // ── Extended vector types: float8/16, int8/16, half8/16 ───────────────
        // MSL 2.2 defines vectors only for n=2,3,4. The names float8 etc. are
        // reserved as incomplete; these structs complete them.
        "struct __Reserved_Name__Do_not_use_float8  { float4 lo, hi; };\n" +
        "struct __Reserved_Name__Do_not_use_float16 { float8 lo, hi; };\n" +
        "struct __Reserved_Name__Do_not_use_int8    { int4   lo, hi; };\n" +
        "struct __Reserved_Name__Do_not_use_int16   { int8   lo, hi; };\n" +
        "struct __Reserved_Name__Do_not_use_half8   { half4  lo, hi; };\n" +
        "struct __Reserved_Name__Do_not_use_half16  { half8  lo, hi; };\n" +
        // Arithmetic operators for extended vector types
        "inline float8   operator+(float8   a,float8   b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline float8   operator-(float8   a,float8   b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline float8   operator*(float8   a,float8   b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline float8   operator/(float8   a,float8   b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        "inline float16  operator+(float16  a,float16  b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline float16  operator-(float16  a,float16  b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline float16  operator*(float16  a,float16  b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline float16  operator/(float16  a,float16  b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        "inline int8     operator+(int8     a,int8     b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline int8     operator-(int8     a,int8     b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline int8     operator*(int8     a,int8     b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline int8     operator/(int8     a,int8     b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        "inline int16    operator+(int16    a,int16    b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline int16    operator-(int16    a,int16    b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline int16    operator*(int16    a,int16    b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline int16    operator/(int16    a,int16    b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        "inline half8    operator+(half8    a,half8    b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline half8    operator-(half8    a,half8    b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline half8    operator*(half8    a,half8    b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline half8    operator/(half8    a,half8    b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        "inline half16   operator+(half16   a,half16   b){return{a.lo+b.lo,a.hi+b.hi};}\n" +
        "inline half16   operator-(half16   a,half16   b){return{a.lo-b.lo,a.hi-b.hi};}\n" +
        "inline half16   operator*(half16   a,half16   b){return{a.lo*b.lo,a.hi*b.hi};}\n" +
        "inline half16   operator/(half16   a,half16   b){return{a.lo/b.lo,a.hi/b.hi};}\n" +
        // vload8/vstore8/vload16/vstore16 — device address space
        "inline float8  vload8 (uint n,const device float* p){const device float4* q=(const device float4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(float8  v,uint n,device float* p){device float4* q=(device float4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline float16 vload16(uint n,const device float* p){float8 lo=vload8(0,p+n*16);float8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(float16 v,uint n,device float* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        "inline int8    vload8 (uint n,const device int* p){const device int4* q=(const device int4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(int8    v,uint n,device int* p){device int4* q=(device int4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline int16   vload16(uint n,const device int* p){int8 lo=vload8(0,p+n*16);int8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(int16 v,uint n,device int* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        "inline half8   vload8 (uint n,const device half* p){const device half4* q=(const device half4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(half8  v,uint n,device half* p){device half4* q=(device half4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline half16  vload16(uint n,const device half* p){half8 lo=vload8(0,p+n*16);half8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(half16 v,uint n,device half* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        // vload8/vstore8/vload16/vstore16 — thread address space
        "inline float8  vload8 (uint n,const thread float* p){const thread float4* q=(const thread float4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(float8  v,uint n,thread float* p){thread float4* q=(thread float4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline float16 vload16(uint n,const thread float* p){float8 lo=vload8(0,p+n*16);float8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(float16 v,uint n,thread float* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        "inline int8    vload8 (uint n,const thread int* p){const thread int4* q=(const thread int4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(int8    v,uint n,thread int* p){thread int4* q=(thread int4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline int16   vload16(uint n,const thread int* p){int8 lo=vload8(0,p+n*16);int8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(int16 v,uint n,thread int* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        "inline half8   vload8 (uint n,const thread half* p){const thread half4* q=(const thread half4*)p+n*2;return{q[0],q[1]};}\n" +
        "inline void    vstore8(half8  v,uint n,thread half* p){thread half4* q=(thread half4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}\n" +
        "inline half16  vload16(uint n,const thread half* p){half8 lo=vload8(0,p+n*16);half8 hi=vload8(0,p+n*16+8);return{lo,hi};}\n" +
        "inline void    vstore16(half16 v,uint n,thread half* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}\n" +
        "\n" +
        // Integer atomic multiply (MSL has no native atomic-mul intrinsic)
        "inline int atomicMul_Tornado_Int(device atomic_int* a, int val) {\n" +
        "  int expected = atomic_load_explicit(a, memory_order_relaxed);\n" +
        "  int desired;\n" +
        "  do { desired = expected * val; }\n" +
        "  while (!atomic_compare_exchange_weak_explicit(a, &expected, desired,\n" +
        "         memory_order_relaxed, memory_order_relaxed));\n" +
        "  return desired;\n" +
        "}\n";
    // @formatter:on
}

/*
 * Copyright (c) 2018, 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import tornado.graal.compiler.core.common.LIRKind;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;

public class CUDATargetDescription extends TargetDescription {

    private static final int STACK_ALIGNMENT = 8;
    private static final boolean INLINE_OBJECTS = true;
    private static final int IMPLICIT_NULL_CHECK_LIMIT = 4096;
    //@formatter:off
    private static final CUDAKind[][] VECTOR_LOOKUP_TABLE = new CUDAKind[][] {
        {CUDAKind.UCHAR2, CUDAKind.UCHAR3, CUDAKind.UCHAR4, CUDAKind.UCHAR8, CUDAKind.UCHAR16},
        {CUDAKind.CHAR2, CUDAKind.CHAR3, CUDAKind.CHAR4, CUDAKind.CHAR8, CUDAKind.CHAR16},
        {CUDAKind.USHORT2, CUDAKind.USHORT3, CUDAKind.USHORT4, CUDAKind.USHORT8, CUDAKind.USHORT16},
        {CUDAKind.SHORT2, CUDAKind.SHORT3, CUDAKind.SHORT4, CUDAKind.SHORT8, CUDAKind.SHORT16},
        {CUDAKind.USHORT2, CUDAKind.USHORT3, CUDAKind.USHORT4, CUDAKind.USHORT8, CUDAKind.USHORT16},
        {CUDAKind.INT2, CUDAKind.INT3, CUDAKind.INT4, CUDAKind.INT8, CUDAKind.INT16},
        {CUDAKind.UINT2, CUDAKind.UINT3, CUDAKind.UINT4, CUDAKind.UINT8, CUDAKind.UINT16},
        {CUDAKind.LONG2, CUDAKind.LONG3, CUDAKind.LONG4, CUDAKind.LONG8, CUDAKind.LONG16},
        {CUDAKind.ULONG2, CUDAKind.ULONG3, CUDAKind.ULONG4, CUDAKind.ULONG8, CUDAKind.ULONG16},
        {CUDAKind.FLOAT2, CUDAKind.FLOAT3, CUDAKind.FLOAT4, CUDAKind.FLOAT8, CUDAKind.FLOAT16},
        {CUDAKind.DOUBLE2, CUDAKind.DOUBLE3, CUDAKind.DOUBLE4, CUDAKind.DOUBLE8, CUDAKind.DOUBLE16}
    };
    private final boolean supportsFP64;
    private final String extensions;
    private final boolean supportsInt64Atomics;

    private final boolean supportsF16;

    public CUDATargetDescription(Architecture arch, boolean supportsFP64, String extensions) {
        this(arch, false, STACK_ALIGNMENT, IMPLICIT_NULL_CHECK_LIMIT, INLINE_OBJECTS, supportsFP64, extensions);
    }

    protected CUDATargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, boolean supportsFP64, String extensions) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
        this.supportsFP64 = supportsFP64;
        this.extensions = extensions;
        supportsInt64Atomics = extensions.contains("cl_khr_int64_base_atomics");
        supportsF16 = extensions.contains("cl_khr_fp16");
    }
    //@formatter:on

    private int lookupLengthIndex(int vectorLength) {
        switch (vectorLength) {
            case 2:
                return 0;
            case 3:
                return 1;
            case 4:
                return 2;
            case 8:
                return 3;
            case 16:
                return 4;
            default:
                shouldNotReachHere();
        }
        return -1;
    }

    public CUDAArchitecture getArch() {
        return (CUDAArchitecture) arch;
    }

    public boolean supportsFP64() {
        return supportsFP64;
    }

    public boolean supportsFP16() {
        return supportsF16;
    }

    public boolean supportsInt64Atomics() {
        return supportsInt64Atomics;
    }

    public String getExtensions() {
        return extensions;
    }

    public CUDAKind getCUDAKind(JavaKind javaKind, int vectorLength) {
        int index = -1;
        switch (javaKind) {
            case Byte:
                index = (javaKind.isUnsigned()) ? 0 : 1;
                break;
            case Short:
                index = (javaKind.isUnsigned()) ? 2 : 3;
                break;
            case Char:
                index = 2;
                break;
            case Int:
                index = (javaKind.isUnsigned()) ? 4 : 5;
                break;
            case Long:
                index = (javaKind.isUnsigned()) ? 6 : 7;
                break;
            case Float:
                index = 8;
                break;
            case Double:
                index = 9;
                break;
            case Boolean:
            case Object:
                shouldNotReachHere("invalid vector type");
                break;
            default:
                break;
        }

        return VECTOR_LOOKUP_TABLE[index][lookupLengthIndex(vectorLength)];
    }

    public LIRKind getLIRKind(JavaKind javaKind, int vectorLength) {
        return LIRKind.value(getCUDAKind(javaKind, vectorLength));
    }

    public CUDAKind getCUDAKind(JavaKind javaKind) {
        return (CUDAKind) arch.getPlatformKind(javaKind);
    }
}

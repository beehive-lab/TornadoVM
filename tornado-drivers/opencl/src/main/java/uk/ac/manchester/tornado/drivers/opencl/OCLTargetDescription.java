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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import jdk.graal.compiler.core.common.LIRKind;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLTargetDescription extends TargetDescription {

    private static final int STACK_ALIGNMENT = 8;
    private static final boolean INLINE_OBJECTS = true;
    private static final int IMPLICIT_NULL_CHECK_LIMIT = 4096;
    //@formatter:off
    private static final OCLKind[][] VECTOR_LOOKUP_TABLE = new OCLKind[][] {
        {OCLKind.UCHAR2, OCLKind.UCHAR3, OCLKind.UCHAR4, OCLKind.UCHAR8, OCLKind.UCHAR16},
        {OCLKind.CHAR2, OCLKind.CHAR3, OCLKind.CHAR4, OCLKind.CHAR8, OCLKind.CHAR16},
        {OCLKind.USHORT2, OCLKind.USHORT3, OCLKind.USHORT4, OCLKind.USHORT8, OCLKind.USHORT16},
        {OCLKind.SHORT2, OCLKind.SHORT3, OCLKind.SHORT4, OCLKind.SHORT8, OCLKind.SHORT16},
        {OCLKind.USHORT2, OCLKind.USHORT3, OCLKind.USHORT4, OCLKind.USHORT8, OCLKind.USHORT16},
        {OCLKind.INT2, OCLKind.INT3, OCLKind.INT4, OCLKind.INT8, OCLKind.INT16},
        {OCLKind.UINT2, OCLKind.UINT3, OCLKind.UINT4, OCLKind.UINT8, OCLKind.UINT16},
        {OCLKind.LONG2, OCLKind.LONG3, OCLKind.LONG4, OCLKind.LONG8, OCLKind.LONG16},
        {OCLKind.ULONG2, OCLKind.ULONG3, OCLKind.ULONG4, OCLKind.ULONG8, OCLKind.ULONG16},
        {OCLKind.FLOAT2, OCLKind.FLOAT3, OCLKind.FLOAT4, OCLKind.FLOAT8, OCLKind.FLOAT16},
        {OCLKind.DOUBLE2, OCLKind.DOUBLE3, OCLKind.DOUBLE4, OCLKind.DOUBLE8, OCLKind.DOUBLE16}
    };
    private final boolean supportsFP64;
    private final String extensions;
    private final boolean supportsInt64Atomics;

    private final boolean supportsF16;

    public OCLTargetDescription(Architecture arch, boolean supportsFP64, String extensions) {
        this(arch, false, STACK_ALIGNMENT, IMPLICIT_NULL_CHECK_LIMIT, INLINE_OBJECTS, supportsFP64, extensions);
    }

    protected OCLTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, boolean supportsFP64, String extensions) {
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

    public OCLArchitecture getArch() {
        return (OCLArchitecture) arch;
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

    public OCLKind getOCLKind(JavaKind javaKind, int vectorLength) {
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
        return LIRKind.value(getOCLKind(javaKind, vectorLength));
    }

    public OCLKind getOCLKind(JavaKind javaKind) {
        return (OCLKind) arch.getPlatformKind(javaKind);
    }
}

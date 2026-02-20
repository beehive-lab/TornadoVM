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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.core.common.LIRKind;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

public class MetalTargetDescription extends TargetDescription {

    private static final int STACK_ALIGNMENT = 8;
    private static final boolean INLINE_OBJECTS = true;
    private static final int IMPLICIT_NULL_CHECK_LIMIT = 4096;
    //@formatter:off
    private static final MetalKind[][] VECTOR_LOOKUP_TABLE = new MetalKind[][] {
        {MetalKind.UCHAR2, MetalKind.UCHAR3, MetalKind.UCHAR4, MetalKind.UCHAR8, MetalKind.UCHAR16},
        {MetalKind.CHAR2, MetalKind.CHAR3, MetalKind.CHAR4, MetalKind.CHAR8, MetalKind.CHAR16},
        {MetalKind.USHORT2, MetalKind.USHORT3, MetalKind.USHORT4, MetalKind.USHORT8, MetalKind.USHORT16},
        {MetalKind.SHORT2, MetalKind.SHORT3, MetalKind.SHORT4, MetalKind.SHORT8, MetalKind.SHORT16},
        {MetalKind.USHORT2, MetalKind.USHORT3, MetalKind.USHORT4, MetalKind.USHORT8, MetalKind.USHORT16},
        {MetalKind.INT2, MetalKind.INT3, MetalKind.INT4, MetalKind.INT8, MetalKind.INT16},
        {MetalKind.UINT2, MetalKind.UINT3, MetalKind.UINT4, MetalKind.UINT8, MetalKind.UINT16},
        {MetalKind.LONG2, MetalKind.LONG3, MetalKind.LONG4, MetalKind.LONG8, MetalKind.LONG16},
        {MetalKind.ULONG2, MetalKind.ULONG3, MetalKind.ULONG4, MetalKind.ULONG8, MetalKind.ULONG16},
        {MetalKind.FLOAT2, MetalKind.FLOAT3, MetalKind.FLOAT4, MetalKind.FLOAT8, MetalKind.FLOAT16},
        {MetalKind.DOUBLE2, MetalKind.DOUBLE3, MetalKind.DOUBLE4, MetalKind.DOUBLE8, MetalKind.DOUBLE16}
    };
    private final boolean supportsFP64;
    private final String extensions;
    private final boolean supportsInt64Atomics;

    private final boolean supportsF16;

    public MetalTargetDescription(Architecture arch, boolean supportsFP64, String extensions) {
        this(arch, false, STACK_ALIGNMENT, IMPLICIT_NULL_CHECK_LIMIT, INLINE_OBJECTS, supportsFP64, extensions);
    }

    protected MetalTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, boolean supportsFP64, String extensions) {
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

    public MetalArchitecture getArch() {
        return (MetalArchitecture) arch;
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

    public MetalKind getMetalKind(JavaKind javaKind, int vectorLength) {
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
        return LIRKind.value(getMetalKind(javaKind, vectorLength));
    }

    public MetalKind getMetalKind(JavaKind javaKind) {
        return (MetalKind) arch.getPlatformKind(javaKind);
    }
}

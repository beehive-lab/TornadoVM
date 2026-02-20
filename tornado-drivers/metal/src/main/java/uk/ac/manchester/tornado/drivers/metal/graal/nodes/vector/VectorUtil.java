/*
 * Copyright (c) 2018, 2020, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector;

import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.VLOAD16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.VLOAD2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.VLOAD3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.VLOAD4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic.VLOAD8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_BYTE16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_DOUBLE16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_FLOAT16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_HALF16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_INT16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16.VMOV_SHORT16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_BYTE2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_DOUBLE2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_FLOAT2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_HALF2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_INT2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2.VMOV_SHORT2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_BYTE3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_DOUBLE3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_FLOAT3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_HALF3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_INT3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3.VMOV_SHORT3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_BYTE4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_DOUBLE4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_FLOAT4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_HALF4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_INT4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4.VMOV_SHORT4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_BYTE8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_DOUBLE8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_FLOAT8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_HALF8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_INT8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8.VMOV_SHORT8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.VSTORE16;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.VSTORE2;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.VSTORE3;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.VSTORE4;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic.VSTORE8;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp.CAST_TO_BYTE_PTR;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp.CAST_TO_FLOAT_PTR;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp.CAST_TO_INT_PTR;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp.CAST_TO_SHORT_PTR;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp16;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp2;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp3;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp4;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalOp8;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalTernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

public final class VectorUtil {

    private static final MetalBinaryIntrinsic[] loadTable = new MetalBinaryIntrinsic[] { VLOAD2, VLOAD3, VLOAD4, VLOAD8, VLOAD16 };

    private static final MetalTernaryIntrinsic[] storeTable = new MetalTernaryIntrinsic[] { VSTORE2, VSTORE3, VSTORE4, VSTORE8, VSTORE16 };

    private static final MetalUnaryOp[] pointerTable = new MetalUnaryOp[] { CAST_TO_SHORT_PTR, CAST_TO_INT_PTR, CAST_TO_FLOAT_PTR, CAST_TO_BYTE_PTR };

    private static final MetalOp2[] assignOp2Table = new MetalOp2[] { VMOV_SHORT2, VMOV_INT2, VMOV_FLOAT2, VMOV_BYTE2, VMOV_DOUBLE2, VMOV_HALF2 };
    private static final MetalOp3[] assignOp3Table = new MetalOp3[] { VMOV_SHORT3, VMOV_INT3, VMOV_FLOAT3, VMOV_BYTE3, VMOV_DOUBLE3, VMOV_HALF3 };
    private static final MetalOp4[] assignOp4Table = new MetalOp4[] { VMOV_SHORT4, VMOV_INT4, VMOV_FLOAT4, VMOV_BYTE4, VMOV_DOUBLE4, VMOV_HALF4 };
    private static final MetalOp8[] assignOp8Table = new MetalOp8[] { VMOV_SHORT8, VMOV_INT8, VMOV_FLOAT8, VMOV_BYTE8, VMOV_DOUBLE8, VMOV_HALF8 };
    private static final MetalOp16[] assignOp16Table = new MetalOp16[] { VMOV_SHORT16, VMOV_INT16, VMOV_FLOAT16, VMOV_BYTE16, VMOV_DOUBLE16, VMOV_HALF16 };

    private static <T> T lookupValueByLength(T[] array, MetalKind vectorKind) {
        final int index = vectorKind.lookupLengthIndex();
        if (index != -1) {
            return array[index];
        } else {
            throw TornadoInternalError.shouldNotReachHere("Unsupported vector type: " + vectorKind.toString());
        }
    }

    private static <T> T lookupValueByType(T[] array, MetalKind vectorKind) {
        final int index = vectorKind.lookupTypeIndex();
        if (index != -1) {
            return array[index];
        } else {
            throw TornadoInternalError.shouldNotReachHere("Unsupported vector type: " + vectorKind.toString());
        }
    }

    public static MetalOp2 resolveAssignOp2(MetalKind vectorKind) {
        return lookupValueByType(assignOp2Table, vectorKind);
    }

    public static MetalOp3 resolveAssignOp3(MetalKind vectorKind) {
        return lookupValueByType(assignOp3Table, vectorKind);
    }

    public static MetalOp4 resolveAssignOp4(MetalKind vectorKind) {
        return lookupValueByType(assignOp4Table, vectorKind);
    }

    public static MetalOp8 resolveAssignOp8(MetalKind vectorKind) {
        return lookupValueByType(assignOp8Table, vectorKind);
    }

    public static MetalOp16 resolveAssignOp16(MetalKind vectorKind) {
        return lookupValueByType(assignOp16Table, vectorKind);
    }

    public static MetalTernaryIntrinsic resolveStoreIntrinsic(MetalKind vectorKind) {
        return lookupValueByLength(storeTable, vectorKind);
    }

    public static MetalBinaryIntrinsic resolveLoadIntrinsic(MetalKind vectorKind) {
        return lookupValueByLength(loadTable, vectorKind);
    }

}

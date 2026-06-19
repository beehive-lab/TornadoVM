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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic.VLOAD16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic.VLOAD2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic.VLOAD3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic.VLOAD4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic.VLOAD8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_BYTE16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_DOUBLE16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_FLOAT16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_HALF16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_INT16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16.VMOV_SHORT16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_BYTE2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_DOUBLE2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_FLOAT2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_HALF2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_INT2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2.VMOV_SHORT2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_BYTE3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_DOUBLE3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_FLOAT3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_HALF3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_INT3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3.VMOV_SHORT3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_BYTE4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_DOUBLE4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_FLOAT4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_HALF4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_INT4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4.VMOV_SHORT4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_BYTE8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_DOUBLE8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_FLOAT8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_HALF8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_INT8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8.VMOV_SHORT8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic.VSTORE16;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic.VSTORE2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic.VSTORE3;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic.VSTORE4;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic.VSTORE8;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp.CAST_TO_BYTE_PTR;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp.CAST_TO_FLOAT_PTR;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp.CAST_TO_INT_PTR;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp.CAST_TO_SHORT_PTR;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp16;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp2;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp3;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp4;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAOp8;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDATernaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;

public final class VectorUtil {

    private static final CUDABinaryIntrinsic[] loadTable = new CUDABinaryIntrinsic[] { VLOAD2, VLOAD3, VLOAD4, VLOAD8, VLOAD16 };

    private static final CUDATernaryIntrinsic[] storeTable = new CUDATernaryIntrinsic[] { VSTORE2, VSTORE3, VSTORE4, VSTORE8, VSTORE16 };

    private static final CUDAUnaryOp[] pointerTable = new CUDAUnaryOp[] { CAST_TO_SHORT_PTR, CAST_TO_INT_PTR, CAST_TO_FLOAT_PTR, CAST_TO_BYTE_PTR };

    private static final CUDAOp2[] assignOp2Table = new CUDAOp2[] { VMOV_SHORT2, VMOV_INT2, VMOV_FLOAT2, VMOV_BYTE2, VMOV_DOUBLE2, VMOV_HALF2 };
    private static final CUDAOp3[] assignOp3Table = new CUDAOp3[] { VMOV_SHORT3, VMOV_INT3, VMOV_FLOAT3, VMOV_BYTE3, VMOV_DOUBLE3, VMOV_HALF3 };
    private static final CUDAOp4[] assignOp4Table = new CUDAOp4[] { VMOV_SHORT4, VMOV_INT4, VMOV_FLOAT4, VMOV_BYTE4, VMOV_DOUBLE4, VMOV_HALF4 };
    private static final CUDAOp8[] assignOp8Table = new CUDAOp8[] { VMOV_SHORT8, VMOV_INT8, VMOV_FLOAT8, VMOV_BYTE8, VMOV_DOUBLE8, VMOV_HALF8 };
    private static final CUDAOp16[] assignOp16Table = new CUDAOp16[] { VMOV_SHORT16, VMOV_INT16, VMOV_FLOAT16, VMOV_BYTE16, VMOV_DOUBLE16, VMOV_HALF16 };

    private static <T> T lookupValueByLength(T[] array, CUDAKind vectorKind) {
        final int index = vectorKind.lookupLengthIndex();
        if (index != -1) {
            return array[index];
        } else {
            throw TornadoInternalError.shouldNotReachHere("Unsupported vector type: " + vectorKind.toString());
        }
    }

    private static <T> T lookupValueByType(T[] array, CUDAKind vectorKind) {
        final int index = vectorKind.lookupTypeIndex();
        if (index != -1) {
            return array[index];
        } else {
            throw TornadoInternalError.shouldNotReachHere("Unsupported vector type: " + vectorKind.toString());
        }
    }

    public static CUDAOp2 resolveAssignOp2(CUDAKind vectorKind) {
        return lookupValueByType(assignOp2Table, vectorKind);
    }

    public static CUDAOp3 resolveAssignOp3(CUDAKind vectorKind) {
        return lookupValueByType(assignOp3Table, vectorKind);
    }

    public static CUDAOp4 resolveAssignOp4(CUDAKind vectorKind) {
        return lookupValueByType(assignOp4Table, vectorKind);
    }

    public static CUDAOp8 resolveAssignOp8(CUDAKind vectorKind) {
        return lookupValueByType(assignOp8Table, vectorKind);
    }

    public static CUDAOp16 resolveAssignOp16(CUDAKind vectorKind) {
        return lookupValueByType(assignOp16Table, vectorKind);
    }

    public static CUDATernaryIntrinsic resolveStoreIntrinsic(CUDAKind vectorKind) {
        return lookupValueByLength(storeTable, vectorKind);
    }

    public static CUDABinaryIntrinsic resolveLoadIntrinsic(CUDAKind vectorKind) {
        return lookupValueByLength(loadTable, vectorKind);
    }

}

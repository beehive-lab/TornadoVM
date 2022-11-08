/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.DOT;

import org.graalvm.compiler.lir.Variable;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class PTXVectorSplit {
    private static final int MAX_VECTOR_SIZE_BYTES = 16;

    public PTXKind actualKind;
    public String[] vectorNames;
    public PTXKind newKind;
    public boolean fullUnwrapVector;

    public PTXVectorSplit(Variable actualVector) {
        this(actualVector.getName(), (PTXKind) actualVector.getPlatformKind());
    }

    public PTXVectorSplit(String actualVectorName, PTXKind actualKind) {
        this.actualKind = actualKind;

        this.newKind = lowerVectorPTXKind(actualKind);
        this.vectorNames = new String[actualKind.getVectorLength() / newKind.getVectorLength()];
        for (int i = 0; i < vectorNames.length; i++) {
            vectorNames[i] = actualVectorName + i;
        }
    }

    /**
     * This private method can be used as a replacement for the constructor above.
     * Instead of fully unwrapping the Tornado vector to single PTX variables, it
     * will perform unwrapping of the Tornado vector to vector types which exist in
     * PTX.
     */
    private void PTXVectorSplit(String actualVectorName, PTXKind actualKind) {
        this.actualKind = actualKind;

        if (actualKind.getSizeInBytes() <= MAX_VECTOR_SIZE_BYTES && actualKind.getVectorLength() != 3) {
            this.vectorNames = new String[] { actualVectorName };
            this.newKind = actualKind;
            return;
        }

        if (actualKind.getVectorLength() == 3) {
            this.fullUnwrapVector = true;
        }

        this.newKind = lowerVectorPTXKindNotUsed(actualKind);
        this.vectorNames = new String[actualKind.getVectorLength() / newKind.getVectorLength()];
        for (int i = 0; i < vectorNames.length; i++) {
            vectorNames[i] = actualVectorName + i;
        }
    }

    /**
     * The OpenCL Nvidia driver fully unwraps vector types to variables. For now, we
     * do the same due to memory alignment issues (loads and stores on vector types
     * must be aligned by the size of the vector in PTX). The method below does what
     * we should normally do if memory alignment wouldn't be an issue.
     */
    private PTXKind lowerVectorPTXKindNotUsed(PTXKind vectorKind) {
        switch (vectorKind) {
            case DOUBLE3:
                return PTXKind.F64;
            case DOUBLE4:
            case DOUBLE8:
                return PTXKind.DOUBLE2;
            case FLOAT8:
                return PTXKind.FLOAT4;
            case FLOAT3:
                return PTXKind.F32;
            case INT3:
                return PTXKind.S32;
            case CHAR3:
                return PTXKind.U8;
            default:
                TornadoInternalError.shouldNotReachHere();
        }
        return null;
    }

    private PTXKind lowerVectorPTXKind(PTXKind vectorKind) {
        fullUnwrapVector = true;
        return vectorKind.getElementKind();
    }

    public String getVectorElement(int laneId) {
        assert laneId < 16;
        String vectorElement = vectorNames[laneId / newKind.getVectorLength()];
        if (!fullUnwrapVector) {
            vectorElement += DOT + laneIdToVectorSuffix(laneId);
        }
        return vectorElement;
    }

    private String laneIdToVectorSuffix(int laneId) {
        assert laneId < 16;
        switch ((laneId % 4) % newKind.getVectorLength()) {
            case 0:
                return "x";
            case 1:
                return "y";
            case 2:
                return "z";
            case 3:
                return "w";
            default:
                shouldNotReachHere();
        }
        return null;
    }
}

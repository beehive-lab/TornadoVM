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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.lir.Variable;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class PTXVectorSplit {
    private static final int MAX_VECTOR_SIZE_BYTES = 16;

    public PTXKind actualKind;
    public String[] vectorNames;
    public PTXKind newKind;
    public boolean fullUnwrapVector;

    private Map<String, Integer> vectorVariableMap;

    public PTXVectorSplit(Variable actualVector) {
        this(actualVector.toString(), (PTXKind) actualVector.getPlatformKind());
    }

    public PTXVectorSplit(String actualVectorName, PTXKind actualKind) {
        this.actualKind = actualKind;
        this.vectorVariableMap = new HashMap<>();
        this.newKind = lowerVectorPTXKind(actualKind);
        this.vectorNames = generateVectorNames(actualVectorName, actualKind.getVectorLength(), newKind.getVectorLength());
        convertVectorNames();
    }

    /**
     * It generates an array of vector names.
     *
     * @param baseName
     *     The base name for vector names.
     * @param actualVectorLength
     *     The actual vector length.
     * @param newVectorLength
     *     The new vector length.
     * @return An array of generated vector names.
     */
    private String[] generateVectorNames(String baseName, int actualVectorLength, int newVectorLength) {
        String[] names = new String[actualVectorLength / newVectorLength];
        for (int i = 0; i < names.length; i++) {
            names[i] = baseName + i;
        }
        return names;

    }

    /**
     * It converts vector names using the provided conversion logic.
     */
    private void convertVectorNames() {
        for (int i = 0; i < vectorNames.length; i++) {
            vectorNames[i] = convertVariableName(vectorNames[i], actualKind);
        }
    }

    /**
     * It converts the original variable name to a formatted intermediate name.
     *
     * @param originalName
     *     The original variable name in the format "vXX|StringXY".
     * @param actualKind
     *     The actual kind of the variable (e.g., PTXKind).
     * @return The formatted intermediate variable name.
     */
    private String convertVariableName(String originalName, PTXKind actualKind) {
        String[] parts = originalName.split("\\|");

        if (parts.length != 1) {
            String variableName = parts[1];
            int vectorLength = extractVectorLength(variableName);
            String intermediateName = actualKind.getRegisterTypeString() + parts[0] + vectorLength + "Vec";
            return intermediateName + getOrCreateCounter(intermediateName);
        } else {
            // This case covers when we pass around vectors on multiple func call aka a0 for
            // example
            return parts[0];
        }

    }

    /**
     * It extracts the vector length from the variable name.
     *
     * @param variableName
     *     The variable name containing the vector length.
     * @return The extracted vector length.
     */
    private int extractVectorLength(String variableName) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(variableName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 0;
    }

    /**
     * It gets or creates a counter for the given variable name.
     *
     * @param variableName
     *     The variable name for which to get or create the counter.
     * @return The counter value before incrementing.
     */
    private int getOrCreateCounter(String variableName) {
        vectorVariableMap.putIfAbsent(variableName, 0);
        int counter = vectorVariableMap.get(variableName) + 1;
        vectorVariableMap.put(variableName, counter);
        return counter - 1; // Return the previous counter value before incrementing
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
            case FLOAT16:
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
        assert laneId <= 16;
        String vectorElement = vectorNames[laneId / newKind.getVectorLength()];
        if (!fullUnwrapVector) {
            vectorElement += DOT + laneIdToVectorSuffix(laneId);
        }
        return vectorElement;
    }

    private String laneIdToVectorSuffix(int laneId) {
        assert laneId <= 16;
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

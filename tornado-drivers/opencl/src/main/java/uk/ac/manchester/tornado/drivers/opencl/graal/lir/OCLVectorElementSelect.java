/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("VSEL")
public class OCLVectorElementSelect extends OCLLIROp {

    final Value vector;
    private final Value selection;

    public OCLVectorElementSelect(LIRKind lirKind, Value vector, Value selection) {
        super(lirKind);
        this.vector = vector;
        this.selection = selection;
    }

    /**
     * Converts a numeric index to comply with the specified rules for a 16-component vector.
     * <p>
     * The specification allows numeric indices in the range:
     * 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, a, A, b, B, c, C, d, D, e, E, f, F
     * <p>
     * For values between 10 and 15 (inclusive), this method returns the corresponding uppercase letter (A to F).
     * Otherwise, it throws an {@code IllegalArgumentException} for invalid values.
     *
     * @param value
     *     The numeric index to be converted.
     * @return The converted character complying with the specified rules.
     * @throws IllegalArgumentException
     *     If the value is outside the allowed range.
     *
     *
     */
    public static char convertForWithOf16(int value) {
        if (value >= 10 && value <= 15) {
            // Convert values 10 to 15 to letters A to F
            return (char) ('A' + value - 10);
        } else {
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emitValueOrOp(crb, vector);
        int idx = Integer.parseInt(OCLAssembler.getAbsoluteIndexFromValue(selection));
        String vectorIndex = idx > 9 ? String.valueOf(convertForWithOf16(idx)) : OCLAssembler.getAbsoluteIndexFromValue(selection);
        asm.emitSymbol(".s" + vectorIndex);
    }

    @Override
    public String toString() {
        return String.format("vselect(%s, %s)", vector, selection);
    }

}

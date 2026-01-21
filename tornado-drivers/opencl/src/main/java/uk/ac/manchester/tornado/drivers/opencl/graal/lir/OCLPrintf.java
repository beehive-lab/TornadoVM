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
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("PRINTF")
public class OCLPrintf extends OCLLIROp {

    private Value[] inputs;

    public OCLPrintf(Value[] inputs) {
        super(LIRKind.Illegal);
        this.inputs = inputs;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emit("printf( \"tornado[%%3d,%%3d,%%3d]> %s\"",
            asm.formatConstant((ConstantValue) inputs[0]));

        for (int i = 1; i < 4; i++) {
            asm.emit(", ");
            asm.emitValue(crb, inputs[i]);
        }

        if (inputs.length > 4) {
            asm.emit(", ");
        }

        for (int i = 4; i < inputs.length - 1; i++) {
            asm.emitValue(crb, inputs[i]);
            asm.emit(", ");
        }

        if (inputs.length > 4) {
            asm.emitValue(crb, inputs[inputs.length - 1]);
        }

        asm.emit(")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("printf( \"%s\"", inputs[0]));

        if (inputs.length > 4) {
            sb.append(", ");
        }
        for (int i = 4; i < inputs.length - 1; i++) {
            sb.append(inputs[i]);
            sb.append(", ");
        }
        if (inputs.length > 4) {
            sb.append(inputs[inputs.length - 1]);
        }
        sb.append(" )");
        return sb.toString();
    }

}

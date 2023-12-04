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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("TPRINTF")
public class OCLTPrintf extends OCLLIROp {

    private Value[] inputs;

    public OCLTPrintf(Value[] inputs) {
        super(LIRKind.Illegal);
        this.inputs = inputs;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        int depth = crb.getResult().getMeta().getDomain().getDepth();

        asm.emit("if( ");
        asm.emit("get_global_id(0) == ");
        asm.emitValue(crb, inputs[0]);

        if (depth > 1) {
            asm.emit(" && get_global_id(1) == ");
            asm.emitValue(crb, inputs[1]);
        }

        if (depth > 2) {
            asm.emit(" && get_global_id(2) == ");
            asm.emitValue(crb, inputs[2]);
        }

        asm.emit(" )");
        asm.beginScope();

        asm.indent();
        asm.emit("printf( \"tornado[%3d,%3d,%3d]> ");
        asm.emitValue(crb, inputs[3]);
        asm.emit("\", ");
        for (int i = 0; i < 3; i++) {
            asm.emitValue(crb, inputs[i]);
            asm.emit(", ");
        }
        for (int i = 4; i < inputs.length - 1; i++) {
            asm.emitValue(crb, inputs[i]);
            asm.emit(", ");
        }
        asm.emitValue(crb, inputs[inputs.length - 1]);
        asm.emit(")");
        asm.delimiter();
        asm.eol();
        asm.endScope("  -- ");

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<%s,%s,%s> tprintf( %s", inputs[0], inputs[1], inputs[2], inputs[3]));
        for (int i = 4; i < inputs.length - 1; i++) {
            sb.append(inputs[i]);
            sb.append(", ");
        }
        sb.append(inputs[inputs.length - 1]);
        sb.append(" )");
        return sb.toString();
    }

}

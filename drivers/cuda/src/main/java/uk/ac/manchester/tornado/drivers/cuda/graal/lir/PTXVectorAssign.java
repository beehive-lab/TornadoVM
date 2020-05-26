/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.CURLY_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.CURLY_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.DOT;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.MOVE;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.TAB;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.VECTOR;

public class PTXVectorAssign {

    /**
     * PTX vector assignment expression
     */
    public static class AssignVectorExpr extends PTXLIROp {

        @Use protected Value[] values;

        public AssignVectorExpr(PTXKind ptxKind, Value... values) {
            super(LIRKind.value(ptxKind));
            this.values = values;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            asm.emitSymbol(TAB);
            asm.emitSymbol(MOVE);
            asm.emitSymbol(DOT);
            asm.emit(VECTOR + dest.getPlatformKind().getVectorLength());
            asm.emitSymbol(DOT);
            asm.emit(((PTXKind)dest.getPlatformKind()).getElementKind().toString());
            asm.emitSymbol(TAB);

            asm.emitValue(dest);
            asm.emitSymbol(COMMA);
            asm.emitSymbol(SPACE);
            asm.emitSymbol(CURLY_BRACKETS_OPEN);
            asm.emitValues(values);
            asm.emitSymbol(CURLY_BRACKETS_CLOSE);
        }
    }
}

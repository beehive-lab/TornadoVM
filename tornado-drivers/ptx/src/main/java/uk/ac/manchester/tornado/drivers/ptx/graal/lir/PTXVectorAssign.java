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

/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.getFPURoundingMode;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CONVERT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.DOT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.MOVE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;
import static uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.AssignStmt.shouldEmitMove;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

public class PTXVectorAssign {

    public static void doVectorToVectorAssign(PTXAssembler asm, PTXVectorSplit lhsVectorSplit, PTXVectorSplit rhsVectorSplit) {
        PTXKind destElementKind = lhsVectorSplit.newKind;
        PTXKind srcElementKind = rhsVectorSplit.newKind;

        for (int i = 0; i < rhsVectorSplit.vectorNames.length; i++) {
            asm.emitSymbol(TAB);
            if (shouldEmitMove(destElementKind, srcElementKind)) {
                asm.emit(MOVE + "." + destElementKind.toString());
            } else {
                asm.emit(CONVERT + ".");
                if ((destElementKind.isFloating() || srcElementKind.isFloating()) && getFPURoundingMode(destElementKind, srcElementKind) != null) {
                    asm.emit(getFPURoundingMode(destElementKind, srcElementKind));
                    asm.emitSymbol(DOT);
                }
                asm.emit(destElementKind.toString());
                asm.emitSymbol(DOT);
                asm.emit(srcElementKind.toString());
            }
            asm.emitSymbol(TAB);
            asm.emitSymbol(lhsVectorSplit.vectorNames[i]);
            asm.emitSymbol(COMMA);
            asm.emitSymbol(SPACE);
            asm.emitSymbol(rhsVectorSplit.vectorNames[i]);

            if (i < rhsVectorSplit.vectorNames.length - 1) {
                asm.delimiter();
                asm.eol();
            }
        }
    }

    /**
     * PTX vector assignment expression
     */
    public static class AssignVectorExpr extends PTXLIROp {

        @Use
        protected Value[] values;

        public AssignVectorExpr(PTXKind ptxKind, Value... values) {
            super(LIRKind.value(ptxKind));
            this.values = values;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
            PTXKind destElementKind = ((PTXKind) dest.getPlatformKind()).getElementKind();
            PTXVectorSplit vectorSplitData = new PTXVectorSplit(dest);

            for (int i = 0; i < vectorSplitData.vectorNames.length; i++) {
                Value[] intermValues = new Value[vectorSplitData.newKind.getVectorLength()];
                if (vectorSplitData.newKind.getVectorLength() >= 0) {
                    System.arraycopy(values, i * vectorSplitData.newKind.getVectorLength(), intermValues, 0, vectorSplitData.newKind.getVectorLength());
                }
                PTXKind valueKind = (PTXKind) values[i * vectorSplitData.newKind.getVectorLength()].getPlatformKind();

                asm.emitSymbol(TAB);
                if (shouldEmitMove(destElementKind, valueKind)) {
                    asm.emit(MOVE + "." + destElementKind.toString());
                } else {
                    asm.emit(CONVERT + ".");
                    if ((destElementKind.isFloating() || valueKind.isFloating() || (destElementKind.isB16() && valueKind.isU64()) || (destElementKind.isB16() && valueKind
                            .isS32())) && getFPURoundingMode(destElementKind, valueKind) != null) {
                        asm.emit(getFPURoundingMode(destElementKind, valueKind));
                        asm.emitSymbol(DOT);
                    }
                    if (destElementKind.isB16()) {
                        asm.emit("f16");
                    } else {
                        asm.emit(destElementKind.toString());
                    }
                    asm.emitSymbol(DOT);
                    asm.emit(valueKind.toString());
                }
                asm.emitSymbol(TAB);
                asm.emitSymbol(vectorSplitData.vectorNames[i]);
                asm.emitSymbol(COMMA);
                asm.emitSymbol(SPACE);
                asm.emitValuesOrOp(crb, intermValues, dest);

                if (i < vectorSplitData.vectorNames.length - 1) {
                    asm.delimiter();
                    asm.eol();
                }
            }
        }
    }

}

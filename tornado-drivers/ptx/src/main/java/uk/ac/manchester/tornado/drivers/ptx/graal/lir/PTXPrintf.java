/*
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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

@Opcode("PRINTF")
public class PTXPrintf extends PTXLIROp {

    private final Value formatParam;
    private final Value valList;

    public PTXPrintf(Value formatParam, Value valList) {
        super(LIRKind.Illegal);
        this.formatParam = formatParam;
        this.valList = valList;
    }

    @Override
    public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
        asm.emitSymbol(PTXAssemblerConstants.TAB);
        asm.emit("call (_), vprintf, (");
        asm.emitValue(formatParam);
        asm.emitSymbol(PTXAssemblerConstants.COMMA);
        asm.emitSymbol(PTXAssemblerConstants.SPACE);
        asm.emitValue(valList);
        asm.emit(")");
    }
}

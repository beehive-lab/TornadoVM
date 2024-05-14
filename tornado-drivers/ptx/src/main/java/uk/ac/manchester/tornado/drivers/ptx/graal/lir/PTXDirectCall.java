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

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.CALL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.COMMA;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_BRACKETS_CLOSE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_BRACKETS_OPEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.SPACE;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.TAB;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodes.DirectCallTargetNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;

public class PTXDirectCall extends PTXLIROp {

    protected DirectCallTargetNode target;
    @LIRInstruction.Def
    protected Value result;
    @LIRInstruction.Use
    protected Value[] parameters;

    public PTXDirectCall(DirectCallTargetNode target, Value result, Value[] parameters) {
        super(LIRKind.value(result.getPlatformKind()));
        this.result = result;
        this.parameters = parameters;
        this.target = target;
    }

    @Override
    public void emit(PTXCompilationResultBuilder crb, PTXAssembler asm, Variable dest) {
        final String methodName = PTXCodeUtil.makeMethodName(target.targetMethod());

        asm.emitSymbol(TAB);
        asm.emitSymbol(CALL + SPACE);
        if (result != null && !Value.ILLEGAL.equals(result)) {
            asm.emit("(%s), ", PTXAssembler.toString(result));
        }
        asm.emit(methodName);
        asm.emit(COMMA + SPACE + ROUND_BRACKETS_OPEN);
        int i = 0;
        for (Value param : parameters) {
            PTXKind paramKind = (PTXKind) param.getPlatformKind();
            if (paramKind.isVector()) {
                TornadoInternalError.guarantee(param instanceof Variable, "Function parameter should be a variable !");
                PTXVectorSplit vectorSplit = new PTXVectorSplit((Variable) param);
                for (int j = 0; j < vectorSplit.vectorNames.length; j++) {
                    asm.emit(vectorSplit.vectorNames[j]);
                    if (j < vectorSplit.vectorNames.length - 1) {
                        asm.emit(COMMA + SPACE);
                    }
                }
            } else {
                asm.emit(PTXAssembler.toString(param));
            }
            if (i < parameters.length - 1) {
                asm.emit(COMMA + SPACE);
            }
            i++;
        }
        asm.emit(ROUND_BRACKETS_CLOSE);

        crb.addNonInlinedMethod(target.targetMethod());
    }
}

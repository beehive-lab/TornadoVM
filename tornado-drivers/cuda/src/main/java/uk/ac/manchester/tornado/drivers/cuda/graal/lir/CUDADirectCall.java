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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction.Def;
import jdk.graal.compiler.lir.LIRInstruction.Use;
import jdk.graal.compiler.nodes.DirectCallTargetNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAUtils;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

public class CUDADirectCall extends CUDALIROp {

    protected DirectCallTargetNode target;
    protected LIRFrameState frameState;
    @Def
    protected Value result;
    @Use
    protected Value[] parameters;

    public CUDADirectCall(DirectCallTargetNode target, Value result, Value[] parameters, LIRFrameState frameState) {
        super(LIRKind.value(result.getPlatformKind()));
        this.result = result;
        this.parameters = parameters;
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm) {

        final String methodName = CUDAUtils.makeMethodName(target.targetMethod());

        asm.emit(methodName);
        asm.emit("(");
        int paramCounter = 0;
        asm.emit(((CUDAArchitecture) crb.target.arch).getCallingConvention());
        asm.emit(", ");
        for (Value param : parameters) {
            asm.emit(asm.toString(param));
            if (paramCounter < parameters.length - 1) {
                asm.emit(", ");
            }
            paramCounter++;
        }
        asm.emit(")");

        crb.addNonInlinedMethod(target.targetMethod());
    }
}

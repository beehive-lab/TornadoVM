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

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.globalSpace;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.STACK_BASE_OFFSET;

import java.util.HashMap;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary.MemoryAccess;

public class PTXGenTool {

    protected PTXLIRGenerator gen;

    private final HashMap<ParameterNode, Variable> parameterToVariable = new HashMap<>();

    public PTXGenTool(PTXLIRGenerator generator) {
        gen = generator;
    }

    public void emitVectorLoad(Variable result, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new PTXLIRStmt.VectorLoadStmt(result, address));
    }

    public Value emitParameterLoad(ParameterNode paramNode, int paramOffset) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));

        PTXKind kind = (PTXKind) lirKind.getPlatformKind();

        PTXTargetDescription target = gen.target();

        Variable result = (kind.isVector()) ? gen.newVariable(LIRKind.value(target.getPTXKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, paramOffset);
        parameterToVariable.put(paramNode, result);

        if (kind.isVector()) {
            Variable vector = gen.newVariable(lirKind);
            PTXArchitecture.PTXMemoryBase base = globalSpace;
            MemoryAccess address = new MemoryAccess(base, result);

            emitVectorLoad(vector, address);
            result = vector;
        }

        return result;
    }

    /**
     * Generate code for an address access from the stack frame.
     *
     * PTX Code equivalent:
     *
     * <code>
     *     ldu.global.u64	rud1, [rud0+24];
     * </code>
     *
     * @param dst
     *            result
     * @param index
     *            index from the stack frame to load.
     */
    private void emitParameterLoad(AllocatableValue dst, int index) {
        ConstantValue stackIndex = new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt((index + STACK_BASE_OFFSET) * PTXKind.U64.getSizeInBytes()));

        Variable parameterAllocation = gen.getParameterAllocation(PTXArchitecture.STACK_POINTER);
        MemoryAccess memoryAccess = new MemoryAccess(globalSpace, parameterAllocation, stackIndex);
        PTXLIRStmt.LoadStmt loadStmt = new PTXLIRStmt.LoadStmt(memoryAccess, (Variable) dst, PTXAssembler.PTXNullaryOp.LDU);
        gen.append(loadStmt);
    }

    public HashMap<ParameterNode, Variable> getParameterToVariable() {
        return parameterToVariable;
    }

}

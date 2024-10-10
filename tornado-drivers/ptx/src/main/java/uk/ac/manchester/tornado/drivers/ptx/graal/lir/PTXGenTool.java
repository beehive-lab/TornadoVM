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

import java.util.HashMap;

import jdk.vm.ci.meta.Local;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;

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

    private final HashMap<ParameterNode, Value> parameterToVariable = new HashMap<>();

    public PTXGenTool(PTXLIRGenerator generator) {
        gen = generator;
    }

    public void emitVectorLoad(Variable result, MemoryAccess address) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new PTXLIRStmt.VectorLoadStmt(result, address));
    }

    public Value emitParameterLoad(Local local,  ParameterNode paramNode) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));

        PTXKind kind = (PTXKind) lirKind.getPlatformKind();

        PTXTargetDescription target = gen.target();

        Variable result = (kind.isVector()) ? gen.newVariable(LIRKind.value(target.getPTXKind(JavaKind.Object))) : gen.newVariable(lirKind);
        gen.append(new PTXLIRStmt.LoadStmt(new PTXUnary.MemoryAccess(local.getName()), result, PTXAssembler.PTXNullaryOp.LD));
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

    public HashMap<ParameterNode, Value> getParameterToVariable() {
        return parameterToVariable;
    }

}

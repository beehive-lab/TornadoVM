/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

public class PTXLIRGenerationResult extends LIRGenerationResult {

    private final Map<PTXKind, Set<VariableData>> variableTable;
    private final Map<PTXKind, List<Variable>> returnVariables;

    public PTXLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig, CallingConvention callingConvention) {
        super(identifier, lir, frameMapBuilder, registerAllocationConfig, callingConvention);

        variableTable = new HashMap<>();
        returnVariables = new HashMap<>();
    }

    public int insertVariableAndGetIndex(Variable variable, boolean isArray) {
        guarantee(variable.getPlatformKind() instanceof PTXKind, "invalid variable kind: %s", variable.getValueKind());
        PTXKind kind = (PTXKind) variable.getPlatformKind();

        variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(new VariableData(variable, isArray));
        int arrayCount = isArray ? 0 : (int) variableTable.get(kind).stream().filter(varData -> varData.isArray).count();
        return variableTable.get(kind).size() - arrayCount - 1;
    }

    public Map<PTXKind, Set<VariableData>> getVariableTable() {
        return variableTable;
    }

    public void setReturnVariable(Variable variable) {
        PTXKind ptxKind = (PTXKind) variable.getPlatformKind();
        returnVariables.computeIfAbsent(ptxKind, k -> new ArrayList<>()).add(variable);
    }

    public List<Variable> getReturnVariables(PTXKind kind) {
        return returnVariables.get(kind);
    }

    public static class VariableData {
        public boolean isArray;
        public Variable variable;

        public VariableData(Variable variable, boolean isArray) {
            this.variable = variable;
            this.isArray = isArray;
        }
    }
}

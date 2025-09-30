/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

public class MetalLIRGenerationResult extends LIRGenerationResult {

    private final Map<MetalKind, Set<Variable>> variableTable;

    public MetalLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig, CallingConvention callingConvention) {
        super(identifier, lir, frameMapBuilder, registerAllocationConfig, callingConvention);
        variableTable = new HashMap<>();
    }

    public void insertVariable(Variable variable) {
        guarantee(variable.getPlatformKind() instanceof MetalKind, "invalid variable kind: %s", variable.getValueKind());
        MetalKind kind = (MetalKind) variable.getPlatformKind();

        variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(variable);
    }

    public Map<MetalKind, Set<Variable>> getVariableTable() {
        return variableTable;
    }

}

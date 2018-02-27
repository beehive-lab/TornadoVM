/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLLIRGenerationResult extends LIRGenerationResult {

    private final Map<OCLKind, Set<Variable>> variableTable;

    public OCLLIRGenerationResult(
            CompilationIdentifier identifier,
            LIR lir,
            FrameMapBuilder frameMapBuilder,
            CallingConvention callingConvention) {
        super(identifier, lir, frameMapBuilder, callingConvention);
        variableTable = new HashMap<>();
    }

    public void insertVariable(Variable variable) {
        guarantee(variable.getPlatformKind() instanceof OCLKind, "invalid variable kind: %s", variable.getValueKind());
        OCLKind kind = (OCLKind) variable.getPlatformKind();

        variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(variable);
    }

    public Map<OCLKind, Set<Variable>> getVariableTable() {
        return variableTable;
    }

}

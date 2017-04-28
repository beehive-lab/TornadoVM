/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.CompilationIdentifier;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jdk.vm.ci.code.CallingConvention;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

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

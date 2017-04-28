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
package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction.Def;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.nodes.DirectCallTargetNode;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLUtils;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLDirectCall extends OCLLIROp {

    protected DirectCallTargetNode target;
    protected LIRFrameState frameState;
    @Def
    protected Value result;
    @Use
    protected Value[] parameters;

    public OCLDirectCall(DirectCallTargetNode target, Value result,
            Value[] parameters, LIRFrameState frameState) {
        super(LIRKind.value(result.getPlatformKind()));
        this.result = result;
        this.parameters = parameters;
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {

        final String methodName = OCLUtils
                .makeMethodName(target.targetMethod());

        asm.emit(methodName);
        asm.emit("(");
        int i = 0;
        asm.emit(((OCLArchitecture) crb.target.arch).getCallingConvention());
        asm.emit(", ");
        for (Value param : parameters) {
            // System.out.printf("param: %s\n",param);
            asm.emit(asm.toString(param));
            if (i < parameters.length - 1) {
                asm.emit(", ");
            }

            i++;
        }
        asm.emit(")");

        // System.out.printf("direct call: method=%s, frameState=%s\n",target.targetMethod(),frameState);
        crb.addNonInlinedMethod(target.targetMethod());

    }
}

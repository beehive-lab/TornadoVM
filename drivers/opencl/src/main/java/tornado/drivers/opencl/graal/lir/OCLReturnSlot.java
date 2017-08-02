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

import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.FRAME_REF_NAME;
import static tornado.drivers.opencl.mm.OCLCallStack.RETURN_VALUE_INDEX;

@Opcode("RETURN VALUE")
public class OCLReturnSlot extends AllocatableValue {

    private OCLNullaryOp op;

    public OCLReturnSlot(LIRKind lirKind) {
        super(lirKind);
        op = OCLNullaryOp.SLOTS_BASE_ADDRESS;
    }

    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        OCLKind type = ((OCLKind) getPlatformKind());
        asm.emit("%s[%d]", FRAME_REF_NAME, RETURN_VALUE_INDEX);
    }

    @Override
    public String toString() {
        return "RETURN_SLOT";
    }

}

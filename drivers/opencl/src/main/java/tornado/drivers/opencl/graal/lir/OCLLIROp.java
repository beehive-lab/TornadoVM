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

import org.graalvm.compiler.core.common.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public abstract class OCLLIROp extends Value {

    public OCLLIROp(LIRKind lirKind) {
        super(lirKind);
    }

    public final void emit(OCLCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(OCLCompilationResultBuilder crb, OCLAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public OCLKind getOCLKind() {
        PlatformKind pk = getPlatformKind();
        return (pk instanceof OCLKind) ? (OCLKind) pk : OCLKind.ILLEGAL;
    }

}

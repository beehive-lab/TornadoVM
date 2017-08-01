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
import org.graalvm.compiler.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryTemplate;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

public class OCLNullary {

    /**
     * Abstract operation which consumes no inputs
     */
    protected static class NullaryConsumer extends OCLLIROp {

        @Opcode
        protected final OCLNullaryOp opcode;

        protected NullaryConsumer(OCLNullaryOp opcode, LIRKind lirKind) {
            super(lirKind);
            this.opcode = opcode;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
        }

        @Override
        public String toString() {
            return String.format("%s", opcode.toString());
        }
    }

    public static class Expr extends NullaryConsumer {

        public Expr(OCLNullaryOp opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

    }

    public static class Parameter extends NullaryConsumer {

        public Parameter(String name, LIRKind lirKind) {
            super(new OCLNullaryTemplate(name), lirKind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit(opcode.toString());
        }
    }

    public static class Intrinsic extends NullaryConsumer {

        public Intrinsic(OCLNullaryIntrinsic opcode, LIRKind lirKind) {
            super(opcode, lirKind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            opcode.emit(crb);
            asm.emit("()");
        }

    }

}

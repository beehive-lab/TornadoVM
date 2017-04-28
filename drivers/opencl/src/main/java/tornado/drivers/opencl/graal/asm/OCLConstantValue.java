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
package tornado.drivers.opencl.graal.asm;

import com.oracle.graal.compiler.common.LIRKind;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLLIROp;

public class OCLConstantValue extends OCLLIROp {

    private final String value;

    public OCLConstantValue(String value) {
        super(LIRKind.Illegal);
        this.value = value;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emit(value);
    }

    public String getValue() {
        return value;
    }
}

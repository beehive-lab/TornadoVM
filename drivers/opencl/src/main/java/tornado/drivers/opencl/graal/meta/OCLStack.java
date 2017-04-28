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
package tornado.drivers.opencl.graal.meta;

import com.oracle.graal.compiler.common.LIRKind;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLRegister;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

public class OCLStack extends Value {

    // @formatter:off
    public static final OCLStack STACK = new OCLStack(OCLAssemblerConstants.STACK_REF_NAME);
    // @formatter:on

    private final String name;

    protected OCLStack(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public OCLRegister getBase() {
        return OCLArchitecture.sp;

    }

    public String name() {
        return name;
    }
}

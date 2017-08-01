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

import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.lir.asm.DataBuilder;
import jdk.vm.ci.meta.Constant;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLDataBuilder extends DataBuilder {

    @Override
    public Data createDataItem(Constant cnstnt) {
        unimplemented();
        return null;
    }

    @Override
    public boolean needDetailedPatchingInformation() {
//        unimplemented();
        return false;
    }

}

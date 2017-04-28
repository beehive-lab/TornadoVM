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
package tornado.drivers.opencl.graal;

import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import tornado.common.TornadoLogger;

public class OCLFrameContext extends TornadoLogger implements FrameContext {

    @Override
    public void enter(CompilationResultBuilder crb) {
        trace("FrameContext.enter()");

    }

    @Override
    public boolean hasFrame() {
        return false;
    }

    @Override
    public void leave(CompilationResultBuilder crb) {
        trace("FrameContext.leave()");

    }

}

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

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLMoveFactory implements MoveFactory {

    @Override
    public boolean canInlineConstant(Constant jc) {
        return true;
    }

    @Override
    public boolean allowConstantToStackMove(Constant cnstnt) {
        unimplemented();
        return false;
    }

    @Override
    public LIRInstruction createMove(AllocatableValue av, Value value) {
        AssignStmt assign = new AssignStmt(av, value);
        return assign;
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue av, AllocatableValue av1) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue av, Constant cnstnt) {
        unimplemented();
        return null;
    }

}

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

import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLRegisterConfig implements RegisterConfig {

    private final static Register DUMMY = new Register(0, 0, "dummy", OCLArchitecture.OCL_ABI);
    private final static RegisterArray EMPTY = new RegisterArray(new Register[0]);

    @Override
    public RegisterArray getCalleeSaveRegisters() {
        return EMPTY;
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType jt, JavaType[] jts, ValueKindFactory<?> vkf) {
        unimplemented();
        return null;
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        unimplemented();
        return null;
    }

    @Override
    public Register getFrameRegister() {
        return DUMMY;
    }

    @Override
    public RegisterArray getCallingConventionRegisters(Type type, JavaKind kind) {
        return EMPTY;
    }

    @Override
    public RegisterArray getAllocatableRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterArray filterAllocatableRegisters(PlatformKind kind, RegisterArray registers) {
        unimplemented();
        return null;
    }

    @Override
    public RegisterArray getCallerSaveRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        unimplemented();
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        unimplemented();
        return false;
    }

}

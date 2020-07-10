package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXRegisterConfig implements RegisterConfig {
    private final static Register DUMMY = new Register(0, 0, "dummy", PTXArchitecture.PTX_ABI);
    private final static RegisterArray EMPTY = new RegisterArray(new Register[0]);

    @Override
    public RegisterArray getCalleeSaveRegisters() {
        return EMPTY;
    }

    @Override
    public CallingConvention getCallingConvention(CallingConvention.Type type, JavaType jt, JavaType[] jts, ValueKindFactory<?> vkf) {
        unimplemented("Get calling convention not implemented yet.");
        return null;
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        unimplemented("return register method not implemented yet.");
        return null;
    }

    @Override
    public Register getFrameRegister() {
        return DUMMY;
    }

    @Override
    public RegisterArray getCallingConventionRegisters(CallingConvention.Type type, JavaKind kind) {
        return EMPTY;
    }

    @Override
    public RegisterArray getAllocatableRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterArray filterAllocatableRegisters(PlatformKind kind, RegisterArray registers) {
        unimplemented("Filter allocation registers not implemented yet.");
        return null;
    }

    @Override
    public RegisterArray getCallerSaveRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        unimplemented("Get attributes map not implemented yet");
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        unimplemented("get all allocatable registers caller saved, not implemented yet");
        return false;
    }
}

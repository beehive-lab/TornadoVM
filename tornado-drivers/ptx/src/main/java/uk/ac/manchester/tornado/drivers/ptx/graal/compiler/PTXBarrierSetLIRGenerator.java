package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.BarrierSetLIRGenerator;

import jdk.vm.ci.meta.Value;

public class PTXBarrierSetLIRGenerator extends BarrierSetLIRGenerator {
    @Override
    public Variable emitBarrieredLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        return null;
    }
}

package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.framemap.FrameMap;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVFrameMap extends FrameMap {

    public SPIRVFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        super(codeCache, registerConfig, referenceMapFactory);

        // (negative) offset relative to sp + total frame size
        initialSpillSize = returnAddressSize();
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        return frameSize() + returnAddressSize();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize - returnAddressSize());
    }

    @Override
    protected int alignFrameSize(int size) {
        return NumUtil.roundUp(size + returnAddressSize(), getTarget().stackAlignment) - returnAddressSize();
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        assert spillSize == initialSpillSize || spillSize == initialSpillSize
                + spillSlotSize(LIRKind.value(SPIRVKind.OP_TYPE_INT_64)) : "Deoptimization rescue slot must be the first or second (if there is an RBP spill slot) stack slot";
        return allocateSpillSlot(LIRKind.value(SPIRVKind.OP_TYPE_INT_64));
    }

}

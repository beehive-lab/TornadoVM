package uk.ac.manchester.tornado.drivers.spirv.graal;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;

public class SPIRVLIRKindTool implements LIRKindTool {

    final SPIRVTargetDescription targetDescription;

    public SPIRVLIRKindTool(SPIRVTargetDescription target) {
        this.targetDescription = target;
    }

    @Override
    public LIRKind getIntegerKind(int bits) {
        return null;
    }

    @Override
    public LIRKind getFloatingKind(int bits) {
        return null;
    }

    @Override
    public LIRKind getObjectKind() {
        return null;
    }

    @Override
    public LIRKind getWordKind() {
        return null;
    }

    @Override
    public LIRKind getNarrowOopKind() {
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        return null;
    }
}

package uk.ac.manchester.tornado.drivers.spirv.graal;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVLIRKindTool implements LIRKindTool {

    final SPIRVTargetDescription targetDescription;

    public SPIRVLIRKindTool(SPIRVTargetDescription target) {
        this.targetDescription = target;
    }

    @Override
    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_8);
        } else if (bits <= 16) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_16);
        } else if (bits <= 32) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_32);
        } else if (bits <= 64) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_64);
        } else {
            throw new RuntimeException("Data Type Not Supported");
        }
    }

    @Override
    public LIRKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_32);
            case 64:
                return LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_64);
            default:
                throw new RuntimeException("Data Type Not Supported.");
        }
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(targetDescription.getArch().getWordKind());
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

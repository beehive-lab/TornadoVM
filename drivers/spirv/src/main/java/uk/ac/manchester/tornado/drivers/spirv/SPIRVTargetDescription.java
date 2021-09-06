package uk.ac.manchester.tornado.drivers.spirv;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

/**
 * Descriptor that represents the SPIRV Architecture.
 */
public class SPIRVTargetDescription extends TargetDescription {

    private boolean supportsFP64;

    public SPIRVTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, boolean supportsFP64, String extensions) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
        this.supportsFP64 = supportsFP64;
    }

    public SPIRVArchitecture getArch() {
        return (SPIRVArchitecture) arch;
    }

    public SPIRVKind getSPIRVKind(JavaKind kind) {
        return (SPIRVKind) arch.getPlatformKind(kind);
    }

    public boolean isSupportsFP64() {
        return this.supportsFP64;
    }

}

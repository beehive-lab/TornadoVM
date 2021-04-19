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

    public SPIRVTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, boolean supportsFP64, String extensions) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
    }

    public SPIRVArchitecture getArch() {
        return (SPIRVArchitecture) arch;
    }

    public SPIRVKind getSPIRVKind(JavaKind kind) {
        return (SPIRVKind) arch.getPlatformKind(kind);
    }

}

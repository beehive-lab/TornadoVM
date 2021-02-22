package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVCodeProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

public class SPIRVHotSpotBackendFactory {

    public static SPIRVBackend createBackend(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, SPIRVDevice device) {
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();

        SPIRVArchitecture architecture = new SPIRVArchitecture(SPIRVKind.U32, device.getByteOrder());
        final int stackAlignment = 8;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        SPIRVTargetDescription targetDescription = new SPIRVTargetDescription(architecture, false, stackAlignment, implicitNullCheckLimit, inlineObjects);

        // TODO: Finish this call
        // SPIRVDeviceContext deviceContext =
        // device.getSPIRVContext().getDeviceContext();

        SPIRVCodeProvider codeProvider = new SPIRVCodeProvider(targetDescription);

        SPIRVProviders providers;
        SPIRVSuitesProvider suites;

        return null;
    }

}

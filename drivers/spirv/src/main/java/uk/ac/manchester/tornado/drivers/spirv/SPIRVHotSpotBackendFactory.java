package uk.ac.manchester.tornado.drivers.spirv;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

public class SPIRVHotSpotBackendFactory {

    public static SPIRVBackend createBackend(OptionValues options, HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, SPIRVDevice device) {
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();

        return null;
    }

}

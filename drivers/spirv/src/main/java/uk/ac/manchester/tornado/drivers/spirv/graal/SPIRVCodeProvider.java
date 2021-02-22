package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;

/**
 * Access to the code cache for SPIRV and interaction with JVMCI
 */
public class SPIRVCodeProvider implements CodeCacheProvider {

    private SPIRVTargetDescription target;

    public SPIRVCodeProvider(SPIRVTargetDescription target) {
        this.target = target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault) {
        return null;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {

    }

    /**
     * Obtain a register configuration that will be used when compiling a given
     * method.
     * 
     * @return SPIRVRegisterConfig
     */
    @Override
    public RegisterConfig getRegisterConfig() {
        return new SPIRVRegisterConfig();
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    /**
     * A descriptor for the target architecture.
     * 
     * @return {@link TargetDescription}
     */
    @Override
    public TargetDescription getTarget() {
        return target;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        return null;
    }

    @Override
    public long getMaxCallTargetOffset(long address) {
        return 0;
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        return false;
    }
}
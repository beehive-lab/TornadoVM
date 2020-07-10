package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;

public class PTXCodeProvider implements CodeCacheProvider {
    private PTXTargetDescription target;

    public PTXCodeProvider(PTXTargetDescription target) {
        this.target = target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault) {
        return null;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {

    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return new PTXRegisterConfig();
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

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

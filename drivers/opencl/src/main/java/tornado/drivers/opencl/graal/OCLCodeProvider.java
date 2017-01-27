package tornado.drivers.opencl.graal;

import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import tornado.drivers.opencl.OCLTargetDescription;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLCodeProvider implements CodeCacheProvider {

    private final TargetDescription target;

    public OCLCodeProvider(TargetDescription target) {
        this.target = target;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getMaxCallTargetOffset(long l) {
        unimplemented();
        return -1;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return new OCLRegisterConfig();
    }

    @Override
    public OCLTargetDescription getTarget() {
        return (OCLTargetDescription) target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod rjm, CompiledCode cc, InstalledCode ic, SpeculationLog sl, boolean bln) {
        unimplemented("waiting for CompiledCode to be implemented first");
//  return addMethod(method, method.getName(), result.);
        return null;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode ic) {
        ic.invalidate();
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        unimplemented();
        return false;
    }

}

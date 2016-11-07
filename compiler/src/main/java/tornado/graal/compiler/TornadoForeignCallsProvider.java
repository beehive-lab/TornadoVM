package tornado.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import jdk.vm.ci.meta.JavaKind;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class TornadoForeignCallsProvider implements ForeignCallsProvider {

    @Override
    public boolean isReexecutable(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor fcd) {
        unimplemented();
        return null;
    }

    @Override
    public boolean canDeoptimize(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public boolean isGuaranteedSafepoint(ForeignCallDescriptor fcd) {
        unimplemented();
        return false;
    }

    @Override
    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor fcd) {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getValueKind(JavaKind jk) {
        unimplemented();
        return LIRKind.Illegal;
    }
    
}

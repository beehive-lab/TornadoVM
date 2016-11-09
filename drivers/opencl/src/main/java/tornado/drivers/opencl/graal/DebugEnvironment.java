package tornado.drivers.opencl.graal;

import com.oracle.graal.debug.*;
import com.oracle.graal.serviceprovider.GraalServices;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.runtime.JVMCI;

import static com.oracle.graal.debug.GraalDebugConfig.Options.*;

public class DebugEnvironment {

    public static GraalDebugConfig initialize(PrintStream log, Object... extraArgs) {
        // Initialize JVMCI before loading class Debug
        JVMCI.initialize();
        if (!Debug.isEnabled()) {
            log.println("WARNING: Scope debugging needs to be enabled with -esa");
            return null;
        }
        List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
        List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
        GraalDebugConfig debugConfig = new GraalDebugConfig(Log.getValue(), Count.getValue(), TrackMemUse.getValue(), Time.getValue(), Dump.getValue(), Verify.getValue(), MethodFilter.getValue(),
                MethodMeter.getValue(),
                log, dumpHandlers, verifyHandlers);

        for (DebugConfigCustomizer customizer : GraalServices.load(DebugConfigCustomizer.class)) {
            if (!customizer.getClass().getSimpleName().startsWith("Truffle")) {
                customizer.customize(debugConfig, extraArgs);
            }
        }

        Debug.setConfig(debugConfig);
        return debugConfig;
    }

}

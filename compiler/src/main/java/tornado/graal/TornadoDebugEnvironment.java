/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.graal;

import com.oracle.graal.debug.*;
import com.oracle.graal.serviceprovider.GraalServices;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.runtime.JVMCI;

import static com.oracle.graal.debug.GraalDebugConfig.Options.*;

public class TornadoDebugEnvironment {

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

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

import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.debug.*;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.*;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class TornadoDebugEnvironment {

    public static GraalDebugConfig initialize(Object... capabilities) {
        // Initialize JVMCI before loading class Debug
        JVMCI.initialize();
        if (!Debug.isEnabled()) {
            return null;
        }

        List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
        List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
        OptionValues options = getTornadoRuntime().getOptions();

        GraalDebugConfig debugConfig = new GraalDebugConfig(options,
                Log.getValue(options),
                Count.getValue(options),
                TrackMemUse.getValue(options),
                Time.getValue(options),
                Dump.getValue(options),
                Verify.getValue(options),
                MethodFilter.getValue(options),
                MethodMeter.getValue(options),
                TTY.out, dumpHandlers, verifyHandlers);

        for (DebugConfigCustomizer customizer : GraalServices.load(DebugConfigCustomizer.class)) {
            if (!customizer.getClass().getSimpleName().startsWith("Truffle")) {
                customizer.customize(debugConfig);
            }
        }

        Debug.setConfig(debugConfig);

        if (capabilities != null) {
            for (Object o : capabilities) {
                for (DebugDumpHandler handler : debugConfig.dumpHandlers()) {
                    handler.addCapability(o);
                }
            }
        }

        System.out.printf("DEBUG: %s %s %s\n", Debug.isEnabled(), Debug.isDumpEnabledForMethod(), Debug.currentScope());
        return debugConfig;
    }

}

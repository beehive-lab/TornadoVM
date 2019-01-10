/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graal;

import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.debug.*;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.*;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

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

        GraalDebugConfig debugConfig = new GraalDebugConfig(options, Log.getValue(options), Count.getValue(options), TrackMemUse.getValue(options), Time.getValue(options), Dump.getValue(options),
                Verify.getValue(options), MethodFilter.getValue(options), MethodMeter.getValue(options), TTY.out, dumpHandlers, verifyHandlers);

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

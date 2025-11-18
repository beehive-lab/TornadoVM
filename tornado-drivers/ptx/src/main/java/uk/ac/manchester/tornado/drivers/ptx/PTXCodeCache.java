/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class PTXCodeCache {

    private final PTXDeviceContext deviceContext;
    private final ConcurrentHashMap<String, PTXInstalledCode> cache;

    PTXCodeCache(PTXDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public PTXInstalledCode installSource(String name, byte[] targetCode, String resolvedMethodName, boolean debugKernel) {

        if (!cache.containsKey(name)) {
            if (debugKernel) {
                RuntimeUtilities.dumpKernel(targetCode);
            }

            /*int[] CompilerFlags = {TornadoOptions.PTX_COMPILER_OPT_LEVEL, TornadoOptions.PTX_COMPILER_JIT_TARGET, TornadoOptions.PTX_COMPILER_MAX_REG, TornadoOptions.PTX_COMPILER_CACHE_MODE,
                    TornadoOptions.PTX_COMPILER_GENERATE_DEBUG_INFO, TornadoOptions.PTX_COMPILER_LOG_VERBOSE, TornadoOptions.PTX_COMPILER_GENERATE_LINE_INFO};*/
            int[] CompilerFlags = {TornadoOptions.PTX_COMPILER_OPT_LEVEL, TornadoOptions.PTX_COMPILER_JIT_TARGET, TornadoOptions.PTX_COMPILER_MAX_REG, TornadoOptions.PTX_COMPILER_CACHE_MODE};
            PTXModule module = new PTXModule(resolvedMethodName, targetCode, name, CompilerFlags);

            if (module.isPTXJITSuccess()) {
                PTXInstalledCode code = new PTXInstalledCode(name, module, deviceContext);
                cache.put(name, code);
                return code;
            } else {
                throw new TornadoBailoutRuntimeException("PTX JIT compilation failed!");
            }
        }

        return cache.get(name);
    }

    PTXInstalledCode getCachedCode(String name) {
        return cache.get(name);
    }

    boolean isCached(String name) {
        return cache.containsKey(name);
    }

    void reset() {
        for (PTXInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }
}

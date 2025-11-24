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
import java.util.Set;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXCodeCache {

    private final PTXDeviceContext deviceContext;
    private final ConcurrentHashMap<String, PTXInstalledCode> cache;

    private static final Set<String> SUPPORTED_PTX_JIT_FLAGS = Set.of(
            "CU_JIT_OPTIMIZATION_LEVEL",
            "CU_JIT_MAX_REGISTERS",
            "CU_JIT_CACHE_MODE",
            "CU_JIT_GENERATE_DEBUG_INFO",
            "CU_JIT_LOG_VERBOSE",
            "CU_JIT_GENERATE_LINE_INFO"
    );

    PTXCodeCache(PTXDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public PTXInstalledCode installSource(TaskDataContext taskMeta, String name, byte[] targetCode, String resolvedMethodName, boolean debugKernel) {

        if (!cache.containsKey(name)) {
            if (debugKernel) {
                RuntimeUtilities.dumpKernel(targetCode);
            }

            String compilerFlags = taskMeta.getCompilerFlags(TornadoVMBackendType.PTX);
            String[] parts = compilerFlags.trim().split("\\s+");

            if (parts.length % 2 != 0) {
                throw new TornadoBailoutRuntimeException(
                        "Malformed compilerFlags string: expected pairs of <flag> <value>. Got: " + compilerFlags
                );
            }

            String[] flagNames = new String[parts.length / 2];
            int[] flagValues = new int[parts.length / 2];

            for (int i = 0; i < parts.length; i += 2) {
                flagNames[i / 2] = parts[i];
                try {
                    flagValues[i / 2] = Integer.parseInt(parts[i + 1]);
                } catch (NumberFormatException e) {
                    throw new TornadoBailoutRuntimeException(
                            "Invalid flag value (must be integer): '" + parts[i + 1] + "'", e
                    );
                }
            }

            for (String flag : flagNames) {
                if (!SUPPORTED_PTX_JIT_FLAGS.contains(flag)) {
                    throw new TornadoBailoutRuntimeException(
                            "Unsupported PTX JIT compiler flag: " + flag + ". Supported flags are: " + SUPPORTED_PTX_JIT_FLAGS
                    );
                }
            }

            PTXModule module = new PTXModule(resolvedMethodName, targetCode, name, compilerFlags);

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

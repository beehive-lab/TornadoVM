/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public abstract class SPIRVCodeCache {

    protected final SPIRVDeviceContext deviceContext;
    protected final ConcurrentHashMap<String, SPIRVInstalledCode> cache;

    public SPIRVCodeCache(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public SPIRVInstalledCode getCachedCode(String name) {
        return cache.get(name);
    }

    public boolean isCached(String name) {
        return cache.containsKey(name);
    }

    public void reset() {
        for (SPIRVInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }

    public abstract SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile);

    public abstract SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] binary);

}

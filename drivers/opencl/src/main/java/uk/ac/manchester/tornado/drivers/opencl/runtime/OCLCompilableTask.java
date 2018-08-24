/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class OCLCompilableTask extends CompilableTask {

    private OCLInstalledCode activeCode;
    private OCLBackend activeBackend;

    private final Map<OCLBackend, OCLInstalledCode> codeCache;

    public OCLCompilableTask(ScheduleMetaData meta, String id, Method method, Object thisObject, Object... args) {
        super(meta, id, method, thisObject, args);
        this.codeCache = new HashMap<>();
    }

    public void execute() {
        if (activeCode != null && activeCode.isValid()) {
            executeOnDevice();
        }
    }

    @Override
    public CompilableTask mapTo(final TornadoDevice mapping) {
        super.mapTo(mapping);

        activeBackend = ((OCLTornadoDevice) mapping).getBackend();
        if (codeCache.containsKey(activeBackend)) {
            activeCode = codeCache.get(activeBackend);
        }

        return this;
    }

    public void dumpCode() {
        for (byte b : activeCode.getCode()) {
            System.out.printf("%c", b);
        }

    }

    protected void scheduleOnDevice(List<Event> waitEvents) {
        Tornado.debug("scheduling %s...", method.getName());
        Tornado.debug(toString());
        Tornado.debug("after %s...", method.getName());
        Tornado.debug(toString());
    }

    protected void executeOnDevice() {
        scheduleOnDevice(Collections.emptyList());
    }

    public void schedule() {
        scheduleOnDevice(Collections.emptyList());
    }

    public void schedule(Event... waitEvents) {
        final List<Event> events = new ArrayList<>();
        for (Event event : waitEvents) {
            events.add(event);
        }
        scheduleOnDevice(events);
    }

    public void schedule(List<Event> waitEvents) {
        scheduleOnDevice(waitEvents);
    }

    public void invalidate() {
        activeCode.invalidate();
    }

    public void disableJIT() {
        shouldCompile = false;
    }
}

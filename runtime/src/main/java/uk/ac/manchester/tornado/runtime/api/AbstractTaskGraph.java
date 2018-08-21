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
package uk.ac.manchester.tornado.runtime.api;

import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;

public interface AbstractTaskGraph {

    public SchedulableTask getTask(String i);

    public TornadoDevice getDevice();

    public void setDevice(TornadoDevice device);

    public TornadoDevice getDeviceForTask(String id);

    public long getReturnValue(String id);

    public void addInner(SchedulableTask task);

    public boolean isLastDeviceListEmpty();

    public void scheduleInner();

    public void apply(Consumer<SchedulableTask> consumer);

    public void mapAllToInner(TornadoDevice device);

    public void dumpTimes();

    public void dumpProfiles();

    public void dumpEvents();

    public void clearProfiles();

    public void waitOn();

    public void streamInInner(Object... objects);

    public void streamOutInner(Object... objects);

    public void dump();

    public void warmup();

    public void invalidateObjects();

    public void syncObject(Object object);

    public void syncObjects();

    public void syncObjects(Object... objects);

    public String getId();

    public ScheduleMetaData meta();

    public abstract AbstractTaskGraph schedule();

    public void addTask(TaskPackage taskPackage);
}

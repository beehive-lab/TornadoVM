/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api;

import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface;

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

    public TaskMetaDataInterface meta();

    public abstract AbstractTaskGraph schedule();

    public abstract AbstractTaskGraph scheduleWithProfile(Policy policy);

    public void addTask(TaskPackage taskPackage);

    public void addPrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions);

    public void addScalaTask(String id, Object function, Object[] args);
}

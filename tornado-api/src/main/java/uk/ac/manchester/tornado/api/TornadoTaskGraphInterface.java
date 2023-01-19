/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.memory.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.profiler.ProfileInterface;

public interface TornadoTaskGraphInterface extends ProfileInterface {

    SchedulableTask getTask(String taskNameID);

    TornadoDevice getDevice();

    void setDevice(TornadoDevice device);

    TornadoDevice getDeviceForTask(String id);

    void addInner(SchedulableTask task);

    boolean isLastDeviceListEmpty();

    void scheduleInner();

    void batch(String batchSize);

    void apply(Consumer<SchedulableTask> consumer);

    void mapAllToInner(TornadoDevice device);

    void dumpTimes();

    void dumpProfiles();

    void dumpEvents();

    void clearProfiles();

    void waitOn();

    void transferToDevice(final int mode, Object... objects);

    void transferToHost(final int mode, Object... objects);

    void dump();

    void warmup();

    void freeDeviceMemory();

    void syncRuntimeTransferToHost(Object... objects);

    String getId();

    TaskMetaDataInterface meta();

    TornadoTaskGraphInterface schedule();

    TornadoTaskGraphInterface schedule(GridScheduler gridScheduler);

    TornadoTaskGraphInterface scheduleWithProfile(Policy policy);

    TornadoTaskGraphInterface scheduleWithProfileSequential(Policy policy);

    void addTask(TaskPackage taskPackage);

    void addPrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions);

    void addPrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions, int[] atomics);

    void addScalaTask(String id, Object function, Object[] args);

    String getTaskGraphName();

    void replaceParameter(Object oldParameter, Object newParameter);

    void useDefaultThreadScheduler(boolean use);

    boolean isFinished();

    Set<Object> getArgumentsLookup();

    TornadoTaskGraphInterface createImmutableTaskGraph();

    Collection<?> getOutputs();

    void enableProfiler(ProfilerMode profilerMode);

    void disableProfiler(ProfilerMode profilerMode);
}

/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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

import java.util.HashSet;
import java.util.Set;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task11;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task12;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task13;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task14;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoAPIProvider;

/**
 * Tornado Task Graph API.
 * <p>
 * Task-based parallel API to express methods to be accelerated on any OpenCL,
 * PTX or SPIRV compatible device.
 * </p>
 */
public class TaskGraph implements TornadoAPI {

    private static final String ERROR_TASK_NAME_DUPLICATION = "[TornadoVM ERROR]. There are more than 1 tasks with the same task-name. Use different a different task name for each task within a TaskGraph.";

    private final String taskScheduleName;
    protected TaskGraphInterface taskScheduleImpl;
    protected HashSet<String> taskNames;

    public TaskGraph(String name) {
        this.taskScheduleName = name;
        taskScheduleImpl = TornadoAPIProvider.loadScheduleRuntime(name);
        taskNames = new HashSet<>();
    }

    private void checkTaskName(String id) {
        if (taskNames.contains(id)) {
            throw new TornadoTaskRuntimeException(ERROR_TASK_NAME_DUPLICATION);
        }
        taskNames.add(id);
    }

    @Override
    public TaskGraph addTask(TaskPackage taskPackage) {
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public TaskGraph task(String id, Task code) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1> TaskGraph task(String id, Task1<T1> code, T1 arg) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2> TaskGraph task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3> TaskGraph task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4> TaskGraph task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5> TaskGraph task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> TaskGraph task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> TaskGraph task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskGraph task(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskGraph task(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskGraph task(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskGraph task(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskGraph task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions) {
        checkTaskName(id);
        taskScheduleImpl.addPrebuiltTask(id, entryPoint, filename, args, accesses, device, dimensions);
        return this;
    }

    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions, int[] atomics) {
        checkTaskName(id);
        taskScheduleImpl.addPrebuiltTask(id, entryPoint, filename, args, accesses, device, dimensions, atomics);
        return this;
    }

    @Override
    public String getTaskScheduleName() {
        return taskScheduleName;
    }

    @Override
    public TaskGraph task(SchedulableTask task) {
        taskScheduleImpl.addInner(task);
        return this;
    }

    TaskGraph setDevice(TornadoDevice device) {
        taskScheduleImpl.setDevice(device);
        return this;
    }

    @Override
    public TaskGraph transferToDevice(final int mode, Object... objects) {
        taskScheduleImpl.transferToDevice(mode, objects);
        return this;
    }

    @Override
    public TaskGraph transferToHost(Object... objects) {
        taskScheduleImpl.transferToHost(objects);
        return this;
    }

    @Override
    public ImmutableTaskGraph freeze() {
        TaskGraph cloneTaskGraph = new TaskGraph(this.getTaskScheduleName());
        cloneTaskGraph.taskScheduleImpl = this.taskScheduleImpl.createImmutableTaskGraph();
        cloneTaskGraph.taskNames = this.taskNames;
        return new ImmutableTaskGraph(cloneTaskGraph);
    }

    TaskGraph schedule() {
        taskScheduleImpl.scheduleInner();
        return this;
    }

    TaskGraph batch(String batchSize) {
        taskScheduleImpl.batch(batchSize);
        return this;
    }

    @Override
    @Deprecated(forRemoval = true)
    public void execute() {
        taskScheduleImpl.schedule().waitOn();
    }

    void execute(GridScheduler gridScheduler) {
        taskScheduleImpl.schedule(gridScheduler).waitOn();
    }

    void executeWithProfiler(DynamicReconfigurationPolicy policy) {
        taskScheduleImpl.scheduleWithProfile(policy).waitOn();
    }

    void executeWithProfilerSequential(DynamicReconfigurationPolicy policy) {
        taskScheduleImpl.scheduleWithProfileSequential(policy).waitOn();
    }

    @Override
    @Deprecated(forRemoval = true)
    public void executeWithProfilerSequentialGlobal(DynamicReconfigurationPolicy policy) {
        taskScheduleImpl.scheduleWithProfileSequentialGlobal(policy).waitOn();
    }

    void warmup() {
        taskScheduleImpl.warmup();
    }

    void dumpEvents() {
        taskScheduleImpl.dumpEvents();
    }

    void dumpTimes() {
        taskScheduleImpl.dumpTimes();
    }

    void dumpProfiles() {
        taskScheduleImpl.dumpProfiles();
    }

    void clearProfiles() {
        taskScheduleImpl.clearProfiles();
    }

    TaskGraph lockObjectsInMemory(Object... objects) {
        taskScheduleImpl.lockObjectsInMemory(objects);
        return this;
    }

    TaskGraph unlockObjectFromMemory(Object object) {
        taskScheduleImpl.unlockObjectFromMemory(object);
        return this;
    }

    void syncObjects() {
        taskScheduleImpl.syncObjects();
    }

    void syncField(Object object) {
        taskScheduleImpl.syncField(object);
    }

    void syncObjects(Object... objects) {
        taskScheduleImpl.syncObjects(objects);
    }

    @Override
    public SchedulableTask getTask(String id) {
        return taskScheduleImpl.getTask(id);
    }

    @Override
    @Deprecated(forRemoval = true)
    public TornadoDevice getDevice() {
        return taskScheduleImpl.getDevice();
    }

    @Override
    public TornadoDevice getDeviceForTask(String id) {
        return taskScheduleImpl.getDeviceForTask(id);
    }

    @Override
    public void waitOn() {
        taskScheduleImpl.waitOn();
    }

    // *************************************************
    // Profiler Interface
    // *************************************************
    long getTotalTime() {
        return taskScheduleImpl.getTotalTime();
    }

    long getCompileTime() {
        return taskScheduleImpl.getCompileTime();
    }

    long getTornadoCompilerTime() {
        return taskScheduleImpl.getTornadoCompilerTime();
    }

    long getDriverInstallTime() {
        return taskScheduleImpl.getDriverInstallTime();
    }

    long getDataTransfersTime() {
        return taskScheduleImpl.getDataTransfersTime();
    }

    long getWriteTime() {
        return taskScheduleImpl.getWriteTime();
    }

    long getReadTime() {
        return taskScheduleImpl.getReadTime();
    }

    long getDataTransferDispatchTime() {
        return taskScheduleImpl.getDataTransferDispatchTime();
    }

    long getKernelDispatchTime() {
        return taskScheduleImpl.getKernelDispatchTime();
    }

    long getDeviceWriteTime() {
        return taskScheduleImpl.getDeviceWriteTime();
    }

    long getDeviceKernelTime() {
        return taskScheduleImpl.getDeviceKernelTime();
    }

    long getDeviceReadTime() {
        return taskScheduleImpl.getDeviceReadTime();
    }

    protected String getProfileLog() {
        return taskScheduleImpl.getProfileLog();
    }
    // ************************************************************************

    TaskGraph useDefaultThreadScheduler(boolean use) {
        taskScheduleImpl.useDefaultThreadScheduler(use);
        return this;
    }

    /**
     * Update a data reference from one array to another within TornadoVM.
     *
     * Arrays can be of different sizes.
     *
     * If a {@link GridScheduler} is not passed in the {@link #execute()} method,
     * then it will also trigger recompilation. Otherwise, TornadoVM will not
     * recompile the code, since the first compilation was generic.
     *
     *
     * @param oldParameter
     * @param newParameter
     */
    @Override
    @Deprecated(forRemoval = true)
    public TaskGraph replaceParameter(Object oldParameter, Object newParameter) {
        taskScheduleImpl.replaceParameter(oldParameter, newParameter);
        return this;
    }

    boolean isFinished() {
        return taskScheduleImpl.isFinished();
    }

    public Set<Object> getArgumentsLookup() {
        return taskScheduleImpl.getArgumentsLookup();
    }
}

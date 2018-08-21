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
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.runtime.Loader;

public class TaskSchedule implements TornadoAPI {

    private String taskScheduleName;
    private AbstractTaskGraph taskScheduleImpl;

    public TaskSchedule(String name) {
        this.taskScheduleName = name;
        taskScheduleImpl = Loader.loadRuntime(name);
    }

    @Override
    public <T1> TaskSchedule task(String id, Task1<T1> code, T1 arg) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2> TaskSchedule task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3> TaskSchedule task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4> TaskSchedule task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5> TaskSchedule task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> TaskSchedule task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> TaskSchedule task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskSchedule task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskSchedule task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskSchedule task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskSchedule task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        taskScheduleImpl.addTask(taskPackage);
        return this;
    }

    @Override
    public TaskSchedule prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, GenericDevice device, int[] dimensions) {
        taskScheduleImpl.addPrebuiltTask(id, entryPoint, filename, args, accesses, device, dimensions);
        return this;
    }

    @Override
    public String getTaskScheduleName() {
        return taskScheduleName;
    }

    @Override
    public TaskSchedule task(SchedulableTask task) {
        taskScheduleImpl.addInner(task);
        return this;
    }

    @Override
    public TaskSchedule mapAllTo(GenericDevice device) {
        taskScheduleImpl.setDevice(device);
        return this;
    }

    @Override
    public TaskSchedule streamIn(Object... objects) {
        taskScheduleImpl.streamInInner(objects);
        return this;
    }

    @Override
    public TaskSchedule streamOut(Object... objects) {
        taskScheduleImpl.streamOutInner(objects);
        return this;
    }

    @Override
    public TaskSchedule schedule() {
        taskScheduleImpl.scheduleInner();
        return this;
    }

    @Override
    public void execute() {
        taskScheduleImpl.schedule().waitOn();
    }

    @Override
    public void warmup() {
        taskScheduleImpl.warmup();
    }

    @Override
    public long getReturnValue(String id) {
        return taskScheduleImpl.getReturnValue(id);
    }

    @Override
    public void dumpEvents() {
        taskScheduleImpl.dumpEvents();
    }

    @Override
    public void dumpTimes() {
        taskScheduleImpl.dumpTimes();
    }

    @Override
    public void dumpProfiles() {
        taskScheduleImpl.dumpProfiles();
    }

    @Override
    public void clearProfiles() {
        taskScheduleImpl.clearProfiles();
    }

    @Override
    public void syncObjects() {
        taskScheduleImpl.syncObjects();
    }

    @Override
    public void syncObject(Object object) {
        taskScheduleImpl.syncObject(object);
    }

    @Override
    public void syncObjects(Object... objects) {
        taskScheduleImpl.syncObjects(objects);
    }

    @Override
    public SchedulableTask getTask(String id) {
        return taskScheduleImpl.getTask(id);
    }

    @Override
    public GenericDevice getDevice() {
        return taskScheduleImpl.getDevice();
    }

    @Override
    public void setDevice(GenericDevice device) {
        taskScheduleImpl.setDevice(device);
    }

    @Override
    public GenericDevice getDeviceForTask(String id) {
        return taskScheduleImpl.getDeviceForTask(id);
    }

    @Override
    public void waitOn() {
        taskScheduleImpl.waitOn();
    }
}

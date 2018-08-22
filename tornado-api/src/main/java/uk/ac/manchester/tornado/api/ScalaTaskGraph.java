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

import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoAPIProvider;

public abstract class ScalaTaskGraph implements TornadoAPI {

    private AbstractTaskGraph taskScheduleImpl;
    private String taskName;

    public ScalaTaskGraph(String name) {
        taskName = name;
        taskScheduleImpl = TornadoAPIProvider.loadRuntime(name);
    }

    public ScalaTaskGraph task(String id, Object function, Object... args) {
        taskScheduleImpl.addScalaTask(id, function, args);
        return this;
    }

    public ScalaTaskGraph streamInScala(Object... objects) {
        taskScheduleImpl.streamInInner(objects);
        return this;
    }

    public ScalaTaskGraph streamOutScala(Object... objects) {
        taskScheduleImpl.streamOutInner(objects);
        return this;
    }

    public ScalaTaskGraph scheduleScala() {
        taskScheduleImpl.scheduleInner();
        return this;
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public String getTaskScheduleName() {
        return taskName;
    }

    @Override
    public ScalaTaskGraph task(SchedulableTask task) {
        taskScheduleImpl.addInner(task);
        return this;
    }

    @Override
    public ScalaTaskGraph mapAllTo(GenericDevice device) {
        taskScheduleImpl.setDevice(device);
        return this;
    }

    @Override
    public ScalaTaskGraph streamIn(Object... objects) {
        taskScheduleImpl.streamInInner(objects);
        return this;
    }

    @Override
    public ScalaTaskGraph streamOut(Object... objects) {
        taskScheduleImpl.streamOutInner(objects);
        return this;
    }

    @Override
    public ScalaTaskGraph schedule() {
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

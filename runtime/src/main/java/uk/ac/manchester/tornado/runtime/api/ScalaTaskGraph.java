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

public class ScalaTaskGraph {

    private AbstractTaskGraph taskGraphImpl;
    private String taskName;

    public ScalaTaskGraph(String name) {
        taskName = name;
        taskGraphImpl = new TornadoTaskSchedule(name);
    }

    public ScalaTaskGraph task(String id, Object function, Object... args) {
        taskGraphImpl.addInner(TaskUtils.scalaTask(id, function, args));
        return this;
    }

    public ScalaTaskGraph streamIn(Object... objects) {
        taskGraphImpl.streamInInner(objects);
        return this;
    }

    public ScalaTaskGraph streamOut(Object... objects) {
        taskGraphImpl.streamOutInner(objects);
        return this;
    }

    public ScalaTaskGraph schedule() {
        taskGraphImpl.scheduleInner();
        return this;
    }

    public String getTaskName() {
        return taskName;
    }
}

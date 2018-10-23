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
package uk.ac.manchester.tornado.runtime;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;

public class EmptyEvent implements Event {

    private final String name;

    public EmptyEvent(String name) {
        this.name = name;
    }

    public EmptyEvent() {
        this("Empty Event");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getExecutionTime() {
        return 0;
    }

    @Override
    public double getExecutionTimeInSeconds() {
        return 0;
    }

    @Override
    public double getExecutionTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getTotalQueuedTime() {
        return 0;
    }

    @Override
    public double getTotalQueuedTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getElapsedQueuedTime() {
        return 0;
    }

    @Override
    public double getElapsedQueuedTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getElapsedSubmitTime() {
        return 0;
    }

    @Override
    public double getElapsedSubmitTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getQueuedTime() {
        return 0;
    }

    @Override
    public double getQueuedTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return TornadoExecutionStatus.COMPLETE;
    }

    @Override
    public long getTotalTime() {
        return 0;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return 0;
    }

    @Override
    public double getTotalTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public void retain() {

    }

    @Override
    public void waitOn() {

    }

    @Override
    public long getSubmitTime() {
        return 0;
    }

    @Override
    public double getSubmitTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return 0;
    }

    @Override
    public double getStartTimeInMilliSeconds() {
        return 0;
    }

    @Override
    public long getEndTime() {
        return 0;
    }

    @Override
    public double getEndTimeInMilliSeconds() {
        return 0;
    }

}

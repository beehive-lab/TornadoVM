/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.runtime;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;

public class EmptyEvent implements Event {

    private final String name;

    private EmptyEvent(String name) {
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
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public long getDriverDispatchTime() {
        return 0;
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return TornadoExecutionStatus.COMPLETE;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return 0;
    }

    @Override
    public void waitOn() {
    }

    @Override
    public long getQueuedTime() {
        return 0;
    }

    @Override
    public long getSubmitTime() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return 0;
    }

    @Override
    public long getEndTime() {
        return 0;
    }

    @Override
    public void waitForEvents(long executionPlanId) {

    }
}

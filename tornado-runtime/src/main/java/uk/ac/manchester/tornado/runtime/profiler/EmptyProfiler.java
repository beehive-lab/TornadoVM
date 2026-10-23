/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.profiler;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;

public class EmptyProfiler implements TornadoProfiler {

    public EmptyProfiler() {

    }

    @Override
    public synchronized void addValueToMetric(ProfilerType type, String taskName, long value) {
    }

    @Override
    public synchronized void start(ProfilerType type) {
    }

    @Override
    public synchronized void start(ProfilerType type, String taskName) {
    }

    @Override
    public synchronized void registerDeviceName(String taskName, String deviceInfo) {

    }

    @Override
    public synchronized void registerBackend(String taskName, String backend) {

    }

    @Override
    public synchronized void registerDeviceID(String taskName, String deviceID) {
    }

    @Override
    public synchronized void registerMethodHandle(ProfilerType type, String taskName, String methodName) {
    }

    @Override
    public synchronized void stop(ProfilerType type) {
    }

    @Override
    public synchronized void stop(ProfilerType type, String taskName) {
    }

    @Override
    public synchronized long getTimer(ProfilerType type) {
        System.out.println("Enable the profiler with: -Dtornado.profiler=True");
        return 0;
    }

    @Override
    public long getSize(ProfilerType type) {
        return 0;
    }

    @Override
    public synchronized long getTaskTimer(ProfilerType type, String taskName) {
        return 0;
    }

    @Override
    public synchronized void setTimer(ProfilerType type, long time) {

    }

    @Override
    public synchronized void dump() {
    }

    @Override
    public synchronized String createJson(StringBuilder json, String sectionName) {
        return null;
    }

    @Override
    public synchronized void dumpJson(StringBuilder stringBuffer, String id) {
    }

    @Override
    public synchronized void clean() {
    }

    @Override
    public synchronized void setTaskTimer(ProfilerType type, String taskId, long timer) {
    }

    @Override
    public void setTaskPowerUsage(ProfilerType type, String taskId, long power) {
    }

    @Override
    public synchronized void sum(ProfilerType type, long sum) {

    }

}

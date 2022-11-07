/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.runtime.profiler;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;

public class EmptyProfiler implements TornadoProfiler {

    public EmptyProfiler() {

    }

    @Override
    public void addValueToMetric(ProfilerType type, String taskName, long value) {
    }

    @Override
    public void start(ProfilerType type) {
    }

    @Override
    public void start(ProfilerType type, String taskName) {
    }

    @Override
    public void registerDeviceName(String taskName, String deviceInfo) {

    }

    @Override
    public void registerBackend(String taskName, String backend) {

    }

    @Override
    public void registerDeviceID(String taskName, String deviceID) {
    }

    @Override
    public void registerMethodHandle(ProfilerType type, String taskName, String methodName) {
    }

    @Override
    public void stop(ProfilerType type) {
    }

    @Override
    public void stop(ProfilerType type, String taskName) {
    }

    @Override
    public long getTimer(ProfilerType type) {
        System.out.println("Enable the profiler with: -Dtornado.profiler=True");
        return 0;
    }

    @Override
    public long getTaskTimer(ProfilerType type, String taskName) {
        return 0;
    }

    @Override
    public void setTimer(ProfilerType type, long time) {

    }

    @Override
    public void dump() {
    }

    @Override
    public String createJson(StringBuffer json, String sectionName) {
        return null;
    }

    @Override
    public void dumpJson(StringBuffer stringBuffer, String id) {
    }

    @Override
    public void clean() {
    }

    @Override
    public void setTaskTimer(ProfilerType totalKernelTime, String taskId, long timer) {
    }

    @Override
    public void sum(ProfilerType type, long sum) {

    }

}

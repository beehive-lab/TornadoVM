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
package uk.ac.manchester.tornado.api.profiler;

public interface TornadoProfiler {

    void addValueToMetric(ProfilerType type, String taskName, long value);

    void start(ProfilerType type);

    void start(ProfilerType type, String taskName);

    void registerDeviceName(String taskName, String deviceInfo);

    void registerBackend(String taskName, String backend);

    void registerDeviceID(String taskName, String deviceID);

    void registerMethodHandle(ProfilerType type, String taskName, String methodName);

    void stop(ProfilerType type);

    void stop(ProfilerType type, String taskName);

    long getTimer(ProfilerType type);

    long getTaskTimer(ProfilerType type, String taskName);

    void setTimer(ProfilerType type, long time);

    void dump();

    String createJson(StringBuffer json, String sectionName);

    void dumpJson(StringBuffer stringBuffer, String id);

    void clean();

    void setTaskTimer(ProfilerType totalKernelTime, String taskId, long timer);

    void sum(ProfilerType type, long timer);
}

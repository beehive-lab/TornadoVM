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

import java.util.HashMap;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;

public class TimeProfiler implements TornadoProfiler {

    private HashMap<ProfilerType, Long> profilerTime;
    private HashMap<String, HashMap<ProfilerType, Long>> taskTimers;

    private StringBuffer indent;

    public TimeProfiler() {
        profilerTime = new HashMap<>();
        taskTimers = new HashMap<>();
        indent = new StringBuffer("");
    }

    @Override
    public void start(ProfilerType type) {
        long start = System.nanoTime();
        profilerTime.put(type, start);
    }

    @Override
    public void start(ProfilerType type, String taskName) {
        long start = System.nanoTime();
        if (!taskTimers.containsKey(taskName)) {
            taskTimers.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, Long> profilerType = taskTimers.get(taskName);
        profilerType.put(type, start);
        taskTimers.put(taskName, profilerType);
    }

    @Override
    public void stop(ProfilerType type) {
        long end = System.nanoTime();
        long start = profilerTime.get(type);
        long total = end - start;
        profilerTime.put(type, total);
    }

    @Override
    public void stop(ProfilerType type, String taskName) {
        long end = System.nanoTime();
        HashMap<ProfilerType, Long> profiledType = taskTimers.get(taskName);
        long start = profiledType.get(type);
        long total = end - start;
        profiledType.put(type, total);
        taskTimers.put(taskName, profiledType);
    }

    @Override
    public long getTimer(ProfilerType type) {
        if (!profilerTime.containsKey(type)) {
            return 0;
        }
        return profilerTime.get(type);
    }

    @Override
    public long getTaskTimer(ProfilerType type, String taskName) {
        if (!taskTimers.containsKey(taskName)) {
            return 0;
        }
        if (!taskTimers.get(taskName).containsKey(type)) {
            return 0;
        }
        return taskTimers.get(taskName).get(type);
    }

    @Override
    public void setTimer(ProfilerType type, long time) {
        profilerTime.put(type, time);
    }

    @Override
    public void dump() {
        for (ProfilerType p : profilerTime.keySet()) {
            System.out.println("[PROFILER] " + p.getDescription() + ": " + profilerTime.get(p));
        }

        for (String p : taskTimers.keySet()) {
            System.out.println("[PROFILER-TASK] " + p + ": " + taskTimers.get(p));

        }
    }

    private void increaseIndent() {
        indent.append("    ");
    }

    private void decreaseIndent() {
        indent.delete(indent.length() - 4, indent.length());
    }

    private void closeScope(StringBuffer json) {
        json.append(indent.toString() + "}");
    }

    private void newLine(StringBuffer json) {
        json.append("\n");
    }

    @Override
    public String createJson(StringBuffer json, String sectionName) {
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString() + "\"" + sectionName + "\": " + "{\n");
        increaseIndent();
        for (ProfilerType p : profilerTime.keySet()) {
            json.append(indent.toString() + "\"" + p + "\"" + ": " + "\"" + profilerTime.get(p) + "\",\n");
        }

        final int size = taskTimers.keySet().size();
        int counter = 0;
        for (String p : taskTimers.keySet()) {
            json.append(indent.toString() + "\"" + p + "\"" + ": {\n");
            increaseIndent();
            counter++;
            for (ProfilerType p2 : taskTimers.get(p).keySet()) {
                json.append(indent.toString() + "\"" + p2 + "\"" + ": " + "\"" + taskTimers.get(p).get(p2) + "\",\n");
            }
            json.delete(json.length() - 2, json.length() - 1); // remove last comma
            decreaseIndent();
            closeScope(json);
            if (counter != size) {
                json.append(", ");
            }
            newLine(json);
        }
        decreaseIndent();
        closeScope(json);
        newLine(json);
        decreaseIndent();
        closeScope(json);
        newLine(json);
        return json.toString();
    }

    @Override
    public void dumpJson(StringBuffer json, String id) {
        String jsonContent = this.createJson(json, id);
        System.out.println(jsonContent);
    }

    @Override
    public void clean() {
        profilerTime.clear();
        taskTimers.clear();
        indent = new StringBuffer("");
    }

    @Override
    public void setTaskTimer(ProfilerType type, String taskID, long timer) {
        if (!taskTimers.containsKey(taskID)) {
            taskTimers.put(taskID, new HashMap<>());
        }
        taskTimers.get(taskID).put(type, timer);
    }

    @Override
    public void sum(ProfilerType acc, long value) {
        long sum = getTimer(acc) + value;
        profilerTime.put(acc, sum);
    }

}

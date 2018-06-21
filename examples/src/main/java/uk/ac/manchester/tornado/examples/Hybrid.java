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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.common.Tornado;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

/**
 * First try: It assumes the first device is a GPU and the second device is an
 * FPGA.
 *
 * 
 */
public class Hybrid {

    public static final int GPU_INDEX = 0;
    public static final int FPGA_INDEX = 1;

    public static final int MAX_DEVICES = 2;

    public static void saxpy(int alpha, int[] x, int[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void vectorAddition(int[] x, int[] y, int[] z) {
        for (@Parallel int i = 0; i < y.length; i++) {
            z[i] = x[i] * y[i];
        }
    }

    /**
     * 
     * 
     * A)
     * 
     * x0.t0.device=0:0 : GPU
     * 
     * v0.t0.device=0:0 : GPU
     * 
     * B)
     * 
     * x0.t1.device=0:0 : GPU
     * 
     * v0.t1.device=0:1 : FPGA
     * 
     * c)
     * 
     * x1.t0.device=0:1 : FPGA
     * 
     * v1.t0.device0:0 : GPU
     * 
     * D)
     * 
     * x1.t1.device=0:1 : FPGA
     * 
     * v1.t1.device=0:1 : FPGA
     * 
     * 
     * How to run:
     * 
     * 
     * <p>
     * <code>
     * $ tornado  --debug -Dtornado.opencl.codecache.loadbin=True -Dtornado.precompiled.binary=kernel/lookupBufferAddress,v0.t1.device=0:1,kernel/lookupBufferAddress,x1.t0.device=0:1,kernel/lookupBufferAddress,x1.t1.device=0:1,kernel/lookupBufferAddress,v1.t1.device=0:1 uk.ac.manchester.tornado.examples.Hybrid 
     * </code>
     * </p>
     * 
     * 
     * 
     */
    public void engineExploration(KernelPackage kernelPackage) {

        for (int i = 0; i < MAX_DEVICES; i++) {
            for (int j = 0; j < MAX_DEVICES; j++) {
                // We play with 2 devices
                TaskSchedule s0 = new TaskSchedule("x" + i);
                Tornado.setProperty("x" + i + ".t" + j + ".device", "0:" + i);
                System.out.println("x" + i + ".t" + j + ".device" + "0:" + i);
                s0.task("t" + j, Hybrid::saxpy, kernelPackage.alpha, kernelPackage.x, kernelPackage.y);
                s0.streamOut(kernelPackage.z);
                s0.execute();

                TaskSchedule s1 = new TaskSchedule("v" + i);
                Tornado.setProperty("v" + i + ".t" + j + ".device", "0:" + j);
                System.out.println("v" + i + ".t" + j + ".device" + "0:" + j);
                s1.task("t" + j, Hybrid::vectorAddition, kernelPackage.x, kernelPackage.y, kernelPackage.z);
                s1.streamOut(kernelPackage.z);
                s1.execute();
            }
        }

    }

    public void engineExplorationV2(KernelPackage kernelPackage) {

        ArrayList<TaskSchedule> tasks = new ArrayList<>();
        HashMap<String, String> tasksLocation = new HashMap<>();
        ArrayList<String> tasksKey = new ArrayList<>();

        // Tasks preparation
        for (int i = 0; i < MAX_DEVICES; i++) {
            for (int j = 0; j < MAX_DEVICES; j++) {

                // Task X
                TaskSchedule s0 = new TaskSchedule("x" + i);
                String taskID = "x" + i + ".t" + j + ".device";
                String location = "0:" + i;
                s0.task("t" + j, Hybrid::saxpy, kernelPackage.alpha, kernelPackage.x, kernelPackage.y);
                s0.streamOut(kernelPackage.z);
                tasks.add(s0);
                tasksLocation.put(taskID, location);
                tasksKey.add(taskID);

                // Task V
                TaskSchedule s1 = new TaskSchedule("v" + i);
                taskID = "v" + i + ".t" + j + ".device";
                location = "0:" + j;
                s1.task("t" + j, Hybrid::vectorAddition, kernelPackage.x, kernelPackage.y, kernelPackage.z);
                s1.streamOut(kernelPackage.z);

                tasks.add(s1);
                tasksLocation.put(taskID, location);
                tasksKey.add(taskID);
            }
        }

        // Tasks Execution
        for (int i = 0; i < tasks.size(); i += 2) {
            TaskSchedule t0 = tasks.get(i);
            String key = tasksKey.get(i);
            String locX = tasksLocation.get(key);
            System.out.println(key + "=" + locX);
            Tornado.setProperty(key, locX);
            t0.execute();

            TaskSchedule t1 = tasks.get(i + 1);
            key = tasksKey.get(i + 1);
            String locY = tasksLocation.get(key);
            System.out.println(key + "=" + locY);
            Tornado.setProperty(key, locY);
            t1.execute();
        }

    }

    public static class KernelPackage {
        int numElements = 65536;
        int alpha = 2;

        int[] x = new int[numElements];
        int[] y = new int[numElements];
        int[] z = new int[numElements];

        public KernelPackage() {
            Random r = new Random();
            for (int i = 0; i < numElements; i++) {
                x[i] = r.nextInt();
                y[i] = r.nextInt();
            }
        }
    }

    public static void main(String[] args) {
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        if (driver.getDeviceCount() < 2) {
            return;
        }
        KernelPackage kernelPackage = new KernelPackage();
        new Hybrid().engineExplorationV2(kernelPackage);
    }

}

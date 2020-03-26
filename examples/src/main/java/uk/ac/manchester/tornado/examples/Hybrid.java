/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * First try: It assumes the first device is a GPU and the second device is an
 * FPGA.
 * 
 * The goal of this experiment is to show that Tornado is able to execute two
 * different tasks in two different devices and migrate the task at runtime
 * between devices. We emphasis in the Virtual Layer of Tornado.
 * 
 * We execute two methods on the heterogeneous devices. We iterate multiple
 * times, each time, the method is relocated on different device.
 * {@link uk.ac.manchester.tornado.examples.Hybrid#engineExplorationV1}
 *
 * 
 */
public class Hybrid {

    public static final int GPU_INDEX = 0;
    public static final int FPGA_INDEX = 1;
    public static final int MAX_DEVICES = 2;

    public static final int[] DEVICE_INDEXES = new int[] { GPU_INDEX, FPGA_INDEX };

    // Task X
    public static void saxpy(int alpha, int[] x, int[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    // Task V
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
     * $ tornado  --debug -Dtornado.precompiled.binary=kernel/lookupBufferAddress,v0.t1.device=0:1,kernel/lookupBufferAddress,x1.t0.device=0:1,kernel/lookupBufferAddress,x1.t1.device=0:1,kernel/lookupBufferAddress,v1.t1.device=0:1 uk.ac.manchester.tornado.examples.Hybrid 
     * </code>
     * </p>
     * 
     * 
     * 
     */
    public void engineExplorationV1(KernelPackage kernelPackage) {

        for (int i = 0; i < MAX_DEVICES; i++) {
            for (int j = 0; j < MAX_DEVICES; j++) {
                // We play with 2 devices
                TaskSchedule s0 = new TaskSchedule("x" + i);
                TornadoRuntime.setProperty("x" + i + ".t" + j + ".device", "0:" + DEVICE_INDEXES[i]);
                System.out.println("x" + i + ".t" + j + ".device" + "0:" + DEVICE_INDEXES[i]);
                s0.task("t" + j, Hybrid::saxpy, kernelPackage.alpha, kernelPackage.x, kernelPackage.y);
                s0.streamOut(kernelPackage.z);
                s0.execute();

                TaskSchedule s1 = new TaskSchedule("v" + i);
                TornadoRuntime.setProperty("v" + i + ".t" + j + ".device", "0:" + DEVICE_INDEXES[j]);
                System.out.println("v" + i + ".t" + j + ".device" + "0:" + DEVICE_INDEXES[j]);
                s1.task("t" + j, Hybrid::vectorAddition, kernelPackage.x, kernelPackage.y, kernelPackage.z);
                s1.streamOut(kernelPackage.z);
                s1.execute();
            }
        }
        System.out.println(Arrays.toString(kernelPackage.z));
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

        for (int k = 0; k < 3; k++) {

            System.out.println("\n\nITERATION: " + k);

            // Tasks Execution
            for (int i = 0; i < tasks.size(); i += 2) {
                TaskSchedule t0 = tasks.get(i);
                String key = tasksKey.get(i);
                String locX = tasksLocation.get(key);
                System.out.println(key + "=" + locX);
                TornadoRuntime.setProperty(key, locX);
                t0.execute();

                TaskSchedule t1 = tasks.get(i + 1);
                key = tasksKey.get(i + 1);
                String locY = tasksLocation.get(key);
                System.out.println(key + "=" + locY);
                TornadoRuntime.setProperty(key, locY);
                t1.execute();
            }
        }

    }

    public void engineExplorationV3(KernelPackage kernelPackage) {

        ArrayList<TaskSchedule> tasks = new ArrayList<>();
        HashMap<String, String> tasksLocation = new HashMap<>();
        ArrayList<String> tasksKey = new ArrayList<>();

        // Tasks preparation
        for (int i = 0; i < MAX_DEVICES; i++) {
            for (int j = 0; j < MAX_DEVICES; j++) {

                // Task X
                TaskSchedule s0 = new TaskSchedule("x" + i);
                String taskID = "x" + i + ".t" + j + ".device";
                String location = "0:" + DEVICE_INDEXES[i];
                s0.task("t" + j, Hybrid::saxpy, kernelPackage.alpha, kernelPackage.x, kernelPackage.y);
                s0.streamOut(kernelPackage.z);
                tasks.add(s0);
                tasksLocation.put(taskID, location);
                tasksKey.add(taskID);

                // Task V
                TaskSchedule s1 = new TaskSchedule("v" + i);
                taskID = "v" + i + ".t" + j + ".device";
                location = "0:" + DEVICE_INDEXES[j];
                s1.task("t" + j, Hybrid::vectorAddition, kernelPackage.x, kernelPackage.y, kernelPackage.z);
                s1.streamOut(kernelPackage.z);

                tasks.add(s1);
                tasksLocation.put(taskID, location);
                tasksKey.add(taskID);
            }
        }

        int MAX_INNER = 1;

        // Tasks Execution
        for (int i = 0; i < tasks.size(); i += 2) {
            TaskSchedule t0 = tasks.get(i);
            String key = tasksKey.get(i);
            String locX = tasksLocation.get(key);
            System.out.println(key + "=" + locX);
            TornadoRuntime.setProperty(key, locX);

            for (int j = 0; j < MAX_INNER; j++) {
                long start = System.nanoTime();
                t0.execute();
                long stop = System.nanoTime();
                System.out.println("Total Time X: " + (stop - start) + " (ns)");
            }

            TaskSchedule t1 = tasks.get(i + 1);
            key = tasksKey.get(i + 1);
            String locY = tasksLocation.get(key);
            System.out.println(key + "=" + locY);
            TornadoRuntime.setProperty(key, locY);

            for (int j = 0; j < MAX_INNER; j++) {
                long start = System.nanoTime();
                t1.execute();
                long stop = System.nanoTime();
                System.out.println("Total Time V: " + (stop - start) + " (ns)");
            }
        }

        // System.out.println(Arrays.toString(kernelPackage.z));
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
                x[i] = 100;
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
        new Hybrid().engineExplorationV3(kernelPackage);
    }

}

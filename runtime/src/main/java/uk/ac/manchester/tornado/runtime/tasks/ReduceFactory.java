/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.Map;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

class ReduceFactory {

    private static void rAdd(int[] array, final int size) {
        int acc = array[0];
        for (int i = 1; i < size; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    private static void rAdd(long[] array, final int size) {
        long acc = array[0];
        for (int i = 1; i < size; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    private static void rAdd(float[] array, final int size) {
        float acc = array[0];
        for (int i = 1; i < size; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    private static void rAdd(double[] array, final int size) {
        double acc = array[0];
        for (int i = 1; i < size; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    private static void rMul(int[] array, final int size) {
        int acc = array[0];
        for (int i = 1; i < size; i++) {
            acc *= array[i];
        }
        array[0] = acc;
    }

    private static void rMul(long[] array, final int size) {
        long acc = array[0];
        for (int i = 1; i < size; i++) {
            acc *= array[i];
        }
        array[0] = acc;
    }

    private static void rMul(float[] array, final int size) {
        float acc = array[0];
        for (int i = 1; i < size; i++) {
            acc *= array[i];
        }
        array[0] = acc;
    }

    private static void rMul(double[] array, final int size) {
        double acc = array[0];
        for (int i = 1; i < size; i++) {
            acc *= array[i];
        }
        array[0] = acc;
    }

    private static void rMax(int[] array, final int size) {
        int acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMax(long[] array, final int size) {
        long acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMax(float[] array, final int size) {
        float acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMax(double[] array, final int size) {
        double acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMin(int[] array, final int size) {
        int acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMin(long[] array, final int size) {
        long acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMin(float[] array, final int size) {
        float acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array[i]);
        }
        array[0] = acc;
    }

    private static void rMin(double[] array, final int size) {
        double acc = array[0];
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array[i]);
        }
        array[0] = acc;
    }

    static void handleAdd(Object newArray, int sizeReduceArray, TaskSchedule task, String taskName, Map<String, Object> properties) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rAdd, (int[]) newArray, sizeReduceArray), properties);
                break;
            case "long[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rAdd, (long[]) newArray, sizeReduceArray), properties);
                break;
            case "float[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rAdd, (float[]) newArray, sizeReduceArray), properties);
                break;
            case "double[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rAdd, (double[]) newArray, sizeReduceArray), properties);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    static void handleMul(Object newArray, int sizeReduceArray, TaskSchedule task, String taskName, Map<String, Object> properties) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMul, (int[]) newArray, sizeReduceArray), properties);
                break;
            case "long[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMul, (long[]) newArray, sizeReduceArray), properties);
                break;
            case "float[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMul, (float[]) newArray, sizeReduceArray), properties);
                break;
            case "double[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMul, (double[]) newArray, sizeReduceArray), properties);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    static void handleMax(Object newArray, int sizeReduceArray, TaskSchedule task, String taskName, Map<String, Object> properties) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMax, (int[]) newArray, sizeReduceArray), properties);
                break;
            case "long[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMax, (long[]) newArray, sizeReduceArray), properties);
                break;
            case "float[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMax, (float[]) newArray, sizeReduceArray), properties);
                break;
            case "double[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMax, (double[]) newArray, sizeReduceArray), properties);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    static void handleMin(Object newArray, int sizeReduceArray, TaskSchedule task, String taskName, Map<String, Object> properties) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMin, (int[]) newArray, sizeReduceArray), properties);
                break;
            case "long[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMin, (long[]) newArray, sizeReduceArray), properties);
                break;
            case "float[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMin, (float[]) newArray, sizeReduceArray), properties);
                break;
            case "double[]":
                task.addTask(TaskPackage.createPackage(taskName, ReduceFactory::rMin, (double[]) newArray, sizeReduceArray), properties);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }
}

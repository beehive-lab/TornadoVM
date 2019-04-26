/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

public class ReduceFactory {

    public static void radd(int[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] += array[i];
        }
    }

    public static void radd(float[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] += array[i];
        }
    }

    public static void radd(double[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] += array[i];
        }
    }

    public static void rmul(int[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] *= array[i];
        }
    }

    public static void rmul(float[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] *= array[i];
        }
    }

    public static void rmul(double[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] *= array[i];
        }
    }

    public static void rmax(int[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.max(array[0], array[i]);
        }
    }

    public static void rmax(float[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.max(array[0], array[i]);
        }
    }

    public static void rmax(double[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.max(array[0], array[i]);
        }
    }

    public static void rmin(int[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.min(array[0], array[i]);
        }
    }

    public static void rmin(float[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.min(array[0], array[i]);
        }
    }

    public static void rmin(double[] array, final int size) {
        for (int i = 1; i < size; i++) {
            array[0] = Math.min(array[0], array[i]);
        }
    }

    public static void handleAdd(Object newArray, TaskSchedule task, int sizeReduceArray) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::radd, (int[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::radd, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::radd, (double[]) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    public static void handleMul(Object newArray, TaskSchedule task, int sizeReduceArray) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmul, (int[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmul, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmul, (double[]) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    public static void handleMax(Object newArray, TaskSchedule task, int sizeReduceArray) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmax, (int[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmax, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmax, (double[]) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

    public static void handleMin(Object newArray, TaskSchedule task, int sizeReduceArray) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmin, (int[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmin, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(ReduceTaskSchedule.SEQUENTIAL_TASK_REDUCE_NAME, ReduceFactory::rmin, (double[]) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException("[ERROR] Reduce data type not supported yet: " + newArray.getClass().getTypeName());
        }
    }

}

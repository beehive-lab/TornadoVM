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
package uk.ac.manchester.tornado.runtime.tasks;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

class ReduceFactory {

    private static final String ERROR_MESSAGE = "[ERROR] Reduce data type not supported yet: ";

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

    private static void rAdd(FloatArray array, final int size) {
        float acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc += array.get(i);
        }
        array.set(0, acc);
    }

    private static void rAdd(IntArray array, final int size) {
        int acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc += array.get(i);
        }
        array.set(0, acc);
    }

    private static void rAdd(DoubleArray array, final int size) {
        double acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc += array.get(i);
        }
        array.set(0, acc);
    }

    private static void rAdd(LongArray array, final int size) {
        long acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc += array.get(i);
        }
        array.set(0, acc);
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

    private static void rMul(IntArray array, final int size) {
        int acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc *= array.get(i);
        }
        array.set(0, acc);
    }

    private static void rMul(FloatArray array, final int size) {
        float acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc *= array.get(i);
        }
        array.set(0, acc);
    }

    private static void rMul(DoubleArray array, final int size) {
        double acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc *= array.get(i);
        }
        array.set(0, acc);
    }

    private static void rMul(LongArray array, final int size) {
        long acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc *= array.get(i);
        }
        array.set(0, acc);
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

    private static void rMax(IntArray array, final int size) {
        int acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMax(FloatArray array, final int size) {
        float acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMax(DoubleArray array, final int size) {
        double acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMax(LongArray array, final int size) {
        long acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.max(acc, array.get(i));
        }
        array.set(0, acc);
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

    private static void rMin(IntArray array, final int size) {
        int acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMin(FloatArray array, final int size) {
        float acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMin(DoubleArray array, final int size) {
        double acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array.get(i));
        }
        array.set(0, acc);
    }

    private static void rMin(LongArray array, final int size) {
        long acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc = Math.min(acc, array.get(i));
        }
        array.set(0, acc);
    }

    static void handleAdd(Object newArray, TaskGraph task, int sizeReduceArray, String taskName) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(taskName, ReduceFactory::rAdd, (int[]) newArray, sizeReduceArray);
                break;
            case "long[]":
                task.task(taskName, ReduceFactory::rAdd, (long[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(taskName, ReduceFactory::rAdd, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(taskName, ReduceFactory::rAdd, (double[]) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.IntArray":
                task.task(taskName, ReduceFactory::rAdd, (IntArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                task.task(taskName, ReduceFactory::rAdd, (FloatArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                task.task(taskName, ReduceFactory::rAdd, (DoubleArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.LongArray":
                task.task(taskName, ReduceFactory::rAdd, (LongArray) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException(ERROR_MESSAGE + newArray.getClass().getTypeName());
        }
    }

    static void handleMul(Object newArray, TaskGraph task, int sizeReduceArray, String taskName) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(taskName, ReduceFactory::rMul, (int[]) newArray, sizeReduceArray);
                break;
            case "long[]":
                task.task(taskName, ReduceFactory::rMul, (long[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(taskName, ReduceFactory::rMul, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(taskName, ReduceFactory::rMul, (double[]) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.IntArray":
                task.task(taskName, ReduceFactory::rMul, (IntArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                task.task(taskName, ReduceFactory::rMul, (FloatArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                task.task(taskName, ReduceFactory::rMul, (DoubleArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.LongArray":
                task.task(taskName, ReduceFactory::rMul, (LongArray) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException(ERROR_MESSAGE + newArray.getClass().getTypeName());
        }
    }

    static void handleMax(Object newArray, TaskGraph task, int sizeReduceArray, String taskName) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(taskName, ReduceFactory::rMax, (int[]) newArray, sizeReduceArray);
                break;
            case "long[]":
                task.task(taskName, ReduceFactory::rMax, (long[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(taskName, ReduceFactory::rMax, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(taskName, ReduceFactory::rMax, (double[]) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.IntArray":
                task.task(taskName, ReduceFactory::rMax, (IntArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                task.task(taskName, ReduceFactory::rMax, (FloatArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                task.task(taskName, ReduceFactory::rMax, (DoubleArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.LongArray":
                task.task(taskName, ReduceFactory::rMax, (LongArray) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException(ERROR_MESSAGE + newArray.getClass().getTypeName());
        }
    }

    static void handleMin(Object newArray, TaskGraph task, int sizeReduceArray, String taskName) {
        switch (newArray.getClass().getTypeName()) {
            case "int[]":
                task.task(taskName, ReduceFactory::rMin, (int[]) newArray, sizeReduceArray);
                break;
            case "long[]":
                task.task(taskName, ReduceFactory::rMin, (long[]) newArray, sizeReduceArray);
                break;
            case "float[]":
                task.task(taskName, ReduceFactory::rMin, (float[]) newArray, sizeReduceArray);
                break;
            case "double[]":
                task.task(taskName, ReduceFactory::rMin, (double[]) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.IntArray":
                task.task(taskName, ReduceFactory::rMin, (IntArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.FloatArray":
                task.task(taskName, ReduceFactory::rMin, (FloatArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.DoubleArray":
                task.task(taskName, ReduceFactory::rMin, (DoubleArray) newArray, sizeReduceArray);
                break;
            case "uk.ac.manchester.tornado.api.types.arrays.LongArray":
                task.task(taskName, ReduceFactory::rMin, (LongArray) newArray, sizeReduceArray);
                break;
            default:
                throw new TornadoRuntimeException(ERROR_MESSAGE + newArray.getClass().getTypeName());
        }
    }
}

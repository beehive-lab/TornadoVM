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

package uk.ac.manchester.tornado.examples.compression;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class HuffmanTornadoDecoder {

    public static final int GPU_INDEX = 0;
    public static final int FPGA_INDEX = 1;
    public static final int MAX_DEVICES = 2;

    private static void decodeTornadoKernel(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, int[] output) {
        final int rootNode = 0;
        int iteratorNode = 0;
        int outIndex = 0;
        for (int idx = 0; idx < input.length; idx++) {
            byte bitInput = input[idx];
            int l = left[iteratorNode];
            if (l == -1) {
                int realData = data[iteratorNode];
                output[outIndex] = realData;
                iteratorNode = rootNode;
                outIndex++;
                idx--;
                continue;
            } else if (bitInput == 0) {
                iteratorNode = left[iteratorNode];
            } else {
                iteratorNode = right[iteratorNode];
            }
        }
    }

    /**
     * A)
     * 
     * x0.t0.device=0:0 : GPU
     * 
     * x0.t1.device=0:1 : FPGA
     * 
     * 
     * How to run:
     * 
     * <p>
     * <code>
     * $ tornado  --debug -Dtornado.precompiled.binary=kernel/lookupBufferAddress,x0.t1.device=0:1 uk.ac.manchester.tornado.examples.compression.HuffmanTornadoDecoder 
     * </code>
     * </p>
     * 
     * 
     * 
     */
    public void engineExploration(KernelPackage kernelPackage) {

        ArrayList<TaskSchedule> tasks = new ArrayList<>();
        HashMap<String, String> tasksLocation = new HashMap<>();
        ArrayList<String> tasksKey = new ArrayList<>();

        // Tasks preparation
        for (int i = 0; i < MAX_DEVICES; i++) {

            String taskID = "x0.t" + i + ".device";
            String location = "0:" + i;

            TaskSchedule s0 = new TaskSchedule("x0");
            s0.task("t" + i, HuffmanTornadoDecoder::decodeTornadoKernel, kernelPackage.input, kernelPackage.frequencies, kernelPackage.data, kernelPackage.left, kernelPackage.right,
                    kernelPackage.output);
            s0.streamOut(kernelPackage);
            tasks.add(s0);
            tasksLocation.put(taskID, location);
            tasksKey.add(taskID);
        }

        int MAX_INNER = 1;

        // Tasks Execution
        for (int i = 0; i < tasks.size(); i++) {
            TaskSchedule t0 = tasks.get(i);
            String key = tasksKey.get(i);
            String locX = tasksLocation.get(key);
            System.out.println(key + "=" + locX);
            TornadoRuntime.setProperty(key, locX);

            System.out.println("RUNNING: " + key);

            for (int j = 0; j < MAX_INNER; j++) {
                long start = System.nanoTime();
                t0.execute();
                long stop = System.nanoTime();
                System.out.println("Total Time X: " + (stop - start) + " (ns)");
            }
        }
    }

    private static class KernelPackage {
        byte[] input;
        int[] frequencies;
        int[] left;
        int[] right;
        int[] data;
        int[] output;

        public KernelPackage(byte[] input, int[] frequencies, int[] left, int[] right, int[] data, int[] output) {
            super();
            this.input = input;
            this.frequencies = frequencies;
            this.left = left;
            this.right = right;
            this.data = data;
            this.output = output;
        }
    }

    public static KernelPackage readInputData() throws ClassNotFoundException, IOException {
        System.out.println("Reading file for decompressing");
        FileInputStream iStream = new FileInputStream("/tmp/huffman.txt");
        @SuppressWarnings("resource") ObjectInputStream inObject = new ObjectInputStream(iStream);

        int[] frequencies = (int[]) inObject.readObject();
        int[] data = (int[]) inObject.readObject();
        int[] left = (int[]) inObject.readObject();
        int[] right = (int[]) inObject.readObject();

        byte[] compressedData = null;
        BitSet bitSetCompressed = null;

        compressedData = (byte[]) inObject.readObject();
        bitSetCompressed = BitSet.valueOf(compressedData);

        byte[] bits = new byte[bitSetCompressed.length()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = (byte) (bitSetCompressed.get(i) ? 1 : 0);
        }

        int size = 5000000;
        int[] result = new int[size];
        return new KernelPackage(bits, frequencies, left, right, data, result);
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException {
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        if (driver.getDeviceCount() < 2) {
            return;
        }

        KernelPackage kernelPackage = readInputData();
        new HuffmanTornadoDecoder().engineExploration(kernelPackage);
    }
}

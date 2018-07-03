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
package uk.ac.manchester.tornado.examples.compression;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;

import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class Huffman {

    private static HashMap<Integer, String> dictionary = new HashMap<>();
    private static boolean CHECK_RESULT = true;

    private static boolean CHECK_DECODE_ONLY = false;

    private static BitSet convertStringToBinary(String binary) {
        BitSet bitset = new BitSet(binary.length());
        for (int i = 0; i < binary.length(); i++) {
            if (binary.charAt(i) == '1') {
                bitset.set(i);
            } else {
                bitset.clear(i);
            }
        }
        return bitset;
    }

    public static void printAndAssignCode(HuffmanNode node, String code) {
        if (node.left == null && node.right == null && node.frequency != -1) {
            dictionary.put(node.realData, code);
            return;
        }
        printAndAssignCode(node.left, code + "0");
        printAndAssignCode(node.right, code + "1");
    }

    public static HashMap<Integer, Integer> getFrequencies(ArrayList<Integer> inputData) {
        HashMap<Integer, Integer> frequencies = new HashMap<>();
        for (int i = 0; i < inputData.size(); i++) {
            if (!frequencies.containsKey(inputData.get(i))) {
                frequencies.put(inputData.get(i), 1);
            } else {
                frequencies.put(inputData.get(i), frequencies.get(inputData.get(i)) + 1);
            }
        }
        return frequencies;
    }

    public static void decoding(ArrayList<Integer> inputData) throws ClassNotFoundException, IOException {
        System.out.println("5. Reading file for decompressing");
        FileInputStream iStream = new FileInputStream("/tmp/huffman.txt");
        @SuppressWarnings("resource") ObjectInputStream inObject = new ObjectInputStream(iStream);

        // 1. Read huffman tree
        // HuffmanNode root = (HuffmanNode) inObject.readObject();
        int[] frequencies = (int[]) inObject.readObject();
        int[] data = (int[]) inObject.readObject();
        int[] left = (int[]) inObject.readObject();
        int[] right = (int[]) inObject.readObject();

        // 2. Read byte[] compressed data
        byte[] compressedData = null;
        BitSet bitSetCompressed = null;

        compressedData = (byte[]) inObject.readObject();
        bitSetCompressed = BitSet.valueOf(compressedData);

        byte[] bits = new byte[bitSetCompressed.length()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = (bitSetCompressed.get(i) == false) ? (byte) 0 : (byte) 1;
        }

        // 3. decode
        System.out.println("6. Decode");
        long s0 = System.nanoTime();
        // decode2(bitSetCompressed, frequencies, data, left, right, inputData);
        // decode2a(bits, frequencies, data, left, right, inputData);
        int size = 5000000;
        if (inputData != null) {
            size = inputData.size();
        }
        int[] result = new int[size];

        decodeTornado(bits, frequencies, data, left, right, result, inputData);
        long s1 = System.nanoTime();

        System.out.println("Decoding time: " + (s1 - s0) + " (ns)");
    }

    @SuppressWarnings("unused")
    private static void decode(BitSet input, HuffmanNode root, ArrayList<Integer> inputData) {
        boolean isData = true;
        int idx = 0;
        HuffmanNode aux = root;

        int jdx = 0;
        while (isData) {

            if (idx >= input.length() - 1) {
                isData = false;
            }

            boolean bitInput = input.get(idx);

            if (aux.left == null && aux.right == null && aux.realData != 0) {
                int realData = aux.realData;
                aux = root;
                if (CHECK_RESULT) {
                    // System.out.println("REAL DATA --> " + realData);
                    if (realData != inputData.get(jdx)) {
                        System.out.println("Result is not correct");
                        break;
                    }
                }
                jdx++;
            } else if (bitInput == false) {
                aux = aux.left;
                idx++;
            } else {
                aux = aux.right;
                idx++;
            }
        }
    }

    @SuppressWarnings("unused")
    private static void decode2(BitSet input, int[] frequencies, int[] data, int[] left, int[] right, ArrayList<Integer> inputData) {
        int rootNode = 0;
        int iteratorNode = 0;

        int jdx = 0;
        for (int idx = 0; idx < input.length(); idx++) {

            boolean bitInput = input.get(idx);

            if (left[iteratorNode] == -1 && right[iteratorNode] == -1 && data[iteratorNode] != -1) {
                int realData = data[iteratorNode];
                iteratorNode = rootNode;
                if (CHECK_RESULT) {
                    // System.out.println("REAL DATA --> " + realData);
                    if (realData != inputData.get(jdx)) {
                        System.out.println("Result is not correct");
                        break;
                    }
                }
                idx--;
                jdx++;
            } else if (bitInput == false) {
                // System.out.println("Moving left to index: " +
                // left[iteratorNode]);
                iteratorNode = left[iteratorNode];
            } else {
                // System.out.println("Moving right to index: " +
                // right[iteratorNode]);
                iteratorNode = right[iteratorNode];
            }
        }
    }

    @SuppressWarnings("unused")
    private static void decode2a(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, ArrayList<Integer> inputData) {
        int rootNode = 0;
        int iteratorNode = 0;

        int jdx = 0;
        for (int idx = 0; idx < input.length; idx++) {

            char bitInput = (char) input[idx];

            if (left[iteratorNode] == -1 && right[iteratorNode] == -1 && data[iteratorNode] != -1) {
                int realData = data[iteratorNode];
                iteratorNode = rootNode;
                if (CHECK_RESULT) {
                    // System.out.println("REAL DATA --> " + realData);
                    if (realData != inputData.get(jdx)) {
                        System.out.println("Result is not correct");
                        break;
                    }
                }
                idx--;
                jdx++;
            } else if (bitInput == 0) {
                iteratorNode = left[iteratorNode];
            } else {
                iteratorNode = right[iteratorNode];
            }
        }
    }

    private static void decodeTornado(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, int[] message, ArrayList<Integer> inputData) {

        // @formatter:off
        new TaskSchedule("s0") 
              .task("t0", Huffman::decodeTornadoKernel, input, frequencies, data, left, right, message)
              .streamOut(message)
              .execute();
        // @formatter:on

        if (CHECK_RESULT && inputData != null) {
            for (int i = 0; i < inputData.size(); i++) {
                if (message[i] != inputData.get(i)) {
                    System.out.println("Result is not correct: " + inputData.get(i) + " vs " + message[i] + " INDEX: " + i);
                    break;
                }
            }
        }
    }

    private static void decodeTornadoKernel(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, int[] output) {
        final int rootNode = 0;
        int iteratorNode = 0;
        int outIndex = 0;
        for (int idx = 0; idx < input.length; idx++) {
            byte bitInput = input[idx];
            int l = left[iteratorNode];
            int r = right[iteratorNode];
            int d = data[iteratorNode];

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

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        ArrayList<Integer> inputData = null;

        if (!CHECK_DECODE_ONLY) {

            System.out.println("1. Reading");

            // 1. Read data file
            long ss0 = System.nanoTime();
            // ArrayList<Integer> inputData =
            // InputScanner.getNumbers("/tmp/framesNonCompress.txt");
            inputData = InputScanner.getIntNumbers("/tmp/foo.txt");
            long ss1 = System.nanoTime();
            System.out.println("Reading time: " + (ss1 - ss0) + " (ns)");

            // 2. Compute frequencies
            System.out.println("2. Computing frequencies");
            HashMap<Integer, Integer> frequencyHash = getFrequencies(inputData);
            int[] dataKeyToCompress = frequencyHash.keySet().stream().mapToInt(i -> i).toArray();
            int n = dataKeyToCompress.length;
            int[] frequencyArray = frequencyHash.values().stream().mapToInt(i -> i).toArray();
            System.out.println("3. Compressing");

            PriorityQueue<HuffmanNode> queue = new PriorityQueue<HuffmanNode>(n, new HuffmanTreeComparator());

            for (int i = 0; i < n; i++) {
                HuffmanNode hn = new HuffmanNode();
                hn.realData = dataKeyToCompress[i];
                hn.frequency = frequencyArray[i];
                hn.left = null;
                hn.right = null;
                queue.add(hn);
            }

            HuffmanNode root = null;

            while (queue.size() > 1) {
                HuffmanNode x = queue.peek();
                queue.poll();

                HuffmanNode y = queue.peek();
                queue.poll();

                HuffmanNode f = new HuffmanNode();
                f.frequency = x.frequency + y.frequency;
                f.realData = 0;

                f.left = x;
                f.right = y;

                root = f;
                queue.add(f);
            }

            printAndAssignCode(root, "");

            int numNodes = getNumNodes(root);
            System.out.println("NUM NODES: " + numNodes);

            int[] frequencies = new int[numNodes];
            int[] data = new int[numNodes];
            int[] left = new int[numNodes];
            int[] right = new int[numNodes];

            fillArrays(frequencies, data, left, right, root);

            System.out.println("4. Writing");

            FileOutputStream stream = new FileOutputStream("/tmp/huffman.txt");
            ObjectOutputStream out = new ObjectOutputStream(stream);

            // 4.1 Write huffman tree
            // out.writeObject(root);
            out.writeObject(frequencies);
            out.writeObject(data);
            out.writeObject(left);
            out.writeObject(right);

            StringBuffer compressData = new StringBuffer();
            System.out.println("SIZE: " + inputData.size());
            // 4.2 Write compress data in byte[]
            for (int i = 0; i < inputData.size(); i++) {
                compressData.append(dictionary.get(inputData.get(i)));
            }

            // System.out.println("COMPRESS: " + compressData.toString());
            BitSet compressedBits = convertStringToBinary(compressData.toString());

            // System.out.println(Arrays.toString(compressedBits.toByteArray()));

            try {
                out.writeObject(compressedBits.toByteArray());
            } catch (Exception e) {

            }

            stream.close();
            out.close();
        }

        decoding(inputData);
    }

    private static void fillArrays(int[] frequencies, int[] data, int[] left, int[] right, HuffmanNode root) {
        if (root == null) {
            return;
        }

        HashSet<HuffmanNode> visited = new HashSet<>();
        HashMap<Integer, HuffmanNode> nodeIndexes = new HashMap<>();
        HashMap<HuffmanNode, Integer> nodeIndexes2 = new HashMap<>();

        Deque<HuffmanNode> f = new LinkedList<>();
        f.push(root);
        visited.add(root);
        int nodeIndex = 0;

        frequencies[0] = root.frequency;
        data[0] = root.realData;
        left[0] = -1;
        right[0] = -1;

        while (!f.isEmpty()) {

            HuffmanNode aux = f.poll();
            nodeIndexes.put(nodeIndex, aux);
            nodeIndexes2.put(aux, nodeIndex++);
            visited.add(aux);

            if (aux.left != null && !visited.contains(aux.left)) {
                f.addLast(aux.left);
            }
            if (aux.right != null && !visited.contains(aux.right)) {
                f.addLast(aux.right);
            }
        }

        for (int i = 0; i < visited.size(); i++) {
            HuffmanNode n = nodeIndexes.get(i);
            frequencies[i] = n.frequency;
            data[i] = n.realData;
            left[i] = (n.left != null) ? nodeIndexes2.get(n.left) : -1;
            right[i] = (n.right != null) ? nodeIndexes2.get(n.right) : -1;

            // System.out.println("Node: " + i + " ::DATA: " + data[i] + " L: "
            // + left[i] + " R: " + right[i] + " FRQ: " + frequencies[i]);
        }
    }

    private static int getNumNodes(HuffmanNode root) {

        if (root == null) {
            return 0;
        }

        HashSet<HuffmanNode> visited = new HashSet<>();
        Deque<HuffmanNode> f = new LinkedList<>();
        f.push(root);
        visited.add(root);

        while (!f.isEmpty()) {

            HuffmanNode aux = f.poll();
            visited.add(aux);

            if (aux.left != null && !visited.contains(aux.left)) {
                f.addLast(aux.left);
            }
            if (aux.right != null && !visited.contains(aux.right)) {
                f.addLast(aux.right);
            }
        }

        return visited.size();
    }
}

/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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

package uk.ac.manchester.tornado.examples.compute;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Parallel Implementation of the BFS: this is based on the Marawacc compiler framework.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.BFS
 * </code>
 */
public class BFS {
    // CHECKSTYLE:OFF

    public static final boolean SAMPLE = false;
    private static final boolean BIDIRECTIONAL = false;
    private static final boolean PRINT_SOLUTION = false;
    private static final boolean VALIDATION = true;

    IntArray vertices;
    IntArray verticesJava;
    IntArray adjacencyMatrix;
    IntArray modify;
    IntArray modifyJava;
    IntArray currentDepth;

    /**
     * Set to one the connection between node from and node to into the adjacency matrix.
     */
    public static void connect(int from, int to, IntArray graph, int N) {
        if (from != to && (graph.get(from * N + to) == 0)) {
            graph.set(from * N + to, 1);
        }
    }

    /**
     * It builds a simple graph just for showing the example.
     */
    public static void initializeAdjacencyMatrixSimpleGraph(IntArray adjacencyMatrix, int numNodes) {
        adjacencyMatrix.init(0);
        connect(0, 1, adjacencyMatrix, numNodes);
        connect(0, 4, adjacencyMatrix, numNodes);
        connect(1, 2, adjacencyMatrix, numNodes);
        connect(2, 3, adjacencyMatrix, numNodes);
        connect(2, 4, adjacencyMatrix, numNodes);
        connect(3, 4, adjacencyMatrix, numNodes);
    }

    private static int[] generateIntRandomArray(int numNodes) {
        Random r = new Random();
        int bound = r.nextInt(numNodes);
        IntStream streamArray = r.ints(bound, 0, numNodes);
        int[] array = streamArray.toArray();
        return array;
    }

    public static void generateRandomGraph(IntArray adjacencyMatrix, int numNodes, int root) {
        Random r = new Random();
        int bound = r.nextInt(numNodes);
        IntStream fromStream = r.ints(bound, 0, numNodes);
        int[] f = fromStream.toArray();
        for (int k = 0; k < f.length; k++) {

            int from = f[k];
            if (k == 0) {
                from = root;
            }

            int[] toArray = generateIntRandomArray(numNodes);

            for (int i = 0; i < toArray.length; i++) {
                connect(from, toArray[i], adjacencyMatrix, numNodes);
            }
        }
    }

    private static void initializeVertices(int numNodes, IntArray vertices, int root) {
        for (@Parallel int i = 0; i < numNodes; i++) {
            if (i == root) {
                vertices.set(i, 0);
            } else {
                vertices.set(i, -1);
            }
        }
    }

    private static void runBFS(IntArray vertices, IntArray adjacencyMatrix, int numNodes, IntArray h_true, IntArray currentDepth) {
        for (@Parallel int from = 0; from < numNodes; from++) {
            for (@Parallel int to = 0; to < numNodes; to++) {
                int elementAccess = from * numNodes + to;

                if (adjacencyMatrix.get(elementAccess) == 1) {
                    int dfirst = vertices.get(from);
                    int dsecond = vertices.get(to);
                    if ((currentDepth.get(0) == dfirst) && (dsecond == -1)) {
                        vertices.set(to, dfirst + 1);
                        h_true.set(0, 0);
                    }

                    if (BIDIRECTIONAL) {
                        if ((currentDepth.get(0) == dsecond) && (dfirst == -1)) {
                            vertices.set(from, dsecond + 1);
                            h_true.set(0, 0);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        int size = 10000;
        if (SAMPLE) {
            size = 5;
        }
        new BFS().tornadoBFS(0, size);
    }

    public boolean validateBFS(IntArray vertices, IntArray verticesJava) {
        boolean check = true;
        for (int i = 0; i < vertices.getSize(); i++) {
            if (vertices.get(i) != verticesJava.get(i)) {
                check = false;
            }
        }
        return check;
    }

    public boolean checkModify(IntArray modify, IntArray modifyJava) {
        boolean check = true;
        for (int i = 0; i < modify.getSize(); i++) {
            if (modify.get(i) != modifyJava.get(i)) {
                check = false;
            }
        }
        return check;
    }

    public void tornadoBFS(int rootNode, int numNodes) {

        vertices = new IntArray(numNodes);
        verticesJava = new IntArray(numNodes);
        adjacencyMatrix = new IntArray(numNodes * numNodes);
        boolean validModifyResults = true;

        if (SAMPLE) {
            initializeAdjacencyMatrixSimpleGraph(adjacencyMatrix, numNodes);
        } else {
            generateRandomGraph(adjacencyMatrix, numNodes, rootNode);
        }

        // Step 1: vertices initialisation
        initializeVertices(numNodes, vertices, rootNode);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", BFS::initializeVertices, numNodes, vertices, rootNode) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, vertices);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.execute();

        // initialization of Java vertices
        initializeVertices(numNodes, verticesJava, rootNode);

        modify = new IntArray(1);
        modify.init(1);

        modifyJava = new IntArray(1);
        modifyJava.init(1);

        currentDepth = new IntArray(1);
        currentDepth.init(0);

        TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, vertices, adjacencyMatrix, modify, currentDepth) //
                .task("t1", BFS::runBFS, vertices, adjacencyMatrix, numNodes, modify, currentDepth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, vertices, modify);

        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1) //
                .withDevice(device);

        boolean done = false;

        while (!done) {
            // 2. Parallel BFS
            boolean allDone = true;
            System.out.println("Current Depth: " + currentDepth.get(0));
            runBFS(verticesJava, adjacencyMatrix, numNodes, modifyJava, currentDepth);
            executor1.execute();
            currentDepth.set(0, currentDepth.get(0) + 1);

            if (VALIDATION && !(validModifyResults = checkModify(modify, modifyJava))) {
                break;
            }

            for (int i = 0; i < modify.getSize(); i++) {
                if (modify.get(i) == 0) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                done = true;
            }
            modify.init(1);
            modifyJava.init(1);
        }

        if (PRINT_SOLUTION) {
            System.out.println("Solution: " + vertices.toString());
        }

        if (VALIDATION) {
            if (validateBFS(vertices, verticesJava) && validModifyResults) {
                System.out.println("Validation true");
            } else {
                System.out.println("Validation false");
            }
        }
    }
}
// CHECKSTYLE:ON

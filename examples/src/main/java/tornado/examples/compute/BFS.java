/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */
package tornado.examples.compute;

import java.util.Arrays;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

// Parallel Implementation of the BFS
public class BFS {
    
    /**
     * Set to one the connection between node from and node to into the adjacency matrix.
     *
     * @param from
     * @param to
     * @param graph
     * @param N
     */
    public static void connect(int from, int to, int[] graph, int N) {
        graph[from * N + to] = 1;
    }

    /**
     * It builds a simple graph just for showing the example.
     *
     * @param adjacencyMatrix
     * @param numNodes
     */
    public static void initilizeAdjacencyMatrixSimpleGraph(int[] adjacencyMatrix, int numNodes) {
        Arrays.fill(adjacencyMatrix, 0);
        connect(0, 1, adjacencyMatrix, numNodes);
        connect(0, 4, adjacencyMatrix, numNodes);
        connect(1, 2, adjacencyMatrix, numNodes);
        connect(2, 3, adjacencyMatrix, numNodes);
        connect(2, 4, adjacencyMatrix, numNodes);
        connect(3, 4, adjacencyMatrix, numNodes);
    }

    private static void initializeVertices(int numNodes, int[] vertices, int root) {
        for (@Parallel int i = 0; i < numNodes; i++) {
            if (i == root) {
                vertices[i] = 0;
            } else {
                vertices[i] = -1;
            }
        }
    }
    
    private static void runBFS(int[] vertices, int[] adjacencyMatrix, int numNodes, int[] h_true) {
        for (@Parallel int i = 0; i < numNodes; i++) {
            for (@Parallel int j = 0; j < numNodes; j++) {
                h_true[0] = 1;
                int elementAccess = i * numNodes + j;
                
                if (adjacencyMatrix[elementAccess] == 1) {
                    int dfirst = vertices[i];
                    int dsecond = vertices[j];
                    if ((i == dfirst) && (dsecond == -1)) {
                        vertices[j] = dfirst + 1;
                        h_true[0] = 0;
                    }

                    if ((i == dsecond) && (dfirst == -1)) {
                        vertices[i] = dsecond + 1;
                        h_true[0] = 0;
                    }
                }
            }
        }
    }
    
    public static void tornadoBFS(int root, int numberOfNodes) {
        int numNodes = numberOfNodes;
        
        int[] vertices = new int[numNodes];
        int[] adjacencyMatrix = new int[numNodes * numNodes];

        initilizeAdjacencyMatrixSimpleGraph(adjacencyMatrix, numNodes);

        // Step 1: vertices initialisation
        initializeVertices(numNodes, vertices, root);
        
        int[] modify = new int[] { 1 };
        
        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", BFS::runBFS, vertices, adjacencyMatrix, numNodes, modify);
        s0.streamOut(vertices, modify).execute();
        
        boolean done = false;
        while (!done) {
            // 2. Parallel BFS
            //runBFS(vertices, adjacencyMatrix, numNodes, modify);
            s0.execute();
            if (modify[0] == 1) {
                done = true;
            }
            System.out.println("Partial Solution: " + Arrays.toString(vertices));
        }
        System.out.println("Solution: " + Arrays.toString(vertices));
    }
    
    public static void main(String[] args) {
        tornadoBFS(0, 5);
    }

}

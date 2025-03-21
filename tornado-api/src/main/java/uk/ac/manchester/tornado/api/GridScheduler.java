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
package uk.ac.manchester.tornado.api;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to store the thread grid configuration for a specific task in a Task-Graph.
 */
public class GridScheduler {

    private final ConcurrentHashMap<String, WorkerGrid> gridTaskMap;

    /**
     * Creates a new GridScheduler object with an empty grid task map.
     */
    public GridScheduler() {
        gridTaskMap = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new GridScheduler with a specific task name and WorkerGrid. Task names must be unique
     * for the whole execution plan that the grid scheduler will be attached to.
     * 
     * @param taskName
     * @param workerGrid
     */
    public GridScheduler(String taskName, WorkerGrid workerGrid) {
        gridTaskMap = new ConcurrentHashMap<>();
        gridTaskMap.put(taskName, workerGrid);
    }

    /**
     * Adds a new WorkerGrid object to the grid scheduler for a specific task.
     * 
     * @param taskName
     * @param workerGrid
     * @since v1.1.0
     */
    public void addWorkerGrid(String taskName, WorkerGrid workerGrid) {
        gridTaskMap.put(taskName, workerGrid);
    }

    /**
     * Returns the WorkerGrid object associated with a given task name.
     * 
     * @param taskName
     * @return
     */
    public WorkerGrid get(String taskName) {
        return gridTaskMap.get(taskName);
    }

    /**
     * Checks if the grid scheduler contains a specific worker grid for a task.
     *
     * @param taskName
     * @return boolean
     */
    public boolean contains(String taskScheduleName, String taskName) {
        return gridTaskMap.containsKey(taskScheduleName + "." + taskName);
    }

    /**
     * It returns all task names associated with a grid scheduler.
     * 
     * @return
     */
    public Set<String> keySet() {
        return gridTaskMap.keySet();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String taskName : gridTaskMap.keySet()) {
            sb.append(taskName) //
                    .append("::GlobalWorkGroup=") //
                    .append(Arrays.toString(gridTaskMap.get(taskName).getGlobalWork())) //
                    .append("::LocalWorkGroup=") //
                    .append(Arrays.toString(gridTaskMap.get(taskName).getLocalWork()));
        }
        return sb.toString();
    }
}

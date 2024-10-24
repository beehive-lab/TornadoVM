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

import java.security.Key;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GridScheduler {

    private final ConcurrentHashMap<String, WorkerGrid> gridTaskMap;

    public GridScheduler() {
        gridTaskMap = new ConcurrentHashMap<>();
    }

    public GridScheduler(String taskName, WorkerGrid workerGrid) {
        gridTaskMap = new ConcurrentHashMap<>();
        gridTaskMap.put(taskName, workerGrid);
    }

    public void setWorkerGrid(String taskName, WorkerGrid workerGrid) {
        gridTaskMap.put(taskName, workerGrid);
    }

    public WorkerGrid get(String taskName) {
        return gridTaskMap.get(taskName);
    }

    public boolean contains(String taskScheduleName, String taskName) {
        return gridTaskMap.containsKey(taskScheduleName + "." + taskName);
    }

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

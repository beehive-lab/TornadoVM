/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

public class ExecutorFrame {

    private final long id;
    private DRMode drMode;
    private Policy policy;
    private GridScheduler gridScheduler;

    public ExecutorFrame(long id) {
        this.id = id;
    }

    public ExecutorFrame withPolicy(Policy policy) {
        this.policy = policy;
        return this;
    }

    public ExecutorFrame withMode(DRMode drMode) {
        this.drMode = drMode;
        return this;
    }

    public ExecutorFrame withGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }

    public Policy getPolicy() {
        return policy;
    }

    public DRMode getDRMode() {
        return drMode;
    }

    public GridScheduler getGridScheduler() {
        return gridScheduler;
    }

    public long getId() {
        return this.id;
    }
}

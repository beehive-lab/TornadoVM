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

public abstract class AbstractWorkerGrid implements WorkerGrid {

    protected long[] globalWork;
    protected long[] localWork;
    protected long[] numOfWorkgroups;
    protected long[] globalOffset;

    protected AbstractWorkerGrid(long x, long y, long z) {
        globalWork = new long[] { x, y, z };
        globalOffset = new long[] { 0, 0, 0 };
    }

    @Override
    public long[] getGlobalWork() {
        return globalWork;
    }

    @Override
    public long[] getLocalWork() {
        return localWork;
    }

    @Override
    public long[] getNumberOfWorkgroups() {
        return numOfWorkgroups;
    }

    @Override
    public long[] getGlobalOffset() {
        return globalOffset;
    }

    @Override
    public void setGlobalWork(long x, long y, long z) {
        globalWork = new long[] { x, y, z };
    }

    @Override
    public void setLocalWork(long x, long y, long z) {
        localWork = new long[] { x, y, z };
        calculateNumberOfWorkgroups();
    }

    @Override
    public void setGlobalOffset(long x, long y, long z) {
        globalOffset = new long[] { x, y, z };
    }

    private void calculateNumberOfWorkgroups() {
        numOfWorkgroups = new long[globalWork.length];
        for (int i = 0; i < globalWork.length; i++) {
            numOfWorkgroups[i] = globalWork[i] / localWork[i];
        }
    }

    @Override
    public void setLocalWorkToNull() {
        this.localWork = null;
    }

    @Override
    public void setNumberOfWorkgroupsToNull() {
        this.numOfWorkgroups = null;
    }
}

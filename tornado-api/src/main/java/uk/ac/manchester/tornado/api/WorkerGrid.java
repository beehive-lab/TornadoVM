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

public interface WorkerGrid {

    /**
     * Returns the number of parallel dimensions. This
     * could be 1D, 2D or 3D.
     * 
     * @return int
     */
    int dimension();

    /**
     * Returns an array of 3 elements with the total number of threads per dimension.
     * 
     * @return {@link long[]}
     */
    long[] getGlobalWork();

    /**
     * Returns an array of 3 elements with the total number of threads per for the local work groups.
     * 
     * @return {@link long[]}
     */
    long[] getLocalWork();

    /**
     * Returns an array with the total number of work groups per dimension.
     * 
     * @return {@link long[]}
     */
    long[] getNumberOfWorkgroups();

    /**
     * Returns an array of 3 elements with the offset per dimension.
     * 
     * @return {@link long[]}
     */
    long[] getGlobalOffset();

    /**
     * Sets the total number of threads per dimension to launch on the accelerator.
     * 
     * @param x
     * @param y
     * @param z
     */
    void setGlobalWork(long x, long y, long z);

    /**
     * Sets the local work group threads per dimension to launch on the accelerator.
     * 
     * @param x
     * @param y
     * @param z
     */
    void setLocalWork(long x, long y, long z);

    /**
     * Sets the global work group to null.
     */
    void setNumberOfWorkgroupsToNull();

    /**
     * Sets the local work group to null. In this case, the corresponding driver will
     * launch a default number of thread blocks (e.g., OpenCL Runtime).
     */
    void setLocalWorkToNull();

    /**
     * Sets the global offsets per dimension.
     *
     * @param x
     * @param y
     * @param z
     */
    void setGlobalOffset(long x, long y, long z);
}

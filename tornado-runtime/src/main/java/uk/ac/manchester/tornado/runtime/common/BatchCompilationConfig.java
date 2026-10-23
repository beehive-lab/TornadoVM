/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common;

/**
 * This class encapsulates all the information related to batch processing
 * that is necessary during compilation.
 */
public class BatchCompilationConfig {

    private long batchThreads;
    private int batchNumber;
    private long batchSize;

    public BatchCompilationConfig(long batchThreads, int batchNumber, long batchSize) {
        this.batchThreads = batchThreads;
        this.batchNumber = batchNumber;
        this.batchSize = batchSize;
    }

    public long getBatchThreads() {
        return batchThreads;
    }

    public int getBatchNumber() {
        return batchNumber;
    }

    public long getBatchSize() {
        return batchSize;
    }
}

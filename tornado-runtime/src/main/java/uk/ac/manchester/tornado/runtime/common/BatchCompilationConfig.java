/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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

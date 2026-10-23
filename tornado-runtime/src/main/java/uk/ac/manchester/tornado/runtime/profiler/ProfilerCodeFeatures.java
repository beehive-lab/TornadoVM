/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.profiler;

public enum ProfilerCodeFeatures {

    // @formatter:off
    GLOBAL_LOADS ("Global Memory Loads"),
    GLOBAL_STORES ("Global Memory Stores"),
    LOCAL_LOADS ("Local Memory Loads"),
    LOCAL_STORES ("Local Memory Stores"),
    CONSTANT_LOADS ("Constant Memory Loads"),
    CONSTANT_STORES ("Constant Memory Stores"),
    PRIVATE_LOADS ("Private Memory Loads"),
    PRIVATE_STORES ("Private Memory Stores"),
    LOOPS ("Total Loops"),
    PARALLEL_LOOPS ("Parallel Loops"),
    IFS ("If Statements"),
    SWITCH ("Switch Statements"),
    CASE ("Switch Cases"),
    CAST ("Cast Operations"),
    VECTORS ("Vector Operations"),
    INTEGER_OPS ("Total Integer Operations"),
    FLOAT_OPS ("Total Float Operations"),
    FP32 ("Single Precision Float Operations"),
    DOUBLES ("Double Precision Float Operations"),
    BINARY ("Binary Operations"),
    BOOLEAN ("Boolean Operations"),
    F_MATH ("Float Math Functions"),
    I_MATH ("Integer Math Functions"),
    I_CMP ("Integer Comparison"),
    F_CMP ("Float Comparison");
    // @formatter:on

    private String feature;

    ProfilerCodeFeatures(String featureType) {
        this.feature = featureType;
    }

    @Override
    public String toString() {
        return feature;
    }
}

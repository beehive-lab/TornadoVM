/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.enums;

/**
 * The quantity that auto-tuning measures and minimises for each candidate local work size.
 */
public enum AutoTuneMetric {

    /**
     * Kernel execution time of the task being tuned, read from the profiler. Tuning switches the profiler
     * on in {@link ProfilerMode#SILENT} mode for the duration of the search and restores the previous mode
     * afterwards.
     */
    KERNEL_TIME,

    /**
     * Wall-clock time of one execution of the whole task-graph, including the data transfers it performs.
     * It covers every task in the graph, not only the one being tuned.
     */
    END_TO_END_TIME;
}
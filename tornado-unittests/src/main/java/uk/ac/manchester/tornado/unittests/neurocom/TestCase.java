/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.neurocom;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.neurocom.TestCase
 * </code>
 */
public class TestCase extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static final int N = 512;

    private static void KMeansCalculateCentroids(ShortArray cache_dqsize, IntArray cache_dstart, IntArray cache_dqid, FloatArray cache_dqtfidf, FloatArray cache_kmeans, IntArray doc_group,
            IntArray sizes) {
        int N = sizes.get(0);
        int V = sizes.get(1);
        int K = sizes.get(5);
        for (@Parallel int j = 0; j < K; j++) {
            int kj_len = 1;
            for (int k = 0; k < V; k++) {
                cache_kmeans.set(V * j + k, 0);
            }
            for (int i = 0; i < N; i++) {
                if (doc_group.get(i) == j) {
                    kj_len++;
                    int di_start = cache_dstart.get(i);
                    int di_end = cache_dqsize.get(i) + di_start;
                    for (int di_idx = di_start; di_idx < di_end; di_idx++) {
                        int qi = cache_dqid.get(di_idx);
                        cache_kmeans.set(V * j + qi, cache_kmeans.get(V * j + qi) + cache_dqtfidf.get(di_idx));
                    }
                }
            }

            for (int k = 0; k < V; k++) {
                cache_kmeans.set(V * j + k, cache_kmeans.get(V * j + k) / kj_len);
            }
        }
    }

    /**
     * Test code generation of the following kernel.
     */
    @Test
    public void test() throws TornadoExecutionPlanException {
        ShortArray cache_dqsize = new ShortArray(N);
        IntArray cache_dstart = new IntArray(N);
        FloatArray cache_kmeans = new FloatArray(N * N);
        IntArray doc_group = new IntArray(N);
        int total_len = 10;
        IntArray cache_dqid = new IntArray(total_len);
        FloatArray cache_dqtfidf = new FloatArray(total_len);
        IntArray sizes = new IntArray(6);

        TaskGraph taskGraph = new TaskGraph("foo") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, cache_dqsize, cache_dqid, cache_dqtfidf, cache_kmeans, doc_group) //
                .task("bar", TestCase::KMeansCalculateCentroids, cache_dqsize, cache_dstart, cache_dqid, cache_dqtfidf, cache_kmeans, doc_group, sizes) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cache_dstart);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

    }
    // CHECKSTYLE:ON
}

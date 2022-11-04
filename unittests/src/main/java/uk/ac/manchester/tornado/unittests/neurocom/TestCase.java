/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.neurocom.TestCase
 * </code>
 */
public class TestCase extends TornadoTestBase {

    private static final int N = 512;

    private static void KMeansCalculateCentroids(short[] cache_dqsize, int[] cache_dstart, int[] cache_dqid, float[] cache_dqtfidf, float[] cache_kmeans, int[] doc_group, int[] sizes) {
        int N = sizes[0];
        int V = sizes[1];
        int K = sizes[5];
        for (@Parallel int j = 0; j < K; j++) {
            int kj_len = 1;
            for (int k = 0; k < V; k++) {
                cache_kmeans[V * j + k] = 0;
            }
            for (int i = 0; i < N; i++) {
                if (doc_group[i] == j) {
                    kj_len++;
                    int di_start = cache_dstart[i];
                    int di_end = cache_dqsize[i] + di_start;
                    for (int di_idx = di_start; di_idx < di_end; di_idx++) {
                        int qi = cache_dqid[di_idx];
                        cache_kmeans[V * j + qi] += cache_dqtfidf[di_idx];
                    }
                }
            }

            for (int k = 0; k < V; k++) {
                cache_kmeans[V * j + k] /= kj_len;
            }
        }
    }

    /**
     * Test code generation of the following kernel.
     */
    @Test
    public void test() {
        short[] cache_dqsize = new short[N];
        int[] cache_dstart = new int[N];
        float[] cache_kmeans = new float[N * N];
        int[] doc_group = new int[N];
        int total_len = 10;
        int[] cache_dqid = new int[total_len];
        float[] cache_dqtfidf = new float[total_len];
        int[] sizes = new int[6];

        TaskGraph taskGraph = new TaskGraph("foo") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, cache_dqsize, cache_dqid, cache_dqtfidf, cache_kmeans, doc_group) //
                .task("bar", TestCase::KMeansCalculateCentroids, cache_dqsize, cache_dstart, cache_dqid, cache_dqtfidf, cache_kmeans, doc_group, sizes) //
                .transferToHost(cache_dstart);

        taskGraph.warmup();
        taskGraph.execute();
    }

}
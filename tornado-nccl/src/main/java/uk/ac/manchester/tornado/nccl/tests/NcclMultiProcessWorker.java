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
package uk.ac.manchester.tornado.nccl.tests;

import java.time.Duration;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.nccl.Nccl;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclPlanGroup;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.NcclUniqueId;

/**
 * One rank of a communicator that spans several processes. It joins the communicator with the
 * unique id it is given, then runs a kernel feeding an all-reduce and a ring exchange with its
 * neighbours, and checks both results. Every process of the job runs the same steps with its own
 * rank, so starting this program once per rank (with the id created by one of them) exercises
 * NCCL across processes.
 *
 * <p>
 * How to run? Start rank 0 with {@code new} instead of an id: it creates the unique id, prints it as
 * {@code NCCL-ID <id>} and must stay running while the other ranks join (NCCL's bootstrap lives in
 * the process that created the id). Then start every other rank with that id.
 * </p>
 * <code>
 * tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker new &lt;world size&gt; 0 &lt;device index&gt;
 * tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker &lt;id&gt; &lt;world size&gt; &lt;rank&gt; &lt;device index&gt;
 * </code>
 */
public class NcclMultiProcessWorker {

    private static final int SIZE = 1 << 16;
    private static final int LOCAL_SIZE = 256;

    public static void fill(KernelContext context, FloatArray array, int rank) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, (rank + 1) * (id % 7 + 1));
        }
    }

    private static void runStep(String name, TaskGraph graph, TornadoDevice device) throws Exception {
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice(device).withGridScheduler(new GridScheduler(name + ".fill", worker));
            try (NcclPlanGroup ranks = new NcclPlanGroup(plan).withStepTimeout(Duration.ofSeconds(120))) {
                ranks.execute();
            }
        }
    }

    /**
     * Joins the communicator as {@code rank} of {@code worldSize} on {@code device}, then runs and
     * checks an all-reduce and a ring exchange. Blocks until every rank has joined.
     */
    public static void runRank(NcclUniqueId id, int worldSize, int rank, TornadoDevice device) throws Exception {
        try (NcclCommunicator communicator = NcclCommunicator.create(id, worldSize, rank, device)) {
            FloatArray sum = new FloatArray(SIZE);
            String allReduce = "allreduce" + rank;
            runStep(allReduce, new TaskGraph(allReduce) //
                    .task("fill", NcclMultiProcessWorker::fill, new KernelContext(), sum, rank) //
                    .libraryTask("sum", Nccl::allReduceInPlace, communicator, sum, NcclRedOp.SUM) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sum), device);
            float ranksSum = worldSize * (worldSize + 1) / 2.0f;
            for (int i = 0; i < SIZE; i++) {
                if (sum.get(i) != ranksSum * (i % 7 + 1)) {
                    throw new TornadoRuntimeException("[ERROR] rank " + rank + ": all-reduce element " + i + " is " + sum.get(i) + ", expected " + ranksSum * (i % 7 + 1));
                }
            }

            FloatArray sent = new FloatArray(SIZE);
            FloatArray received = new FloatArray(SIZE);
            String ring = "ring" + rank;
            runStep(ring, new TaskGraph(ring) //
                    .task("fill", NcclMultiProcessWorker::fill, new KernelContext(), sent, rank) //
                    .libraryTask("shift", Nccl::sendRecv, communicator, sent, (rank + 1) % worldSize, received, (rank - 1 + worldSize) % worldSize) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, received), device);
            int previous = (rank - 1 + worldSize) % worldSize;
            for (int i = 0; i < SIZE; i++) {
                if (received.get(i) != (previous + 1) * (i % 7 + 1)) {
                    throw new TornadoRuntimeException("[ERROR] rank " + rank + ": ring element " + i + " is " + received.get(i) + ", expected " + (previous + 1) * (i % 7 + 1));
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("usage: NcclMultiProcessWorker <unique id in Base64 | new> <world size> <rank> <device index>");
            System.exit(2);
        }
        int rank = Integer.parseInt(args[2]);
        try {
            NcclUniqueId id;
            if (args[0].equals("new")) {
                id = NcclUniqueId.create();
                System.out.println("NCCL-ID " + id.toBase64());
                System.out.flush();
            } else {
                id = NcclUniqueId.fromBase64(args[0]);
            }
            runRank(id, Integer.parseInt(args[1]), rank, TornadoExecutionPlan.getDevice(0, Integer.parseInt(args[3])));
            System.out.println("NCCL-WORKER rank " + rank + " OK");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("NCCL-WORKER rank " + rank + " FAILED: " + t);
            t.printStackTrace(System.out);
            System.exit(1);
        }
    }
}

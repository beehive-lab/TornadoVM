/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package hattasks;

import static hat.KernelContext.GIX;

import java.lang.invoke.MethodHandles;

import hat.Accelerator;
import hat.backend.Backend;
import hat.buffer.F32Array;
import jdk.incubator.code.Reflect;
import optkl.ifacemapper.BoundSchema;
import optkl.ifacemapper.Buffer;
import optkl.ifacemapper.Schema;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.babylon.hat.HatTasks;

/**
 * HAT kernels over a HAT struct buffer, run as tasks of a TornadoVM task graph: three kernels in one graph,
 * TornadoVM transfers and grid scheduler, and the same plan with and without a CUDA graph.
 *
 * <pre>
 * ./run.sh -d 1:0 build/examples hattasks.NBodyTaskGraph [bodies] [steps]    (-d: the TornadoVM device, driver:device)
 * </pre>
 */
public class NBodyTaskGraph {

    /** A HAT buffer of structs: bodies with a position and a velocity. */
    public interface Universe extends Buffer {
        int length();

        interface Body extends Struct {
            float x();
            float y();
            float z();
            float vx();
            float vy();
            float vz();
            void x(float x);
            void y(float y);
            void z(float z);
            void vx(float vx);
            void vy(float vy);
            void vz(float vz);
        }

        Body body(long index);

        Schema<Universe> schema = Schema.of(Universe.class, universe -> universe
                .arrayLen("length").array("body", body -> body.fields("x", "y", "z", "vx", "vy", "vz")));

        static Universe create(Accelerator accelerator, int length) {
            return BoundSchema.of(accelerator, schema, length).allocate();
        }
    }

    /** The new velocity of body GIX(), from the positions of all bodies (which this kernel only reads). */
    @Reflect
    public static void accelerate(Universe universe, float mass, float delT) {
        Universe.Body body = universe.body(GIX());
        float ax = 0f;
        float ay = 0f;
        float az = 0f;
        for (int i = 0; i < universe.length(); i++) {
            Universe.Body other = universe.body(i);
            float dx = other.x() - body.x();
            float dy = other.y() - body.y();
            float dz = other.z() - body.z();
            float invDist = 1.0f / (float) Math.sqrt(dx * dx + dy * dy + dz * dz + 0.01f);
            float s = mass * invDist * invDist * invDist;
            ax += s * dx;
            ay += s * dy;
            az += s * dz;
        }
        body.vx(body.vx() + ax * delT);
        body.vy(body.vy() + ay * delT);
        body.vz(body.vz() + az * delT);
    }

    /** The new position of body GIX(): a second kernel, so that no body moves while others still read it. */
    @Reflect
    public static void move(Universe universe, float delT) {
        Universe.Body body = universe.body(GIX());
        body.x(body.x() + body.vx() * delT);
        body.y(body.y() + body.vy() * delT);
        body.z(body.z() + body.vz() * delT);
    }

    /** The kinetic energy of body GIX(), into a HAT F32Array. */
    @Reflect
    public static void energy(Universe universe, F32Array energy) {
        Universe.Body body = universe.body(GIX());
        energy.array(GIX(), 0.5f * (body.vx() * body.vx() + body.vy() * body.vy() + body.vz() * body.vz()));
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 4096;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST); // TornadoHatBackend
        String[] d = System.getProperty("hat.t.device", "0:0").split(":"); // run.sh -d
        TornadoDevice device = TornadoExecutionPlan.getDevice(Integer.parseInt(d[0]), Integer.parseInt(d[1]));
        for (boolean cudaGraph : new boolean[] { false, true }) {
            if (cudaGraph && device.getTornadoVMBackend() != TornadoVMBackendType.CUDA) {
                System.out.println("CUDA graphs need a CUDA device: " + device.getPhysicalDevice().getDeviceName() + " is not one");
                continue;
            }
            Universe universe = Universe.create(accelerator, n);
            F32Array energy = F32Array.create(accelerator, n);
            Universe reference = Universe.create(accelerator, n);
            for (int i = 0; i < n; i++) {
                for (Universe u : new Universe[] { universe, reference }) {
                    var b = u.body(i);
                    b.x(i % 17);
                    b.y(i % 29);
                    b.z(i % 41);
                    b.vx(0f);
                    b.vy(0f);
                    b.vz(0f);
                }
            }

            // The TornadoVM side: a task graph with two HAT kernels, sharing the universe on the device.
            var hat = HatTasks.on(accelerator);
            var graph = new TaskGraph("nbody").transferToDevice(DataTransferMode.FIRST_EXECUTION, hat.of(universe));
            hat.task(graph, "accelerate", NBodyTaskGraph::accelerate, universe, 1f, 0.005f);
            hat.task(graph, "move", NBodyTaskGraph::move, universe, 0.005f);
            hat.task(graph, "energy", NBodyTaskGraph::energy, universe, energy);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(energy));
            graph.transferToHost(DataTransferMode.UNDER_DEMAND, hat.of(universe));

            var grid = new GridScheduler("nbody.accelerate", new WorkerGrid1D(n));
            grid.addWorkerGrid("nbody.move", new WorkerGrid1D(n));
            grid.addWorkerGrid("nbody.energy", new WorkerGrid1D(n));
            try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withDevice(device).withGridScheduler(grid);
                if (cudaGraph) {
                    plan.withCUDAGraph();
                }
                if (Boolean.getBoolean("strict")) {
                    plan.withStrictFloatingPoint(); // no FMA contraction: Java's rounding
                }
                TornadoExecutionResult result = plan.execute(); // the first step compiles (and captures the CUDA graph)
                long start = System.nanoTime();
                for (int s = 1; s < steps; s++) {
                    result = plan.execute();
                }
                double ms = (System.nanoTime() - start) / 1e6 / (steps - 1);
                result.transferToHost(hat.of(universe));

                // The same steps in plain Java, on the reference copy.
                float[] e = new float[n];
                for (int s = 0; s < steps; s++) {
                    javaStep(reference, 1f, 0.005f);
                }
                double maxDiff = 0;
                for (int i = 0; i < n; i++) {
                    var b = reference.body(i);
                    e[i] = 0.5f * (b.vx() * b.vx() + b.vy() * b.vy() + b.vz() * b.vz());
                    maxDiff = Math.max(maxDiff, Math.abs(universe.body(i).x() - b.x()) / Math.max(1f, Math.abs(b.x())));
                    maxDiff = Math.max(maxDiff, Math.abs(energy.array(i) - e[i]) / Math.max(1f, Math.abs(e[i])));
                }
                String what = cudaGraph ? "with a CUDA graph" : "without a CUDA graph";
                System.out.printf("%s, %s: %d bodies, %d steps, %.3f ms a step after the first, max relative difference to Java %.2e -> %s%n",
                        device.getPhysicalDevice().getDeviceName(), what, n, steps, ms, maxDiff, maxDiff < 1e-3 ? "OK" : "WRONG");
            }
        }
    }

    private static void javaStep(Universe universe, float mass, float delT) {
        int n = universe.length();
        float[] ax = new float[n];
        float[] ay = new float[n];
        float[] az = new float[n];
        for (int j = 0; j < n; j++) {
            var body = universe.body(j);
            for (int i = 0; i < n; i++) {
                var other = universe.body(i);
                float dx = other.x() - body.x();
                float dy = other.y() - body.y();
                float dz = other.z() - body.z();
                float invDist = 1.0f / (float) Math.sqrt(dx * dx + dy * dy + dz * dz + 0.01f);
                float s = mass * invDist * invDist * invDist;
                ax[j] += s * dx;
                ay[j] += s * dy;
                az[j] += s * dz;
            }
        }
        for (int j = 0; j < n; j++) {
            var body = universe.body(j);
            body.vx(body.vx() + ax[j] * delT);
            body.vy(body.vy() + ay[j] * delT);
            body.vz(body.vz() + az[j] * delT);
            body.x(body.x() + body.vx() * delT);
            body.y(body.y() + body.vy() * delT);
            body.z(body.z() + body.vz() * delT);
        }
    }
}

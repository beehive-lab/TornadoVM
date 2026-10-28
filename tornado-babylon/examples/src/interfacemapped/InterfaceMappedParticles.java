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
package interfacemapped;

import java.lang.invoke.MethodHandles;

import jdk.incubator.code.Reflect;
import optkl.ifacemapper.Buffer;
import optkl.ifacemapper.Schema;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.babylon.hat.TornadoBuffers;

/**
 * Interface-mapped data in a plain TornadoVM program: the particles are a Java interface with a struct
 * per particle (no memory layout written by hand), allocated with TornadoBuffers and passed to the task
 * graph as they are. The kernels are TornadoVM kernels (KernelContext) that use the interface's
 * accessors; TC compiles them from their code models. No HAT Accelerator, no HatTasks.
 *
 * <pre>
 * ./run.sh -d 1:0 build/examples interfacemapped.InterfaceMappedParticles [particles] [steps]
 * </pre>
 */
public class InterfaceMappedParticles {

    /** The data: a length and that many particles of four floats. */
    public interface Particles extends Buffer {
        int length();

        interface Particle extends Struct {
            float x();
            float y();
            float vx();
            float vy();
            void x(float x);
            void y(float y);
            void vx(float vx);
            void vy(float vy);
        }

        Particle particle(long index);

        Schema<Particles> schema = Schema.of(Particles.class, p -> p.arrayLen("length").array("particle", q -> q.fields("x", "y", "vx", "vy")));
    }

    /** Gravity and a bounce off the floor (y = 0). */
    @Reflect
    public static void step(KernelContext context, Particles particles, float dt) {
        int i = context.globalIdx;
        if (i < particles.length()) {
            Particles.Particle p = particles.particle(i);
            float vy = p.vy() - 9.81f * dt;
            float y = p.y() + vy * dt;
            if (y < 0f) {
                y = -y;
                vy = -vy;
            }
            p.vy(vy);
            p.y(y);
            p.x(p.x() + p.vx() * dt);
        }
    }

    /** Each particle's kinetic energy, into a plain TornadoVM array. */
    @Reflect
    public static void energy(KernelContext context, Particles particles, FloatArray energy) {
        int i = context.globalIdx;
        if (i < particles.length()) {
            Particles.Particle p = particles.particle(i);
            energy.set(i, 0.5f * (p.vx() * p.vx() + p.vy() * p.vy()));
        }
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        float dt = 0.01f;
        Particles particles = TornadoBuffers.allocate(MethodHandles.lookup(), Particles.schema, n);
        float[][] java = new float[n][4];
        for (int i = 0; i < n; i++) {
            var p = particles.particle(i);
            p.x(i % 100);
            p.y(1 + i % 37);
            p.vx(0.5f * (i % 7));
            p.vy(0f);
            java[i] = new float[] { p.x(), p.y(), p.vx(), p.vy() };
        }
        FloatArray energy = new FloatArray(n);

        var graph = new TaskGraph("particles") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, particles) //
                .task("step", InterfaceMappedParticles::step, new KernelContext(), particles, dt) //
                .task("energy", InterfaceMappedParticles::energy, new KernelContext(), particles, energy) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, energy) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, particles);
        var grid = new GridScheduler("particles.step", new WorkerGrid1D(n));
        grid.addWorkerGrid("particles.energy", new WorkerGrid1D(n));
        String[] d = System.getProperty("hat.t.device", "0:0").split(":"); // run.sh -d
        try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice(TornadoExecutionPlan.getDevice(Integer.parseInt(d[0]), Integer.parseInt(d[1]))).withGridScheduler(grid);
            TornadoExecutionResult result = null;
            for (int s = 0; s < steps; s++) {
                result = plan.execute();
            }
            result.transferToHost(particles); // the interface-mapped buffer, as it is
        }

        // the same steps in Java
        for (int s = 0; s < steps; s++) {
            for (float[] p : java) {
                float vy = p[3] - 9.81f * dt;
                float y = p[1] + vy * dt;
                if (y < 0f) {
                    y = -y;
                    vy = -vy;
                }
                p[3] = vy;
                p[1] = y;
                p[0] = p[0] + p[2] * dt;
            }
        }
        double worst = 0;
        for (int i = 0; i < n; i++) {
            var p = particles.particle(i);
            float[] q = java[i];
            float e = 0.5f * (q[2] * q[2] + q[3] * q[3]);
            worst = Math.max(worst, Math.abs(p.x() - q[0]) / Math.max(1f, Math.abs(q[0])));
            worst = Math.max(worst, Math.abs(p.y() - q[1]) / Math.max(1f, Math.abs(q[1])));
            worst = Math.max(worst, Math.abs(p.vy() - q[3]) / Math.max(1f, Math.abs(q[3])));
            worst = Math.max(worst, Math.abs(energy.get(i) - e) / Math.max(1f, Math.abs(e)));
        }
        System.out.printf("%d particles, %d steps: max relative difference to Java %.1e -> %s%n", n, steps, worst, worst < 1e-4 ? "PASS" : "FAIL");
    }
}

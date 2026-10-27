/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.babylon.hat;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hat.ComputeContext;
import hat.KernelContext;
import hat.NDRange;
import hat.backend.Backend;
import optkl.ifacemapper.Buffer;
import hat.callgraph.KernelCallGraph;
import optkl.ifacemapper.MappableIface;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.WorkerGrid3D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.babylon.tc.HatLayouts;

/**
 * A HAT backend that runs kernels on TornadoVM: {@code -Dhat.backend=uk.ac.manchester.tornado.babylon.hat.TornadoHatBackend}
 * (or the first backend found). Compute methods run as plain Java, as in HAT's other backends; each
 * {@code dispatchKernel} becomes a TornadoVM task: TC compiles the kernel's code model through its
 * Babylon frontend for the TornadoVM device ({@code -Dtornado.backend}, {@code -Dtornado.device}),
 * the {@code NDRange} becomes the task's worker grid, and HAT buffers travel as {@code ByteArray}s
 * (copied in and out: TornadoVM arrays carry their own header, so a HAT segment is not wrapped in place).
 */
public class TornadoHatBackend extends Backend {

    /** {@code -Dtornado.hat.strictFP=true}: IEEE division/sqrt and no contraction (plan.withStrictFloatingPoint()). */
    private static final boolean STRICT_FP = Boolean.getBoolean("tornado.hat.strictFP");

    public TornadoHatBackend() {
        super(java.lang.foreign.Arena.global(), MethodHandles.lookup(), hat.Config.fromEnvOrProperty());
    }

    @Override
    public void computeContextHandoff(ComputeContext computeContext) {
    }

    @Override
    public void dispatchCompute(ComputeContext computeContext, Object... args) {
        try {
            var compute = computeContext.computeCallGraph().callDag.entryPoint.method();
            compute.setAccessible(true);
            compute.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispatchKernel(KernelCallGraph kernelCallGraph, KernelContext kernelContext, Object... args) {
        Method kernel = kernelCallGraph.callDag.entryPoint.method();
        var parameters = kernel.getParameterTypes();
        if (parameters.length != args.length) {
            throw new IllegalArgumentException(kernel + " takes " + parameters.length + " arguments, given " + args.length);
        }
        var key = key(kernel, kernelContext.ndRange, parameters, args);
        var dispatch = plans.computeIfAbsent(key, k -> prepare(kernel, kernelContext.ndRange, parameters, args));
        // HAT memory is the truth between dispatches: in before every launch, out after it.
        for (int i = 0; i < dispatch.buffers().size(); i++) {
            MemorySegment.copy(dispatch.segments().get(i), 0, dispatch.buffers().get(i).getSegment(), 0, dispatch.segments().get(i).byteSize());
        }
        try {
            dispatch.plan().execute();
        } catch (Exception e) {
            throw new RuntimeException("TornadoVM could not run HAT kernel " + kernel.getName() + ": " + e.getMessage(), e);
        }
        for (int i = 0; i < dispatch.buffers().size(); i++) {
            MemorySegment.copy(dispatch.buffers().get(i).getSegment(), 0, dispatch.segments().get(i), 0, dispatch.segments().get(i).byteSize());
        }
    }

    /** One execution plan per kernel, buffers, scalar values and range: compiled once, launched on every dispatch. */
    private record Dispatch(TornadoExecutionPlan plan, List<ByteArray> buffers, List<MemorySegment> segments) {
    }

    private final java.util.Map<List<Object>, Dispatch> plans = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<Object> key(Method kernel, NDRange range, Class<?>[] parameters, Object[] args) {
        var key = new ArrayList<Object>();
        key.add(kernel);
        key.add(range.toString());
        for (int i = 0; i < args.length; i++) {
            if (parameters[i] == KernelContext.class) {
                key.add("kc");
            } else if (args[i] instanceof Buffer buffer) {
                var segment = MappableIface.getMemorySegment(buffer);
                key.add(segment.address() + "+" + segment.byteSize());
            } else {
                key.add(args[i]);
            }
        }
        return key;
    }

    private Dispatch prepare(Method kernel, NDRange range, Class<?>[] parameters, Object[] args) {
        Object[] tornadoArgs = new Object[args.length];
        List<ByteArray> buffers = new ArrayList<>();
        List<MemorySegment> segments = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (parameters[i] == KernelContext.class) {
                tornadoArgs[i] = new uk.ac.manchester.tornado.api.KernelContext();
            } else if (a instanceof Buffer buffer) {
                var segment = MappableIface.getMemorySegment(buffer);
                HatLayouts.record(kernel, i, (GroupLayout) MappableIface.getLayout(buffer));
                var bytes = ByteArray.fromSegment(segment);
                tornadoArgs[i] = bytes;
                buffers.add(bytes);
                segments.add(segment);
            } else {
                tornadoArgs[i] = a;
            }
        }
        var graph = new TaskGraph("hat");
        if (!buffers.isEmpty()) {
            graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, buffers.toArray());
        }
        addTask(graph, kernel, tornadoArgs);
        if (!buffers.isEmpty()) {
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, buffers.toArray());
        }
        var plan = new TornadoExecutionPlan(graph.snapshot());
        plan.withGridScheduler(new GridScheduler("hat.t", workerGrid(range)));
        if (STRICT_FP) {
            plan.withStrictFloatingPoint();
        }
        return new Dispatch(plan, buffers, segments);
    }

    private static WorkerGrid workerGrid(NDRange range) {
        WorkerGrid grid = switch (range.global()) {
            case NDRange.Global1D g -> new WorkerGrid1D(g.x());
            case NDRange.Global2D g -> new WorkerGrid2D(g.x(), g.y());
            case NDRange.Global3D g -> new WorkerGrid3D(g.x(), g.y(), g.z());
            default -> throw new IllegalArgumentException("unknown NDRange " + range.global());
        };
        if (range.hasLocal()) {
            switch (range.local()) {
                case NDRange.Local1D l -> grid.setLocalWork(l.x(), 1, 1);
                case NDRange.Local2D l -> grid.setLocalWork(l.x(), l.y(), 1);
                case NDRange.Local3D l -> grid.setLocalWork(l.x(), l.y(), l.z());
                default -> {
                }
            }
        }
        return grid;
    }

    /**
     * {@code graph.task("t", <TaskN implemented by kernel>, args...)}: a serializable lambda made at
     * run time, which TornadoVM resolves to the kernel method as it does any method reference.
     */
    private static void addTask(TaskGraph graph, Method kernel, Object[] args) {
        int n = args.length;
        try {
            Class<?> taskInterface = Class.forName("uk.ac.manchester.tornado.api.common.TornadoFunctions$Task" + n);
            // Defined in the kernel's own class, so private kernels work too.
            var lookup = MethodHandles.privateLookupIn(kernel.getDeclaringClass(), MethodHandles.lookup());
            var target = lookup.unreflect(kernel);
            var erased = MethodType.genericMethodType(n).changeReturnType(void.class);
            var site = LambdaMetafactory.altMetafactory(lookup, "apply", MethodType.methodType(taskInterface), erased, target, target.type().wrap().changeReturnType(void.class), LambdaMetafactory.FLAG_SERIALIZABLE);
            Object task = site.getTarget().invoke();
            Method taskMethod = Arrays.stream(TaskGraph.class.getMethods()).filter(m -> m.getName().equals("task") && m.getParameterCount() == n + 2 && m.getParameterTypes()[1] == taskInterface).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("TaskGraph has no task method with " + n + " arguments"));
            Object[] call = new Object[n + 2];
            call[0] = "t";
            call[1] = task;
            System.arraycopy(args, 0, call, 2, n);
            taskMethod.invoke(graph, call);
        } catch (Throwable e) {
            throw new RuntimeException("cannot make a TornadoVM task for " + kernel + ": " + e, e);
        }
    }
}

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
 * over their memory in place (see {@link HeaderArena}).
 *
 * <p>Buffers stay on the device between dispatches. A buffer is uploaded only when the host has
 * written it since (HAT's buffer tracking, {@code preMutate}/{@code postMutate}, which this backend
 * always asks HAT to inject into compute methods) or when another kernel's plan holds the current
 * copy, and copied back when the host reads it ({@code preAccess}), when another plan needs it, or
 * when the compute method returns. With {@code HAT=MC} the last copy is skipped, as on HAT's own
 * backends: the program asks for results with {@code ComputeContext.copyOutOnExit}, and buffers
 * stay on the device across compute calls. {@code -Dtornado.hat.resident=false} copies every buffer
 * in and out of every dispatch instead.
 */
public class TornadoHatBackend extends Backend implements optkl.ifacemapper.BufferTracker {

    /** {@code -Dtornado.hat.strictFP=true}: IEEE division/sqrt and no contraction (plan.withStrictFloatingPoint()). */
    private static final boolean STRICT_FP = Boolean.getBoolean("tornado.hat.strictFP");

    /** {@code -Dtornado.hat.cudaGraph=true}: each kernel's plan launches through a CUDA graph (CUDA devices). */
    private static final boolean CUDA_GRAPH = Boolean.getBoolean("tornado.hat.cudaGraph");

    /** {@code -Dtornado.hat.resident=false}: copy every buffer to the device and back on every dispatch. */
    private static final boolean RESIDENT = !"false".equals(System.getProperty("tornado.hat.resident"));

    /** The program asked for HAT's minimised copies ({@code HAT=MC}): no copy back when a compute method returns. */
    private final boolean minimizeCopiesRequested;

    public TornadoHatBackend() {
        this(hat.Config.fromEnvOrProperty());
    }

    private TornadoHatBackend(hat.Config requested) {
        super(new HeaderArena(), MethodHandles.lookup(), requested);
        this.minimizeCopiesRequested = requested.minimizeCopies();
    }

    /** {@code -Dtornado.hat.zeroCopy=false}: copy HAT buffers into TornadoVM arrays on every dispatch instead. */
    private static final boolean ZERO_COPY = !"false".equals(System.getProperty("tornado.hat.zeroCopy"));

    @Override
    public void computeContextHandoff(ComputeContext computeContext) {
        // The compute method's buffer accesses tell this backend when to copy (see ComputeTracking).
        if (RESIDENT) {
            var entry = computeContext.computeCallGraph().callDag.entryPoint;
            entry.funcOp(ComputeTracking.inject(computeContext.lookup(), entry.funcOp()));
        }
    }

    @Override
    public void dispatchCompute(ComputeContext computeContext, Object... args) {
        if (!minimizeCopiesRequested) {
            // Between compute calls the program may write any buffer without HAT tracking it.
            synchronized (this) {
                residences.values().forEach(r -> r.device = null);
            }
        }
        computeContext.invokeWithArgs(args);
        if (!minimizeCopiesRequested) {
            synchronized (this) {
                residences.forEach((buffer, r) -> toHost(r));
            }
        }
    }

    // ---- where each HAT buffer's current contents are

    /** Where a HAT buffer's current contents are: on the host, in one plan's device memory, or both. */
    private static final class Residence {
        /** The plan whose device memory holds the current contents, or null. */
        Dispatch device;
        /** That plan's TornadoVM object for the buffer. */
        ByteArray object;
        /** That plan's last execution, to copy the buffer back with. */
        uk.ac.manchester.tornado.api.TornadoExecutionResult result;
        boolean hostCurrent = true;
        /** The members kernels may have written since the host was current; null: any of them. */
        java.util.Set<String> dirty = new java.util.HashSet<>();
    }

    private final java.util.Map<Object, Residence> residences = new java.util.IdentityHashMap<>();

    /** {@code -Dtornado.hat.traceResidency=true}: print every upload, copy back and host write this backend sees. */
    private static final boolean TRACE = Boolean.getBoolean("tornado.hat.traceResidency");

    private static void trace(String what, Object buffer) {
        if (TRACE) {
            System.err.println("[hat residency] " + what + " " + buffer.getClass().getInterfaces()[0].getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(buffer)));
        }
    }

    private static void toHost(Residence r) {
        if (!r.hostCurrent) {
            r.result.transferToHost(r.object);
            r.hostCurrent = true;
            r.dirty = new java.util.HashSet<>();
            if (TRACE) {
                System.err.println("[hat residency] copied back " + r.object.getSize() + " bytes");
            }
        }
    }

    @Override
    public synchronized void preMutate(MappableIface b) {
        var r = residences.get(b);
        if (r != null) {
            toHost(r);
        }
    }

    @Override
    public synchronized void postMutate(MappableIface b) {
        trace("host wrote", b);
        var r = residences.get(b);
        if (r != null) {
            r.device = null; // the host wrote it
        }
    }

    /** {@code -Dtornado.hat.memberCopies=false}: copy a whole buffer back for any host read. */
    private static final boolean MEMBER_COPIES = !"false".equals(System.getProperty("tornado.hat.memberCopies"));

    /** A host read of one member: only that member's bytes come back when it is a scalar. */
    synchronized void preAccessMember(MappableIface b, String member, boolean indexed) {
        var r = residences.get(b);
        if (r == null || r.hostCurrent) {
            return;
        }
        if (MEMBER_COPIES && r.dirty != null && !r.dirty.contains(member)) {
            return; // no kernel wrote this member since the host had it
        }
        long[] range = indexed || !MEMBER_COPIES ? null : ComputeTracking.scalarMember(b, member);
        if (range == null) {
            toHost(r);
            return;
        }
        r.result.transferToHost(new uk.ac.manchester.tornado.api.DataRange(r.object).withOffset(range[0]).withSize(range[1]));
        if (TRACE) {
            System.err.println("[hat residency] copied back " + member + " (" + range[1] + " bytes)");
        }
    }

    @Override
    public synchronized void preAccess(MappableIface b) {
        var r = residences.get(b);
        if (r != null) {
            toHost(r);
        }
    }

    @Override
    public void postAccess(MappableIface b) {
    }

    /**
     * {@code dispatchContextAndArgs[0]} is HAT's dispatch context (the launch shape, which TornadoVM
     * takes from the worker grid instead); the rest are the kernel's arguments.
     */
    @Override
    public void dispatchKernel(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... dispatchContextAndArgs) {
        dispatch(kernelCallGraph, ndRange, false, dispatchContextAndArgs);
    }

    private void dispatch(KernelCallGraph kernelCallGraph, NDRange ndRange, boolean tile, Object... dispatchContextAndArgs) {
        Method kernel = kernelCallGraph.callDag.entryPoint.method();
        Object[] args = Arrays.copyOfRange(dispatchContextAndArgs, 1, dispatchContextAndArgs.length);
        var parameters = kernel.getParameterTypes();
        if (parameters.length != args.length) {
            throw new IllegalArgumentException(kernel + " takes " + parameters.length + " arguments, given " + args.length);
        }
        var event = new uk.ac.manchester.tornado.babylon.Events.Dispatch();
        event.begin();
        var key = key(kernel, ndRange, args);
        key.add(tile);
        boolean[] built = new boolean[1];
        var dispatch = plans.computeIfAbsent(key, k -> {
            built[0] = true;
            return prepare(kernel, ndRange, args, tile);
        });
        if (dispatch.resident()) {
            launchResident(kernel, dispatch, args);
        } else {
            // HAT memory is the truth between dispatches: in before every launch, out after it.
            for (int i = 0; i < dispatch.buffers().size(); i++) {
                if (dispatch.segments().get(i) != null) {
                    MemorySegment.copy(dispatch.segments().get(i), 0, dispatch.buffers().get(i).getSegment(), 0, dispatch.segments().get(i).byteSize());
                }
            }
            launch(kernel, dispatch);
            for (int i = 0; i < dispatch.buffers().size(); i++) {
                if (dispatch.segments().get(i) != null) {
                    MemorySegment.copy(dispatch.buffers().get(i).getSegment(), 0, dispatch.segments().get(i), 0, dispatch.segments().get(i).byteSize());
                }
            }
        }
        event.end();
        if (event.shouldCommit()) {
            event.kernel = kernel.getDeclaringClass().getSimpleName() + "." + kernel.getName();
            event.range = String.valueOf(ndRange);
            event.tile = tile;
            event.newPlan = built[0];
            event.commit();
        }
    }

    private static uk.ac.manchester.tornado.api.TornadoExecutionResult launch(Method kernel, Dispatch dispatch) {
        try {
            var result = dispatch.plan().execute();
            dispatch.executed[0] = true;
            return result;
        } catch (Exception e) {
            throw new RuntimeException("TornadoVM could not run HAT kernel " + kernel.getName() + ": " + e.getMessage(), e);
        }
    }

    /** A launch whose buffers stay on the device: uploads only what this plan does not hold current. */
    private synchronized void launchResident(Method kernel, Dispatch dispatch, Object[] args) {
        int b = 0;
        for (Object arg : args) {
            if (arg instanceof Buffer buffer) {
                var object = dispatch.buffers().get(b++);
                var r = residences.computeIfAbsent(buffer, k -> new Residence());
                if (r.device == dispatch) {
                    continue; // already current in this plan's device memory
                }
                toHost(r); // from another plan's device memory, if that is where it is current
                if (dispatch.executed[0]) {
                    dispatch.plan().transferToDevice(object); // the first execution uploads every buffer itself
                    trace("uploaded", buffer);
                }
            }
        }
        var result = launch(kernel, dispatch);
        boolean[] written = dispatch.written();
        b = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Buffer buffer) {
                var r = residences.get(buffer);
                r.device = dispatch;
                r.object = dispatch.buffers().get(b++);
                if (written[i]) {
                    r.hostCurrent = false;
                    r.result = result;
                    var members = uk.ac.manchester.tornado.babylon.tc.HatWrites.written(kernel, i);
                    if (members == null || r.dirty == null) {
                        r.dirty = null;
                    } else {
                        r.dirty.addAll(members);
                    }
                }
            }
        }
    }

    /** Which parameters a kernel may write, from TC's access analysis (all of them when it cannot tell). */
    private static boolean[] written(Method kernel) {
        var written = new boolean[kernel.getParameterCount()];
        java.util.Arrays.fill(written, true);
        try {
            var access = uk.ac.manchester.tornado.runtime.common.KernelAccess.of(kernel, written.length);
            for (int i = 0; i < written.length && access != null && i < access.length; i++) {
                written[i] = access[i] != uk.ac.manchester.tornado.api.common.Access.READ_ONLY && access[i] != uk.ac.manchester.tornado.api.common.Access.NONE;
            }
        } catch (RuntimeException | LinkageError e) {
            // unknown: assume written
        }
        return written;
    }

    /**
     * A HAT tile kernel: one CUDA Tile block per tile ({@code ceil(global / tile)} along each
     * dimension), with the 1x1x1 block shape CUDA Tile requires. TC compiles it to CUDA Tile C++,
     * so it runs on the CUDA backend only.
     */
    @Override
    public void dispatchTile(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... dispatchContextAndArgs) {
        dispatch(kernelCallGraph, ndRange, true, dispatchContextAndArgs);
    }

    /** One execution plan per kernel, buffers, scalar values and range: compiled once, launched on every dispatch. */
    private record Dispatch(TornadoExecutionPlan plan, List<ByteArray> buffers, List<MemorySegment> segments, boolean resident, boolean[] written, boolean[] executed) {
    }

    private final java.util.Map<List<Object>, Dispatch> plans = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<Object> key(Method kernel, NDRange range, Object[] args) {
        var key = new ArrayList<Object>();
        key.add(kernel);
        key.add(range.toString());
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Buffer buffer) {
                var segment = MappableIface.getMemorySegment(buffer);
                key.add(segment.address() + "+" + segment.byteSize());
            } else {
                key.add(args[i]);
            }
        }
        return key;
    }

    private Dispatch prepare(Method kernel, NDRange range, Object[] args, boolean tile) {
        Object[] tornadoArgs = new Object[args.length];
        List<ByteArray> buffers = new ArrayList<>();
        List<MemorySegment> segments = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Buffer buffer) {
                var segment = MappableIface.getMemorySegment(buffer);
                HatLayouts.record(kernel, i, (GroupLayout) MappableIface.getLayout(buffer));
                var inPlace = ZERO_COPY && arena() instanceof HeaderArena headers ? headers.tornadoSegment(segment) : java.util.Optional.<MemorySegment>empty();
                var bytes = inPlace.isPresent() ? ByteArray.fromSegmentShallow(inPlace.get()) : ByteArray.fromSegment(segment);
                tornadoArgs[i] = bytes;
                buffers.add(bytes);
                // wrapped in place: nothing to copy around the launch
                segments.add(inPlace.isPresent() ? null : segment);
            } else {
                tornadoArgs[i] = a;
            }
        }
        // Resident buffers (all in place): uploaded by the first execution, then only when the host or
        // another plan has newer contents; copied back on demand (launchResident, the tracking hooks).
        boolean resident = RESIDENT && segments.stream().allMatch(java.util.Objects::isNull);
        var graph = new TaskGraph("hat");
        if (!buffers.isEmpty()) {
            graph.transferToDevice(resident ? DataTransferMode.FIRST_EXECUTION : DataTransferMode.EVERY_EXECUTION, buffers.toArray());
        }
        addTask(graph, "t", kernel, tornadoArgs);
        if (!buffers.isEmpty()) {
            graph.transferToHost(resident ? DataTransferMode.UNDER_DEMAND : DataTransferMode.EVERY_EXECUTION, buffers.toArray());
        }
        var plan = new TornadoExecutionPlan(graph.snapshot());
        // The plan's device, not only the task's (-Dhat.t.device): on-demand copies back use the plan's.
        String device = System.getProperty("hat.t.device");
        if (device != null && device.matches("\\d+:\\d+")) {
            String[] d = device.split(":");
            plan.withDevice(TornadoExecutionPlan.getDevice(Integer.parseInt(d[0]), Integer.parseInt(d[1])));
        }
        plan.withGridScheduler(new GridScheduler("hat.t", tile ? tileGrid(range) : workerGrid(range)));
        if (STRICT_FP) {
            plan.withStrictFloatingPoint();
        }
        if (CUDA_GRAPH) {
            plan.withCUDAGraph();
        }
        return new Dispatch(plan, buffers, segments, resident, resident ? written(kernel) : null, new boolean[1]);
    }

    private static WorkerGrid tileGrid(NDRange range) {
        // As HAT's own backends: one block per local (or tile) extent along each dimension; with a
        // warp flag on X the blocks are numbered along X only (a kernel swizzles BIDX() itself).
        int[] global = extents(range.global());
        int[] per = range.hasLocal() ? extents(range.local()) : extents(range.tile());
        int[] blocks = new int[global.length];
        for (int d = 0; d < global.length; d++) {
            blocks[d] = ceil(global[d], per[d]);
        }
        if (range.hasWarp() && range.warp() instanceof NDRange.Warp2D w && w.x() || range.hasWarp() && range.warp() instanceof NDRange.Warp3D w3 && w3.x()) {
            int all = 1;
            for (int b : blocks) {
                all *= b;
            }
            blocks = new int[] { all };
        }
        WorkerGrid tiles = switch (blocks.length) {
            case 1 -> new WorkerGrid1D(blocks[0]);
            case 2 -> new WorkerGrid2D(blocks[0], blocks[1]);
            default -> new WorkerGrid3D(blocks[0], blocks[1], blocks[2]);
        };
        tiles.setLocalWork(1, 1, 1);
        return tiles;
    }

    /**
     * A kernel's launch: the global range, or, with a tile size (warp-level kernels such as HAT's
     * Tensor ones), one warp per tile on the axes with a warp flag, as HAT's own backends size it.
     */
    private static WorkerGrid workerGrid(NDRange range) {
        if (range.hasTile()) {
            int[] global = extents(range.global());
            int[] tile = extents(range.tile());
            boolean[] warp = warpFlags(range, global.length);
            int[] threads = new int[global.length];
            for (int d = 0; d < global.length; d++) {
                threads[d] = ceil(global[d], tile[d]) * (warp[d] ? 32 : 1);
            }
            WorkerGrid grid = switch (threads.length) {
                case 1 -> new WorkerGrid1D(threads[0]);
                case 2 -> new WorkerGrid2D(threads[0], threads[1]);
                default -> new WorkerGrid3D(threads[0], threads[1], threads[2]);
            };
            if (range.hasLocal()) {
                int[] local = extents(range.local());
                grid.setLocalWork(local[0], local.length > 1 ? local[1] : 1, local.length > 2 ? local[2] : 1);
            }
            return grid;
        }
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

    private static boolean[] warpFlags(NDRange range, int rank) {
        var flags = new boolean[rank];
        if (range.hasWarp()) {
            switch (range.warp()) {
                case NDRange.Warp1D w -> flags[0] = w.x();
                case NDRange.Warp2D w -> {
                    flags[0] = w.x();
                    flags[1] = w.y();
                }
                case NDRange.Warp3D w -> {
                    flags[0] = w.x();
                    flags[1] = w.y();
                    flags[2] = w.z();
                }
                default -> {
                }
            }
        }
        return flags;
    }

    private static int[] extents(Object dims) {
        return switch (dims) {
            case NDRange.Global1D g -> new int[] { g.x() };
            case NDRange.Global2D g -> new int[] { g.x(), g.y() };
            case NDRange.Global3D g -> new int[] { g.x(), g.y(), g.z() };
            case NDRange.Local1D l -> new int[] { l.x() };
            case NDRange.Local2D l -> new int[] { l.x(), l.y() };
            case NDRange.Local3D l -> new int[] { l.x(), l.y(), l.z() };
            case NDRange.Tile1D t -> new int[] { t.x() };
            case NDRange.Tile2D t -> new int[] { t.x(), t.y() };
            case NDRange.Tile3D t -> new int[] { t.x(), t.y(), t.z() };
            default -> throw new IllegalArgumentException("unknown range " + dims);
        };
    }

    private static int ceil(int n, int d) {
        return (n + d - 1) / d;
    }

    /**
     * {@code graph.task("t", <TaskN implemented by kernel>, args...)}: a serializable lambda made at
     * run time, which TornadoVM resolves to the kernel method as it does any method reference.
     */
    static void addTask(TaskGraph graph, String id, Method kernel, Object[] args) {
        int n = args.length;
        try {
            Class<?> taskInterface = Class.forName("uk.ac.manchester.tornado.api.common.TornadoFunctions$Task" + n);
            // Defined in the kernel's own class, so private kernels work too.
            var lookup = MethodHandles.privateLookupIn(kernel.getDeclaringClass(), MethodHandles.lookup());
            var target = lookup.unreflect(kernel);
            var erased = MethodType.genericMethodType(n).changeReturnType(void.class);
            var site = LambdaMetafactory.altMetafactory(lookup, "apply", MethodType.methodType(taskInterface), erased, target, target.type().wrap().changeReturnType(void.class),
                    LambdaMetafactory.FLAG_SERIALIZABLE);
            Object task = site.getTarget().invoke();
            Method taskMethod = Arrays.stream(TaskGraph.class.getMethods())
                    .filter(m -> m.getName().equals("task") && m.getParameterCount() == n + 2 && m.getParameterTypes()[1] == taskInterface).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("TaskGraph has no task method with " + n + " arguments"));
            Object[] call = new Object[n + 2];
            call[0] = id;
            call[1] = task;
            System.arraycopy(args, 0, call, 2, n);
            taskMethod.invoke(graph, call);
        } catch (Throwable e) {
            throw new RuntimeException("cannot make a TornadoVM task for " + kernel + ": " + e, e);
        }
    }
}

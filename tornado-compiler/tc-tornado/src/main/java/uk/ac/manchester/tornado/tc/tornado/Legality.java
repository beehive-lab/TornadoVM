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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Stream;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * What the target cannot run, refused before code is generated, from {@link TargetCaps} (the
 * driver's device queries). Each check names the construct and the limit:
 * <ul>
 * <li>TC-E4012: double precision on a device without it;</li>
 * <li>TC-E4013: half precision on a device without it (not on OpenCL, whose compilers take half
 * with the pragma);</li>
 * <li>TC-E4015: warp operations, written for 32-lane warps, on another SIMD width;</li>
 * <li>TC-E4016: a device launch where kernels cannot launch kernels;</li>
 * <li>TC-E4017: tensor-core MMA or {@code cp.async} below CUDA sm_80, or MMA on an AMD GPU without matrix cores;</li>
 * <li>TC-E4018: FP8 tensor-core MMA below sm_89, or with an NVRTC older than 12.4 (PTX ISA 8.4);</li>
 * <li>TC-E4019: on a 64-lane HIP wavefront, a loop that waits for a local-memory write from its own
 * wavefront ({@link #localWait});</li>
 * <li>TC-E4010, TC-E4011: CUDA Tile kernels ({@link TileTyping#checkTarget}).</li>
 * </ul>
 */
public final class Legality {

    private Legality() {
    }

    public static List<Diagnostic> run(Function function, TargetCaps target) {
        var problems = new ArrayList<Diagnostic>();
        if (!target.fp64()) {
            first(function, t -> uses(t, Type.F64)).ifPresent(i -> problems.add(at(function, i, "TC-E4012", "this kernel uses double precision, which the device does not support")));
        }
        // OpenCL compilers accept half, scalars and vectors, with the cl_khr_fp16 pragma even where the
        // extension is not advertised (the RTX 4090's driver), which the Graal path relies on too.
        if (!target.fp16() && target.dialect() != TargetCaps.Dialect.OPENCL) {
            first(function, t -> uses(t, Type.F16)).ifPresent(i -> problems.add(at(function, i, "TC-E4013", "this kernel uses half precision, which the device does not support")));
        }
        // Local memory is not checked here: the driver is the authority, and its limit is not the one the
        // device reports (CUDA takes up to the opt-in size, 99 KB on sm_89, against 48 KB reported;
        // NVIDIA's OpenCL adds its own bytes; Metal's pipeline aligns). It refuses with the reason.
        if (!target.warps32()) {
            insts(function).filter(i -> intrinsic(i, "tornado.simd.")).findFirst()
                    .ifPresent(i -> problems.add(at(function, i, "TC-E4015", "warp operations are written for 32-lane warps; the device's SIMD width is " + target.warpSize())));
        }
        if (!target.deviceLaunch()) {
            insts(function).filter(i -> intrinsic(i, "tornado.device_launch")).findFirst()
                    .ifPresent(i -> problems.add(at(function, i, "TC-E4016", "KernelContext.launch needs kernels that launch kernels (CUDA dynamic parallelism), which this target lacks")));
        }
        if (target.dialect() == TargetCaps.Dialect.CUDA) {
            var mma = insts(function).filter(i -> intrinsic(i, "tornado.mma.") || intrinsic(i, "tornado.async_copy")).findFirst();
            var fp8 = insts(function).filter(i -> intrinsic(i, "tornado.mma.m16n8k32_e4m3") || intrinsic(i, "tornado.mma.m16n8k32_e5m2")).findFirst();
            if (!target.mma() && mma.isPresent()) {
                problems.add(at(function, mma.get(), "TC-E4017", "tensor-core MMA and cp.async need compute capability 8.0 or higher; the device is sm_" + target.computeCapability()));
            } else if (!target.fp8Mma() && fp8.isPresent()) {
                problems.add(at(function, fp8.get(), "TC-E4018", target.computeCapability() < 89
                        ? "FP8 tensor-core MMA needs compute capability 8.9 or higher; the device is sm_" + target.computeCapability()
                        : "FP8 tensor-core MMA needs PTX ISA 8.4 (NVRTC 12.4 or newer); NVRTC is " + target.nvrtcVersion() / 1000 + "." + target.nvrtcVersion() % 1000));
            }
        }
        // HIP copies asynchronously-staged tiles synchronously, which any AMD GPU can do; MMA needs matrix cores.
        if (target.dialect() == TargetCaps.Dialect.HIP && !target.mma()) {
            insts(function).filter(i -> intrinsic(i, "tornado.mma.")).findFirst()
                    .ifPresent(i -> problems.add(at(function, i, "TC-E4017", hipNoMatrixCores(target))));
        }
        if (target.dialect() == TargetCaps.Dialect.HIP && target.warpSize() == 64) {
            localWait(function, target.maxWorkGroupSize(), target.warpSize()).ifPresent(i -> problems.add(at(function, i, "TC-E4019",
                    "this loop waits for a local-memory value that only threads of the waiting thread's 64-lane wavefront write; the wavefront runs its two 32-lane warps in lockstep, so the wait can hang before the write runs")));
        }
        insts(function).filter(i -> intrinsic(i, "tornado.vector.lane_at") || intrinsic(i, "tornado.vector.insert_at")).findFirst()
                .ifPresent(i -> problems.add(at(function, i, "TC-E1010", "vector lanes can only be accessed at a constant index (a loop over lanes must have a constant trip count to unroll)")));
                if (TileTyping.usesTiles(function)) {
            if (target.dialect() != TargetCaps.Dialect.CUDA) {
                problems.add(new Diagnostic("TC-E4010", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend", "@" + function.name(), Loc.UNKNOWN));
            } else {
                problems.addAll(TileTyping.checkTarget(function, target.computeCapability()));
            }
        }
        return problems;
    }

    private static String hipNoMatrixCores(TargetCaps target) {
        return "tensor-core MMA needs AMD matrix cores (MFMA on CDNA, WMMA on RDNA3, RDNA4 or gfx1250); the device is " + (target.arch().isEmpty() ? "an AMD GPU without them" : target.arch());
    }

    /**
     * The read of a loop in which a thread can wait for a local-memory write that only threads of its own
     * wavefront make. The lanes of a wavefront do not make independent progress, so the loop can spin
     * before the write runs. The loop carries no value from one iteration to the next, nothing in it writes
     * memory or synchronises, and one local-memory read decides every exit. The kernel is refused only when
     * the wait is certain to be of that kind ({@link #sameWavefrontWait}). A wait on global memory is not
     * reported: its writer may be another work-group, which does progress.
     */
    static java.util.Optional<Inst> localWait(Function function, int maxThreads, int wavefront) {
        var dominators = new DominatorTree(function);
        var loops = new LoopForest(function, dominators);
        for (var header : function.blocks()) {
            var loop = loops.loopWithHeader(header).orElse(null);
            if (loop == null || !header.args().isEmpty()) {
                continue;
            }
            var exits = new ArrayList<Inst>();
            var waits = true;
            for (var block : function.blocks()) {
                if (!loop.contains(block)) {
                    continue;
                }
                for (var inst : block.insts()) {
                    if (inst.isTerminator()) {
                        if (inst.successors().stream().anyMatch(s -> !loop.contains(s.target()))) {
                            exits.add(inst);
                        }
                    } else if (!inst.opcode().isPure() && !(inst.opcode() instanceof Opcode.Load) && !(inst.opcode() instanceof Opcode.ArrayLoad) && !readOnlyCas(inst)) {
                        waits = false;
                    }
                }
            }
            if (!waits || exits.isEmpty()) {
                continue;
            }
            Inst read = null;
            var single = true;
            for (var exit : exits) {
                var r = exit.opcode() instanceof Opcode.CondBranch ? localRead(exit.operand(0), loop) : null;
                single &= r != null && (read == null || r == read);
                read = r;
            }
            if (single && sameWavefrontWait(function, dominators, loop, exits, read, maxThreads > 0 ? maxThreads : 1024, wavefront)) {
                return java.util.Optional.of(read);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Whether the wait certainly involves only one wavefront, for every work-group that holds the threads
     * involved. Writes to the array outside the loop that cannot end the wait (a constant the exits do not
     * accept, or a compare-and-swap storing its expected value) are ignored. A write that can end it and
     * runs before the loop (in the waiting thread, or before a point where all threads meet again ahead of
     * the loop, as at a barrier) means the loop may end at once: not refused. The remaining writes are
     * made after the waiter has started, and every thread that can make one must lie in one wavefront,
     * which a thread certainly entering the loop shares.
     *
     * <p>Threads are told apart by evaluating the conditions that depend only on {@code localIdx}; thread
     * {@code x} of a one-dimensional work-group is lane {@code x % wavefront} of wavefront
     * {@code x / wavefront}. A kernel that uses another dimension is not refused: the threads of other
     * rows, in other wavefronts, may write too.
     *
     * <p>The kernel is compiled without its launch shape, so two launches of a refused kernel complete:
     * a kernel that uses only {@code localIdx} launched with a 2D or 3D work-group, where a writer in
     * another row, in a wavefront without waiters, releases the waiters (rows of 56 threads, writer
     * {@code localIdx == 40}, waiters {@code localIdx < 8}); and a work-group too small for any thread to
     * reach the wait.
     */
    private static boolean sameWavefrontWait(Function function, DominatorTree dominators, LoopForest.Loop loop, List<Inst> exits, Inst read, int threads, int wavefront) {
        if (insts(function).anyMatch(i -> i.opcode() instanceof Opcode.Intrinsic && i.immediates().integer("dim").orElse(0L) != 0)) {
            return false;
        }
        var array = root(read.operand(0));
        var writers = new ArrayList<Block>();
        for (var block : function.blocks()) {
            if (loop.contains(block)) {
                continue;
            }
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Call && inst.operands().stream().anyMatch(o -> root(o) == array)) {
                    return false;
                }
                if (!(inst.opcode() instanceof Opcode.Store || intrinsic(inst, "tornado.atomic.")) || inst.operandCount() == 0 || root(inst.operand(0)) != array || readOnlyCas(inst)) {
                    continue;
                }
                var value = writtenConstant(inst);
                if (value != null && !endsWait(loop, exits, read, value)) {
                    continue;
                }
                if (runsBefore(block, loop.header(), dominators)) {
                    return false;
                }
                writers.add(block);
            }
        }
        if (writers.isEmpty()) {
            return false;
        }
        var entered = new boolean[threads];
        var certain = new boolean[threads];
        var writes = new boolean[threads];
        for (int x = 0; x < threads; x++) {
            var thread = new Lane(new IdentityHashMap<>(), x);
            var reached = reachable(function.entry(), null, thread);
            entered[x] = reached.contains(loop.header());
            certain[x] = entered[x] && reachable(function.entry(), loop.header(), thread).stream().noneMatch(Legality::exitBlock);
            writes[x] = writers.stream().anyMatch(reached::contains);
        }
        int firstWriter = first(writes, 0, threads);
        int firstEntered = first(entered, 0, threads);
        int firstCertain = first(certain, 0, threads);
        if (firstWriter < 0 || firstCertain < 0) {
            return false;
        }
        int group = firstWriter / wavefront * wavefront;
        if (first(writes, group + wavefront, threads) >= 0) {
            return false;
        }
        int waiter = first(certain, group, Math.min(group + wavefront, threads));
        // In a work-group of n threads where some thread may wait (n > firstEntered): while n leaves out
        // every writer, a thread that certainly waits must be there (it waits forever on any GPU); once n
        // holds a writer, the waiter of the writers' wavefront must be there.
        return waiter >= 0 && waiter <= Math.max(firstEntered, firstWriter) && (firstWriter <= firstEntered || firstCertain == firstEntered);
    }

    private static int first(boolean[] set, int from, int to) {
        for (int i = from; i < to; i++) {
            if (set[i]) {
                return i;
            }
        }
        return -1;
    }

    /** The constant a store, exchange or compare-and-swap writes, or null. */
    private static Long writtenConstant(Inst inst) {
        Value value = null;
        if (inst.opcode() instanceof Opcode.Store || intrinsic(inst, "tornado.atomic.exchange")) {
            value = inst.operandCount() == 2 ? inst.operand(1) : null;
        } else if (intrinsic(inst, "tornado.atomic.cas")) {
            value = inst.operandCount() == 3 ? inst.operand(2) : null;
        }
        return value instanceof Inst i && i.opcode() instanceof Opcode.ConstInt(var c) ? c : null;
    }

    /** Whether some exit of the loop may be taken when {@code read} gives {@code value}, for any thread. */
    private static boolean endsWait(LoopForest.Loop loop, List<Inst> exits, Inst read, long value) {
        var known = new IdentityHashMap<Value, Long>();
        known.put(read, value);
        var thread = new Lane(known, -1);
        for (var exit : exits) {
            var condition = thread.eval(exit.operand(0));
            if (condition == null || !loop.contains(exit.successors().get(condition != 0 ? 0 : 1).target())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a write in {@code block} runs before the loop at {@code header} starts: some block
     * dominating the header (the header included) is on every path from the write to the kernel's end,
     * so the threads that write and the threads that wait meet there first.
     */
    private static boolean runsBefore(Block block, Block header, DominatorTree dominators) {
        for (var meet = header; meet != null; meet = dominators.immediateDominator(meet).orElse(null)) {
            if (meet == block || reachable(block, meet, null).stream().noneMatch(Legality::exitBlock)) {
                return true;
            }
        }
        return false;
    }

    private static boolean exitBlock(Block block) {
        return block.terminator().map(t -> t.opcode() instanceof Opcode.Return || t.opcode() instanceof Opcode.Unreachable).orElse(true);
    }

    /** Blocks reachable from {@code from} without entering {@code avoid}, along the branches {@code thread} can take (all of them when null). */
    private static java.util.Set<Block> reachable(Block from, Block avoid, Lane thread) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        var work = new ArrayDeque<Block>();
        seen.add(from);
        work.push(from);
        while (!work.isEmpty()) {
            for (var next : taken(work.pop(), thread)) {
                if (next != avoid && seen.add(next)) {
                    work.push(next);
                }
            }
        }
        return seen;
    }

    private static List<Block> taken(Block block, Lane thread) {
        var terminator = block.terminator().orElse(null);
        if (terminator == null) {
            return List.of();
        }
        var targets = terminator.successors().stream().map(s -> s.target()).toList();
        var key = thread == null || targets.size() < 2 ? null : thread.eval(terminator.operand(0));
        if (key == null) {
            return targets;
        }
        if (terminator.opcode() instanceof Opcode.Switch(var keys)) {
            int i = keys.indexOf(key);
            return List.of(targets.get(i < 0 ? targets.size() - 1 : i));
        }
        return List.of(targets.get(key != 0 ? 0 : 1));
    }

    /**
     * The integer values one thread computes: thread {@code x} ({@code localIdx}) of the work-group, or any
     * thread when {@code x} is negative. {@code known} binds values (such as a read) in advance; null
     * where the value is not known.
     */
    private record Lane(java.util.Map<Value, Long> known, long x) {

        Long eval(Value value) {
            if (known.containsKey(value)) {
                return known.get(value);
            }
            var result = value instanceof Inst inst && inst.type() instanceof Type.IntType(var bits) ? compute(inst, bits) : null;
            known.put(value, result);
            return result;
        }

        private Long compute(Inst inst, int bits) {
            Long result = switch (inst.opcode()) {
                case Opcode.ConstInt(var c) -> Long.valueOf(c);
                case Opcode.Intrinsic(var name) when name.equals("tornado.thread_id.local") && x >= 0 && inst.immediates().integer("dim").orElse(-1L) == 0 -> Long.valueOf(x);
                case Opcode.Convert(var op) when op == Opcode.ConvertOp.SEXT || op == Opcode.ConvertOp.TRUNC -> eval(inst.operand(0));
                case Opcode.Convert(var op) when op == Opcode.ConvertOp.ZEXT -> unsigned(eval(inst.operand(0)), inst.operand(0).type());
                case Opcode.Unary(var op) when op == Opcode.UnaryOp.NEG || op == Opcode.UnaryOp.NOT -> {
                    var a = eval(inst.operand(0));
                    yield a == null ? null : Long.valueOf(op == Opcode.UnaryOp.NEG ? -a : ~a);
                }
                case Opcode.Binary(var op) when !op.isFloat -> binary(op, eval(inst.operand(0)), eval(inst.operand(1)), inst.operand(0).type(), bits);
                case Opcode.ICmp(var predicate) -> compare(predicate, eval(inst.operand(0)), eval(inst.operand(1)), inst.operand(0).type());
                case Opcode.Select() -> {
                    var condition = eval(inst.operand(0));
                    yield condition == null ? null : eval(inst.operand(condition != 0 ? 1 : 2));
                }
                default -> null;
            };
            if (result == null || bits == 64) {
                return result;
            }
            return result << (64 - bits) >> (64 - bits);
        }

        private static Long unsigned(Long value, Type type) {
            return value == null || !(type instanceof Type.IntType(var bits)) ? null : bits == 64 ? value : value & ((1L << bits) - 1);
        }

        private static Long binary(Opcode.BinaryOp op, Long a, Long b, Type type, int bits) {
            if (a == null || b == null || (op == Opcode.BinaryOp.DIV || op == Opcode.BinaryOp.REM) && b == 0) {
                return null;
            }
            int shift = (int) (b & (bits > 32 ? 63 : 31));
            return switch (op) {
                case ADD -> a + b;
                case SUB -> a - b;
                case MUL -> a * b;
                case DIV -> a / b;
                case REM -> a % b;
                case AND -> a & b;
                case OR -> a | b;
                case XOR -> a ^ b;
                case SHL -> a << shift;
                case SHR -> a >> shift;
                case USHR -> unsigned(a, type) >>> shift;
                default -> throw new IllegalArgumentException(op.text);
            };
        }

        private static Long compare(Opcode.IntPredicate predicate, Long a, Long b, Type type) {
            if (a == null || b == null) {
                return null;
            }
            var ua = unsigned(a, type);
            var ub = unsigned(b, type);
            var holds = switch (predicate) {
                case EQ -> a.longValue() == b.longValue();
                case NE -> a.longValue() != b.longValue();
                case SLT -> a < b;
                case SLE -> a <= b;
                case SGT -> a > b;
                case SGE -> a >= b;
                case ULT -> Long.compareUnsigned(ua, ub) < 0;
                case ULE -> Long.compareUnsigned(ua, ub) <= 0;
                case UGT -> Long.compareUnsigned(ua, ub) > 0;
                case UGE -> Long.compareUnsigned(ua, ub) >= 0;
            };
            return holds ? 1L : 0L;
        }
    }

    /** A compare-and-swap that stores the value it expects: a read of the location. */
    private static boolean readOnlyCas(Inst inst) {
        return intrinsic(inst, "tornado.atomic.cas") && inst.operandCount() == 3 && sameValue(inst.operand(1), inst.operand(2));
    }

    private static boolean sameValue(Value a, Value b) {
        if (a == b) {
            return true;
        }
        return a instanceof Inst x && b instanceof Inst y && x.opcode() instanceof Opcode.ConstInt(var u) && y.opcode() instanceof Opcode.ConstInt(var v) && u == v;
    }

    /** A local-memory read in the loop that {@code value} depends on through pure instructions, or null. */
    private static Inst localRead(Value value, LoopForest.Loop loop) {
        if (!(value instanceof Inst inst) || inst.block().map(b -> !loop.contains(b)).orElse(true)) {
            return null;
        }
        if ((inst.opcode() instanceof Opcode.Load || readOnlyCas(inst)) && inst.operand(0).type() instanceof Type.PointerType(var space) && space == AddressSpace.SHARED) {
            return inst;
        }
        if (!inst.opcode().isPure()) {
            return null;
        }
        for (var operand : inst.operands()) {
            var read = localRead(operand, loop);
            if (read != null) {
                return read;
            }
        }
        return null;
    }

    private static Value root(Value pointer) {
        while (pointer instanceof Inst inst && inst.opcode() instanceof Opcode.PointerOffset) {
            pointer = inst.operand(0);
        }
        return pointer;
    }

    private static Stream<Inst> insts(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream());
    }

    private static java.util.Optional<Inst> first(Function function, java.util.function.Predicate<Type> type) {
        // The first instruction producing or using such a value (parameters count where they are used).
        return insts(function).filter(i -> type.test(i.type()) || i.operands().stream().anyMatch(o -> type.test(o.type()))).findFirst();
    }

    private static boolean uses(Type type, Type scalar) {
        return switch (type) {
            case Type.VectorType(var element, var length) -> element.equals(scalar);
            case Type.ArrayType(var element) -> uses(element, scalar);
            default -> type.equals(scalar);
        };
    }

    private static boolean intrinsic(Inst inst, String prefix) {
        return inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(prefix);
    }

    private static Diagnostic at(Function function, Inst inst, String code, String message) {
        return new Diagnostic(code, message, "@" + function.name(), inst.loc());
    }
}

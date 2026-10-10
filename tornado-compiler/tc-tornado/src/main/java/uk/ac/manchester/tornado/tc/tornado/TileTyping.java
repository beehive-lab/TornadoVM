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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Type.TileType;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Static types for CUDA Tile values (TileContext kernels). The frontend leaves tiles and views as
 * opaque objects; once arguments are specialised, every shape, axis and element type is a
 * constant, and this pass gives each {@code tile.*} value its {@code tile<DTYPE, shape>} (or
 * {@code tileview<...>}) type: the shape is part of a CUDA Tile C++ type. The rules are the
 * TileContext contract: elementwise operations broadcast extents of 1, reductions keep the
 * reduced axis as 1, comparisons give PRED tiles, matmul widens per element type, mma keeps the
 * accumulator's type. Loop-carried tiles (block arguments) take the type of their incoming values.
 *
 * <p>Error codes: {@code TC-E3401} a shape, axis or element type is not a constant (or a shape is
 * not a power of two), {@code TC-E3402} operands that do not fit together, {@code TC-E3403} an
 * unknown tile operation.</p>
 */
public final class TileTyping {

    /** {@code DType} constants in declaration order: the frontend passes a DType as its ordinal. */
    public static final List<String> DTYPES = List.of("F16", "BF16", "F32", "F64", "TF32", "FP8_E4M3", "FP8_E5M2", "S8", "S32", "PRED");

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Function function;

    private TileTyping(Function function) {
        this.function = function;
    }

    /** Whether {@code function} uses CUDA Tile (it is then emitted as a {@code __tile_global__} kernel). */
    public static boolean usesTiles(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof Opcode.Intrinsic(var n) && n.startsWith("tile."));
    }

    public static List<Diagnostic> run(Function function) {
        var typing = new TileTyping(function);
        typing.type();
        return List.copyOf(typing.diagnostics);
    }

    private static final class Failed extends RuntimeException {
        Failed() {
            super(null, null, false, false);
        }
    }

    private void type() {
        // Instructions in block order; a block argument (loop accumulator) is typed from the first
        // incoming value that is known, then the loop is revisited until nothing changes.
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < 16) {
            changed = false;
            for (var block : function.blocks()) {
                for (var arg : block.args()) {
                    if (isOpaque(arg.type())) {
                        var incoming = incomingTile(arg);
                        if (incoming.isPresent()) {
                            arg.setType(incoming.get());
                            changed = true;
                        }
                    }
                }
                for (var inst : block.insts()) {
                    if (inst.producesValue() && isOpaque(inst.type()) && inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith("tile.")) {
                        try {
                            var t = typeOf(inst, name);
                            if (t.isPresent()) {
                                inst.setType(t.get());
                                changed = true;
                            }
                        } catch (Failed f) {
                            return;
                        }
                    }
                }
            }
        }
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.producesValue() && isOpaque(inst.type()) && inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith("tile.")) {
                    diagnostics.add(new Diagnostic("TC-E3402", "the type of this tile value cannot be determined (" + name + ")", "@" + function.name(), inst.loc()));
                    return;
                }
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith("tile.atomic.")) {
                    atomicOperands(inst, name);
                }
            }
        }
    }

    /**
     * A tile atomic needs element pointers, not a view: its operands gain the view's buffer and,
     * for rank 2, the row extent ({@code tile.atomic.<op>(view, tile, indices..., buffer[, extent])}).
     */
    private void atomicOperands(Inst inst, String name) {
        if (inst.operand(0) instanceof Inst partition && partition.operand(0) instanceof Inst view && view.opcode() instanceof Opcode.Intrinsic(var viewName)
                && viewName.startsWith("tile.view.")) {
            int first = viewName.endsWith(".dtype") ? 2 : 1;
            inst.addOperand(view.operand(0));
            int rank = ((TileType) partition.type()).shape().size();
            if (rank == 2) {
                inst.addOperand(view.operand(first + 1));
            }
        } else {
            diagnostics.add(new Diagnostic("TC-E3403", name + " needs a partition of a plain view (view(...), not viewStrided)", "@" + function.name(), inst.loc()));
        }
    }

    /** The device must run CUDA Tile (sm_80+), and fp8 tiles need sm_90+ ({@code computeCapability} as 89 for 8.9). */
    public static List<Diagnostic> checkTarget(Function function, int computeCapability) {
        int major = computeCapability / 10;
        int minor = computeCapability % 10;
        if (major < 8) {
            return List.of(new Diagnostic("TC-E4010", "CUDA Tile requires compute capability 8.0 or higher (Ampere and newer); device reports " + major + "." + minor,
                    "@" + function.name(), uk.ac.manchester.tornado.tc.ir.Loc.UNKNOWN));
        }
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (major < 9 && inst.type() instanceof TileType t && t.dtype().startsWith("FP8") && inst.opcode() instanceof Opcode.Intrinsic(var name)) {
                    return List.of(new Diagnostic("TC-E4011", "the tile operation '" + name.substring(5) + "' uses " + t.dtype()
                            + ", and CUDA Tile supports FP8 tiles only from compute capability 9.0 (Hopper) onwards; device reports " + major + "." + minor, "@" + function.name(),
                            inst.loc()));
                }
            }
        }
        return List.of();
    }

    private static boolean isOpaque(Type type) {
        return type instanceof Type.ObjectType o && (o.className().equals("Tile") || o.className().equals("TensorView") || o.className().equals("PartitionView"));
    }

    private Optional<Type> incomingTile(BlockArg arg) {
        int index = arg.block().args().indexOf(arg);
        for (var block : function.blocks()) {
            var term = block.terminator().orElse(null);
            if (term == null) {
                continue;
            }
            for (var s : term.successors()) {
                if (s.target() == arg.block() && index < s.args().size() && s.args().get(index).type() instanceof TileType t) {
                    return Optional.of(t);
                }
            }
        }
        return Optional.empty();
    }

    /** The tile type of {@code inst}, or empty while an operand is not typed yet. */
    private Optional<Type> typeOf(Inst inst, String name) {
        var ops = inst.operands();
        if (name.startsWith("tile.view.") || name.startsWith("tile.view_strided.")) {
            var element = name.substring(name.lastIndexOf('.') + 1);
            // tile.view.dtype(array, dtype, ...), tile.view_strided.dtype(array, dtype, ...)
            return Optional.of(new TileType(element.equals("dtype") ? dtype(inst, ops.get(1)) : element, List.of(), true));
        }
        if (name.equals("tile.partition")) {
            var view = tile(ops.get(0));
            return view.map(v -> new TileType(v.dtype(), shape(inst, ops.subList(1, ops.size())), true));
        }
        switch (name) {
            case "tile.zeros", "tile.ones", "tile.iota" -> {
                return Optional.of(new TileType(dtype(inst, ops.get(0)), shape(inst, ops.subList(1, ops.size())), false));
            }
            case "tile.full" -> {
                return Optional.of(new TileType(dtype(inst, ops.get(0)), shape(inst, ops.subList(2, ops.size())), false));
            }
            case "tile.load", "tile.load_masked", "tile.atomic_load" -> {
                return tile(ops.get(0)).map(v -> new TileType(v.dtype(), v.shape(), false));
            }
            case "tile.broadcast", "tile.reshape" -> {
                return tile(ops.get(0)).map(t -> new TileType(t.dtype(), shape(inst, ops.subList(1, ops.size())), false));
            }
            case "tile.extract.1", "tile.extract.2" -> {
                int rank = name.endsWith("1") ? 1 : 2;
                return tile(ops.get(0)).map(t -> new TileType(t.dtype(), shape(inst, ops.subList(1, 1 + rank)), false));
            }
            case "tile.select" -> {
                var a = tile(ops.get(1));
                var b = tile(ops.get(2));
                if (a.isEmpty() || b.isEmpty() || tile(ops.get(0)).isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new TileType(a.get().dtype(), broadcast(inst, a.get().shape(), b.get().shape()), false));
            }
            case "tile.fma" -> {
                var a = tile(ops.get(0));
                var b = tile(ops.get(1));
                var c = tile(ops.get(2));
                if (a.isEmpty() || b.isEmpty() || c.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new TileType(a.get().dtype(), broadcast(inst, broadcast(inst, a.get().shape(), b.get().shape()), c.get().shape()), false));
            }
            case "tile.mma" -> {
                return tile(ops.get(2)).map(acc -> new TileType(acc.dtype(), acc.shape(), false));
            }
            case "tile.scale" -> {
                return tile(ops.get(0)).map(t -> new TileType(t.dtype(), t.shape(), false));
            }
            case "tile.cast", "tile.bitcast" -> {
                return tile(ops.get(0)).map(t -> new TileType(dtype(inst, ops.get(1)), t.shape(), false));
            }
            case "tile.concat" -> {
                var a = tile(ops.get(0));
                var b = tile(ops.get(1));
                if (a.isEmpty() || b.isEmpty()) {
                    return Optional.empty();
                }
                int axis = axis(inst, ops.get(2), a.get().shape().size());
                var shape = new ArrayList<>(a.get().shape());
                shape.set(axis, a.get().shape().get(axis) + b.get().shape().get(axis));
                return Optional.of(new TileType(a.get().dtype(), shape, false));
            }
            default -> {
            }
        }
        if (name.startsWith("tile.binary.")) {
            var op = name.substring("tile.binary.".length());
            var a = tile(ops.get(0));
            var b = tile(ops.get(1));
            if (a.isEmpty() || b.isEmpty()) {
                return Optional.empty();
            }
            if (op.equals("matmul")) {
                var l = a.get().shape();
                var r = b.get().shape();
                if (l.size() != 2 || r.size() != 2 || !l.get(1).equals(r.get(0))) {
                    throw fail(inst, "matmul shapes do not fit: " + l + " times " + r);
                }
                return Optional.of(new TileType(matmulResult(a.get().dtype()), List.of(l.get(0), r.get(1)), false));
            }
            var dtype = op.equals("logicalAnd") || op.equals("logicalOr") ? "PRED" : a.get().dtype();
            return Optional.of(new TileType(dtype, broadcast(inst, a.get().shape(), b.get().shape()), false));
        }
        if (name.startsWith("tile.compare.")) {
            var a = tile(ops.get(0));
            var b = tile(ops.get(1));
            if (a.isEmpty() || b.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new TileType("PRED", broadcast(inst, a.get().shape(), b.get().shape()), false));
        }
        if (name.startsWith("tile.compare_scalar.")) {
            return tile(ops.get(0)).map(t -> new TileType("PRED", t.shape(), false));
        }
        if (name.startsWith("tile.unary.")) {
            var op = name.substring("tile.unary.".length());
            return tile(ops.get(0)).map(t -> switch (op) {
                case "isNaN", "isInfinite", "logicalNot" -> new TileType("PRED", t.shape(), false);
                case "transpose" -> {
                    if (t.shape().size() != 2) {
                        throw fail(inst, "transpose needs a rank-2 tile");
                    }
                    yield new TileType(t.dtype(), List.of(t.shape().get(1), t.shape().get(0)), false);
                }
                default -> new TileType(t.dtype(), t.shape(), false);
            });
        }
        if (name.startsWith("tile.reduce.")) {
            var op = name.substring("tile.reduce.".length());
            return tile(ops.get(0)).map(t -> {
                int axis = axis(inst, ops.get(1), t.shape().size());
                if (op.startsWith("prefix")) {
                    return new TileType(t.dtype(), t.shape(), false);
                }
                var shape = new ArrayList<>(t.shape());
                shape.set(axis, 1);
                return new TileType(op.equals("allOf") || op.equals("anyOf") ? "PRED" : t.dtype(), shape, false);
            });
        }
        diagnostics.add(new Diagnostic("TC-E3403", "tile operation " + name + " is not compiled by TC yet", "@" + function.name(), inst.loc()));
        throw new Failed();
    }

    private Optional<TileType> tile(Value v) {
        return v.type() instanceof TileType t ? Optional.of(t) : Optional.empty();
    }

    private static String matmulResult(String dtype) {
        return switch (dtype) {
            case "S8", "S32" -> "S32";
            case "FP8_E4M3", "FP8_E5M2", "F16" -> "F16";
            case "F64" -> "F64";
            default -> "F32";
        };
    }

    private String dtype(Inst inst, Value v) {
        long ordinal = constant(inst, v, "element type");
        if (ordinal < 0 || ordinal >= DTYPES.size()) {
            throw fail(inst, "unknown element type ordinal " + ordinal);
        }
        return DTYPES.get((int) ordinal);
    }

    private List<Integer> shape(Inst inst, List<Value> extents) {
        var shape = new ArrayList<Integer>();
        for (var e : extents) {
            long n = constant(inst, e, "tile extent");
            if (n <= 0 || (n & (n - 1)) != 0) {
                diagnostics.add(new Diagnostic("TC-E3401", "a tile extent must be a positive power of two, got " + n, "@" + function.name(), inst.loc()));
                throw new Failed();
            }
            shape.add((int) n);
        }
        return shape;
    }

    private int axis(Inst inst, Value v, int rank) {
        long axis = constant(inst, v, "axis");
        if (axis < 0 || axis >= rank) {
            throw fail(inst, "axis " + axis + " is out of range for a rank-" + rank + " tile");
        }
        return (int) axis;
    }

    private long constant(Inst inst, Value v, String role) {
        if (v instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var value)) {
            return value;
        }
        diagnostics.add(new Diagnostic("TC-E3401", "the " + role + " of this tile operation must be a compile-time constant", "@" + function.name(), inst.loc()));
        throw new Failed();
    }

    private List<Integer> broadcast(Inst inst, List<Integer> a, List<Integer> b) {
        if (a.size() != b.size()) {
            throw fail(inst, "tile operands of rank " + a.size() + " and " + b.size());
        }
        var shape = new ArrayList<Integer>();
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i)) && a.get(i) != 1 && b.get(i) != 1) {
                throw fail(inst, "dimension " + i + " does not broadcast: " + a.get(i) + " against " + b.get(i));
            }
            shape.add(Math.max(a.get(i), b.get(i)));
        }
        return shape;
    }

    private Failed fail(Inst inst, String message) {
        diagnostics.add(new Diagnostic("TC-E3402", message, "@" + function.name(), inst.loc()));
        return new Failed();
    }
}

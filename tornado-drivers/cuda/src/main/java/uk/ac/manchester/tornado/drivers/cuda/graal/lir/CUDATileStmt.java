/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.lir.LIRInstruction;
import tornado.graal.compiler.lir.LIRInstructionClass;
import tornado.graal.compiler.lir.asm.CompilationResultBuilder;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

/**
 * LIR statements for the CUDA Tile programming model.
 *
 * <p>
 * Every statement here emits CUDA Tile C++ ({@code ct::} calls) and nothing else. The tile
 * compiler, not this emitter, decides the layout, the tensor-core instruction, the staging
 * through shared memory and whether TMA is used. That is the whole point of the tile path:
 * the hand-written {@code mma.sync} emitter in {@link CUDALIRStmt} is what it replaces, so no
 * statement in this file may emit inline PTX.
 * </p>
 *
 * <p>
 * Results are declared at their definition site with {@code auto}, because a tile's C++ type
 * depends on its shape and the grouped declarations in the backend prologue cannot name it.
 * Tile values that are carried across a loop are the exception: the backend has to pre-declare
 * that variable, so tile-valued statements report a concrete type through
 * {@link TileValued#getTileCppType()}.
 * </p>
 */
public class CUDATileStmt {

    private CUDATileStmt() {
    }

    /**
     * Implemented by statements whose result is a tile value, so that the backend can
     * pre-declare a tile variable that a loop carries across iterations.
     */
    public interface TileValued {

        /**
         * @return the concrete C++ type of the result, for example
         *     {@code ct::tile<float, ct::shape<64, 64>>}, or null when the result is a view
         *     descriptor whose type cannot be spelled out.
         */
        String getTileCppType();

        /**
         * @return the value this statement defines.
         */
        Value getTileResult();
    }

    protected abstract static class AbstractTileInstruction extends LIRInstruction {

        protected AbstractTileInstruction(LIRInstructionClass<? extends AbstractTileInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((CUDACompilationResultBuilder) crb, (CUDAAssembler) crb.asm);
        }

        public abstract void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm);

        /**
         * Spells out a {@code ct::tile} type.
         */
        static String tileType(DType dtype, int[] shape) {
            return DType.tileCppType(dtype, shape);
        }

        /**
         * Spells out a compile-time integer constant literal, {@code 64_ic}.
         */
        static String constant(int value) {
            return value + "_ic";
        }
    }

    /**
     * Wraps a device buffer in a {@code ct::partition_view}, the descriptor every tile load and
     * store addresses through. Emits, for a rank-2 view:
     *
     * <pre>
     * auto v12 = ct::partition_view{ct::tensor_span{ct::assume_aligned(
     *         reinterpret_cast&lt;float *&gt;(buffer + 32), 16_ic), ct::extents{m, n}}, ct::shape{64_ic, 64_ic}};
     * </pre>
     *
     * The alignment hint is what makes a load TMA eligible, and the offset must be the real
     * payload offset of a TornadoVM native array, not a hardcoded header size.
     */
    public static class TilePartitionViewStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TilePartitionViewStmt> TYPE = LIRInstructionClass.create(TilePartitionViewStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value buffer;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] extents;

        private final DType dtype;
        private final int[] tileShape;
        private final int payloadOffset;

        public TilePartitionViewStmt(Value result, Value buffer, Value[] extents, DType dtype, int[] tileShape, int payloadOffset) {
            super(TYPE);
            this.result = result;
            this.buffer = buffer;
            this.extents = extents;
            this.dtype = dtype;
            this.tileShape = tileShape;
            this.payloadOffset = payloadOffset;
        }

        @Override
        public String getTileCppType() {
            return null;
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            StringBuilder span = new StringBuilder();
            span.append("ct::tensor_span{ct::assume_aligned(reinterpret_cast<").append(dtype.getCppType()).append(" *>(");
            span.append(asm.getStringValue(crb, buffer)).append(" + ").append(payloadOffset).append("), 16_ic), ct::extents{");
            for (int i = 0; i < extents.length; i++) {
                if (i > 0) {
                    span.append(", ");
                }
                span.append(asm.getStringValue(crb, extents[i]));
            }
            span.append("}}");

            StringBuilder shape = new StringBuilder("ct::shape{");
            for (int i = 0; i < tileShape.length; i++) {
                if (i > 0) {
                    shape.append(", ");
                }
                shape.append(constant(tileShape[i]));
            }
            shape.append("}");

            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::partition_view{" + span + ", " + shape + "}");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Creates a tile filled with a constant: {@code auto v = ct::full<ct::tile<...>>(0.0f);}
     */
    public static class TileCreateStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileCreateStmt> TYPE = LIRInstructionClass.create(TileCreateStmt.class);

        @Def
        protected Value result;

        private final DType dtype;
        private final int[] shape;
        private final String initializer;

        /**
         * ct::iota takes no argument and fills the tile with 0, 1, 2, ..., so it is a distinct
         * factory rather than a ct::full with a different value.
         */
        private final boolean iota;

        public TileCreateStmt(Value result, DType dtype, int[] shape, String initializer) {
            this(result, dtype, shape, initializer, false);
        }

        public TileCreateStmt(Value result, DType dtype, int[] shape, String initializer, boolean iota) {
            super(TYPE);
            this.result = result;
            this.dtype = dtype;
            this.shape = shape;
            this.initializer = initializer;
            this.iota = iota;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            String type = tileType(dtype, shape);
            asm.indent();
            if (iota) {
                asm.emit(type + " " + asm.getStringValue(crb, result) + " = ct::iota<" + type + ">()");
            } else {
                asm.emit(type + " " + asm.getStringValue(crb, result) + " = ct::full<" + type + ">(" + initializer + ")");
            }
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Loads one tile from a partition view: {@code auto v = view.load(bx, by);}. The masked
     * form zero-pads a ragged edge and is what the code generator selects whenever the extents
     * are not provably divisible by the tile shape, because CUDA Tile C++ does not bounds check
     * the plain form.
     */
    public static class TileLoadStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileLoadStmt> TYPE = LIRInstructionClass.create(TileLoadStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value view;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] blockIndices;

        private final DType dtype;
        private final int[] tileShape;
        private final boolean masked;

        public TileLoadStmt(Value result, Value view, Value[] blockIndices, DType dtype, int[] tileShape, boolean masked) {
            super(TYPE);
            this.result = result;
            this.view = view;
            this.blockIndices = blockIndices;
            this.dtype = dtype;
            this.tileShape = tileShape;
            this.masked = masked;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, tileShape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = " + asm.getStringValue(crb, view) + "."
                    + (masked ? "load_masked(" : "load("));
            for (int i = 0; i < blockIndices.length; i++) {
                if (i > 0) {
                    asm.emit(", ");
                }
                asm.emit(asm.getStringValue(crb, blockIndices[i]));
            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Stores one tile through a partition view: {@code view.store(tile, bx, by);}
     */
    public static class TileStoreStmt extends AbstractTileInstruction {

        public static final LIRInstructionClass<TileStoreStmt> TYPE = LIRInstructionClass.create(TileStoreStmt.class);

        @Use
        protected Value view;
        @Use
        protected Value tile;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] blockIndices;

        private final boolean masked;

        public TileStoreStmt(Value view, Value tile, Value[] blockIndices, boolean masked) {
            super(TYPE);
            this.view = view;
            this.tile = tile;
            this.blockIndices = blockIndices;
            this.masked = masked;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit(asm.getStringValue(crb, view) + "." + (masked ? "store_masked(" : "store(")
                    + asm.getStringValue(crb, tile));
            for (Value blockIndex : blockIndices) {
                asm.emit(", " + asm.getStringValue(crb, blockIndex));
            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Tile multiply accumulate: {@code auto v = ct::mma(a, b, acc);}. This single call is what
     * replaces the hand-written mma.sync sequence, ldmatrix staging included. Which tensor-core
     * instruction it becomes, and the operand layouts, are chosen by the tile compiler.
     */
    public static class TileMmaStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileMmaStmt> TYPE = LIRInstructionClass.create(TileMmaStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tileA;
        @Use
        protected Value tileB;
        @Use
        protected Value accumulator;

        private final DType accumulatorType;
        private final int[] accumulatorShape;

        public TileMmaStmt(Value result, Value tileA, Value tileB, Value accumulator, DType accumulatorType, int[] accumulatorShape) {
            super(TYPE);
            this.result = result;
            this.tileA = tileA;
            this.tileB = tileB;
            this.accumulator = accumulator;
            this.accumulatorType = accumulatorType;
            this.accumulatorShape = accumulatorShape;
        }

        @Override
        public String getTileCppType() {
            return tileType(accumulatorType, accumulatorShape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::mma(" + asm.getStringValue(crb, tileA) + ", "
                    + asm.getStringValue(crb, tileB) + ", " + asm.getStringValue(crb, accumulator) + ")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Elementwise arithmetic over whole tiles, with the broadcasting CUDA Tile already defines
     * for its operators: {@code auto v = a + b;}
     */
    public static class TileBinaryStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileBinaryStmt> TYPE = LIRInstructionClass.create(TileBinaryStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value left;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value right;

        private final String operator;
        private final DType dtype;
        private final int[] shape;

        public TileBinaryStmt(Value result, Value left, Value right, String operator, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.left = left;
            this.right = right;
            this.operator = operator;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = " + asm.getStringValue(crb, left) + " " + operator
                    + " " + asm.getStringValue(crb, right));
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * A two-operand tile op spelled as a call: {@code auto v = ct::max(a, b);}
     */
    public static class TileBinaryCallStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileBinaryCallStmt> TYPE = LIRInstructionClass.create(TileBinaryCallStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value left;
        @Use
        protected Value right;

        private final String function;
        private final DType dtype;
        private final int[] shape;

        public TileBinaryCallStmt(Value result, Value left, Value right, String function, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.left = left;
            this.right = right;
            this.function = function;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::" + function + "("
                    + asm.getStringValue(crb, left) + ", " + asm.getStringValue(crb, right) + ")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Reduces along one axis, keeping the reduced dimension as CUDA Tile C++ does:
     * {@code auto v = ct::sum(a, 1_ic);}
     */
    public static class TileReduceStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileReduceStmt> TYPE = LIRInstructionClass.create(TileReduceStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tile;

        private final String reduction;
        private final int axis;
        private final DType dtype;
        private final int[] shape;

        public TileReduceStmt(Value result, Value tile, String reduction, int axis, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.tile = tile;
            this.reduction = reduction;
            this.axis = axis;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::" + reduction + "("
                    + asm.getStringValue(crb, tile) + ", " + constant(axis) + ")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * A one-operand tile operation.
     *
     * <p>
     * Two spellings are needed and they differ in shape, which is why this carries the call
     * text rather than an operator: {@code ct::transpose(t)} is a plain call, while an element
     * conversion is {@code ct::element_cast<float>(t)} with the target type as a template
     * argument. There is no {@code ct::cast} - that spelling does not exist.
     * </p>
     */
    public static class TileUnaryStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileUnaryStmt> TYPE = LIRInstructionClass.create(TileUnaryStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tile;

        private final String function;
        private final String templateArgument;
        private final DType dtype;
        private final int[] shape;

        public TileUnaryStmt(Value result, Value tile, String function, String templateArgument, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.tile = tile;
            this.function = function;
            this.templateArgument = templateArgument;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            // A one-character function name is a prefix operator: CUDA Tile spells predicate
            // negation "!mask" and bitwise complement "~tile", not as named functions.
            if (function.length() == 1) {
                asm.emit("auto " + asm.getStringValue(crb, result) + " = " + function + asm.getStringValue(crb, tile));
            } else {
                asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::" + function);
                if (templateArgument != null) {
                    asm.emit("<" + templateArgument + ">");
                }
                asm.emit("(" + asm.getStringValue(crb, tile) + ")");
            }
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Scales a tile by a scalar. CUDA Tile has no named function for this: a scalar broadcasts
     * across the tile, so it is plain multiplication. The scalar is cast to the tile's element
     * type first, because a Java double operand would otherwise widen the arithmetic.
     */
    public static class TileScaleStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileScaleStmt> TYPE = LIRInstructionClass.create(TileScaleStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tile;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value scalar;

        private final DType dtype;
        private final int[] shape;

        public TileScaleStmt(Value result, Value tile, Value scalar, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.tile = tile;
            this.scalar = scalar;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = " + asm.getStringValue(crb, tile) + " * ("
                    + dtype.getCppType() + ") " + asm.getStringValue(crb, scalar));
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Index of this tile block, or the size of the grid of tile blocks:
     * {@code v = ct::bid().x;} and {@code v = ct::num_blocks().y;}
     */
    public static class TileBlockIdStmt extends AbstractTileInstruction {

        public static final LIRInstructionClass<TileBlockIdStmt> TYPE = LIRInstructionClass.create(TileBlockIdStmt.class);

        @Def
        protected Value result;

        private final boolean gridSize;
        private final char dimension;

        public TileBlockIdStmt(Value result, boolean gridSize, char dimension) {
            super(TYPE);
            this.result = result;
            this.gridSize = gridSize;
            this.dimension = dimension;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit(asm.getStringValue(crb, result) + " = ct::" + (gridSize ? "num_blocks()." : "bid().") + dimension);
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * A three-operand tile call: {@code auto v = ct::select(c, a, b);}.
     */
    public static class TileTernaryCallStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileTernaryCallStmt> TYPE = LIRInstructionClass.create(TileTernaryCallStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value first;
        @Use
        protected Value second;
        @Use
        protected Value third;

        private final String function;
        private final DType dtype;
        private final int[] shape;

        public TileTernaryCallStmt(Value result, Value first, Value second, Value third, String function, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.first = first;
            this.second = second;
            this.third = third;
            this.function = function;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::" + function + "(" + asm.getStringValue(crb, first) + ", " //
                    + asm.getStringValue(crb, second) + ", " + asm.getStringValue(crb, third) + ")");
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * Compares a tile against one scalar: {@code auto v = t < (int) limit;}.
     *
     * <p>
     * The cast names the <em>operand</em> element type rather than the result type, which is
     * {@code bool}. Leaving it out would let an {@code int} tile compare against a promoted
     * value and widen the comparison.
     * </p>
     */
    public static class TileScalarCompareStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileScalarCompareStmt> TYPE = LIRInstructionClass.create(TileScalarCompareStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tile;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value scalar;

        private final String operator;
        private final DType operandType;
        private final int[] shape;

        public TileScalarCompareStmt(Value result, Value tile, Value scalar, String operator, DType operandType, int[] shape) {
            super(TYPE);
            this.result = result;
            this.tile = tile;
            this.scalar = scalar;
            this.operator = operator;
            this.operandType = operandType;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(DType.PRED, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = " + asm.getStringValue(crb, tile) + " " + operator //
                    + " (" + operandType.getCppType() + ") " + asm.getStringValue(crb, scalar));
            asm.delimiter();
            asm.eol();
        }
    }

    /**
     * An atomic read-modify-write on a view, which CUDA Tile expresses over a tile of pointers.
     *
     * <p>
     * The emitted sequence builds the pointer tile for the addressed block from the base
     * pointer and the view's extents. Rank 2 needs the row and column index within the tile
     * separately, and neither {@code /} nor {@code %} is defined on a tile, so the indices come
     * from two {@code iota} tiles broadcast to the tile shape rather than from dividing a linear
     * one.
     * </p>
     *
     * <p>
     * {@code memory_order_relaxed} with {@code thread_scope_device}: an accumulator in device
     * memory needs each element's update to be atomic, not ordered against the others, and
     * device scope lowers to {@code ATOMG.E.ADD.F32.FTZ.RN.STRONG.GPU} where system scope would
     * force the more expensive {@code .SYS} form.
     * </p>
     */
    public static class TileAtomicStmt extends AbstractTileInstruction {

        public static final LIRInstructionClass<TileAtomicStmt> TYPE = LIRInstructionClass.create(TileAtomicStmt.class);

        @Use
        protected Value buffer;
        @Use
        protected Value tile;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] extents;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] blockIndices;

        private final DType dtype;
        private final int[] tileShape;
        private final int payloadOffset;

        private final String function;

        public TileAtomicStmt(Value buffer, Value tile, Value[] extents, Value[] blockIndices, String function, DType dtype, int[] tileShape, int payloadOffset) {
            super(TYPE);
            this.function = function;
            this.buffer = buffer;
            this.tile = tile;
            this.extents = extents;
            this.blockIndices = blockIndices;
            this.dtype = dtype;
            this.tileShape = tileShape;
            this.payloadOffset = payloadOffset;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            String base = "reinterpret_cast<" + dtype.getCppType() + " *>(" + asm.getStringValue(crb, buffer) + " + " + payloadOffset + ")";
            String pointers;
            if (tileShape.length == 1) {
                String indices = "ct::iota<" + tileType(DType.S32, tileShape) + ">() + " + asm.getStringValue(crb, blockIndices[0]) + " * " + tileShape[0];
                pointers = base + " + (" + indices + ")";
            } else {
                String rowIota = "ct::broadcast(ct::iota<" + tileType(DType.S32, new int[] { tileShape[0], 1 }) + ">(), " + shapeTemplate() + ")";
                String columnIota = "ct::broadcast(ct::iota<" + tileType(DType.S32, new int[] { 1, tileShape[1] }) + ">(), " + shapeTemplate() + ")";
                String row = "(" + rowIota + " + " + asm.getStringValue(crb, blockIndices[0]) + " * " + tileShape[0] + ")";
                String column = "(" + columnIota + " + " + asm.getStringValue(crb, blockIndices[1]) + " * " + tileShape[1] + ")";
                pointers = base + " + (" + row + " * " + asm.getStringValue(crb, extents[1]) + " + " + column + ")";
            }
            asm.indent();
            asm.emit("ct::" + function + "(" + pointers + ", " + asm.getStringValue(crb, tile)
                    + ", ct::memory_order_relaxed_t{}, ct::thread_scope_device_t{})");
            asm.delimiter();
            asm.eol();
        }

        private String shapeTemplate() {
            StringBuilder builder = new StringBuilder("ct::shape<");
            for (int i = 0; i < tileShape.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(tileShape[i]);
            }
            return builder.append(">{}").toString();
        }
    }

    /**
     * {@code ct::broadcast}, {@code ct::reshape} or {@code ct::extract}: a tile, a target shape
     * as a template argument, and for extract the sub-tile indices.
     */
    public static class TileShapeOpStmt extends AbstractTileInstruction implements TileValued {

        public static final LIRInstructionClass<TileShapeOpStmt> TYPE = LIRInstructionClass.create(TileShapeOpStmt.class);

        @Def
        protected Value result;
        @Use
        protected Value tile;
        @Use({ OperandFlag.REG, OperandFlag.CONST })
        protected Value[] blockIndices;

        private final String function;
        private final DType dtype;
        private final int[] shape;

        public TileShapeOpStmt(Value result, Value tile, Value[] blockIndices, String function, DType dtype, int[] shape) {
            super(TYPE);
            this.result = result;
            this.tile = tile;
            this.blockIndices = blockIndices;
            this.function = function;
            this.dtype = dtype;
            this.shape = shape;
        }

        @Override
        public String getTileCppType() {
            return tileType(dtype, shape);
        }

        @Override
        public Value getTileResult() {
            return result;
        }

        @Override
        public void emitCode(CUDACompilationResultBuilder crb, CUDAAssembler asm) {
            StringBuilder target = new StringBuilder("ct::shape<");
            for (int i = 0; i < shape.length; i++) {
                if (i > 0) {
                    target.append(", ");
                }
                target.append(shape[i]);
            }
            target.append(">{}");

            asm.indent();
            asm.emit("auto " + asm.getStringValue(crb, result) + " = ct::" + function + "(" + asm.getStringValue(crb, tile) + ", " + target);
            for (Value blockIndex : blockIndices) {
                asm.emit(", " + asm.getStringValue(crb, blockIndex));
            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }
    }
}

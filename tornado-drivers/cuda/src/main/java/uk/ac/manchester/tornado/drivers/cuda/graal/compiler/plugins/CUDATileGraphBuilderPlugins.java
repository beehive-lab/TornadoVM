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

package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.graal.compiler.nodes.PiNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.ValueProxyNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.TensorView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileAtomicAddNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileBinaryCallNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileBinaryNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileBlockIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileCreateNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileLoadNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileMmaNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATilePartitionViewNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileReduceNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileScalarCompareNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileScaleNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileShapeOpNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileStoreNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileTernaryNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileUnaryNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDATileViewNode;

/**
 * Intrinsifies the CUDA Tile API onto tile nodes, so that a kernel written against
 * {@link TileContext} lowers to {@code ct::} calls and never to hand-written PTX.
 *
 * <p>
 * Two properties of the API make this tractable. Accessors are fixed arity, so no plugin has to
 * constant-fold an array allocation to read an index list. And shapes are compile-time constants
 * by contract, so a shape that fails to fold is reported here, by name, instead of bailing out of
 * the sketch with no explanation.
 * </p>
 *
 * <p>
 * Every method registered here must also be handled in
 * {@code TornadoCUDAIntrinsicsReplacements}: a kernel resolved through reflection never reaches
 * plugin lookup, and an intrinsic registered in only one of the two places works in some launch
 * modes and silently fails in others.
 * </p>
 */
public class CUDATileGraphBuilderPlugins {

    /**
     * Offset of the payload inside a TornadoVM native array. The runtime pads the device
     * allocation so that this offset is 32 byte aligned, which is what makes the
     * {@code ct::assume_aligned(p, 16_ic)} hint in the emitted view legal, and therefore what
     * keeps a load TMA eligible.
     */
    private static final int PAYLOAD_OFFSET = (int) TornadoNativeArray.ARRAY_HEADER;

    private CUDATileGraphBuilderPlugins() {
    }

    public static void registerTileContextPlugins(InvocationPlugins plugins) {
        Registration context = new Registration(plugins, TileContext.class);
        registerBlockIndexPlugins(context);
        registerViewPlugins(context);
        registerPartitionPlugins(context);
        registerTileCreationPlugins(context);
        registerTileFactoryPlugins(context);
        registerTileComputePlugins(context);

        Registration view = new Registration(plugins, PartitionView.class);
        registerAccessPlugins(view);
    }

    // -------------------------------------------------------------------------------------
    // Grid
    // -------------------------------------------------------------------------------------

    private static void registerBlockIndexPlugins(Registration r) {
        registerBlockIndex(r, "bidX", false, 'x');
        registerBlockIndex(r, "bidY", false, 'y');
        registerBlockIndex(r, "bidZ", false, 'z');
        registerBlockIndex(r, "numBlocksX", true, 'x');
        registerBlockIndex(r, "numBlocksY", true, 'y');
        registerBlockIndex(r, "numBlocksZ", true, 'z');
    }

    private static void registerBlockIndex(Registration r, String name, boolean gridSize, char dimension) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                b.addPush(JavaKind.Int, new CUDATileBlockIdNode(gridSize, dimension));
                return true;
            }
        });
    }

    // -------------------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------------------

    private static void registerViewPlugins(Registration r) {
        registerView(r, FloatArray.class, DType.F32);
        registerView(r, HalfFloatArray.class, DType.F16);
        registerView(r, BFloat16Array.class, DType.BF16);
        registerView(r, IntArray.class, DType.S32);
        registerView(r, Int8Array.class, DType.S8);
        registerView(r, DoubleArray.class, DType.F64);
        registerFp8View(r);
    }

    /**
     * An fp8 view takes its format as an argument, because {@link FP8Array} is a byte buffer
     * with both an e4m3 and an e5m2 accessor and carries no record of which it holds.
     */
    private static void registerFp8View(Registration r) {
        r.register(new InvocationPlugin("view", InvocationPlugin.Receiver.class, FP8Array.class, DType.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode format, ValueNode extent) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDATileViewNode(array, new ValueNode[] { extent }, resolveDType(b, format)));
                return true;
            }
        });
        r.register(new InvocationPlugin("view", InvocationPlugin.Receiver.class, FP8Array.class, DType.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode format, ValueNode rows, ValueNode columns) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDATileViewNode(array, new ValueNode[] { rows, columns }, resolveDType(b, format)));
                return true;
            }
        });
    }

    private static void registerView(Registration r, Class<?> arrayType, DType dtype) {
        r.register(new InvocationPlugin("view", InvocationPlugin.Receiver.class, arrayType, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode extent) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDATileViewNode(array, new ValueNode[] { extent }, dtype));
                return true;
            }
        });
        r.register(new InvocationPlugin("view", InvocationPlugin.Receiver.class, arrayType, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode rows, ValueNode columns) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDATileViewNode(array, new ValueNode[] { rows, columns }, dtype));
                return true;
            }
        });
    }

    private static void registerPartitionPlugins(Registration r) {
        r.register(new InvocationPlugin("partition", InvocationPlugin.Receiver.class, TensorView.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode viewNode, ValueNode tileExtent) {
                receiver.get(true);
                b.addPush(JavaKind.Object, partitionOf(viewNode, new int[] { shapeConstant(tileExtent, "tile extent") }));
                return true;
            }
        });
        r.register(new InvocationPlugin("partition", InvocationPlugin.Receiver.class, TensorView.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode viewNode, ValueNode tileRows, ValueNode tileColumns) {
                receiver.get(true);
                int[] shape = { shapeConstant(tileRows, "tile rows"), shapeConstant(tileColumns, "tile columns") };
                b.addPush(JavaKind.Object, partitionOf(viewNode, shape));
                return true;
            }
        });
    }

    /**
     * Folds a view descriptor and a tile shape into the partition view that actually reaches the
     * generated code. The view node is a parse time carrier with no device representation, so it
     * is consumed here rather than lowered.
     */
    private static CUDATilePartitionViewNode partitionOf(ValueNode viewNode, int[] tileShape) {
        if (!(viewNode instanceof CUDATileViewNode view)) {
            throw new IllegalStateException("[TileContext] partition(...) expects the result of view(...) directly. "
                    + "Storing a TensorView in a field or passing it between methods is not supported, because a view "
                    + "is compile-time metadata rather than a value.");
        }
        if (view.getRank() != tileShape.length) {
            throw new IllegalStateException("[TileContext] A rank " + view.getRank() + " view cannot be partitioned "
                    + "into a rank " + tileShape.length + " tile.");
        }
        return new CUDATilePartitionViewNode(view.getBuffer(), view.getExtents(), view.getDType(), tileShape, PAYLOAD_OFFSET);
    }

    // -------------------------------------------------------------------------------------
    // Tile creation and compute
    // -------------------------------------------------------------------------------------

    private static void registerTileCreationPlugins(Registration r) {
        r.register(new InvocationPlugin("zeros", InvocationPlugin.Receiver.class, DType.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode extent) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, new int[] { shapeConstant(extent, "tile extent") }, zeroLiteral(dtype)));
                return true;
            }
        });
        r.register(new InvocationPlugin("zeros", InvocationPlugin.Receiver.class, DType.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode rows, ValueNode columns) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                int[] shape = { shapeConstant(rows, "tile rows"), shapeConstant(columns, "tile columns") };
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, shape, zeroLiteral(dtype)));
                return true;
            }
        });
    }

    private static void registerTileFactoryPlugins(Registration r) {
        r.register(new InvocationPlugin("full", InvocationPlugin.Receiver.class, DType.class, double.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode valueNode, ValueNode extent) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                int[] shape = { shapeConstant(extent, "tile extent") };
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, shape, literal(dtype, valueNode)));
                return true;
            }
        });
        r.register(new InvocationPlugin("full", InvocationPlugin.Receiver.class, DType.class, double.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode valueNode, ValueNode rows, ValueNode columns) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                int[] shape = { shapeConstant(rows, "tile rows"), shapeConstant(columns, "tile columns") };
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, shape, literal(dtype, valueNode)));
                return true;
            }
        });
        r.register(new InvocationPlugin("iota", InvocationPlugin.Receiver.class, DType.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode rows, ValueNode columns) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                int[] shape = { shapeConstant(rows, "tile rows"), shapeConstant(columns, "tile columns") };
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, shape, null, true));
                return true;
            }
        });
        r.register(new InvocationPlugin("iota", InvocationPlugin.Receiver.class, DType.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dtypeNode, ValueNode extent) {
                receiver.get(true);
                DType dtype = resolveDType(b, dtypeNode);
                int[] shape = { shapeConstant(extent, "tile extent") };
                b.addPush(JavaKind.Object, new CUDATileCreateNode(dtype, shape, null, true));
                return true;
            }
        });
    }

    private static void registerTileComputePlugins(Registration r) {
        registerBinary(r, "add", "+");
        registerBinary(r, "sub", "-");
        registerBinary(r, "mul", "*");

        r.register(new InvocationPlugin("mma", InvocationPlugin.Receiver.class, Tile.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tileA, ValueNode tileB, ValueNode accumulator) {
                receiver.get(true);
                CUDATileNode operand = tileNodeOf(tileA, "the left operand of mma");
                CUDATileNode acc = tileNodeOf(accumulator, "the accumulator of mma");
                b.addPush(JavaKind.Object, new CUDATileMmaNode(tileA, tileB, accumulator, operand.tileDType(), acc.tileDType(), acc.tileShape()));
                return true;
            }
        });

        r.register(new InvocationPlugin("scale", InvocationPlugin.Receiver.class, Tile.class, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode scalar) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of scale");
                b.addPush(JavaKind.Object, new CUDATileScaleNode(tile, scalar, source.tileDType(), source.tileShape()));
                return true;
            }
        });

        registerShapeOp(r, "broadcast", 1, 0);
        registerShapeOp(r, "broadcast", 2, 0);
        registerShapeOp(r, "reshape", 1, 0);
        registerShapeOp(r, "reshape", 2, 0);
        registerShapeOp(r, "extract", 1, 1);
        registerShapeOp(r, "extract", 2, 2);

        r.register(new InvocationPlugin("transpose", InvocationPlugin.Receiver.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of transpose");
                int[] shape = source.tileShape();
                if (shape.length != 2) {
                    throw new IllegalStateException("[TileContext] transpose expects a rank-2 tile, got rank " + shape.length + ".");
                }
                int[] transposed = { shape[1], shape[0] };
                b.addPush(JavaKind.Object, new CUDATileUnaryNode(tile, "transpose", null, source.tileDType(), transposed));
                return true;
            }
        });

        // There is no ct::cast; elementwise conversion is ct::element_cast<Element>(tile). Every
        // scalar pair is accepted, including narrowing ones, which was established by compiling
        // the combinations.
        r.register(new InvocationPlugin("cast", InvocationPlugin.Receiver.class, Tile.class, DType.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode targetNode) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of cast");
                DType target = resolveDType(b, targetNode);
                b.addPush(JavaKind.Object, new CUDATileUnaryNode(tile, "element_cast", target.getCppType(), target, source.tileShape()));
                return true;
            }
        });

        registerBinary(r, "div", "/");
        registerBinaryCall(r, "maximum", "max");

        registerUnaryMath(r, "exp", "exp");
        registerUnaryMath(r, "sin", "sin");
        registerUnaryMath(r, "cos", "cos");
        registerUnaryMath(r, "sqrt", "sqrt");
        registerUnaryMath(r, "rsqrt", "rsqrt");
        registerUnaryMath(r, "log", "log");
        registerUnaryMath(r, "log2", "log2");
        registerUnaryMath(r, "exp2", "exp2");
        registerUnaryMath(r, "tanh", "tanh");
        registerUnaryMath(r, "abs", "abs");
        registerUnaryMath(r, "floor", "floor");

        // ct::max is the elementwise two-operand form; the reduction is ct::reduce_max.
        registerReduction(r, "max", "reduce_max");
        registerReduction(r, "min", "reduce_min");
        registerReduction(r, "prod", "prod");
        registerBinaryCall(r, "minimum", "min");

        // Comparisons yield a predicate tile, which only ct::select consumes. Both operand
        // forms exist because the right-hand side is often a runtime extent, and a tile-to-tile
        // comparison would need a full(...) whose fill value has to fold.
        registerComparison(r, "lessThan", "<");
        registerComparison(r, "lessOrEqual", "<=");
        registerComparison(r, "greaterThan", ">");
        registerComparison(r, "greaterOrEqual", ">=");
        registerComparison(r, "equalTo", "==");
        registerComparison(r, "notEqualTo", "!=");
        registerPredicateCombination(r, "logicalAnd", "&");
        registerPredicateCombination(r, "logicalOr", "|");

        r.register(new InvocationPlugin("select", InvocationPlugin.Receiver.class, Tile.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode predicate, ValueNode whenTrue, ValueNode whenFalse) {
                receiver.get(true);
                CUDATileNode condition = tileNodeOf(predicate, "the condition of select");
                CUDATileNode left = tileNodeOf(whenTrue, "the true operand of select");
                CUDATileNode right = tileNodeOf(whenFalse, "the false operand of select");
                if (condition.tileDType() != DType.PRED) {
                    throw new IllegalStateException("[TileContext] The condition of select must come from a comparison, but it is a "
                            + condition.tileDType() + " tile.");
                }
                if (left.tileDType() != right.tileDType()) {
                    throw new IllegalStateException("[TileContext] select needs both value operands in the same element type, got "
                            + left.tileDType() + " and " + right.tileDType() + ".");
                }
                b.addPush(JavaKind.Object, new CUDATileTernaryNode(predicate, whenTrue, whenFalse, "select", left.tileDType(), //
                        broadcastShape(left.tileShape(), right.tileShape(), "select")));
                return true;
            }
        });

        r.register(new InvocationPlugin("fma", InvocationPlugin.Receiver.class, Tile.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tileA, ValueNode tileB, ValueNode tileC) {
                receiver.get(true);
                CUDATileNode left = tileNodeOf(tileA, "the first operand of fma");
                CUDATileNode right = tileNodeOf(tileB, "the second operand of fma");
                CUDATileNode addend = tileNodeOf(tileC, "the addend of fma");
                int[] shape = broadcastShape(broadcastShape(left.tileShape(), right.tileShape(), "fma"), addend.tileShape(), "fma");
                b.addPush(JavaKind.Object, new CUDATileTernaryNode(tileA, tileB, tileC, "fma", left.tileDType(), shape));
                return true;
            }
        });

        registerReduction(r, "sum", "sum");
    }

    /**
     * A two-operand op spelled as a function rather than an operator, such as {@code ct::max}.
     */
    /**
     * A shape-taking operation. The target shape must fold, like every tile shape; the sub-tile
     * indices of {@code extract} need not, because they are ordinary runtime values.
     */
    private static void registerShapeOp(Registration r, String name, int rank, int indexCount) {
        // One array, receiver first: the constructor takes Type... and a nested Class[] would
        // be one argument of the wrong type rather than a spread signature.
        java.lang.reflect.Type[] signature = new java.lang.reflect.Type[2 + rank + indexCount];
        signature[0] = InvocationPlugin.Receiver.class;
        signature[1] = Tile.class;
        for (int i = 2; i < signature.length; i++) {
            signature[i] = int.class;
        }
        r.register(new InvocationPlugin(name, signature) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                receiver.get(true);
                ValueNode tile = args[0];
                CUDATileNode source = tileNodeOf(tile, "the operand of " + name);
                int[] shape = new int[rank];
                for (int axis = 0; axis < rank; axis++) {
                    shape[axis] = shapeConstant(args[1 + axis], name + " shape");
                }
                ValueNode[] indices = new ValueNode[indexCount];
                for (int i = 0; i < indexCount; i++) {
                    indices[i] = args[1 + rank + i];
                }
                b.addPush(JavaKind.Object, new CUDATileShapeOpNode(tile, indices, name, source.tileDType(), shape));
                return true;
            }
        });
    }

    /**
     * An elementwise comparison, in both its tile-to-tile and tile-to-scalar forms. The result
     * is a {@link DType#PRED} tile regardless of the operand type.
     */
    private static void registerComparison(Registration r, String name, String operator) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode left, ValueNode right) {
                receiver.get(true);
                CUDATileNode leftTile = tileNodeOf(left, "the left operand of " + name);
                CUDATileNode rightTile = tileNodeOf(right, "the right operand of " + name);
                b.addPush(JavaKind.Object, new CUDATileBinaryNode(left, right, operator, DType.PRED, //
                        broadcastShape(leftTile.tileShape(), rightTile.tileShape(), name)));
                return true;
            }
        });
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode scalar) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of " + name);
                b.addPush(JavaKind.Object, new CUDATileScalarCompareNode(tile, scalar, operator, source.tileDType(), source.tileShape()));
                return true;
            }
        });
    }

    /**
     * Combines two predicate tiles, {@code mask & mask}. Separate from
     * {@link #registerBinary} only so the result type stays {@code PRED} and the operands are
     * checked to be predicates - composing a mask with an arithmetic tile is a mistake worth
     * catching at compile time.
     */
    private static void registerPredicateCombination(Registration r, String name, String operator) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode left, ValueNode right) {
                receiver.get(true);
                CUDATileNode leftTile = tileNodeOf(left, "the left operand of " + name);
                CUDATileNode rightTile = tileNodeOf(right, "the right operand of " + name);
                if (leftTile.tileDType() != DType.PRED || rightTile.tileDType() != DType.PRED) {
                    throw new IllegalStateException("[TileContext] " + name + " combines two comparison results, but it was given a "
                            + leftTile.tileDType() + " and a " + rightTile.tileDType() + " tile.");
                }
                b.addPush(JavaKind.Object, new CUDATileBinaryNode(left, right, operator, DType.PRED, //
                        broadcastShape(leftTile.tileShape(), rightTile.tileShape(), name)));
                return true;
            }
        });
    }

    private static void registerBinaryCall(Registration r, String name, String function) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode left, ValueNode right) {
                receiver.get(true);
                CUDATileNode leftTile = tileNodeOf(left, "the left operand of " + name);
                CUDATileNode rightTile = tileNodeOf(right, "the right operand of " + name);
                b.addPush(JavaKind.Object, new CUDATileBinaryCallNode(left, right, function, leftTile.tileDType(),
                        broadcastShape(leftTile.tileShape(), rightTile.tileShape(), name)));
                return true;
            }
        });
    }

    private static void registerUnaryMath(Registration r, String name, String function) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of " + name);
                b.addPush(JavaKind.Object, new CUDATileUnaryNode(tile, function, null, source.tileDType(), source.tileShape()));
                return true;
            }
        });
    }

    /**
     * Reductions keep the reduced dimension, as CUDA Tile C++ does, so the result broadcasts
     * back against the tile it came from.
     */
    private static void registerReduction(Registration r, String name, String function) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode axisNode) {
                receiver.get(true);
                CUDATileNode source = tileNodeOf(tile, "the operand of " + name);
                int axis = axisConstant(axisNode, source.tileShape().length);
                int[] shape = source.tileShape().clone();
                shape[axis] = 1;
                b.addPush(JavaKind.Object, new CUDATileReduceNode(tile, function, axis, source.tileDType(), shape));
                return true;
            }
        });
    }

    /**
     * A reduction axis is a plain index, not a tile dimension, so it is not required to be a
     * power of two the way {@link #shapeConstant} demands.
     */
    private static int axisConstant(ValueNode node, int rank) {
        JavaConstant constant = node.asJavaConstant();
        if (constant == null) {
            throw new IllegalStateException("[TileContext] The reduction axis must be a compile-time constant.");
        }
        int axis = constant.asInt();
        if (axis < 0 || axis >= rank) {
            throw new IllegalStateException("[TileContext] Reduction axis " + axis + " is out of range for a rank-" + rank + " tile.");
        }
        return axis;
    }

    private static void registerBinary(Registration r, String name, String operator) {
        r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, Tile.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode left, ValueNode right) {
                receiver.get(true);
                CUDATileNode leftTile = tileNodeOf(left, "the left operand of " + name);
                CUDATileNode rightTile = tileNodeOf(right, "the right operand of " + name);
                b.addPush(JavaKind.Object, new CUDATileBinaryNode(left, right, operator, leftTile.tileDType(),
                        broadcastShape(leftTile.tileShape(), rightTile.tileShape(), name)));
                return true;
            }
        });
    }

    /**
     * Result shape of an elementwise op. CUDA Tile stretches a dimension of 1 to match, which is
     * what lets a kept-dimension reduction combine with the tile it was reduced from - the shape
     * both softmax and RMS norm depend on.
     */
    private static int[] broadcastShape(int[] left, int[] right, String operation) {
        if (left.length != right.length) {
            throw new IllegalStateException("[TileContext] The operands of " + operation + " must have the same rank, got "
                    + left.length + " and " + right.length + ".");
        }
        int[] shape = new int[left.length];
        for (int i = 0; i < shape.length; i++) {
            if (left[i] != right[i] && left[i] != 1 && right[i] != 1) {
                throw new IllegalStateException("[TileContext] Dimension " + i + " of " + operation + " does not broadcast: "
                        + left[i] + " against " + right[i] + ".");
            }
            shape[i] = Math.max(left[i], right[i]);
        }
        return shape;
    }

    // -------------------------------------------------------------------------------------
    // Loads and stores, registered on PartitionView
    // -------------------------------------------------------------------------------------

    private static void registerAccessPlugins(Registration r) {
        registerLoad(r, "load", false, 1);
        registerLoad(r, "load", false, 2);
        registerLoad(r, "loadMasked", true, 1);
        registerLoad(r, "loadMasked", true, 2);
        registerStore(r, "store", false, 1);
        registerStore(r, "store", false, 2);
        registerStore(r, "storeMasked", true, 1);
        registerStore(r, "storeMasked", true, 2);
        registerAtomicAdd(r, 1);
        registerAtomicAdd(r, 2);
    }

    private static void registerLoad(Registration r, String name, boolean masked, int rank) {
        if (rank == 1) {
            r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode blockX) {
                    ValueNode view = receiver.get(true);
                    b.addPush(JavaKind.Object, loadOf(view, new ValueNode[] { blockX }, masked));
                    return true;
                }
            });
        } else {
            r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode blockX, ValueNode blockY) {
                    ValueNode view = receiver.get(true);
                    b.addPush(JavaKind.Object, loadOf(view, new ValueNode[] { blockX, blockY }, masked));
                    return true;
                }
            });
        }
    }

    private static void registerStore(Registration r, String name, boolean masked, int rank) {
        if (rank == 1) {
            r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode blockX) {
                    ValueNode view = receiver.get(true);
                    b.add(new CUDATileStoreNode(view, tile, new ValueNode[] { blockX }, masked));
                    return true;
                }
            });
        } else {
            r.register(new InvocationPlugin(name, InvocationPlugin.Receiver.class, Tile.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode blockX, ValueNode blockY) {
                    ValueNode view = receiver.get(true);
                    b.add(new CUDATileStoreNode(view, tile, new ValueNode[] { blockX, blockY }, masked));
                    return true;
                }
            });
        }
    }

    /**
     * Atomic accumulation through a view. Unlike a store this needs the view's buffer and
     * extents rather than the view value itself, because CUDA Tile has no read-modify-write on
     * a {@code partition_view} - see {@code CUDATileStmt.TileAtomicAddStmt}.
     */
    private static void registerAtomicAdd(Registration r, int rank) {
        if (rank == 1) {
            r.register(new InvocationPlugin("atomicAdd", InvocationPlugin.Receiver.class, Tile.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode blockX) {
                    // get(false): no null check. Unlike a store, this plugin does not consume
                    // the receiver as a node input - it reads the view's buffer and extents - so
                    // a null check would survive to code generation unused, and the CUDA backend
                    // has no emitter for one.
                    b.add(atomicAddOf(receiver.get(false), tile, new ValueNode[] { blockX }));
                    return true;
                }
            });
        } else {
            r.register(new InvocationPlugin("atomicAdd", InvocationPlugin.Receiver.class, Tile.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tile, ValueNode blockX, ValueNode blockY) {
                    b.add(atomicAddOf(receiver.get(false), tile, new ValueNode[] { blockX, blockY }));
                    return true;
                }
            });
        }
    }

    private static CUDATileAtomicAddNode atomicAddOf(ValueNode view, ValueNode tile, ValueNode[] indices) {
        CUDATileNode resolved = tileNodeOf(view, "the receiver of an atomic add");
        if (!(resolved instanceof CUDATilePartitionViewNode partition)) {
            throw new IllegalStateException("[TileContext] atomicAdd needs the partition view itself as its receiver, "
                    + "not a value derived from one.");
        }
        if (partition.getTileShape().length != indices.length) {
            throw new IllegalStateException("[TileContext] atomicAdd was given " + indices.length + " block indices for a rank-"
                    + partition.getTileShape().length + " view.");
        }
        return new CUDATileAtomicAddNode(partition.getBuffer(), tile, partition.getExtents(), indices, partition.getDType(), //
                partition.getTileShape(), partition.getPayloadOffset());
    }

    private static CUDATileLoadNode loadOf(ValueNode view, ValueNode[] indices, boolean masked) {
        CUDATileNode partition = tileNodeOf(view, "the receiver of a tile load");
        return new CUDATileLoadNode(view, indices, partition.tileDType(), partition.tileShape(), masked);
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    /**
     * Finds the tile node a value came from, seeing through the phi that a loop introduces.
     * An accumulator threaded through a loop reaches the mma plugin as a phi on every iteration
     * after the first, so resolving only the direct node would reject the canonical GEMM.
     */
    private static CUDATileNode tileNodeOf(ValueNode node, String role) {
        CUDATileNode resolved = resolveTileNode(node, 0);
        if (resolved == null || resolved.tileDType() == null || resolved.tileShape() == null) {
            throw new IllegalStateException("[TileContext] Could not determine the tile type of " + role
                    + ". A tile must come from TileContext or from a partition view in the same kernel; tiles cannot "
                    + "be passed across method boundaries or stored in fields.");
        }
        return resolved;
    }

    private static CUDATileNode resolveTileNode(ValueNode node, int depth) {
        if (node == null || depth > 8) {
            return null;
        }
        if (node instanceof CUDATileNode tileNode) {
            return tileNode;
        }
        // receiver.get(true) inserts a null-checking PiNode, so the receiver of a load or store
        // is never the partition node itself. Unwrapping it here is what makes av.load(...) work
        // at all; without it every tile access reports an unresolvable type.
        if (node instanceof PiNode pi) {
            return resolveTileNode(pi.object(), depth + 1);
        }
        // A value that escapes a loop is wrapped in a loop-exit proxy, so a tile produced inside
        // a loop and consumed after it arrives here as a ValueProxyNode rather than the node
        // itself. Missing this rejects any kernel that walks a row in chunks and then reduces.
        if (node instanceof ValueProxyNode proxy) {
            return resolveTileNode(proxy.value(), depth + 1);
        }
        if (node instanceof ValuePhiNode phi) {
            for (ValueNode input : phi.values()) {
                CUDATileNode candidate = resolveTileNode(input, depth + 1);
                if (candidate != null && candidate.tileDType() != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Reads a shape argument, which the API contract requires to be a compile-time constant.
     */
    private static int shapeConstant(ValueNode node, String role) {
        JavaConstant constant = node.asJavaConstant();
        if (constant == null) {
            throw new IllegalStateException("[TileContext] The " + role + " must be a compile-time constant, because "
                    + "a CUDA Tile shape is part of the kernel's type. Use a literal or a static final int.");
        }
        int value = constant.asInt();
        if (value <= 0 || (value & (value - 1)) != 0) {
            throw new IllegalStateException("[TileContext] The " + role + " must be a positive power of two, got " + value + ".");
        }
        return value;
    }

    /**
     * Folds a DType argument. Shares the enum-folding mechanics with the mma intrinsics, which
     * fold an MMAShape the same way.
     */
    private static DType resolveDType(GraphBuilderContext b, ValueNode dtypeNode) {
        return CUDAEnumFolding.resolveEnumConstant(b, dtypeNode, DType.class,
                "[TileContext] The element type must be a compile-time constant DType.");
    }

    /**
     * Renders a constant initialiser for ct::full in the tile's element type. The value has to
     * fold: it becomes part of the generated source, not a runtime argument.
     */
    private static String literal(DType dtype, ValueNode valueNode) {
        JavaConstant constant = valueNode.asJavaConstant();
        if (constant == null) {
            throw new IllegalStateException("[TileContext] The fill value passed to full(...) must be a compile-time "
                    + "constant, because it is emitted into the kernel source.");
        }
        double value = constant.asDouble();
        return switch (dtype) {
            case S8, S32 -> Long.toString((long) value);
            case F64 -> Double.toString(value);
            default -> value + "f";
        };
    }

    /**
     * The zero literal CUDA Tile wants for this element type. An integer tile takes a plain 0,
     * a floating point tile takes a typed literal so the ct::full template deduces correctly.
     */
    private static String zeroLiteral(DType dtype) {
        return switch (dtype) {
            case S8, S32 -> "0";
            case F64 -> "0.0";
            default -> "0.0f";
        };
    }
}

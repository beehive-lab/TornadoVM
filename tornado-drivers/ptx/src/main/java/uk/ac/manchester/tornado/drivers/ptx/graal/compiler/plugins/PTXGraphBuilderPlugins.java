/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2022, 2024, 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins;

import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.word.LocationIdentity;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoMemorySegment;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.DP4APackedNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.Dp4aNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXBarrierNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.function.Supplier;

import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SIGN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode.Operation.POPCOUNT;

public class PTXGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins, HotSpotMetaAccessProvider metaAccessProvider) {
        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            ps.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerFP16ConversionPlugins(plugins);
        registerTornadoIntrinsicsPlugins(plugins);
        registerPTXBuiltinPlugins(plugins);
        PTXMathPlugins.registerTornadoMathPlugins(plugins);
        PTXVectorPlugins.registerPlugins(ps, plugins);
        PTXHalfFloatPlugin.registerPlugins(ps, plugins);
        registerMemoryAccessPlugins(plugins, metaAccessProvider);
        registerKernelContextPlugins(plugins);
        registerQuantizationUtilsPlugins(plugins);
    }

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        var r = new Registration(plugins, Float.class);
        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                receiver.get(true);
                PTXConvertHalfToFloat convertHalfToFloat = new PTXConvertHalfToFloat(halfValue);
                b.addPush(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });
    }

    private static void registerQuantizationUtilsPlugins(InvocationPlugins plugins) {
        var r = new Registration(plugins, QuantizationUtils.class);
        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, Int8Array.class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b,
                    ValueNode accumulator) {
                receiver.get(true);
                Dp4aNode dp4aOp = new Dp4aNode(a, offset_a, b, offset_b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aOp);
                return true;
            }
        });

        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b,
                    ValueNode accumulator) {
                receiver.get(true);
                Dp4aNode dp4aOp = new Dp4aNode(a, offset_a, b, offset_b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aOp);
                return true;
            }
        });

        r.register(new InvocationPlugin("dp4a_packed", int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode b, ValueNode accumulator) {
                receiver.get(true);
                DP4APackedNode dp4aPackedOp = new DP4APackedNode(a, b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aPackedOp);
                return true;
            }
        });

    }

    private static void registerTornadoIntrinsicsPlugins(InvocationPlugins plugins) {
        final var printfPlugin = new InvocationPlugin("printf", String.class, Object[].class) {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 1];
                actualArgs[0] = args[0];

                int argIndex = 0;
                for (Node n : newArrayNode.usages()) {
                    if (n instanceof StoreIndexedNode storeIndexedNode) {
                        var value = storeIndexedNode.value();
                        if (value instanceof BoxNode boxNode) {
                            value = boxNode.getValue();
                            GraphUtil.unlinkFixedNode(boxNode);
                            boxNode.safeDelete();
                        }
                        actualArgs[argIndex + 1] = value;
                        argIndex++;
                    }

                }

                PrintfNode printfNode = new PrintfNode(actualArgs);
                b.add(b.append(printfNode));

                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.usages().first();
                    // need to remove all nodes from the graph that operate on the new array,
                    // however, we cannot remove all inputs as they may be used by the currently
                    // unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps in between fixed nodes
                    if (n instanceof FixedWithNextNode fixedWithNextNode) {
                        GraphUtil.unlinkFixedNode(fixedWithNextNode);
                    }
                    n.clearInputs();
                    n.safeDelete();
                }

                GraphUtil.unlinkFixedNode(newArrayNode);
                newArrayNode.clearInputs();
                newArrayNode.safeDelete();
                return true;
            }
        };

        plugins.register(Debug.class, printfPlugin);
    }

    private static void registerPTXBuiltinPlugins(InvocationPlugins plugins) {
        var r = new Registration(plugins, Math.class);
        r.setAllowOverwrite(true);
        registerPTXOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerPTXOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerPTXOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerPTXOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r, Float.TYPE, JavaKind.Float);
        registerFPIntrinsics(r, Double.TYPE, JavaKind.Double);

        var longReg = new Registration(plugins, Long.class);
        longReg.register(new InvocationPlugin("bitCount", Long.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(PTXIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register(new InvocationPlugin("bitCount", Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(PTXIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerLocalBarrier(Registration r) {
        r.register(new InvocationPlugin("localBarrier", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                PTXBarrierNode localBarrierNode = new PTXBarrierNode(0, -1);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register(new InvocationPlugin("globalBarrier", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                PTXBarrierNode localBarrierNode = new PTXBarrierNode(1, -1);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerAtomicAddOperation(Registration r) {
        // Accessing the vmConfig during initialization was causing a NullPointerException.
        // By using Suppliers, the getVMConfig() is only invoked at compile time, when the Supplier's get() is invoked in the plugins.
        Supplier<Integer> headerSupplier4Byte = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Int);
            return headerSize / JavaKind.Int.getByteCount(); // 16/4=4 or 24/4=6
        };

        Supplier<Integer> headerSupplier8Byte = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Long);
            return headerSize / JavaKind.Long.getByteCount(); // 16/8=2 or 24/8=3
        };

        registerAtomicAddPlugin(r, "atomicAdd", IntArray.class, PTXKind.U32, headerSupplier4Byte);
        registerAtomicAddPlugin(r, "atomicAdd", int[].class, PTXKind.U32, headerSupplier4Byte);
        registerAtomicAddPlugin(r, "atomicAdd", LongArray.class, PTXKind.U64, headerSupplier8Byte);
        registerAtomicAddPlugin(r, "atomicAdd", FloatArray.class, PTXKind.F32, headerSupplier4Byte);
        registerAtomicAddPlugin(r, "atomicAdd", DoubleArray.class, PTXKind.F64, headerSupplier8Byte);
    }

    private static void registerAtomicAddPlugin(Registration r, String methodName, Class<?> arrayType, PTXKind kind, Supplier<Integer> headerSupplier) {
        r.register(new InvocationPlugin(methodName, InvocationPlugin.Receiver.class, arrayType, Integer.TYPE, kind.asJavaKind().toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode segment, ValueNode index, ValueNode inc) {
                JavaKind javaKind = kind.asJavaKind();
                int header = headerSupplier.get();
                AddressNode address = computeAddress(b, segment, index, header, javaKind);
                AtomAddNodeTemplate atomicAddNode = new AtomAddNodeTemplate(address, inc, javaKind);
                b.add(b.append(atomicAddNode));
                return true;
            }
        });
    }

    private static AddressNode computeAddress(GraphBuilderContext b, ValueNode segment, ValueNode index, int panamaOffset, JavaKind kind) {
        ConstantNode constantNode = b.append(new ConstantNode(new RawConstant(panamaOffset), StampFactory.forKind(JavaKind.Int)));
        AddNode newIndex = b.append(new AddNode(index, constantNode));
        SignExtendNode signExtendNode = b.append(new SignExtendNode(newIndex, PTXKind.U64.asJavaKind().getBitCount()));
        MulNode mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forInt(kind.getByteCount())));
        return b.append(new OffsetAddressNode(segment, mulNode));
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateIntLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerLongLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateLongLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerFloatLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateFloatLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerDoubleLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateDoubleLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerByteLocalArray(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("allocateByteLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                // if we do not pass the resolved type, the compiler cannot deduct if the type is char or byte
                MetaAccessProvider metaAccess = b.getMetaAccess();
                ResolvedJavaType resolvedElementType = metaAccess.lookupJavaType(byte.class);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, resolvedElementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerHalfFloatLocalArray(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("allocateHalfFloatLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                MetaAccessProvider metaAccess = b.getMetaAccess();
                ResolvedJavaType resolvedElementType = metaAccess.lookupJavaType(short.class);
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.localSpace, resolvedElementType, size, PTXKind.F16);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        var returnedJavaKind = JavaKind.Object;

        var elementType = PTXKind.B32.asJavaKind();
        registerIntLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B64.asJavaKind();
        registerLongLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B32.asJavaKind();
        registerFloatLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B64.asJavaKind();
        registerDoubleLocalArray(r, returnedJavaKind, elementType);

        registerByteLocalArray(r, returnedJavaKind);

        returnedJavaKind = JavaKind.fromJavaClass(short.class);
        registerHalfFloatLocalArray(r, returnedJavaKind);
    }

    private static void registerKernelContextPlugins(InvocationPlugins plugins) {
        var r = new Registration(plugins, KernelContext.class);

        registerLocalBarrier(r);
        registerGlobalBarrier(r);
        localArraysPlugins(r);
        registerAtomicAddOperation(r);
    }

    private static void registerFPIntrinsics(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("pow", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("signum", Float.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(PTXFPUnaryIntrinsicNode.create(value, SIGN, JavaKind.Float)));
                return true;
            }
        });

        r.register(new InvocationPlugin("signum", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(PTXFPUnaryIntrinsicNode.create(value, SIGN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(PTXFPUnaryIntrinsicNode.create(value, TAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(PTXFPUnaryIntrinsicNode.create(value, TANH, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(PTXFPUnaryIntrinsicNode.create(value, ATAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });
    }

    private static void registerPTXOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    private static void registerMemoryAccessPlugins(InvocationPlugins plugins, HotSpotMetaAccessProvider metaAccessProvider) {
        var r = new Registration(plugins, TornadoMemorySegment.class);
        for (JavaKind kind : JavaKind.values()) {
            if (kind != JavaKind.Object && kind != JavaKind.Void && kind != JavaKind.Illegal) {
                r.register(new InvocationPlugin("get" + kind.name() + "AtIndex", Receiver.class, int.class, int.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode baseIndex) {
                        var absoluteIndexNode = b.append(new AddNode(index, baseIndex));
                        var signExtendNode = b.append(new SignExtendNode(absoluteIndexNode, PTXKind.U64.asJavaKind().getBitCount()));
                        var mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forLong(kind.getByteCount())));
                        var addressNode = b.append(new OffsetAddressNode(receiver.get(true), mulNode));
                        var readNode = new JavaReadNode(kind, addressNode, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.PLAIN, false);
                        b.addPush(kind, readNode);
                        return true;
                    }
                });
                r.register(new InvocationPlugin("setAtIndex", Receiver.class, int.class, kind.toJavaClass(), int.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode value, ValueNode baseIndex) {
                        var absoluteIndexNode = b.append(new AddNode(index, baseIndex));
                        var signExtendNode = b.append(new SignExtendNode(absoluteIndexNode, PTXKind.U64.asJavaKind().getBitCount()));
                        var mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forLong(kind.getByteCount())));
                        var addressNode = b.append(new OffsetAddressNode(receiver.get(true), mulNode));
                        var writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                        b.add(writeNode);
                        return true;
                    }
                });
            }
        }
    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new PTXVectorNodePlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        PTXVectorPlugins.registerParameterPlugins(plugins);
    }
}

/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getJavaKindFromValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode.Operation.ATAN2;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.ACOS;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.ASIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntUnaryIntrinsicNode.Operation.POPCOUNT;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;

import tornado.graal.compiler.core.common.memory.BarrierType;
import tornado.graal.compiler.core.common.memory.MemoryOrderMode;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.PiNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.AddNode;
import tornado.graal.compiler.nodes.calc.MulNode;
import tornado.graal.compiler.nodes.calc.SignExtendNode;
import tornado.graal.compiler.nodes.extended.BoxNode;
import tornado.graal.compiler.nodes.extended.JavaReadNode;
import tornado.graal.compiler.nodes.extended.JavaWriteNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import tornado.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import tornado.graal.compiler.nodes.java.LoadFieldNode;
import tornado.graal.compiler.nodes.java.NewArrayNode;
import tornado.graal.compiler.nodes.java.StoreIndexedNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.memory.address.OffsetAddressNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.DecAtomicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.GetAtomicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalBarrierNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSIMDShuffleDownNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSIMDUnaryNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSimdgroupMatrixZeroNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSimdgroupMatrixLoadNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSimdgroupMatrixMmaNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalSimdgroupMatrixStoreNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalDp4aNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.ReadHalfFloatNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.WriteHalfFloatNode;
import uk.ac.manchester.tornado.api.types.arrays.TornadoMemorySegment;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class MetalGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins) {
        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            ps.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerFP16ConversionPlugins(plugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

        // Register KernelContext Plugins (includes atomics)
        registerKernelContextPlugins(plugins);

        MetalMathPlugins.registerTornadoMathPlugins(plugins);
        registerMetalBuiltinPlugins(plugins);
        MetalVectorPlugins.registerPlugins(ps, plugins);

        // Register TornadoAtomicInteger
        registerTornadoAtomicInteger(ps, plugins);

        MetalHalfFloatPlugins.registerPlugins(ps, plugins);
        registerMemoryAccessPlugins(ps, plugins);
        registerNativeArrayAccessPlugins(plugins);
        registerQuantizationUtilsPlugins(plugins);

    }

    private static boolean isMethodFromAtomicClass(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName().equals("uk.ac.manchester.tornado.api.atomics.TornadoAtomicInteger") || method.getDeclaringClass().toJavaName().equals(
                "java.util.concurrent.atomic.AtomicInteger");
    }

    private static void registerAtomicCall(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("incrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new IncAtomicNode(receiver.get(), MetalUnary.AtomicOperator.INCREMENT_AND_GET)));
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndIncrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new IncAtomicNode(receiver.get(), MetalUnary.AtomicOperator.GET_AND_INCREMENT)));
                return true;
            }
        });

        r.register(new InvocationPlugin("decrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(), MetalUnary.AtomicOperator.DECREMENT_AND_GET)));
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndDecrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(), MetalUnary.AtomicOperator.GET_AND_DECREMENT)));
                return true;
            }
        });

        r.register(new InvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new GetAtomicNode(receiver.get())));
                return true;
            }
        });
    }

    private static void registerTornadoAtomicInteger(final Plugins ps, InvocationPlugins plugins) {

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (isMethodFromAtomicClass(method) && method.getName().equals("<init>")) {
                    final TornadoAtomicIntegerNode atomic = resolveReceiverAtomic(args[0]);
                    if (atomic != null && args.length > 1) {
                        // ========================================================
                        // DOCUMENTATION:
                        // args[0] = current node (new node)
                        // args[1] = arguments to the invoke node being substituted
                        // ========================================================
                        ValueNode initialValue = args[1];
                        if (initialValue instanceof ConstantNode) {
                            int value = Integer.parseInt(((ConstantNode) initialValue).getValue().toValueString());
                            if (value == 0) {
                                atomic.setInitialValue(initialValue);
                            } else {
                                atomic.setInitialValueAtUsages(initialValue);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        Class<?> declaringClass = java.util.concurrent.atomic.AtomicInteger.class;
        JavaKind returnedJavaKind = MetalKind.INT.asJavaKind();
        Registration r1 = new Registration(plugins, declaringClass);
        registerAtomicCall(r1, returnedJavaKind);
    }

    private static TornadoAtomicIntegerNode resolveReceiverAtomic(ValueNode thisObject) {
        TornadoAtomicIntegerNode atomicNode = null;
        if (thisObject instanceof PiNode) {
            thisObject = ((PiNode) thisObject).getOriginalNode();
        }
        if (thisObject instanceof TornadoAtomicIntegerNode) {
            atomicNode = (TornadoAtomicIntegerNode) thisObject;
        }
        return atomicNode;
    }

    private static void registerLocalBarrier(Registration r) {
        r.register(new InvocationPlugin("localBarrier", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                MetalBarrierNode localBarrierNode = new MetalBarrierNode(MetalBarrierNode.MetalMemFenceFlags.LOCAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register(new InvocationPlugin("globalBarrier", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                MetalBarrierNode localBarrierNode = new MetalBarrierNode(MetalBarrierNode.MetalMemFenceFlags.GLOBAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerAtomicAddOperation(Registration r) {
        int intOffset = (int) (TornadoNativeArray.ARRAY_HEADER / Integer.BYTES);
        int longOffset = (int) (TornadoNativeArray.ARRAY_HEADER / Long.BYTES);
        int floatOffset = (int) (TornadoNativeArray.ARRAY_HEADER / Float.BYTES);
        int doubleOffset = (int) (TornadoNativeArray.ARRAY_HEADER / Double.BYTES);
        registerAtomicAddPlugin(r, "atomicAdd", IntArray.class, MetalKind.UINT, intOffset);
        registerAtomicAddPlugin(r, "atomicAdd", int[].class, MetalKind.UINT, intOffset);
        registerAtomicAddPlugin(r, "atomicAdd", LongArray.class, MetalKind.ULONG, longOffset);
        registerAtomicAddPlugin(r, "atomicAdd", FloatArray.class, MetalKind.FLOAT, floatOffset);
        registerAtomicAddPlugin(r, "atomicAdd", DoubleArray.class, MetalKind.DOUBLE, doubleOffset);
    }

    private static void registerAtomicAddPlugin(Registration r, String methodName, Class<?> arrayType, MetalKind kind, int panamaOffset) {
        r.register(new InvocationPlugin(methodName, InvocationPlugin.Receiver.class, arrayType, Integer.TYPE, kind.asJavaKind().toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode segment, ValueNode index, ValueNode inc) {
                JavaKind javaKind = kind.asJavaKind();
                AddressNode address = computeAddress(b, segment, index, panamaOffset, javaKind);
                AtomAddNodeTemplate atomicAddNode = new AtomAddNodeTemplate(address, inc, javaKind);
                b.add(b.append(atomicAddNode));
                return true;
            }
        });
    }


    private static AddressNode computeAddress(GraphBuilderContext b, ValueNode segment, ValueNode index, int panamaOffset, JavaKind kind) {
        ConstantNode constantNode = b.append(new ConstantNode(new RawConstant(panamaOffset), StampFactory.forKind(JavaKind.Int)));
        AddNode newIndex = b.append(new AddNode(index, constantNode));
        SignExtendNode signExtendNode = b.append(new SignExtendNode(newIndex, MetalKind.LONG.asJavaKind().getBitCount()));
        MulNode mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forInt(kind.getByteCount())));
        return b.append(new OffsetAddressNode(segment, mulNode));
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateIntLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerLongLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateLongLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerFloatLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateFloatLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerDoubleLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateDoubleLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerByteLocalArray(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("allocateByteLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                jdk.vm.ci.meta.MetaAccessProvider metaAccess = b.getMetaAccess();
                jdk.vm.ci.meta.ResolvedJavaType resolvedElementType = metaAccess.lookupJavaType(byte.class);
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, resolvedElementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerHalfFloatLocalArray(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("allocateHalfFloatLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(MetalArchitecture.localSpace, MetalKind.HALF, MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_HALF_ARRAY, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Object;

        JavaKind elementType = MetalKind.INT.asJavaKind();
        registerIntLocalArray(r, returnedJavaKind, elementType);

        elementType = MetalKind.LONG.asJavaKind();
        registerLongLocalArray(r, returnedJavaKind, elementType);

        elementType = MetalKind.FLOAT.asJavaKind();
        registerFloatLocalArray(r, returnedJavaKind, elementType);

        elementType = MetalKind.DOUBLE.asJavaKind();
        registerDoubleLocalArray(r, returnedJavaKind, elementType);

        registerByteLocalArray(r, returnedJavaKind);

        returnedJavaKind = JavaKind.fromJavaClass(short.class);
        registerHalfFloatLocalArray(r, returnedJavaKind);
    }

    private static void registerSIMDPlugins(Registration r) {
        r.register(new InvocationPlugin("simdSum", Receiver.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val) {
                b.addPush(JavaKind.Float, new MetalSIMDUnaryNode(val, MetalSIMDUnaryNode.Operation.SIMD_SUM));
                return true;
            }
        });
        r.register(new InvocationPlugin("simdBroadcastFirst", Receiver.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val) {
                b.addPush(JavaKind.Float, new MetalSIMDUnaryNode(val, MetalSIMDUnaryNode.Operation.SIMD_BROADCAST_FIRST));
                return true;
            }
        });
        r.register(new InvocationPlugin("simdShuffleDown", Receiver.class, float.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val, ValueNode delta) {
                b.addPush(JavaKind.Float, new MetalSIMDShuffleDownNode(val, delta));
                return true;
            }
        });
        registerSimdgroupMatrixPrimitives(r);
    }

    /**
     * Low-level matrix-unit (simdgroup_float8x8) primitives: zero / load / multiply-accumulate
     * / store. The surrounding GEMM loop is ordinary Java compiled by the normal pipeline; only
     * these calls become hardware instructions. The fragment flows as an opaque
     * {@code SIMDGROUP_FLOAT8X8} value (pushed as a {@code Matrix8x8Float} object reference).
     */
    private static void registerSimdgroupMatrixPrimitives(Registration r) {
        r.register(new InvocationPlugin("simdgroupMatrixZero", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new MetalSimdgroupMatrixZeroNode());
                return true;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixLoad", Receiver.class, FloatArray.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode base, ValueNode stride) {
                b.addPush(JavaKind.Object, new MetalSimdgroupMatrixLoadNode(array, base, stride));
                return true;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixLoad", Receiver.class, float[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode base, ValueNode stride) {
                b.addPush(JavaKind.Object, new MetalSimdgroupMatrixLoadNode(array, base, stride));
                return true;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixMultiplyAccumulate", Receiver.class, Matrix8x8Float.class, Matrix8x8Float.class, Matrix8x8Float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode bMat, ValueNode c) {
                b.addPush(JavaKind.Object, new MetalSimdgroupMatrixMmaNode(a, bMat, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixStore", Receiver.class, Matrix8x8Float.class, FloatArray.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode matrix, ValueNode array, ValueNode base, ValueNode stride) {
                b.add(new MetalSimdgroupMatrixStoreNode(matrix, array, base, stride));
                return true;
            }
        });
    }

    private static void registerKernelContextPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, KernelContext.class);

        registerLocalBarrier(r);
        registerGlobalBarrier(r);
        localArraysPlugins(r);
        registerAtomicAddOperation(r);
        registerSIMDPlugins(r);
        registerMMAPlugins(r);
        registerSwizzledLocalAccessesPlugins(r);
    }

    private static void registerMMAPlugins(Registration r) {
        // --- mmaFragment(float v) -> float[] ---
        r.register(new InvocationPlugin("mmaFragment",
                InvocationPlugin.Receiver.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode initValue) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadA(int[] aTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadA",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadB(int[] bTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadB",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadBSwizzled(HalfFloat[] bTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadBSwizzled",
                InvocationPlugin.Receiver.class, HalfFloat[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaStoreBSwizzled(HalfFloat[] arr, int row, int col, int stride, HalfFloat value, int byteOffset) -> void ---
        r.register(new InvocationPlugin("mmaStoreBSwizzled",
                InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class,
                int.class, HalfFloat.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode arr, ValueNode row, ValueNode col,
                                 ValueNode stride, ValueNode value, ValueNode byteOffset) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mma(HalfFloat[] fragA, HalfFloat[] fragB, float[] fragC, MMAShape shape) -> float[] ---
        r.register(new InvocationPlugin("mma",
                InvocationPlugin.Receiver.class,
                HalfFloat[].class, HalfFloat[].class, float[].class, MMAShape.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver,
                                 ValueNode fragA, ValueNode fragB, ValueNode fragC,
                                 ValueNode shapeNode) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaStore(float[] fragD, FloatArray c, int tileRow, int tileCol, int dimN) -> void ---
        r.register(new InvocationPlugin("mmaStore",
                InvocationPlugin.Receiver.class,
                float[].class, FloatArray.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver,
                                 ValueNode fragD, ValueNode target,
                                 ValueNode tileRow, ValueNode tileCol, ValueNode dimN) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("mmaFragmentInt",
                InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode initValue) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadAInt8(int[], int) -> byte[] ---
        r.register(new InvocationPlugin("mmaLoadAInt8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadBInt8(int[], int) -> byte[] ---
        r.register(new InvocationPlugin("mmaLoadBInt8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaInt8(byte[], byte[], int[], MMAShape) -> int[] ---
        r.register(new InvocationPlugin("mmaInt8",
                InvocationPlugin.Receiver.class,
                byte[].class, byte[].class, int[].class, MMAShape.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver,
                                 ValueNode fragA, ValueNode fragB, ValueNode fragC,
                                 ValueNode shapeNode) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaStoreInt(int[], IntArray, int, int, int) -> void ---
        r.register(new InvocationPlugin("mmaStoreInt",
                InvocationPlugin.Receiver.class,
                int[].class, IntArray.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver,
                                 ValueNode fragD, ValueNode target,
                                 ValueNode tileRow, ValueNode tileCol, ValueNode dimN) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadA(int[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadA",
                InvocationPlugin.Receiver.class, int[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadB(int[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadB",
                InvocationPlugin.Receiver.class, int[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

        // --- mmaLoadBSwizzled(HalfFloat[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadBSwizzled",
                InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                unimplemented("MMA instructions only supported for the PTX backend.");
                return false;
            }
        });

    }

    private static void registerSwizzledLocalAccessesPlugins(Registration r) {
        r.register(new InvocationPlugin("swizzleLoadFp16Stride32", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("swizzleStoreFp16Stride32", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride, ValueNode value) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("swizzleLoadFp16Stride16", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("swizzleStoreFp16Stride16", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride, ValueNode value) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("swizzleLoadInt8", InvocationPlugin.Receiver.class,
                byte[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                 ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });

        r.register(new InvocationPlugin("swizzleStoreInt8", InvocationPlugin.Receiver.class,
                byte[].class, int.class, int.class, int.class, byte.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                 ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride, ValueNode value) {
                unimplemented("Swizzled local memory accesses are currently only supported for the PTX backend.");
                return false;
            }
        });
    }

    private static void registerMemoryAccessPlugins(final Plugins ps, InvocationPlugins plugins) {
        // Register InvocationPlugins for TornadoMemorySegment get/set methods.
        // This prevents inlining of TornadoMemorySegment methods during bytecode parsing,
        // avoiding LoadField{TornadoMemorySegment#segment} nodes that break TornadoNativeTypeElimination.
        Registration r = new Registration(plugins, TornadoMemorySegment.class);
        for (JavaKind kind : JavaKind.values()) {
            if (kind != JavaKind.Object && kind != JavaKind.Void && kind != JavaKind.Illegal && kind != JavaKind.Boolean) {
                r.register(new InvocationPlugin("get" + kind.name() + "AtIndex", Receiver.class, int.class, int.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode baseIndex) {
                        ValueNode receiverNode = receiver.get(true);
                        ValueNode longIndex = b.append(SignExtendNode.create(index, 64, NodeView.DEFAULT));
                        ValueNode longBaseIndex = b.append(SignExtendNode.create(baseIndex, 64, NodeView.DEFAULT));
                        AddNode absoluteIndexNode = b.append(new AddNode(longIndex, longBaseIndex));
                        MulNode mulNode = b.append(new MulNode(absoluteIndexNode, ConstantNode.forLong(kind.getByteCount())));
                        AddressNode addressNode = b.append(new OffsetAddressNode(receiverNode, mulNode));
                        JavaReadNode readNode = new JavaReadNode(kind, addressNode, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.PLAIN, false);
                        b.addPush(kind, readNode);
                        return true;
                    }
                });
                r.register(new InvocationPlugin("setAtIndex", Receiver.class, int.class, kind.toJavaClass(), int.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode value, ValueNode baseIndex) {
                        ValueNode receiverNode = receiver.get(true);
                        ValueNode longIndex = b.append(SignExtendNode.create(index, 64, NodeView.DEFAULT));
                        ValueNode longBaseIndex = b.append(SignExtendNode.create(baseIndex, 64, NodeView.DEFAULT));
                        AddNode absoluteIndexNode = b.append(new AddNode(longIndex, longBaseIndex));
                        MulNode mulNode = b.append(new MulNode(absoluteIndexNode, ConstantNode.forLong(kind.getByteCount())));
                        AddressNode addressNode = b.append(new OffsetAddressNode(receiverNode, mulNode));
                        JavaWriteNode writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                        b.add(writeNode);
                        return true;
                    }
                });
            }
        }

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                // "MemorySegment.getAtIndex(ValueLayout, long)"
                if (!MemorySegment.class.getName().equals(method.getDeclaringClass().toJavaName())) {
                    return false;
                }
                if (!"getAtIndex".equals(method.getName())) {
                    return false;
                }
                if (args.length != 3) {
                    throw new TornadoRuntimeException("Expecting 3 arguments for getAtIndex but got " + args.length);
                }
                ValueNode receiver = args[0];
                ValueNode layout = args[1];
                ValueNode index = args[2];

                Class valueLayoutClass = getValueLayoutClass(layout);
                JavaKind kind = getJavaKindFromValueLayoutClass(valueLayoutClass);

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forLong(kind.getByteCount())));
                AddressNode addressNode = b.append(new OffsetAddressNode(receiver, mulNode));
                JavaReadNode readNode = new JavaReadNode(kind, addressNode, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.PLAIN, false);
                b.addPush(kind, readNode);
                return true;
            }
        });
        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                // "MemorySegment.setAtIndex(ValueLayout, long, kind)"
                if (!MemorySegment.class.getName().equals(method.getDeclaringClass().toJavaName())) {
                    return false;
                }
                if (!"setAtIndex".equals(method.getName())) {
                    return false;
                }
                if (args.length != 4) {
                    throw new TornadoRuntimeException("Expecting 4 arguments for setAtIndex but got " + args.length);
                }
                ValueNode receiver = args[0];
                ValueNode layout = args[1];
                ValueNode index = args[2];
                ValueNode value = args[3];

                Class valueLayoutClass = getValueLayoutClass(layout);
                JavaKind kind = getJavaKindFromValueLayoutClass(valueLayoutClass);

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forLong(kind.getByteCount())));
                AddressNode addressNode = b.append(new OffsetAddressNode(receiver, mulNode));
                JavaWriteNode writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                b.add(writeNode);
                return true;
            }
        });
    }

    /**
     * Intrinsify the native-array {@code get(index)}/{@code set(index, value)} accessors directly on the array
     * classes (IntArray, FloatArray, ...). On the JVMCI-free reflection path the sketcher cannot descend into
     * {@code IntArray.get -> TornadoMemorySegment.getIntAtIndex -> MemorySegment.getAtIndex} (abstract/bodiless on
     * JDK 22+), so the accessor invoke survives and later crashes canonicalization (null receiver in
     * MethodCallTargetNode). Registering the accessor at the top level emits the same address/read/write the
     * inlined intrinsic would have produced. Mirrors the OpenCL and CUDA backends.
     */
    private static void registerNativeArrayAccessPlugins(InvocationPlugins plugins) {
        registerNativeArrayGetSet(plugins, IntArray.class, JavaKind.Int);
        registerNativeArrayGetSet(plugins, FloatArray.class, JavaKind.Float);
        registerNativeArrayGetSet(plugins, DoubleArray.class, JavaKind.Double);
        registerNativeArrayGetSet(plugins, LongArray.class, JavaKind.Long);
        registerNativeArrayGetSet(plugins, ShortArray.class, JavaKind.Short);
        registerNativeArrayGetSet(plugins, ByteArray.class, JavaKind.Byte);
        registerNativeArrayGetSet(plugins, Int8Array.class, JavaKind.Byte);
        registerNativeArrayGetSet(plugins, CharArray.class, JavaKind.Char);
        registerHalfFloatArrayGetSet(plugins);
        registerByteArrayHalfFloatAccess(plugins);
    }

    /**
     * Like {@link #registerNativeArrayGetSet}, but for {@link HalfFloatArray} whose {@code get}/{@code set} wrap a
     * {@link HalfFloat} around a 2-byte {@code short} segment slot. Emits {@link ReadHalfFloatNode}/
     * {@link WriteHalfFloatNode} directly at the accessor call site so the delegate chain
     * {@code get -> TornadoMemorySegment.getShortAtIndex -> MemorySegment.getAtIndex} (abstract, bodiless on JDK 22+)
     * is never reached on the reflection path. Mirrors the OpenCL and CUDA backends.
     */
    private static void registerHalfFloatArrayGetSet(InvocationPlugins plugins) {
        final Field segmentField;
        final Field baseIndexField;
        try {
            segmentField = HalfFloatArray.class.getDeclaredField("segment");
            baseIndexField = HalfFloatArray.class.getDeclaredField("baseIndex");
        } catch (NoSuchFieldException e) {
            throw new TornadoRuntimeException("HalfFloatArray is missing expected fields for intrinsification: " + e);
        }
        Registration r = new Registration(plugins, HalfFloatArray.class);
        r.register(new InvocationPlugin("get", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index) {
                AddressNode addressNode = arrayElementAddress(b, receiver, index, JavaKind.Short, segmentField, baseIndexField);
                ReadHalfFloatNode readNode = b.append(new ReadHalfFloatNode(addressNode));
                b.push(JavaKind.Object, readNode);
                return true;
            }
        });
        r.register(new InvocationPlugin("set", Receiver.class, int.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode value) {
                AddressNode addressNode = arrayElementAddress(b, receiver, index, JavaKind.Short, segmentField, baseIndexField);
                WriteHalfFloatNode writeNode = new WriteHalfFloatNode(addressNode, value);
                b.add(writeNode);
                return true;
            }
        });
    }

    /**
     * Intrinsify {@code ByteArray.getHalfFloat(byteIndex)}/{@code setHalfFloat(byteIndex, HalfFloat)} directly.
     * These read/write a 2-byte HALF at a raw BYTE offset (Q8_0 tensors pack a fp16 scale inside a byte buffer),
     * delegating to the abstract {@code MemorySegment.getShortAtIndex/getAtIndex} the reflection-path sketcher
     * cannot descend into ("readFieldValue not implemented" / a bogus folded index). The byte offset is
     * {@code baseIndex + byteIndex} (baseIndex == arrayHeaderSize for ByteArray), i.e. {@link #arrayElementAddress}
     * with a Byte element size. Mirrors the OpenCL and CUDA backends.
     */
    private static void registerByteArrayHalfFloatAccess(InvocationPlugins plugins) {
        final Field segmentField;
        final Field baseIndexField;
        try {
            segmentField = ByteArray.class.getDeclaredField("segment");
            baseIndexField = ByteArray.class.getDeclaredField("baseIndex");
        } catch (NoSuchFieldException e) {
            throw new TornadoRuntimeException("ByteArray is missing expected fields for intrinsification: " + e);
        }
        Registration r = new Registration(plugins, ByteArray.class);
        r.register(new InvocationPlugin("getHalfFloat", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode byteIndex) {
                AddressNode addressNode = arrayElementAddress(b, receiver, byteIndex, JavaKind.Byte, segmentField, baseIndexField);
                ReadHalfFloatNode readNode = b.append(new ReadHalfFloatNode(addressNode));
                b.push(JavaKind.Object, readNode);
                return true;
            }
        });
        r.register(new InvocationPlugin("setHalfFloat", Receiver.class, int.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode byteIndex, ValueNode value) {
                AddressNode addressNode = arrayElementAddress(b, receiver, byteIndex, JavaKind.Byte, segmentField, baseIndexField);
                WriteHalfFloatNode writeNode = new WriteHalfFloatNode(addressNode, value);
                b.add(writeNode);
                return true;
            }
        });
    }

    private static void registerNativeArrayGetSet(InvocationPlugins plugins, Class<?> arrayClass, JavaKind kind) {
        final Field segmentField;
        final Field baseIndexField;
        try {
            segmentField = arrayClass.getDeclaredField("segment");
            baseIndexField = arrayClass.getDeclaredField("baseIndex");
        } catch (NoSuchFieldException e) {
            throw new TornadoRuntimeException("Native array type " + arrayClass.getName() + " is missing expected fields for intrinsification: " + e);
        }
        Registration r = new Registration(plugins, arrayClass);
        r.register(new InvocationPlugin("get", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index) {
                AddressNode addressNode = arrayElementAddress(b, receiver, index, kind, segmentField, baseIndexField);
                JavaReadNode readNode = new JavaReadNode(kind, addressNode, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.PLAIN, false);
                b.addPush(kind, readNode);
                return true;
            }
        });
        r.register(new InvocationPlugin("set", Receiver.class, int.class, kind.toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode value) {
                AddressNode addressNode = arrayElementAddress(b, receiver, index, kind, segmentField, baseIndexField);
                JavaWriteNode writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                b.add(writeNode);
                return true;
            }
        });
    }

    /**
     * Build {@code &segment[(baseIndex + index) * elementBytes]} for a native array accessor, loading the
     * {@code segment} and {@code baseIndex} fields from the array so the emitted graph matches the inlined
     * {@code TornadoMemorySegment.get/setAtIndex} intrinsic.
     */
    private static AddressNode arrayElementAddress(GraphBuilderContext b, Receiver receiver, ValueNode index, JavaKind kind, Field segmentField, Field baseIndexField) {
        ValueNode arrayNode = receiver.get(true); // adds the null check
        ResolvedJavaField segment = b.getMetaAccess().lookupJavaField(segmentField);
        ResolvedJavaField baseIndex = b.getMetaAccess().lookupJavaField(baseIndexField);
        ValueNode segmentNode = b.append(LoadFieldNode.create(b.getGraph().getAssumptions(), arrayNode, segment));
        ValueNode baseIndexNode = b.append(LoadFieldNode.create(b.getGraph().getAssumptions(), arrayNode, baseIndex));
        ValueNode longIndex = b.append(SignExtendNode.create(index, 64, NodeView.DEFAULT));
        ValueNode longBaseIndex = b.append(SignExtendNode.create(baseIndexNode, 64, NodeView.DEFAULT));
        AddNode absoluteIndexNode = b.append(new AddNode(longIndex, longBaseIndex));
        MulNode mulNode = b.append(new MulNode(absoluteIndexNode, ConstantNode.forLong(kind.getByteCount())));
        return b.append(new OffsetAddressNode(segmentNode, mulNode));
    }

    private static void registerQuantizationUtilsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, QuantizationUtils.class);

        // dp4a(Int8Array a, long offsetA, Int8Array b, long offsetB, int c)
        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, Int8Array.class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                    ValueNode a, ValueNode offsetA, ValueNode bArr, ValueNode offsetB, ValueNode accumulator) {
                b.addPush(JavaKind.Int, b.append(new MetalDp4aNode(a, offsetA, bArr, offsetB, accumulator)));
                return true;
            }
        });

        // dp4a(Int8Array a, long offsetA, byte[] b, long offsetB, int c) - local array variant
        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                    ValueNode a, ValueNode offsetA, ValueNode bArr, ValueNode offsetB, ValueNode accumulator) {
                b.addPush(JavaKind.Int, b.append(new MetalDp4aNode(a, offsetA, bArr, offsetB, accumulator)));
                return true;
            }
        });

    }

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);

        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                MetalConvertHalfToFloat convertHalfToFloat = new MetalConvertHalfToFloat(halfValue);
                b.addPush(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });
    }

    private static void registerTornadoVMIntrinsicsPlugins(InvocationPlugins plugins) {
        final InvocationPlugin printfPlugin = new InvocationPlugin("printf", String.class, Object[].class) {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 4];
                actualArgs[0] = args[0];

                actualArgs[1] = b.append(new GlobalThreadIdNode(ConstantNode.forInt(0)));
                actualArgs[2] = b.append(new GlobalThreadIdNode(ConstantNode.forInt(1)));
                actualArgs[3] = b.append(new GlobalThreadIdNode(ConstantNode.forInt(2)));

                int argIndex = 0;
                for (Node n : newArrayNode.usages()) {
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            value = box.getValue();
                            GraphUtil.unlinkFixedNode(box);
                            box.safeDelete();
                        }
                        actualArgs[argIndex + 4] = value;
                        argIndex++;
                    }

                }

                PrintfNode printfNode = new PrintfNode(actualArgs);
                b.append(printfNode);

                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.usages().first();
                    // need to remove all nodes from the graph that operate on
                    // the new array. However, we cannot remove all inputs as they
                    // may be used by the currently unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps in
                    // between fixed nodes
                    if (n instanceof FixedWithNextNode) {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
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

    private static void registerMetalBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        // We have to overwrite some of the standard math plugins
        r.setAllowOverwrite(true);
        registerMetalOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerMetalOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerMetalOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerMetalOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r);

        Registration longReg = new Registration(plugins, Long.class);
        longReg.register(new InvocationPlugin("bitCount", Long.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(MetalIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Long)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register(new InvocationPlugin("bitCount", Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(MetalIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerFPIntrinsics(Registration r) {
        r.register(new InvocationPlugin("pow", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(MetalFPBinaryIntrinsicNode.create(x, y, POW, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, SIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, COS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, TAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, TANH, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, ATAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(MetalFPBinaryIntrinsicNode.create(x, y, ATAN2, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(x, ASIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(x, ACOS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, LOG, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(MetalFPUnaryIntrinsicNode.create(value, EXP, JavaKind.Double)));
                return true;
            }
        });
    }

    private static void registerMetalOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(MetalFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(MetalIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(MetalFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(MetalIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(MetalFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new MetalVectorNodePlugin());
        plugins.appendNodePlugin(new MetalAtomicIntegerPlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        MetalVectorPlugins.registerParameterPlugins(plugins);
    }
}

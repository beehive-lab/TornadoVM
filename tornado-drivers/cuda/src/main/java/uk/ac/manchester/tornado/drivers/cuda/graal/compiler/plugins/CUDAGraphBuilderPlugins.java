/*
 * Copyright (c) 2022, 2024-2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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

import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
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
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.word.LocationIdentity;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoMemorySegment;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAComputeNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAFragmentNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadAInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadANode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBInt8Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMALoadBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreBSwizzledNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAMMAStoreNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAShuffleDownNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASimdBroadcastFirstNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASimdSumNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.DecAtomicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GetAtomicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.DP4APackedNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.Dp4aNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDABarrierNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAConvertFP8ToFloat;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASwizzledLoadFP16Stride32Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDASwizzledStoreFP16Stride32Node;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.function.Supplier;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.ATAN2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ACOS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ASIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntUnaryIntrinsicNode.Operation.POPCOUNT;

public class CUDAGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins, final HotSpotMetaAccessProvider metaAccessProvider) {
        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            ps.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerFP16ConversionPlugins(plugins);
        registerFP8ConversionPlugins(plugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

        // Register Atomics
        registerKernelContextPlugins(plugins);

        CUDAMathPlugins.registerTornadoMathPlugins(plugins);
        registerOpenCLBuiltinPlugins(plugins);
        CUDAVectorPlugins.registerPlugins(ps, plugins);

        // Register TornadoAtomicInteger
        registerTornadoAtomicInteger(ps, plugins);

        CUDAHalfFloatPlugins.registerPlugins(ps, plugins);
        registerMemoryAccessPlugins(plugins, metaAccessProvider);
        registerQuantizationUtilsPlugins(plugins);
    }

    private static boolean isMethodFromAtomicClass(ResolvedJavaMethod method) {
        return method.getDeclaringClass() //
        .toJavaName() //
            .equals("uk.ac.manchester.tornado.api.atomics.TornadoAtomicInteger")  //
            || method.getDeclaringClass().toJavaName() //
            .equals("java.util.concurrent.atomic.AtomicInteger");
    }

    private static void registerAtomicCall(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("incrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                IncAtomicNode atomicNode = new IncAtomicNode(receiver.get(true), CUDAUnary.AtomicOperator.INCREMENT_AND_GET);
                b.getGraph().addOrUnique(atomicNode);
                b.addPush(returnedJavaKind, atomicNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndIncrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                b.addPush(returnedJavaKind, b.append(new IncAtomicNode(receiver.get(true), CUDAUnary.AtomicOperator.GET_AND_INCREMENT)));
                return true;
            }
        });

        r.register(new InvocationPlugin("decrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(true), CUDAUnary.AtomicOperator.DECREMENT_AND_GET)));
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndDecrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(true), CUDAUnary.AtomicOperator.GET_AND_DECREMENT)));
                return true;
            }
        });

        r.register(new InvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                b.addPush(returnedJavaKind, b.append(new GetAtomicNode(receiver.get(true))));
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
                        if (initialValue instanceof ConstantNode constantNode) {
                            int value = Integer.parseInt(constantNode.getValue().toValueString());
                            if (value == 0) {
                                atomic.setInitialValue(constantNode);
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
        JavaKind returnedJavaKind = CUDAKind.INT.asJavaKind();
        Registration r1 = new Registration(plugins, declaringClass);
        registerAtomicCall(r1, returnedJavaKind);
    }

    private static TornadoAtomicIntegerNode resolveReceiverAtomic(ValueNode thisObject) {
        TornadoAtomicIntegerNode atomicNode = null;
        if (thisObject instanceof PiNode objectAsPiNode) {
            thisObject = objectAsPiNode.getOriginalNode();
        }
        if (thisObject instanceof TornadoAtomicIntegerNode returnedAtomicNode) {
            atomicNode = returnedAtomicNode;
        }
        return atomicNode;
    }

    private static void registerLocalBarrier(Registration r) {
        r.register(new InvocationPlugin("localBarrier", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                CUDABarrierNode localBarrierNode = new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.LOCAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register(new InvocationPlugin("globalBarrier", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                CUDABarrierNode localBarrierNode = new CUDABarrierNode(CUDABarrierNode.CUDAMemFenceFlags.GLOBAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerAtomicAddOperation(Registration r) {
        // Accessing the vmConfig during initialization was causing a NullPointerException.
        // By using Suppliers, the getVMConfig() is only invoked at compile time, when the Supplier's get() is invoked in the plugins.
        Supplier<Integer> intHeaderSupplier = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Int);
            return headerSize / JavaKind.Int.getByteCount(); // 16/4=4 or 24/4=6
        };

        Supplier<Integer> longHeaderSupplier = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Long);
            return headerSize / JavaKind.Long.getByteCount(); // 16/8=2 or 24/8=3
        };
        Supplier<Integer> floatHeaderSupplier = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Float);
            return headerSize / JavaKind.Float.getByteCount();
        };

        Supplier<Integer> doubleHeaderSupplier = () -> {
            var vmConfig = TornadoCoreRuntime.getVMConfig();
            int headerSize = vmConfig.getArrayBaseOffset(JavaKind.Double);
            return headerSize / JavaKind.Double.getByteCount();
        };

        registerAtomicAddPlugin(r, "atomicAdd", IntArray.class, CUDAKind.UINT, intHeaderSupplier);
        registerAtomicAddPlugin(r, "atomicAdd", int[].class, CUDAKind.UINT, intHeaderSupplier);
        registerAtomicAddPlugin(r, "atomicAdd", LongArray.class, CUDAKind.ULONG, longHeaderSupplier);
        // CUDA atomicAdd has native float (all archs) and double (compute >= 6.0)
        // overloads, so these are supported (unlike the OpenCL backend).
        registerAtomicAddPlugin(r, "atomicAdd", FloatArray.class, CUDAKind.FLOAT, floatHeaderSupplier);
        registerAtomicAddPlugin(r, "atomicAdd", DoubleArray.class, CUDAKind.DOUBLE, doubleHeaderSupplier);
    }

    private static void registerAtomicAddPlugin(Registration r, String methodName, Class<?> arrayType, CUDAKind kind, Supplier<Integer> headerSupplier) {
        r.register(new InvocationPlugin(methodName, InvocationPlugin.Receiver.class, arrayType, Integer.TYPE, kind.asJavaKind().toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode segment, ValueNode index, ValueNode inc) {
                receiver.get(true);
                JavaKind javaKind = kind.asJavaKind();
                int header = headerSupplier.get();
                AddressNode address = computeAddress(b, segment, index, header, javaKind);
                AtomAddNodeTemplate atomicAddNode = new AtomAddNodeTemplate(address, inc, javaKind);
                b.add(atomicAddNode);
                return true;
            }
        });
    }

    private static AddressNode computeAddress(GraphBuilderContext b, ValueNode segment, ValueNode index, int panamaOffset, JavaKind kind) {
        ConstantNode constantNode = b.append(new ConstantNode(new RawConstant(panamaOffset), StampFactory.forKind(JavaKind.Int)));
        AddNode newIndex = b.append(new AddNode(index, constantNode));
        SignExtendNode signExtendNode = b.append(new SignExtendNode(newIndex, CUDAKind.LONG.asJavaKind().getBitCount()));
        MulNode mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forInt(kind.getByteCount())));
        return b.append(new OffsetAddressNode(segment, mulNode));
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateIntLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, elementType, size);
                b.getGraph().addOrUnique(localArrayNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerLongLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateLongLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, elementType, size);
                b.getGraph().addOrUnique(localArrayNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerFloatLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateFloatLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, elementType, size);
                b.getGraph().addOrUnique(localArrayNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerDoubleLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateDoubleLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                receiver.get(true);
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, elementType, size);
                b.getGraph().addOrUnique(localArrayNode);
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
                MetaAccessProvider metaAccess = b.getMetaAccess();
                ResolvedJavaType resolvedElementType = metaAccess.lookupJavaType(byte.class);
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, resolvedElementType, size);
                b.getGraph().addOrUnique(localArrayNode);
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
                LocalArrayNode localArrayNode = new LocalArrayNode(CUDAArchitecture.localSpace, resolvedElementType, size, CUDAKind.HALF);
                b.getGraph().addOrUnique(localArrayNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Object;

        JavaKind elementType = CUDAKind.INT.asJavaKind();
        registerIntLocalArray(r, returnedJavaKind, elementType);

        elementType = CUDAKind.LONG.asJavaKind();
        registerLongLocalArray(r, returnedJavaKind, elementType);

        elementType = CUDAKind.FLOAT.asJavaKind();
        registerFloatLocalArray(r, returnedJavaKind, elementType);

        elementType = CUDAKind.DOUBLE.asJavaKind();
        registerDoubleLocalArray(r, returnedJavaKind, elementType);

        registerByteLocalArray(r, returnedJavaKind);

        returnedJavaKind = JavaKind.fromJavaClass(short.class);
        registerHalfFloatLocalArray(r, returnedJavaKind);
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
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMAFragmentNode(initValue));
                return true;
            }
        });

        // --- mmaLoadA(int[] aTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadA",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadANode(tile, wmmaK));
                return true;
            }
        });

        // --- mmaLoadB(int[] bTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadB",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBNode(tile, wmmaK));
                return true;
            }
        });

        // --- mmaLoadBSwizzled(HalfFloat[] bTile, int wmmaK) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadBSwizzled",
                InvocationPlugin.Receiver.class, HalfFloat[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBSwizzledNode(tile, wmmaK));
                return true;
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
                receiver.get(true);
                b.add(new CUDAMMAStoreBSwizzledNode(arr, row, col, stride, value, byteOffset));
                return true;
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
                receiver.get(true);
                MMAShape shape = resolveShape(b, shapeNode);
                b.addPush(JavaKind.Object, new CUDAMMAComputeNode(fragA, fragB, fragC, shape,
                        CUDALIRStmt.MMAComputeStmt.MMAOperand.F16));
                return true;
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
                receiver.get(true);
                int headerElements = TornadoCoreRuntime.getVMConfig()
                        .getArrayBaseOffset(JavaKind.Float) / JavaKind.Float.getByteCount();
                b.add(new CUDAMMAStoreNode(fragD, target, tileRow, tileCol, dimN, headerElements));
                return true;
            }
        });

        r.register(new InvocationPlugin("mmaFragmentInt",
                InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode initValue) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMAFragmentNode(initValue, true));
                return true;
            }
        });

        // --- mmaLoadAInt8(int[], int) -> byte[] ---
        r.register(new InvocationPlugin("mmaLoadAInt8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadAInt8Node(tile, tileK));
                return true;
            }
        });

        // --- mmaLoadBInt8(int[], int) -> byte[] ---
        r.register(new InvocationPlugin("mmaLoadBInt8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBInt8Node(tile, tileK));
                return true;
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
                receiver.get(true);
                MMAShape shape = resolveShape(b, shapeNode);
                b.addPush(JavaKind.Object, new CUDAMMAComputeNode(fragA, fragB, fragC, shape,
                        CUDALIRStmt.MMAComputeStmt.MMAOperand.S8));
                return true;
            }
        });

        // --- mmaFP8E4M3(byte[], byte[], float[], MMAShape) -> float[] ---
        // --- mmaFP8E5M2(byte[], byte[], float[], MMAShape) -> float[] ---
        // FP8 tensor-core MMA (m16n8k32, f32 accumulator). The A/B fragments are raw
        // byte tuples with the same shared-memory tile layout as the int8 path, so the
        // loads reuse the int8 load nodes; only the mma.sync element type differs.
        registerMMAFP8Compute(r, "mmaFP8E4M3", CUDALIRStmt.MMAComputeStmt.MMAOperand.E4M3);
        registerMMAFP8Compute(r, "mmaFP8E5M2", CUDALIRStmt.MMAComputeStmt.MMAOperand.E5M2);

        // --- mmaLoadAFP8(int[], int) -> byte[] --- (same tile layout as int8: reuse its load node)
        r.register(new InvocationPlugin("mmaLoadAFP8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadAInt8Node(tile, tileK));
                return true;
            }
        });

        // --- mmaLoadBFP8(int[], int) -> byte[] ---
        r.register(new InvocationPlugin("mmaLoadBFP8",
                InvocationPlugin.Receiver.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode tileK) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBInt8Node(tile, tileK));
                return true;
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
                receiver.get(true);
                int headerElements = TornadoCoreRuntime.getVMConfig()
                        .getArrayBaseOffset(JavaKind.Int) / JavaKind.Int.getByteCount();
                b.add(new CUDAMMAStoreNode(fragD, target, tileRow, tileCol, dimN, headerElements, true));
                return true;
            }
        });

        // --- mmaLoadA(int[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadA",
                InvocationPlugin.Receiver.class, int[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadANode(tile, wmmaK, byteOffset));
                return true;
            }
        });

        // --- mmaLoadB(int[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadB",
                InvocationPlugin.Receiver.class, int[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBNode(tile, wmmaK, byteOffset));
                return true;
            }
        });

        // --- mmaLoadBSwizzled(HalfFloat[], int, int) -> HalfFloat[] ---
        r.register(new InvocationPlugin("mmaLoadBSwizzled",
                InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver, ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
                receiver.get(true);
                b.addPush(JavaKind.Object, new CUDAMMALoadBSwizzledNode(tile, wmmaK, byteOffset));
                return true;
            }
        });

    }

    /** Registers one FP8 mma compute plugin ({@code (byte[], byte[], float[], MMAShape) -> float[]}). */
    private static void registerMMAFP8Compute(Registration r, String methodName,
                                              CUDALIRStmt.MMAComputeStmt.MMAOperand operand) {
        r.register(new InvocationPlugin(methodName,
                InvocationPlugin.Receiver.class,
                byte[].class, byte[].class, float[].class, MMAShape.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                 Receiver receiver,
                                 ValueNode fragA, ValueNode fragB, ValueNode fragC,
                                 ValueNode shapeNode) {
                receiver.get(true);
                MMAShape shape = resolveShape(b, shapeNode);
                b.addPush(JavaKind.Object, new CUDAMMAComputeNode(fragA, fragB, fragC, shape, operand));
                return true;
            }
        });
    }

    /**
     * Resolves an MMAShape enum ValueNode to its compile-time constant.
     *
     * Enum references in bytecode appear as getstatic loads (LoadFieldNode) that
     * are only constant-folded in a later phase. We read the static field directly
     * via ConstantReflectionProvider.
     *
     * We can't use SnippetReflection.asObject() because TornadoVM's implementation
     * throws unimplemented(). Instead, we read the enum's ordinal field and index
     * into MMAShape.values().
     */
    private static MMAShape resolveShape(GraphBuilderContext b, ValueNode shapeNode) {
        JavaConstant constant = shapeNode.asJavaConstant();

        // Enum constants usually reach the plugin as a LoadFieldNode — resolve
        // the static-final field directly if it hasn't been folded yet.
        if (constant == null && shapeNode instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) shapeNode;
            if (load.field().isStatic()) {
                constant = b.getConstantReflection().readFieldValue(load.field(), null);
            }
        }

        if (constant == null || constant.isNull()) {
            throw new IllegalStateException(
                    "MMAShape argument to ctx.mma() must be a compile-time constant");
        }

        // Read the `ordinal` field inherited from java.lang.Enum to identify
        // which MMAShape constant this is, then look it up in values().
        ResolvedJavaType enumType = b.getMetaAccess().lookupJavaType(MMAShape.class);
        ResolvedJavaField ordinalField = null;
        for (ResolvedJavaField f : enumType.getInstanceFields(true)) {
            if (f.getName().equals("ordinal")) {
                ordinalField = f;
                break;
            }
        }
        if (ordinalField == null) {
            throw new IllegalStateException("Cannot locate Enum.ordinal field on MMAShape");
        }

        JavaConstant ordinalConst = b.getConstantReflection().readFieldValue(ordinalField, constant);
        if (ordinalConst == null) {
            throw new IllegalStateException("Failed to read ordinal of MMAShape constant");
        }

        return MMAShape.values()[ordinalConst.asInt()];
    }

    private static void registerSIMDPlugins(Registration r) {
        // SIMD-group reductions over a warp, lowered to CUDA warp-shuffle
        // intrinsics (__shfl_*_sync). Without these plugins the KernelContext
        // default implementations are no-ops (return the input unchanged), which
        // would silently produce wrong results.
        r.register(new InvocationPlugin("simdShuffleDown", InvocationPlugin.Receiver.class, float.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val, ValueNode delta) {
                b.addPush(JavaKind.Float, new CUDAShuffleDownNode(val, delta));
                return true;
            }
        });

        r.register(new InvocationPlugin("simdSum", InvocationPlugin.Receiver.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val) {
                b.addPush(JavaKind.Float, new CUDASimdSumNode(val));
                return true;
            }
        });

        r.register(new InvocationPlugin("simdBroadcastFirst", InvocationPlugin.Receiver.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode val) {
                b.addPush(JavaKind.Float, new CUDASimdBroadcastFirstNode(val));
                return true;
            }
        });
    }

    private static void registerSwizzledLocalAccessesPlugins(Registration r) {
        r.register(new InvocationPlugin("swizzleLoadFp16Stride32", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride) {
                receiver.get(true);
                b.addPush(JavaKind.Object,
                        new CUDASwizzledLoadFP16Stride32Node(local_array, row, column, stride));
                return true;
            }
        });

        r.register(new InvocationPlugin("swizzleStoreFp16Stride32", InvocationPlugin.Receiver.class, HalfFloat[].class, int.class, int.class, int.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode local_array, ValueNode row, ValueNode column, ValueNode stride, ValueNode value) {
                receiver.get(true);
                b.add(new CUDASwizzledStoreFP16Stride32Node(local_array, row, column, stride, value));
                return true;
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

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);

        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                CUDAConvertHalfToFloat convertHalfToFloat = new CUDAConvertHalfToFloat(halfValue);
                b.getGraph().addOrUnique(convertHalfToFloat);
                b.push(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });
    }

    /**
     * Routes the in-kernel FP8 decoders to the native cuda_fp8.h conversion path.
     *
     * <p>{@code FP8.e4m3ToFloat}/{@code e5m2ToFloat} are written as pure arithmetic so
     * they compile on any backend; on CUDA that software decode costs 2-10x more than a
     * hardware convert. Intercepting the call here swaps in
     * {@code __nv_cvt_fp8_to_halfraw} (hardware {@code cvt} on sm_89+, the header's own
     * emulation below that) while other backends keep inlining the Java bytecode. Decode
     * only: the hardware float-to-fp8 encoder rounds ties to even while the software
     * encoder rounds half away from zero, so replacing the encoders would change results
     * between host- and device-quantized data.
     */
    private static void registerFP8ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FP8.class);

        r.register(new InvocationPlugin("e4m3ToFloat", byte.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode fp8Byte) {
                CUDAConvertFP8ToFloat convert = new CUDAConvertFP8ToFloat(fp8Byte, CUDAConvertFP8ToFloat.FP8Format.E4M3);
                b.getGraph().addOrUnique(convert);
                b.push(JavaKind.Float, convert);
                return true;
            }
        });

        r.register(new InvocationPlugin("e5m2ToFloat", byte.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode fp8Byte) {
                CUDAConvertFP8ToFloat convert = new CUDAConvertFP8ToFloat(fp8Byte, CUDAConvertFP8ToFloat.FP8Format.E5M2);
                b.getGraph().addOrUnique(convert);
                b.push(JavaKind.Float, convert);
                return true;
            }
        });
    }

    private static void registerQuantizationUtilsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, QuantizationUtils.class);
        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, Int8Array.class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b,
                    ValueNode accumulator) {
                Dp4aNode dp4aOp = new Dp4aNode(a, offset_a, b, offset_b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aOp);
                return true;
            }
        });

        r.register(new InvocationPlugin("dp4a", Int8Array.class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b,
                    ValueNode accumulator) {
                Dp4aNode dp4aOp = new Dp4aNode(a, offset_a, b, offset_b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aOp);
                return true;
            }
        });

        r.register(new InvocationPlugin("dp4a_packed", int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode b, ValueNode accumulator) {
                DP4APackedNode dp4aPackedOp = new DP4APackedNode(a, b, accumulator);
                graphBuilderContext.addPush(JavaKind.Int, dp4aPackedOp);
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

    private static void registerMemoryAccessPlugins(InvocationPlugins plugins, HotSpotMetaAccessProvider metaAccessProvider) {
        Registration r = new Registration(plugins, TornadoMemorySegment.class);

        for (JavaKind kind : JavaKind.values()) {
            if (kind != JavaKind.Object && kind != JavaKind.Void && kind != JavaKind.Illegal && kind != JavaKind.Boolean) {
                r.register(new InvocationPlugin("get" + kind.name() + "AtIndex", Receiver.class, int.class, int.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode baseIndex) {
                        ValueNode receiverNode = receiver.get(true);  // Get receiver first (this adds null check)
                        // Sign-extend int indices to long for 64-bit address arithmetic
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
                        ValueNode receiverNode = receiver.get(true);  // Get receiver first (this adds null check)
                        // Sign-extend int indices to long for 64-bit address arithmetic
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
    }

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        // We have to overwrite some standard math plugins
        r.setAllowOverwrite(true);
        registerOpenCLOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerOpenCLOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerOpenCLOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerOpenCLOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r);

        Registration longReg = new Registration(plugins, Long.class);
        longReg.register(new InvocationPlugin("bitCount", Long.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(CUDAIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register(new InvocationPlugin("bitCount", Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(CUDAIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerFPIntrinsics(Registration r) {
        r.register(new InvocationPlugin("pow", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, POW, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, SIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, COS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, TAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, TANH, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, ATAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, ATAN2, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(x, ASIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(x, ACOS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, LOG, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(CUDAFPUnaryIntrinsicNode.create(value, EXP, JavaKind.Double)));
                return true;
            }
        });
    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(CUDAIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(CUDAIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new CUDAVectorNodePlugin());
        plugins.appendNodePlugin(new CUDAAtomicIntegerPlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        CUDAVectorPlugins.registerParameterPlugins(plugins);
    }
}
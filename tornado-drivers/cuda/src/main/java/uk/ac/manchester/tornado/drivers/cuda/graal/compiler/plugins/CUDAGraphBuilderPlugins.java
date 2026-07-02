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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.replacements.InlineDuringParsingPlugin;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.word.LocationIdentity;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoMemorySegment;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.AtomAddNodeTemplate;
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
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.function.Supplier;

import static jdk.graal.compiler.debug.GraalError.unimplemented;
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
        registerSwizzledLocalAccessesPlugins(r);
        registerUnsupportedSimdgroupMatrixPlugins(r);
    }

    /**
     * The {@code simdgroup_float8x8} matrix-unit intrinsics ({@link uk.ac.manchester.tornado.api.KernelContext}
     * {@code simdgroupMatrix*}) are Metal-only. They cannot be intrinsified on this backend, so reject them at
     * graph-build time with a clear message instead of failing later when the JVM-fallback object allocations
     * are lowered.
     */
    private static void registerUnsupportedSimdgroupMatrixPlugins(Registration r) {
        final String message = "simdgroup_float8x8 matrix operations (KernelContext.simdgroupMatrix*) are only supported on the Metal backend.";
        r.register(new InvocationPlugin("simdgroupMatrixZero", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                unimplemented(message);
                return false;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixLoad", Receiver.class, FloatArray.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode base, ValueNode stride) {
                receiver.get(true);
                unimplemented(message);
                return false;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixLoad", Receiver.class, float[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode base, ValueNode stride) {
                receiver.get(true);
                unimplemented(message);
                return false;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixMultiplyAccumulate", Receiver.class, Matrix8x8Float.class, Matrix8x8Float.class, Matrix8x8Float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode bMat, ValueNode c) {
                receiver.get(true);
                unimplemented(message);
                return false;
            }
        });
        r.register(new InvocationPlugin("simdgroupMatrixStore", Receiver.class, Matrix8x8Float.class, FloatArray.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode matrix, ValueNode array, ValueNode base, ValueNode stride) {
                receiver.get(true);
                unimplemented(message);
                return false;
            }
        });
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
/*
 * Copyright (c) 2022, 2024-2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getJavaKindFromValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.ATAN2;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ACOS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ASIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode.Operation.POPCOUNT;

import java.lang.foreign.MemorySegment;

import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
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
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TornadoVMIntrinsics;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.AtomAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.AtomicAddNodeTemplate;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.DecAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GetAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLBarrierNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class OCLGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins) {
        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            ps.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerFP16ConversionPlugins(plugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

        // Register Atomics
        registerTornadoVMAtomicsPlugins(plugins);
        // Register KernelContext Plugins
        registerKernelContextPlugins(plugins);

        OCLMathPlugins.registerTornadoMathPlugins(plugins);
        registerOpenCLBuiltinPlugins(plugins);
        OCLVectorPlugins.registerPlugins(ps, plugins);

        // Register TornadoAtomicInteger
        registerTornadoAtomicInteger(ps, plugins);

        OCLHalfFloatPlugins.registerPlugins(ps, plugins);
        registerMemoryAccessPlugins(ps);

    }

    private static void registerTornadoVMAtomicsPlugins(Registration r) {
        r.register(new InvocationPlugin("atomic_add", int[].class, Integer.TYPE, Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index, ValueNode inc) {
                AtomicAddNodeTemplate atomicIncNode = new AtomicAddNodeTemplate(array, inc, JavaKind.Int);
                b.addPush(JavaKind.Int, b.append(atomicIncNode));
                return true;
            }
        });
    }

    private static void registerTornadoVMAtomicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, TornadoVMIntrinsics.class);
        registerTornadoVMAtomicsPlugins(r);
    }

    private static boolean isMethodFromAtomicClass(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName().equals("uk.ac.manchester.tornado.api.atomics.TornadoAtomicInteger") || method.getDeclaringClass().toJavaName().equals(
                "java.util.concurrent.atomic.AtomicInteger");
    }

    private static void registerAtomicCall(Registration r, JavaKind returnedJavaKind) {
        r.register(new InvocationPlugin("incrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new IncAtomicNode(receiver.get(), OCLUnary.AtomicOperator.INCREMENT_AND_GET)));
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndIncrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new IncAtomicNode(receiver.get(), OCLUnary.AtomicOperator.GET_AND_INCREMENT)));
                return true;
            }
        });

        r.register(new InvocationPlugin("decrementAndGet", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(), OCLUnary.AtomicOperator.DECREMENT_AND_GET)));
                return true;
            }
        });

        r.register(new InvocationPlugin("getAndDecrement", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(returnedJavaKind, b.append(new DecAtomicNode(receiver.get(), OCLUnary.AtomicOperator.GET_AND_DECREMENT)));
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
        JavaKind returnedJavaKind = OCLKind.INT.asJavaKind();
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
                OCLBarrierNode localBarrierNode = new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.LOCAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register(new InvocationPlugin("globalBarrier", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                OCLBarrierNode localBarrierNode = new OCLBarrierNode(OCLBarrierNode.OCLMemFenceFlags.GLOBAL);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerAtomicAddOperation(Registration r) {
        registerAtomicAddPlugin(r, "atomicAdd", IntArray.class, OCLKind.UINT, 6);
        registerAtomicAddPlugin(r, "atomicAdd", int[].class, OCLKind.UINT, 6);
        registerAtomicAddPlugin(r, "atomicAdd", LongArray.class, OCLKind.ULONG, 3);
        registerUnsupportedAtomicAddPlugin(r);
        registerUnsupportedAtomicAddPlugin(r);
    }

    private static void registerAtomicAddPlugin(Registration r, String methodName, Class<?> arrayType, OCLKind kind, int panamaOffset) {
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

    private static void registerUnsupportedAtomicAddPlugin(Registration r) {
        r.register(new InvocationPlugin("atomicAdd", InvocationPlugin.Receiver.class, FloatArray.class, Integer.TYPE, Float.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode segment, ValueNode index, ValueNode inc) {
                unimplemented("In OpenCL, the atom_add function does not support floating point operations.");
                return false;
            }
        });
        r.register(new InvocationPlugin("atomicAdd", InvocationPlugin.Receiver.class, DoubleArray.class, Integer.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode segment, ValueNode index, ValueNode inc) {
                unimplemented("In OpenCL, the atom_add function does not support floating point operations.");
                return false;
            }
        });
    }

    private static AddressNode computeAddress(GraphBuilderContext b, ValueNode segment, ValueNode index, int panamaOffset, JavaKind kind) {
        ConstantNode constantNode = b.append(new ConstantNode(new RawConstant(panamaOffset), StampFactory.forKind(JavaKind.Int)));
        AddNode newIndex = b.append(new AddNode(index, constantNode));
        SignExtendNode signExtendNode = b.append(new SignExtendNode(newIndex, OCLKind.LONG.asJavaKind().getBitCount()));
        MulNode mulNode = b.append(new MulNode(signExtendNode, ConstantNode.forInt(kind.getByteCount())));
        return b.append(new OffsetAddressNode(segment, mulNode));
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateIntLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(OCLArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerLongLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateLongLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(OCLArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerFloatLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateFloatLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(OCLArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerDoubleLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateDoubleLocalArray", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalArrayNode localArrayNode = new LocalArrayNode(OCLArchitecture.localSpace, elementType, size);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Object;

        JavaKind elementType = OCLKind.INT.asJavaKind();
        registerIntLocalArray(r, returnedJavaKind, elementType);

        elementType = OCLKind.LONG.asJavaKind();
        registerLongLocalArray(r, returnedJavaKind, elementType);

        elementType = OCLKind.FLOAT.asJavaKind();
        registerFloatLocalArray(r, returnedJavaKind, elementType);

        elementType = OCLKind.DOUBLE.asJavaKind();
        registerDoubleLocalArray(r, returnedJavaKind, elementType);
    }

    private static void registerKernelContextPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, KernelContext.class);

        registerLocalBarrier(r);
        registerGlobalBarrier(r);
        localArraysPlugins(r);
        registerAtomicAddOperation(r);
    }

    private static void registerMemoryAccessPlugins(final Plugins ps) {
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

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forInt(kind.getByteCount())));
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

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forInt(kind.getByteCount())));
                AddressNode addressNode = b.append(new OffsetAddressNode(receiver, mulNode));
                JavaWriteNode writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                b.add(writeNode);
                return true;
            }
        });
    }

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);

        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                OCLConvertHalfToFloat convertHalfToFloat = new OCLConvertHalfToFloat(halfValue);
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

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        // We have to overwrite some of the standard math plugins
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
                b.push(JavaKind.Int, b.append(OCLIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Long)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register(new InvocationPlugin("bitCount", Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(OCLIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerFPIntrinsics(Registration r) {
        r.register(new InvocationPlugin("pow", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(OCLFPBinaryIntrinsicNode.create(x, y, POW, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, SIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, COS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, TAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, TANH, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, ATAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(OCLFPBinaryIntrinsicNode.create(x, y, ATAN2, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(x, ASIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(x, ACOS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, LOG, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(OCLFPUnaryIntrinsicNode.create(value, EXP, JavaKind.Double)));
                return true;
            }
        });
    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new OCLVectorNodePlugin());
        plugins.appendNodePlugin(new OCLAtomicIntegerPlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        OCLVectorPlugins.registerParameterPlugins(plugins);
    }
}

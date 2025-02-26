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

import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getJavaKindFromValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getValueLayoutClass;
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
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
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXBarrierNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class PTXGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins) {
        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            ps.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerFP16ConversionPlugins(plugins);
        registerTornadoInstrinsicsPlugins(plugins);
        registerPTXBuiltinPlugins(plugins);
        PTXMathPlugins.registerTornadoMathPlugins(plugins);
        PTXVectorPlugins.registerPlugins(ps, plugins);
        PTXHalfFloatPlugin.registerPlugins(ps, plugins);
        registerMemoryAccessPlugins(ps);
        registerKernelContextPlugins(plugins);
    }

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                PTXConvertHalfToFloat convertHalfToFloat = new PTXConvertHalfToFloat(halfValue);
                b.addPush(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });
    }

    private static void registerTornadoInstrinsicsPlugins(InvocationPlugins plugins) {

        final InvocationPlugin printfPlugin = new InvocationPlugin("printf", String.class, Object[].class) {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 1];
                actualArgs[0] = args[0];

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

    private static void registerPTXBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, Math.class);
        r.setAllowOverwrite(true);
        registerPTXOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerPTXOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerPTXOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerPTXOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r, Float.TYPE, JavaKind.Float);
        registerFPIntrinsics(r, Double.TYPE, JavaKind.Double);

        Registration longReg = new Registration(plugins, Long.class);
        longReg.register(new InvocationPlugin("bitCount", Long.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(PTXIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Long)));
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
                PTXBarrierNode localBarrierNode = new PTXBarrierNode(1, -1);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateIntLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerLongLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateLongLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerFloatLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateFloatLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerDoubleLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin("allocateDoubleLocalArray", InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Object;

        JavaKind elementType = PTXKind.B32.asJavaKind();
        registerIntLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B64.asJavaKind();
        registerLongLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B32.asJavaKind();
        registerFloatLocalArray(r, returnedJavaKind, elementType);

        elementType = PTXKind.B64.asJavaKind();
        registerDoubleLocalArray(r, returnedJavaKind, elementType);
    }

    private static void registerKernelContextPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, KernelContext.class);

        registerLocalBarrier(r);
        registerGlobalBarrier(r);
        localArraysPlugins(r);
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

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new PTXVectorNodePlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        PTXVectorPlugins.registerParameterPlugins(plugins);
    }
}

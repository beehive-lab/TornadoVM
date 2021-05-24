/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins;

import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode.Operation.POPCOUNT;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.LocalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXBarrierNode;
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

        registerTornadoInstrinsicsPlugins(plugins);
        registerPTXBuiltinPlugins(plugins);
        PTXMathPlugins.registerTornadoMathPlugins(plugins);
        PTXVectorPlugins.registerPlugins(ps, plugins);

        registerKernelContextPlugins(plugins);
    }

    private static void registerTornadoInstrinsicsPlugins(InvocationPlugins plugins) {

        final InvocationPlugin printfPlugin = new InvocationPlugin() {

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

        plugins.register(printfPlugin, Debug.class, "printf", String.class, Object[].class);

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
        longReg.register1("bitCount", Long.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(PTXIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Long)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register1("bitCount", Integer.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(PTXIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerLocalBarrier(Registration r) {
        r.register1("localBarrier", InvocationPlugin.Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                PTXBarrierNode localBarrierNode = new PTXBarrierNode(0, -1);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register1("globalBarrier", InvocationPlugin.Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                PTXBarrierNode localBarrierNode = new PTXBarrierNode(1, -1);
                b.add(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerLocalWorkGroup(Registration r, JavaKind returnedJavaKind) {
        r.register2("getLocalGroupSize", InvocationPlugin.Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                LocalThreadSizeNode localThreadSizeNode = new LocalThreadSizeNode((ConstantNode) size);
                b.push(returnedJavaKind, localThreadSizeNode);
                return true;
            }
        });
    }

    private static void registerIntLocalArray(Registration r, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register2("allocateIntLocalArray", InvocationPlugin.Receiver.class, int.class, new InvocationPlugin() {
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
        r.register2("allocateLongLocalArray", InvocationPlugin.Receiver.class, int.class, new InvocationPlugin() {
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
        r.register2("allocateFloatLocalArray", InvocationPlugin.Receiver.class, int.class, new InvocationPlugin() {
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
        r.register2("allocateDoubleLocalArray", InvocationPlugin.Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(PTXArchitecture.sharedSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void localWorkGroupPlugin(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Int;
        registerLocalWorkGroup(r, returnedJavaKind);
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
        localWorkGroupPlugin(r);
        localArraysPlugins(r);
    }

    private static void registerFPIntrinsics(Registration r, Class<?> type, JavaKind kind) {
        r.register2("pow", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });

        r.register1("sin", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register1("cos", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register1("log", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register1("exp", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });
    }

    private static void registerPTXOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
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

        r.register2("max", type, type, new InvocationPlugin() {
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

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
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

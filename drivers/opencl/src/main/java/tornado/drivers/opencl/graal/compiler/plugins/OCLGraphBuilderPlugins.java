/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.compiler.plugins;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import tornado.drivers.opencl.graal.nodes.*;
import tornado.lang.CompilerInternals;
import tornado.lang.Debug;

import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.FABS;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MAX;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MIN;

public class OCLGraphBuilderPlugins {

    public static void registerInvocationPlugins(final InvocationPlugins plugins) {
        registerCompilerInstrinsicsPlugins(plugins);
        registerTornadoInstrinsicsPlugins(plugins);
        registerOpenCLBuiltinPlugins(plugins);

        TornadoMathPlugins.registerTornadoMathPlugins(plugins);
        VectorPlugins.registerPlugins(plugins);

//		AtomicPlugins.registerPlugins(plugins);
    }

    private static void registerCompilerInstrinsicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerInternals.class);

        r.register0("getSlotsAddress", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver) {
                b.addPush(JavaKind.Object, new SlotsBaseAddressNode());
                return true;
            }
        });

    }

    private static void registerTornadoInstrinsicsPlugins(InvocationPlugins plugins) {

        final InvocationPlugin tprintfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode... args) {

                int idCount = 0;
                int index = 0;
                for (; index < 3; index++) {
                    ValueNode arg = args[index];
                    if (arg instanceof ConstantNode && arg.getStackKind().isObject()) {
                        break;
                    }
                    idCount++;
                }

                NewArrayNode newArrayNode = (NewArrayNode) args[index + 1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[4 + length];
                for (int i = 0; i < idCount; i++) {
                    actualArgs[i] = args[i];
                }

                for (int i = idCount; i < 3; i++) {
                    actualArgs[i] = ConstantNode.forInt(0);
                }

                actualArgs[3] = args[index];

                int argIndex = 0;
                for (int i = 0; i < newArrayNode.getUsageCount(); i++) {
                    Node n = newArrayNode.getUsageAt(i);
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            value = box.getValue();
                            GraphUtil.unlinkFixedNode(box);
                            box.safeDelete();
                        }
                        actualArgs[4 + argIndex] = value;
                        argIndex++;
                    }

                }

                TPrintfNode printfNode = new TPrintfNode(actualArgs);

                b.add(b.append(printfNode));
                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.getUsageAt(0);
                    // need to remove all nodes from the graph that operate on the
                    // new array, however, we cannot remove all inputs as they may be
                    // used by the currently unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps inbetween
                    // fixed nodes
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

        plugins.register(tprintfPlugin, Debug.class, "tprintf", String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, int.class, String.class, Object[].class);

        final InvocationPlugin printfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 1];
                actualArgs[0] = args[0];

                int argIndex = 0;
                for (int i = 0; i < newArrayNode.getUsageCount(); i++) {
                    Node n = newArrayNode.getUsageAt(i);
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
                    Node n = newArrayNode.getUsageAt(0);
                    // need to remove all nodes from the graph that operate on the
                    // new array, however, we cannot remove all inputs as they may be
                    // used by the currently unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps inbetween
                    // fixed nodes
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

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        registerOpenCLOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerOpenCLOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerOpenCLOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerOpenCLOverridesForType(r, Long.TYPE, JavaKind.Long);

//        registerFPIntrinsics(r, Float.TYPE, JavaKind.Float);
        registerFPIntrinsics(r, Double.TYPE, JavaKind.Double);

    }

    private static void registerFPIntrinsics(Registration r, Class<?> type, JavaKind kind) {
        r.register2("pow", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });
    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                //else
                //	b.push(kind, b.recursiveAppend(OCLIntUnaryIntrinsicNode.create(value, ABS , kind)));
                return true;
            }
        });

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new OCLVectorNodePlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        VectorPlugins.registerParameterPlugins(plugins);
    }
}

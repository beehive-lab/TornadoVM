package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.runtime.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.DivHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.MultHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;
import uk.ac.manchester.tornado.runtime.graal.nodes.SubHalfFloatNode;

public class OCLHalfFloatPlugins {

    public static void registerPlugins(final GraphBuilderConfiguration.Plugins ps, final InvocationPlugins plugins) {
        registerHalfFloatInit(ps, plugins);
    }

    private static void registerHalfFloatInit(GraphBuilderConfiguration.Plugins ps, InvocationPlugins plugins) {

        final InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, HalfFloat.class);

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && method.toString().contains("HalfFloat.<init>")) {
                    NewHalfFloatInstance newHalfFloatInstance = new NewHalfFloatInstance(args[1]);

                    // Use b.add() to properly insert this FixedWithNextNode into the control flow
                    b.add(newHalfFloatInstance);

                    // Replace usages of the NewInstanceNode (args[0]) with our node
                    args[0].replaceAtUsages(newHalfFloatInstance);

                    // Return false to let normal <init> processing continue
                    // This avoids frame state issues
                    return true;
                }
                return false;
            }
        });

        r.register(new InvocationPlugin("add", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                AddHalfFloatNode addNode = new AddHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(addNode);
                b.push(JavaKind.Object, addNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                SubHalfFloatNode subNode = new SubHalfFloatNode(halfFloat1, halfFloat2);
                //                b.getGraph().addOrUnique(subNode);
                b.addPush(JavaKind.Object, subNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                MultHalfFloatNode multNode = new MultHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(multNode);
                b.push(JavaKind.Object, multNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("div", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                DivHalfFloatNode divNode = new DivHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(divNode);
                b.push(JavaKind.Object, divNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("getHalfFloatValue", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                HalfFloatPlaceholder placeholder = new HalfFloatPlaceholder(receiver.get(true));
                b.getGraph().addOrUnique(placeholder);
                b.push(JavaKind.Short, placeholder);
                return true;
            }
        });

    }

}
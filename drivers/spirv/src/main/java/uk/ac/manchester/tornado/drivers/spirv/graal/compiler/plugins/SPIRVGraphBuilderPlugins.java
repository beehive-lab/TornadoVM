package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import static org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMAX;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMIN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.POW;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.COS;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode.SPIRVIntOperation.MAX;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode.SPIRVIntOperation.MIN;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SlotsBaseAddressNode;
import uk.ac.manchester.tornado.runtime.directives.CompilerInternals;

// FIXME <TODO> When implementing vector types for the SPIRV platform
public class SPIRVGraphBuilderPlugins {

    public static void registerParametersPlugins(Plugins plugins) {
        SPIRVVectorPlugins.registerParameterPlugins(plugins);
    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new SPIRVOCLNodePlugin());
        // FIXME: Atomics for SPIRV Backend not implemented.
    }

    public static void registerInvocationPlugins(Plugins plugins, final InvocationPlugins invocationPlugins) {
        registerCompilerIntrinsicsPlugins(invocationPlugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

        registerOpenCLBuiltinPlugins(invocationPlugins);

    }

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, java.lang.Math.class);
        // We have to overwrite some of standard math plugins
        r.setAllowOverwrite(true);
        registerOpenCLOverridesForType(r, Float.TYPE, JavaKind.Float);

        registerFPIntrinsics(r);

    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        // TODO Regiter unary intrinsics

    }

    private static void registerFPIntrinsics(Registration r) {
        r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, POW, JavaKind.Double)));
                return true;
            }
        });

        r.register1("cos", Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, COS, JavaKind.Double)));
                return true;
            }
        });
    }

    // FIXME: Revisit this method. In SPIR-V we can avoid this compiler Internal.
    private static void registerCompilerIntrinsicsPlugins(InvocationPlugins plugins) {
        System.out.println("SPIRV Registering Intrinsics Plugins - pending");
        // FIXME <REFACTOR> For SPIRV, I am not sure we need the SlotBaseAddressPlugin
        Registration r = new Registration(plugins, CompilerInternals.class);

        r.register0("getSlotsAddress", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new SlotsBaseAddressNode());
                return true;
            }
        });
    }

    private static void registerTornadoVMIntrinsicsPlugins(Plugins plugins) {
        System.out.println("SPIRV Registering VM Intrinsics Plugins - pending");
    }

}

package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import static org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVSlotsBaseAddressNode;
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

    public static void registerInvocationPlugins(Plugins plugins, InvocationPlugins invocationPlugins) {
        registerCompilerIntrinsicsPlugins(invocationPlugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

    }

    // FIXME: Revisit this method. In SPIR-V we can avoid this compiler Internal.
    private static void registerCompilerIntrinsicsPlugins(InvocationPlugins plugins) {
        System.out.println("SPIRV Registering Intrinsics Plugins - pending");
        // FIXME <REFACTOR> For SPIRV, I am not sure we need the SlotBaseAddressPlugin
        Registration r = new Registration(plugins, CompilerInternals.class);

        r.register0("getSlotsAddress", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new SPIRVSlotsBaseAddressNode());
                return true;
            }
        });
    }

    private static void registerTornadoVMIntrinsicsPlugins(Plugins plugins) {
        System.out.println("SPIRV Registering VM Intrinsics Plugins - pending");
    }

}

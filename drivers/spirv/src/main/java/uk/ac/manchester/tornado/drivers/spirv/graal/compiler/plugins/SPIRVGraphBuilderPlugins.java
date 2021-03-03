package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import static org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;

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
        registerCompilerIntrinsicsPlugins(plugins);
        registerTornadoVMIntrinsicsPlugins(plugins);

    }

    private static void registerCompilerIntrinsicsPlugins(Plugins plugins) {
        System.out.println("SPIRV Registering Intrinsics Plugins - pending");
        // FIXME <REFACTOR> For SPIRV, I am not sure we need the SlotBaseAddressPlugin
        // Registration r = new Registration(plugins, CompilerInternals.class);
        //
        // r.register0("getSlotsAddress", new InvocationPlugin() {
        // @Override
        // public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
        // Receiver receiver) {
        // b.addPush(JavaKind.Object, new SPIRVSlotsBaseAddressNode());
        // return true;
        // }
        // });
    }

    private static void registerTornadoVMIntrinsicsPlugins(Plugins plugins) {
        System.out.println("SPIRV Registering VM Intrinsics Plugins - pending");
    }

}

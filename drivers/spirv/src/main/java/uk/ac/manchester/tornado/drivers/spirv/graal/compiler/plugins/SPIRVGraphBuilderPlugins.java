package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;

public class SPIRVGraphBuilderPlugins {

    public static void registerParametersPlugins(GraphBuilderConfiguration.Plugins plugins) {
        SPIRVVectorPlugins.registerParameterPlugins(plugins);
    }
}

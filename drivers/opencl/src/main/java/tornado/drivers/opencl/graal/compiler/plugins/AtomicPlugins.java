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

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.collections.types.FloatOps;
import tornado.drivers.opencl.graal.nodes.AtomicAddNode;

public class AtomicPlugins {

    public static void registerPlugins(InvocationPlugins plugins) {

        registerAtomicPlugins(plugins);

    }

    private static void registerAtomicPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FloatOps.class);

        r.register3("atomicAdd", float[].class, int.class, float.class, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b,
                    ResolvedJavaMethod targetMethod, Receiver receiver,
                    ValueNode array, ValueNode index, ValueNode value) {

                final AtomicAddNode atomicAddNode = new AtomicAddNode(array, index, JavaKind.Float, value);
                b.append(atomicAddNode);
                return true;
            }

        });
    }
}

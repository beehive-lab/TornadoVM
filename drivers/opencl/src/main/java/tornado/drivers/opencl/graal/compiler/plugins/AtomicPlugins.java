package tornado.drivers.opencl.graal.compiler.plugins;

import tornado.collections.types.FloatOps;
import tornado.drivers.opencl.graal.nodes.AtomicAddNode;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.nodes.ValueNode;

public class AtomicPlugins {

	public static void registerPlugins(InvocationPlugins plugins) {
		
		registerAtomicPlugins(plugins);
		
	}
	
	
	private static void registerAtomicPlugins(InvocationPlugins plugins) {
		Registration r = new Registration(plugins, FloatOps.class);
		

		r.register3("atomicAdd", float[].class, int.class, float.class, new InvocationPlugin(){

			@Override
			public boolean apply(GraphBuilderContext b,
					ResolvedJavaMethod targetMethod, Receiver receiver,
					ValueNode array, ValueNode index, ValueNode value) {
	
				
				final AtomicAddNode atomicAddNode = new AtomicAddNode(array,index,Kind.Float,value);
				b.append(atomicAddNode);
				return true;
			}
			
		});
	}
}

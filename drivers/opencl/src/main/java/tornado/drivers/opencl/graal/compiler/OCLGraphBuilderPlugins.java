package tornado.drivers.opencl.graal.compiler;

import tornado.drivers.opencl.graal.compiler.plugins.TornadoMathPlugins;
import tornado.drivers.opencl.graal.compiler.plugins.TornadoNewInstancePlugin;
import tornado.drivers.opencl.graal.compiler.plugins.VectorPlugins;
import tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.SlotsBaseAddressNode;
import tornado.graal.compiler.TornadoGraphBuilderPlugins;
import tornado.lang.CompilerInternals;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;

import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.*;

import com.oracle.graal.nodes.*;

public class OCLGraphBuilderPlugins {
	
	

	public static void registerInvocationPlugins(final InvocationPlugins plugins) {
		registerCompilerInstrinsicsPlugins(plugins);

		registerOpenCLBuiltinPlugins(plugins);
		
		TornadoMathPlugins.registerTornadoMathPlugins(plugins);
		VectorPlugins.registerFloat3Plugins(plugins);
		
	}
	
	private static void registerCompilerInstrinsicsPlugins(InvocationPlugins plugins) {
		Registration r = new Registration(plugins, CompilerInternals.class);

		r.register0("getSlotsAddress", new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver) {
				b.addPush(Kind.Object, new SlotsBaseAddressNode());
				return true;
			}
		});
		
	}

	private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

		Registration r = new Registration(plugins, java.lang.Math.class);
		registerOpenCLOverridesForType(r, Float.TYPE, Kind.Float);
		registerOpenCLOverridesForType(r, Double.TYPE, Kind.Double);
		registerOpenCLOverridesForType(r, Integer.TYPE, Kind.Int);
		registerOpenCLOverridesForType(r, Long.TYPE, Kind.Long);
		
		
		
		
	}
	
	private static final void registerOpenCLOverridesForType(Registration r, Class<?> type , Kind kind){
		r.register2("min", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				if(kind.isNumericFloat())
					b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x,y, FMIN , kind)));
				else
					b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x,y, MIN , kind)));
				return true;
			}
		});
		
		r.register2("max", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				if(kind.isNumericFloat())
					b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x,y, FMAX , kind)));
				else
					b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x,y, MAX , kind)));
				return true;
			}
		});
		
	
		
		r.register1("abs", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				if(kind.isNumericFloat())
					b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FABS , kind)));
				//else
				//	b.push(kind, b.recursiveAppend(OCLIntUnaryIntrinsicNode.create(value, ABS , kind)));
				return true;
			}
		});
		
		
		
	}
	
	

	public static void registerNewInstancePlugins(Plugins plugins) {
		plugins.setNewInstancePlugin(new TornadoNewInstancePlugin());
		
	}
}

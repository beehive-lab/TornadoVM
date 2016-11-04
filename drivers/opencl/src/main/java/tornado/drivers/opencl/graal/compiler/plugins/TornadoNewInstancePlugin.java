package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.NewInstancePlugin;
import com.oracle.graal.hotspot.meta.HotSpotResolvedObjectTypeImpl;
import tornado.api.Vector;
import tornado.common.Tornado;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;

public class TornadoNewInstancePlugin implements NewInstancePlugin {

	@Override
	public boolean apply(GraphBuilderContext b, ResolvedJavaType type) {
		boolean result = false;
		
		if(Tornado.ENABLE_VECTORS && type.getAnnotation(Vector.class)!=null){
			result = createVectorInstance(b, type);
		}
		
		return result;
	}

	private boolean createVectorInstance(GraphBuilderContext b, ResolvedJavaType type) {
		OCLKind vectorKind = resolveOCLKind(type);
		if(vectorKind != OCLKind.ILLEGAL){
			b.push(Kind.Object, b.recursiveAppend(new VectorValueNode(vectorKind)));
			return true;
		} 
		
		return false;
	}
	
	

	private OCLKind resolveOCLKind(ResolvedJavaType type) {
		if(type instanceof HotSpotResolvedObjectTypeImpl){
			final HotSpotResolvedObjectTypeImpl resolvedType = (HotSpotResolvedObjectTypeImpl) type;
			return OCLKind.fromClass(resolvedType.mirror());
		}
	
		return OCLKind.ILLEGAL;
	}

}

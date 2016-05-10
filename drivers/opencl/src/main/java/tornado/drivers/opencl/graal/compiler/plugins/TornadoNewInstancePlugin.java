package tornado.drivers.opencl.graal.compiler.plugins;

import tornado.api.Vector;
import tornado.common.Tornado;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.NewInstancePlugin;
import com.oracle.graal.hotspot.meta.HotSpotResolvedObjectTypeImpl;

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
		VectorKind vectorKind = resolveVectorKind(type);
		if(vectorKind != VectorKind.Illegal){
			b.push(Kind.Object, b.recursiveAppend(new VectorValueNode(vectorKind)));
			return true;
		} 
		
		return false;
	}
	
	

	private VectorKind resolveVectorKind(ResolvedJavaType type) {
		if(type instanceof HotSpotResolvedObjectTypeImpl){
			final HotSpotResolvedObjectTypeImpl resolvedType = (HotSpotResolvedObjectTypeImpl) type;
			return VectorKind.fromClass(resolvedType.mirror());
		}
	
		return VectorKind.Illegal;
	}

}

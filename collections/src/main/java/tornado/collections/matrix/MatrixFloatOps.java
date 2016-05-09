package tornado.collections.matrix;

import org.ejml.factory.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;

import tornado.collections.types.Matrix4x4Float;

public class MatrixFloatOps {
	  public static void inverse(Matrix4x4Float m){
		  try {
	        SimpleMatrix sm = EjmlUtil.toMatrix(m).invert();
	        m.set(EjmlUtil.toMatrix4x4Float(sm));
		  } catch(SingularMatrixException e){
			 // e.printStackTrace();
		  }
		  
		  /*
		     // invert rotation matrix
		  // as R is 3x3 inv(R) == transpose(R)
        for(int i=0;i<3;i++){
        for(int j=0;j<i;j++){
            final float tmp = m.get(i, j);
            m.set(i, j, m.get(j, i));
            m.set(j, i, tmp);
        }
        }
		  
		  // invert translation
		  // -inv(R) * t
		  final Float3 tOld = m.column(3).asFloat3();
		  
		  for(int i=0;i<3;i++){
			  final Float3 r = Float3.mult(m.row(i).asFloat3(),-1f);
			  final Float3 b = new Float3(tOld.get(i),tOld.get(i),tOld.get(i));
			  m.set(i,3, Float3.dot(r, b));
		  }
		   */
	    }
}

package tornado.collections.matrix;

import org.ejml.simple.SimpleMatrix;

import tornado.collections.types.Matrix4x4Float;

public class EjmlUtil {
	
	
//	public static MatrixFloat toMatrixFloat(SimpleMatrix m){
//        //System.out.printf("Matrix: row=%d, col=%d\n",m.numRows(),m.numCols());
//        
//        
//	    MatrixFloat result = new MatrixFloat(m.numCols(),m.numRows());
//        
//        for(int i=0;i<m.numRows();i++)
//            for(int j=0;j<m.numCols();j++)
//                result.set(i,j,(float) m.get(i,j));
//        
//        
//        return result;
//    }
	
	public static Matrix4x4Float toMatrix4x4Float(SimpleMatrix m){
        //System.out.printf("Matrix: row=%d, col=%d\n",m.numRows(),m.numCols());
        
        
		Matrix4x4Float result = new Matrix4x4Float();
        
        for(int i=0;i<m.numRows();i++)
            for(int j=0;j<m.numCols();j++)
                result.set(i,j,(float) m.get(i,j));
        
        
        return result;
    }
	
//	public static SimpleMatrix toMatrix(MatrixFloat m){
//        SimpleMatrix result = new SimpleMatrix(m.M(),m.N());
//        
//        for(int i=0;i<m.M();i++)
//            for(int j=0;j<m.N();j++)
//                result.set(i,j,(double)m.get(i, j));
//        
//        return result;
//    }
	
	public static SimpleMatrix toMatrix(Matrix4x4Float m){
        SimpleMatrix result = new SimpleMatrix(m.M(),m.N());
        
        for(int i=0;i<m.M();i++)
            for(int j=0;j<m.N();j++)
                result.set(i,j,(double)m.get(i, j));
        
        return result;
    }
}

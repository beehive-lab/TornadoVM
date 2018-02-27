/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.matrix;

import static uk.ac.manchester.tornado.collections.matrix.EjmlUtil.toMatrix;
import static uk.ac.manchester.tornado.collections.matrix.EjmlUtil.toMatrix4x4Float;

import org.ejml.factory.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;

import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;

public class MatrixFloatOps {
	  public static void inverse(Matrix4x4Float m){
		  try {
	        SimpleMatrix sm = toMatrix(m).invert();
	        m.set(toMatrix4x4Float(sm));
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

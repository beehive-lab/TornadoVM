/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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

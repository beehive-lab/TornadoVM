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
package tornado.collections.types;

import tornado.collections.math.TornadoMath;

public class FloatOps {
	public static final float EPSILON = 1e-7f;
	public static final String fmt = "%.3f";
	public static final String fmt2 = "{%.3f,%.3f}";
	public static final String fmt3 = "{%.3f,%.3f,%.3f}";
	public static final String fmt3e = "{%.4e,%.4e,%.4e}";
	public static final String fmt4 = "{%.3f,%.3f,%.3f,%.3f}";
	public static final String fmt4m = "%.3f,%.3f,%.3f,%.3f";
	public static final String fmt4em = "%.3e,%.3e,%.3e,%.3e";
	public static final String fmt6= "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
	public static final String fmt8= "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
	public static final String fmt6e= "{%e,%e,%e,%e,%e,%e}";
	
	public static final boolean compareBits(float a, float b){
		long ai = Float.floatToRawIntBits(a);
		long bi = Float.floatToRawIntBits(b);
		
		long diff = ai ^ bi;
		return (diff == 0) ? true : false;
	}
	
	public static final boolean compareULP(float value, float expected, float ulps){
		final float tol = ulps * Math.ulp(expected);
		if(value == expected)
			return true;
		
		if(Math.abs(value - expected) < tol)
			return true;
		else 
			return false;
	}
	
	public static final float findMaxULP(Float2 value, Float2 expected){
		return TornadoMath.findULPDistance(value.storage, expected.storage);
	}
	
	public static final float findMaxULP(Float3 value, Float3 expected){
		return TornadoMath.findULPDistance(value.storage, expected.storage);
	}
	
	public static final float findMaxULP(Float4 value, Float4 expected){
		return TornadoMath.findULPDistance(value.storage, expected.storage);
	}
	
	public static final float findMaxULP(Float6 value, Float6 expected){
		return TornadoMath.findULPDistance(value.storage, expected.storage);
	}
	
	public static final float findMaxULP(Float8 value, Float8 expected){
		return TornadoMath.findULPDistance(value.storage, expected.storage);
	}
	
	public static final float findMaxULP(float value, float expected){
		final float ULP = Math.ulp(expected);
		
		if(value == expected)
			return 0f;
		
		final float absValue = Math.abs(value - expected);
		return absValue / ULP;
	}
	
	public static final boolean compare(float a, float b){
		return (Math.abs(a - b) > FloatOps.EPSILON)? false : true;
	}
	
	public static final boolean compare(float a, float b, float tol){
		return (Math.abs(a - b) > tol)? false : true;
	}
	
	public static final float sq(float value){
		return value * value;
	}
	
	public static final void atomicAdd(float[] array, int index, float value){
		array[index] += value;
	}
}

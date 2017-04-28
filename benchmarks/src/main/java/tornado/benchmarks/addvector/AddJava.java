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
package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.collections.types.Float4;
import tornado.collections.types.VectorFloat4;
import static tornado.benchmarks.GraphicsKernels.*;

public class AddJava extends BenchmarkDriver {

	private final int numElements;
	
	private VectorFloat4 a, b, c;
	
	public AddJava(int iterations, int numElements){
		super(iterations);
		this.numElements = numElements;
	}
	
	@Override
	public void setUp() {
		a = new VectorFloat4(numElements);
		b = new VectorFloat4(numElements);
		c = new VectorFloat4(numElements);
		
		
		final Float4 valueA = new Float4(new float[]{1f,1f,1f,1f});
		final Float4 valueB = new Float4(new float[]{2f,2f,2f,2f});
		for(int i=0;i<numElements;i++){
			a.set(i,valueA);
			b.set(i,valueB);
		}
	}
	
	@Override
	public void tearDown() {
		a = null;
		b = null;
		c = null;
		super.tearDown();
	}

	@Override
	public void code() {
			addVector(a, b, c);
	}
	
	@Override
	public void barrier(){
		
	}

	@Override
	public boolean validate() {
		return true;
	}
	
	public void printSummary(){
		System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n",getElapsed(),getElapsedPerIteration());
	}

}

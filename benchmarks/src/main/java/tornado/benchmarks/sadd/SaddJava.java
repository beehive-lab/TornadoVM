package tornado.benchmarks.sadd;

import tornado.benchmarks.BenchmarkDriver;
import static tornado.benchmarks.LinearAlgebraArrays.*;

public class SaddJava extends BenchmarkDriver {

	private final int numElements;
	
	private float[] a, b, c;
	
	public SaddJava(int iterations, int numElements){
		super(iterations);
		this.numElements = numElements;
	}
	
	@Override
	public void setUp() {
		a = new float[numElements];
		b = new float[numElements];
		c = new float[numElements];
		
		for(int i=0;i<numElements;i++){
			a[i] = 1;
			b[i] = 2;
			c[i] = 0;
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
			sadd(a,b,c);
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

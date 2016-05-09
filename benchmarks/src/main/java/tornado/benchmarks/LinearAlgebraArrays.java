package tornado.benchmarks;

import tornado.api.Atomic;
import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.api.Write;

public class LinearAlgebraArrays {

	
	public static void reduceInt(@Atomic @Write int[] result, @Read int[] input){
		int sum = 0;
		for(@Parallel int i=0;i<input.length;i++){
			sum += input[i];
		}
		result[0] = sum;
	}
	
	public static void reduce1(@Write float[] output, @Read float[] input){
		final int numThreads = output.length;
		for(@Parallel int thread=0;thread<numThreads;thread++){
			
			float sum = 0f;
			for(int i=thread;i<input.length;i+=numThreads)
				sum += input[i];
		
			output[thread] = sum;
		
		}
	}
	
	public static void reduce2(@Write float[] output, @Read float[] input){
		final int numThreads = output.length;
		for(@Parallel int thread=0;thread<numThreads;thread++){
			
			float sum = 0f;
			for(int i=0;i<input.length;i+=numThreads)
				sum += input[i];
		
			output[thread] = sum;
		}
	}
	
	public static void sadd(@Read float[] a, @Read float[] b, @ReadWrite float[] c){
		for (@Parallel
				int i = 0; i < c.length; i++)
					c[i] = a[i] + b[i];
	}
	
	public static void striad(@Read float alpha, @Read float[] a, @Read float[] b, @Write float[] c){
		for (@Parallel
				int i = 0; i < c.length; i++)
					c[i] = alpha * a[i] + b[i];
	}
	
	public static void sscal(@Read float alpha, @Read float[] x) {
		for (@Parallel
		int i = 0; i < x.length; i++)
			x[i] *= alpha;
	}

	public static void saxpy(@Read float alpha, @Read float[] x, @Write float[] y) {
		for (@Parallel
		int i = 0; i < y.length; i++)
			y[i] += alpha * x[i];
	}

	public static void sgemm(@Read final int M, @Read final int N, @Read final int K,  @Read final float A[], @Read final float B[],
			@ReadWrite final float C[] ) {

		for (@Parallel int i = 0; i < N; i++)
			for (@Parallel int j = 0; j < N; j++) {
				float sum = 0.0f;
				for (int k = 0; k < K; k++) {
					sum += A[(i * N) + k] * B[(k * N) + j];
				}
				C[(i * N) + j] = sum;
			}

	}

	public static void spmv(@Read final float[] val, @Read final int[] cols,
			@Read final int[] rowDelimiters, @Read final float[] vec,
			@Read final int dim, @Write final float[] out) {

		for (@Parallel int i = 0; i < dim; i++) {
			float t = 0.0f;
			for (int j = rowDelimiters[i]; j < rowDelimiters[i + 1]; j++) {
				final int col = cols[j];
				t += val[j] * vec[col];
			}
			out[i] = t;
		}
	}

}

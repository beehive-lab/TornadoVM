package tornado.benchmarks;

public class Suite {

	public static void main(String[] args) {
		
		/*
		 * array operations on 64MB arrays
		 */
		tornado.benchmarks.sadd.Benchmark.run(100, 16777216);
		tornado.benchmarks.saxpy.Benchmark.run(100, 16777216);
		tornado.benchmarks.addvector.Benchmark.run(100, 4194304);
		
		/*
		 * sgemm
		 */
		tornado.benchmarks.sgemm.Benchmark.run(50, 512, 512);
		
		/*
		 * operations on 1920x1080 images (2073600 pixels) - full HD
		 */
		tornado.benchmarks.dotvector.Benchmark.run(100, 2073600);
		tornado.benchmarks.rotatevector.Benchmark.run(100, 2073600);
		tornado.benchmarks.rotateimage.Benchmark.run(100, 1920, 1080);
		tornado.benchmarks.convolveimage.Benchmark.run(100, 1080, 1920, 5);
		tornado.benchmarks.convolvearray.Benchmark.run(100, 1080, 1920, 5);

		
		/*
		 * operations on 1366x768 images (1049088 pixels) - HD ready
		 */
		tornado.benchmarks.dotvector.Benchmark.run(100, 1049088);
		tornado.benchmarks.rotatevector.Benchmark.run(100, 1049088);
		tornado.benchmarks.rotateimage.Benchmark.run(100, 1366, 768);

		
		/*
		 * operations on 640x480 images (307200 pixels)
		 */
		tornado.benchmarks.dotvector.Benchmark.run(100, 307200);
		tornado.benchmarks.rotatevector.Benchmark.run(100, 307200);
		tornado.benchmarks.rotateimage.Benchmark.run(100, 640, 480);
		tornado.benchmarks.convolveimage.Benchmark.run(100, 480, 640, 5);
		tornado.benchmarks.convolvearray.Benchmark.run(100, 480, 640, 5);
		
		/*
		 * operations on 320x240 images (76800 pixels)
		 */
		tornado.benchmarks.dotvector.Benchmark.run(100, 76800);
		tornado.benchmarks.rotatevector.Benchmark.run(100, 76800);
		tornado.benchmarks.rotateimage.Benchmark.run(100, 320, 240);
		tornado.benchmarks.convolveimage.Benchmark.run(100, 240, 320, 5);
		tornado.benchmarks.convolvearray.Benchmark.run(100, 240, 320, 5);

	}

}

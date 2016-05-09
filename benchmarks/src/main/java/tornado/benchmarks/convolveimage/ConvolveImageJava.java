package tornado.benchmarks.convolveimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.collections.types.ImageFloat;
import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import static tornado.benchmarks.GraphicsKernels.*;
public class ConvolveImageJava extends BenchmarkDriver {

	private final int imageSizeX, imageSizeY, filterSize;
	
	private ImageFloat input, output, filter;
	
	public ConvolveImageJava(int iterations, int imageSizeX, int imageSizeY, int filterSize){
		super(iterations);
		this.imageSizeX = imageSizeX;
		this.imageSizeY = imageSizeY;
		this.filterSize = filterSize;
	}
	
	@Override
	public void setUp() {
		input = new ImageFloat(imageSizeX,imageSizeY);
        output = new ImageFloat(imageSizeX,imageSizeY);
        filter = new ImageFloat(filterSize,filterSize);

        createImage(input);
        createFilter(filter);
	}
	
	@Override
	public void tearDown() {
		input = null;
		output = null;
		filter = null;
		super.tearDown();
	}

	@Override
	public void code() {
			convolveImage(input,filter,output);
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

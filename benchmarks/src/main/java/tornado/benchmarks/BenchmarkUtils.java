package tornado.benchmarks;

import java.util.Random;

import tornado.collections.types.ImageFloat;

public final class BenchmarkUtils {
	
	 public static final void createFilter(final float[] filter, final int width,
             final int height) {
     float filterSum = 0.0f;
     final Random rand = new Random();

     for (int x = 0; x < height; x++)
             for (int y = 0; y < width; y++) {
                     final float f = rand.nextFloat();
                     filterSum += f;
                     filter[(y * width) + x] = f;
             }

     for (int x = 0; x < height; x++)
             for (int y = 0; y < width; y++)
                     filter[(y * width) + x] /= filterSum;

}
	 
	 public static final void createFilter(final ImageFloat filter){
		 createFilter(filter.asBuffer().array(),filter.X(),filter.Y());
	 }
	 
	   public static final void createImage(final float[] image, final int width,
               final int height) {
       final Random rand = new Random();
       rand.setSeed(7);
       for (int x = 0; x < height; x++)
               for (int y = 0; y < width; y++)
                       image[(y * width) + x] = rand.nextInt(256);
}
	 
	   public static final void createImage(final ImageFloat image) {
		   createImage(image.asBuffer().array(),image.X(),image.Y());
	   }
	   
	   
}

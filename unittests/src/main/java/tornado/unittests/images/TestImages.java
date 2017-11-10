package tornado.unittests.images;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;

public class TestImages {
	
	@Test
	public void testImageFloat01() {
		
		final int N = 128;
		final int M = 128;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}
	
	@Test
	public void testImageFloat02() {
		
		final int M = 128;
		final int N = 32;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}
	
	@Test
	public void testImageFloat03() {
		
		final int M = 128;
		final int N = 32;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}

}

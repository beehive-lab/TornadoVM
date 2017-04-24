package tornado.examples.objects;

import java.util.Random;
import tornado.collections.graphics.ImagingOps;
import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;

public class ResizeImage {

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0])
                : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1])
                : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image1 = new ImageFloat(numElementsX, numElementsY);
        final ImageFloat image2 = new ImageFloat(numElementsX / 2, numElementsY / 2);

        final Random rand = new Random();

        for (int y = 0; y < numElementsY; y++) {
            for (int x = 0; x < numElementsX; x++) {
                image1.set(x, y, rand.nextFloat());
            }
        }

        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", ImagingOps::resizeImage, image2, image1, 2)
                .streamOut(image2);

        schedule.warmup();

        if (image1.X() < 16 && image1.Y() < 16) {
            System.out.println("Before:");
            System.out.println(image1.toString());
        }

        final long start = System.nanoTime();
        schedule.execute();
        final long end = System.nanoTime();
        System.out.printf("time: %.9f s\n", (end - start) * 1e-9);

        /*
         * Ouput result to console
         */
        if (image2.X() < 16 && image2.Y() < 16) {
            System.out.println("Result:");
            System.out.println(image2.toString());
        }
    }
}

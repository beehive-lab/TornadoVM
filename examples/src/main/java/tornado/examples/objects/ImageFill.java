package tornado.examples.objects;

import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;

public class ImageFill {

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0])
                : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1])
                : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image = new ImageFloat(numElementsX, numElementsY);
        image.fill(-1f);

        final TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);

        System.out.println("Before:");
        System.out.println(image.toString());

        /*
         * Fill the array
         */
        graph.execute();
        /*
         * Ouput result to console
         */
        System.out.println("Result:");
        System.out.println(image.toString());

    }
}

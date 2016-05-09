package tornado.examples;

import tornado.collections.types.ImageFloat;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class ImageFill {

    public static void main(final String[] args) {
        final int numElementsX = (args.length == 2) ? Integer.parseInt(args[0])
                : 8;
        final int numElementsY = (args.length == 2) ? Integer.parseInt(args[1])
                : 8;

        System.out.printf("image: x=%d, y=%d\n", numElementsX, numElementsY);
        final ImageFloat image = new ImageFloat(numElementsX, numElementsY);
        image.fill(-1f);

        /*
         * First step is to create a reference to the method invocation
         * This involves finding the methods called and the arguments used
         * in each call.
         */
        final ExecutableTask<?> fillInvocation = TaskUtils.createTask(
                ImageFloat::fill, image, 1f);

        // workaround issue in class loaded with multiple runtime annotations
        // fillInvocation.getArgumentsAccess()[0] = Access.WRITE;

        /*
         * Next we map each invocation onto a specific compute device
         */
        fillInvocation.mapTo(new OCLDeviceMapping(0, 0));

        System.out.println("Before:");
        System.out.println(image.toString());

        /*
         * Fill the array
         */
        fillInvocation.execute();

        /*
         * Ouput result to console
         */
        System.out.println("Result:");
        System.out.println(image.toString());

    }
}

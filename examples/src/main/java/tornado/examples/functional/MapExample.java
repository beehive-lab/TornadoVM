package tornado.examples.functional;

public class MapExample {

    public static Integer inc(Integer value) {
        return value + 1;
    }

    public static final void main(String[] args) {
        int numElements = 8;
        Integer[] a = new Integer[numElements];
        Integer[] b = new Integer[numElements];

        Operators.map(
                MapExample::inc,
                a, b
        );

    }

}

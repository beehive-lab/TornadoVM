public class Init {
    public static void main(String[] a) throws Exception {
        for (var n : a) {
            long s = System.nanoTime();
            Class.forName(n, true, Init.class.getClassLoader());
            System.out.printf("%-70s %.2f ms%n", n, (System.nanoTime() - s) / 1e6);
        }
    }
}

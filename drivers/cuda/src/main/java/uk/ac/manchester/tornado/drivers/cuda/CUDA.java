package uk.ac.manchester.tornado.drivers.cuda;

import java.util.ArrayList;
import java.util.List;

public class CUDA {

    private static final List<CUDAPlatform> platforms = new ArrayList<>();

    public static int getNumPlatforms() {
        return 1;
    }

    public static CUDAPlatform getPlatform(int index) {
        return new CUDAPlatform(0, 0);
    }
}

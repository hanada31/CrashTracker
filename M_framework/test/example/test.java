package example;
import java.util.Calendar;

public class test {
    private int num = 3;
    private int times = 5;
    private final int VENDOR_DATA_SIZE = 100;

    public static void mustGreaterZero(int val) throws Exception {
        if (val <= 0) {
            throw new Exception("val is less than 0");
        }
    }

    public void decreaseOne() throws Exception {
        num--;
        mustGreaterZero(num);
    }

    public void onlyCallFiveTimes() throws Exception {
        times--;
        if (times < 0) {
            throw new Exception("call this function over five times");
        }
    }

    public void timeTrap() throws Exception {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek <= 3) {
            throw new Exception("today is Monday or Tuesday or Wednesday");
        }
    }

    public void nullChecker(String a, String b) throws Exception {
        if (a == null) {
            throw new Exception("a is null");
        }
        if (b == null) {
            throw new Exception("b is null");
        }
        return;
    }

    public void sendMhlVendorCommand(int portId, int offset, int length, byte[] data) throws IllegalAccessException {
        if (data == null || data.length != VENDOR_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid vendor command data.");
        }
        if (offset < 0 || offset >= VENDOR_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid offset:");
        }
        if (length < 0 || offset + length > VENDOR_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid length:");
        }
    }
//
//    public void tryCatchTest(int a, int b, String c) throws Exception {
//        try {
//            int d = a + b;
//            c = c + "abc";
//            char s = c.charAt(-1);
//            System.out.println(s);
//            if (d > 0) {
//                return;
//            }
//        } catch (Exception e) {
//            throw new Exception("this is an exception");
//        }
//    }

    public void tryCatchTest_caller(int a, int b, String c) throws Exception {
        if(b>0) {
            try {
                b += 1;
                System.out.println(b);
                tryCatchTest_callee(a);
            } catch (RuntimeException e) {
                if(c == null)
                    throw new Exception("this is an exception");
            }
        }
    }

    public void tryCatchTest_callee(int a) throws IllegalArgumentException {
        if(a>0)
            throw new IllegalArgumentException("Illegal Argument");
    }
}

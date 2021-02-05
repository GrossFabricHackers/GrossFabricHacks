package net.devtech.grossfabrichacks.util;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

public class LogUtil {
    public static void logMeanTime(TestInfo... tests) {
        DoubleArrayList times = new DoubleArrayList();

        for (TestInfo test : tests) {
            times.add(meanTime(test));
        }

        for (int i = 0, length = tests.length; i < length; i++) {
            TestInfo test = tests[i];

            if (test.format == null) {
                printfln(times.getDouble(i));
            } else {
                printfln(test.format, times.getDouble(i));
            }
        }
    }

    public static void logTime(TestInfo... tests) {
        DoubleArrayList times = new DoubleArrayList();

        for (TestInfo test : tests) {
            times.add(time(test));
        }

        for (int i = 0, length = tests.length; i < length; i++) {
            TestInfo test = tests[i];

            if (test.format == null) {
                printfln(times.getDouble(i));
            } else {
                printfln(test.format, times.getDouble(i));
            }
        }
    }

    public static void logTime(int iterations, ThrowingIntConsumer test) {
        printfln(time(iterations, test));
    }

    public static void logMeanTime(int iterations, ThrowingIntConsumer test) {
        printfln(meanTime(iterations, test));
    }

    public static void logTime(String format, int iterations, ThrowingIntConsumer test) {
        printfln(format, time(iterations, test));
    }

    public static void logMeanTime(String format, int iterations, ThrowingIntConsumer test) {
        printfln(format, meanTime(iterations, test));
    }

    public static double meanTime(TestInfo test) {
        return meanTime(test.iterations, test.test);
    }

    public static double time(TestInfo test) {
        return time(test.iterations, test.test);
    }

    public static double meanTime(int iterations, ThrowingIntConsumer test) {
        return time(iterations, test) / iterations;
    }

    public static double time(int iterations, ThrowingIntConsumer test) {
        long start = System.nanoTime();

        try {
            for (int i = 0; i < iterations; i++) {
                test.accept(i);
            }
        } catch (Throwable throwable) {
            throw Util.crash(throwable);
        }

        return (System.nanoTime() - start) / 1000000000D;
    }

    public static void printfln(Object format, Object... arguments) {
        System.out.printf(format + "%n", arguments);
    }
}

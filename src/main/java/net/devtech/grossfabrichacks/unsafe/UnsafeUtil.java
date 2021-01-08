package net.devtech.grossfabrichacks.unsafe;

import java.io.IOException;
import java.lang.reflect.Array;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.gudenau.lib.unsafe.Unsafe;
import user11681.reflect.Classes;

/**
 * works across all normal JVMs I think
 */
@SuppressWarnings("unchecked")
public class UnsafeUtil {
    public static final FabricLauncherBase launcher = (FabricLauncherBase) FabricLauncherBase.getLauncher();

    // constants
    public static final long BYTE_ARR_KLASS;
    public static final long SHORT_ARR_KLASS;
    public static final long CHAR_ARR_KLASS;
    public static final long INT_ARR_KLASS;
    public static final long LONG_ARR_KLASS;
    public static final long FLOAT_ARR_KLASS;
    public static final long DOUBLE_ARR_KLASS;
    public static final long CLASS_KLASS_OFFSET;

    /**
     * set the first 4 bytes of an object to something, this can be used to mutate the size of an array
     */
    public static void setFirstInt(Object object, int val) {
        Unsafe.putInt(object, Classes.fieldOffset, val);
    }

    /**
     * Convert an array of primitives of a smaller type into one of a larger type, for example
     * to go from a byte array to an int array you would use this. careful, this directly modifies the klass value
     * in the array, it does not copy it<br><br>
     *
     * <b>UnsafeUtil.upcastArray(byte_array, UnsafeUtil.INT_ARR_KLASS, 4)</b>
     *
     * @param array      the original array
     * @param newType    the target type
     * @param conversion the conversion factor, for example an int has 2 shorts so to go from a short array to an int array it would be 2
     * @param <T>        the returned array type
     * @return a non-copied casted array
     */
    public static <T> T upcastArray(Object array, long newType, int conversion) {
        Unsafe.putInt(array, Classes.fieldOffset, Unsafe.getInt(array, Classes.fieldOffset) / conversion);

        return unsafeCast(array, newType);
    }

    /**
     * Convert an array of primitives of a larger type into one of a smaller type, for example
     * to go from an int array to an byte array you would do, careful, this directly modifies the klass value
     * in the array, it does not copy it.<br><br>
     *
     * <b>UnsafeUtil.downcastArray(int_array, UnsafeUtil.BYTE_ARR_KLASS, 4)</b>
     *
     * @param array      the original array
     * @param newType    the target type
     * @param conversion the conversion factor, for example an short has 1/2 ints so to go from an int array to a short array it would be 2
     * @param <T>        the returned array type
     * @return a non-copied casted array
     */
    public static <T> T downcastArray(Object array, int newType, int conversion) {
        Unsafe.putInt(array, Classes.fieldOffset, Unsafe.getInt(array, Classes.fieldOffset) * conversion);

        return unsafeCast(array, newType);
    }

    /**
     * casts the array to a different type of array without copying it,
     * all the classes inside the array should be an instance of the B class
     * you should recast it to it's original type after you have used it!
     *
     * @param obj    the original array
     * @param bClass the class that each of the elements are expected to be
     * @param <B>    the desired type of the array
     */
    public static <B> B[] arrayCast(Object[] obj, Class<B> bClass) {
        return arrayCast(obj, getKlass(Array.newInstance(bClass, 0)));
    }

    /**
     * casts the array with the class' klass value without copying it, obtained from Reflection#getKlass(Class)
     * you should recast it to it's original type after you have used it!
     *
     * @param obj        the array to be casted
     * @param classKlass the integer klass value
     * @param <B>        the desired type
     * @see UnsafeUtil#getKlass(Object)
     */
    public static <B> B[] arrayCast(Object[] obj, long classKlass) {
        if (Classes.longClassPointer) {
            Unsafe.getAndSetLong(obj, Classes.classOffset, classKlass);
        } else {
            Unsafe.getAndAddInt(obj, Classes.classOffset, (int) classKlass);
        }

        return (B[]) obj;
    }

    /**
     * casts the object with the class' klass value without copying it, obtained from Reflection#getKlass(Class)
     * recast to original type or stack corruption may occur!
     *
     * @param object     the object to be casted
     * @param klassValue the integer klass value
     * @param <B>        the desired type
     * @see UnsafeUtil#getKlass(Object)
     */
    public static <B> B unsafeCast(Object object, long klassValue) {
        if (Classes.longClassPointer) {
            Unsafe.getAndSetLong(object, Classes.classOffset, klassValue);
        } else {
            Unsafe.getAndSetInt(object, Classes.classOffset, (int) (klassValue));
        }

        return (B) object;
    }

    /**
     * gets the klass value from an object
     *
     * @param cls an instance of the class to obtain the klass value from
     */
    public static long getKlass(Object cls) {
        if (Classes.longClassPointer) {
            return Unsafe.getLong(cls, Classes.classOffset);
        }

        return Unsafe.getInt(cls, Classes.classOffset);
    }

    /**
     * get the klass pointer of a class, only works on instantiatable classes
     */
    public static long getKlassFromClass(Class<?> type) {
        return getKlass(Unsafe.allocateInstance(type));
    }

    /**
     * get the klass value from a class
     *
     * @deprecated doesn't work, idk why todo fix
     */
    @Deprecated
    public static long getKlassFromClass0(Class<?> type) {
        if (Classes.longClassPointer) {
            return Unsafe.getLong(type, CLASS_KLASS_OFFSET);
        }

        return Unsafe.getInt(type, CLASS_KLASS_OFFSET);
    }

    public static long addressOf(Object object) {
        return addressOf(0, object);
    }

    public static long addressOf(int index, Object... objects) {
        final long offset = Unsafe.arrayBaseOffset(objects.getClass());
        final long scale = Unsafe.arrayIndexScale(objects.getClass());

        return Integer.toUnsignedLong(Unsafe.getInt(objects, offset + index * scale)) * Classes.addressFactor;
    }

    public static <T> Class<T> findClass(String binaryName, ClassLoader loader) {
        try {
            return (Class<T>) loader.loadClass(binaryName);
        } catch (ClassNotFoundException exception) {
            try {
                byte[] bytecode = launcher.getClassByteArray(binaryName, false);

                return Unsafe.defineClass(binaryName, bytecode, 0, bytecode.length, loader, UnsafeUtil.class.getProtectionDomain());
            } catch (IOException ioException) {
                throw Unsafe.throwException(ioException);
            }
        }
    }

    static {
        if (Classes.fieldOffset == 8) { // 32bit JVM
            CLASS_KLASS_OFFSET = 80;
        } else if (Classes.fieldOffset == 12) { // 64bit JVM with compressed OOPs
            CLASS_KLASS_OFFSET = 84;
        } else { // 16 bytes; 64bit JVM
            CLASS_KLASS_OFFSET = 160;
        }

        BYTE_ARR_KLASS = getKlass(new byte[0]);
        SHORT_ARR_KLASS = getKlass(new short[0]);
        CHAR_ARR_KLASS = getKlass(new char[0]);
        INT_ARR_KLASS = getKlass(new int[0]);
        LONG_ARR_KLASS = getKlass(new long[0]);
        FLOAT_ARR_KLASS = getKlass(new float[0]);
        DOUBLE_ARR_KLASS = getKlass(new double[0]);
    }
}
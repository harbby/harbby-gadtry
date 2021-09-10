/*
 * Copyright (C) 2018 The GadTry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.harbby.gadtry.base;

import com.github.harbby.gadtry.io.IOUtils;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;
import sun.reflect.ReflectionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.util.List;
import java.util.function.Supplier;

import static com.github.harbby.gadtry.base.MoreObjects.checkArgument;
import static com.github.harbby.gadtry.base.MoreObjects.checkState;
import static java.util.Objects.requireNonNull;

public final class Platform
{
    private Platform() {}

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to
     * allow safepoint polling during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static final Unsafe unsafe = getUnsafe0();
    private static final int classVersion = getClassVersion0();
    private static final String JAVA_FULL_VERSION = System.getProperty("java.version");
    private static final int javaVersion = getJavaVersion0();
    private static final Supplier<PlatformBase> platformBase = Lazys.of(() -> {
        checkState(getJavaVersion() > 8);
        try {
            return (PlatformBase) Class.forName("com.github.harbby.gadtry.base.PlatformBaseImpl").newInstance();
        }
        catch (Exception e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    });

    public static Unsafe getUnsafe()
    {
        return unsafe;
    }

    private static Unsafe getUnsafe0()
    {
        sun.misc.Unsafe obj = null;
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            obj = (sun.misc.Unsafe) unsafeField.get(null);
        }
        catch (Exception cause) {
            throwException(cause);
        }
        return requireNonNull(obj);
    }

    /**
     * Java9引入模块化后，一些内部api具有破坏性兼容性。
     * 这里的方法多数为Java9以上平台所添加的新方法
     */
    interface PlatformBase
    {
        Class<?> defineClass(Class<?> buddyClass, byte[] classBytes)
                throws IllegalAccessException;

        void freeDirectBuffer(DirectBuffer buffer);

        Object createCleaner(Object ob, Runnable thunk);

        ClassLoader latestUserDefinedLoader();

        ClassLoader getBootstrapClassLoader();

        long getProcessPid(Process process);

        long getCurrentProcessId();

        boolean isOpen(Class<?> source, Class<?> target);
    }

    public static byte[] readClassByteCode(Class<?> aClass)
            throws IOException
    {
        CodeSource codeSource = requireNonNull(aClass.getProtectionDomain().getCodeSource(), "not found codeSource");
        URL location = codeSource.getLocation();
        if ("jar".equals(location.getProtocol()) || "file".equals(location.getProtocol())) {
            try (InputStream inputStream = new URL(location, aClass.getName().replace(".", "/") + ".class").openStream()) {
                return IOUtils.readAllBytes(inputStream);
            }
        }
        throw new UnsupportedOperationException("not support location " + location);
    }

    public static long allocateMemory(long bytes)
    {
        return unsafe.allocateMemory(bytes);  //默认按16个字节对齐
    }

    /**
     * copy  {@link java.nio.ByteBuffer#allocateDirect(int)}
     * must alignByte = Math.sqrt(alignByte)
     *
     * @param len       allocate direct mem size
     * @param alignByte default 16
     * @return [base, dataOffset]
     */
    public static long[] allocateAlignMemory(long len, long alignByte)
    {
        checkArgument(Maths.isPowerOfTwo(alignByte), "Number %s power of two", alignByte);

        long size = Math.max(1L, len + alignByte);
        long base = 0;
        try {
            base = unsafe.allocateMemory(size);
        }
        catch (OutOfMemoryError x) {
            throw x;
        }
        //unsafe.setMemory(base, size, (byte) 0);

        long dataOffset;
        if (base % alignByte != 0) {
            // Round up to page boundary
            dataOffset = base + alignByte - (base & (alignByte - 1));
        }
        else {
            dataOffset = base;
        }
        long[] res = new long[2];
        res[0] = base;
        res[1] = dataOffset;
        return res;
    }

    public static void freeMemory(long address)
    {
        unsafe.freeMemory(address);
    }

    public static void registerForClean(AutoCloseable closeable)
    {
        createCleaner(closeable, () -> {
            try {
                closeable.close();
            }
            catch (Exception e) {
                throwException(e);
            }
        });
    }

    public static int getJavaVersion()
    {
        return javaVersion;
    }

    public static int getClassVersion()
    {
        return classVersion;
    }

    private static int getJavaVersion0()
    {
        String javaSpecVersion = requireNonNull(System.getProperty("java.specification.version"),
                "not found value for System.getProperty(java.specification.version)");
        final String[] split = javaSpecVersion.split("\\.");
        final int[] version = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            version[i] = Integer.parseInt(split[i]);
        }
        if (version[0] == 1) {
            return version[1];
        }
        else {
            return version[0];
        }
    }

    /**
     * java8 52
     * java9 53
     * java10 54
     * java11 55
     * java15 59
     * java16 60
     * java17 61
     *
     * @return vm class major version
     */
    private static int getClassVersion0()
    {
        String javaClassVersion = requireNonNull(System.getProperty("java.class.version"),
                "not found value for System.getProperty(java.class.version)");
        final String[] split = javaClassVersion.split("\\.");
        final int[] version = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            version[i] = Integer.parseInt(split[i]);
        }
        //assert version[0] == 0;
        return version[0];
    }

    public static boolean isWin()
    {
        return osName().startsWith("Windows");
    }

    public static boolean isMac()
    {
        return osName().startsWith("Mac OS X");
    }

    public static boolean isLinux()
    {
        return osName().startsWith("Linux");
    }

    public static String osName()
    {
        return System.getProperty("os.name", "");
    }

    public static boolean isJdkClass(Class<?> aClass)
    {
        if (getJavaVersion() > 8 && !platformBase.get().isOpen(aClass, Platform.class)) {
            return true;
        }

        ClassLoader classLoader = aClass.getClassLoader();
        while (classLoader != null) {
            if (classLoader == Platform.class.getClassLoader()) {
                return false;
            }
            classLoader = classLoader.getParent();
        }
        return true;
    }

    /**
     * jdk8:  sun.misc.VM.latestUserDefinedLoader()
     * jdk9+: jdk.internal.misc.VM.latestUserDefinedLoader()
     *
     * @return latestUserDefinedLoader
     */
    public static ClassLoader latestUserDefinedLoader()
    {
        if (Platform.getJavaVersion() > 8) {
            return platformBase.get().latestUserDefinedLoader();
        }
        else {
            try {
                return (ClassLoader) Class.forName("sun.misc.VM")
                        .getMethod("latestUserDefinedLoader")
                        .invoke(null);
            }
            catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new PlatFormUnsupportedOperation(e);
            }
        }
    }

    /**
     * 用户系统类加载器
     *
     * @return appClassLoader
     */
    public static ClassLoader getAppClassLoader()
    {
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * 获取jdk类加载器
     *
     * @return bootstrap ClassLoader
     */
    public static ClassLoader getBootstrapClassLoader()
    {
        if (Platform.getJavaVersion() > 8) {
            return platformBase.get().getBootstrapClassLoader();
        }
        try {
            Object upath = Class.forName("sun.misc.Launcher").getMethod("getBootstrapClassPath").invoke(null);
            URL[] urls = (URL[]) Class.forName("sun.misc.URLClassPath").getMethod("getURLs").invoke(upath);
            return new URLClassLoader(urls, null);
        }
        catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    public static long getCurrentProcessId()
    {
        if (Platform.getJavaVersion() > 8) { //>= java9
            return platformBase.get().getCurrentProcessId();
        }
        if (Platform.isWin() || Platform.isLinux() || Platform.isMac()) {
            return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        }
        else {
            throw new PlatFormUnsupportedOperation("java" + getJavaVersion() + " get pid does not support current system " + osName());
        }
    }

    public static long getProcessPid(Process process)
    {
        if (Platform.getJavaVersion() > 8) { //>= java9
            return platformBase.get().getProcessPid(process);
        }
        if (Platform.isLinux() || Platform.isMac()) {
            try {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                return (int) field.get(process);
            }
            catch (NoSuchFieldException | IllegalAccessException e) {
                throw new PlatFormUnsupportedOperation(e);
            }
        }
        else if (Platform.isWin()) {
            try {
                Field field = process.getClass().getDeclaredField("handle");
                field.setAccessible(true);

                com.sun.jna.platform.win32.WinNT.HANDLE handle = new com.sun.jna.platform.win32.WinNT.HANDLE();
                handle.setPointer(com.sun.jna.Pointer.createConstant((long) field.get(process)));
                return com.sun.jna.platform.win32.Kernel32.INSTANCE.GetProcessId(handle);
            }
            catch (NoSuchFieldException | IllegalAccessException e) {
                throw new PlatFormUnsupportedOperation(e);
            }
        }
        else {
            throw new UnsupportedOperationException("get pid when java.version < 9 only support UNIX Linux windows macOS");
        }
    }

    /**
     * Creates a new cleaner.
     *
     * @param ob    the referent object to be cleaned
     * @param thunk The cleanup code to be run when the cleaner is invoked.  The
     *              cleanup code is run directly from the reference-handler thread,
     *              so it should be as simple and straightforward as possible.
     * @return The new cleaner
     */
    public static Object createCleaner(Object ob, Runnable thunk)
    {
        if (getJavaVersion() > 8) { //jdk9+
            //jdk9+: --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
            return platformBase.get().createCleaner(ob, thunk);
        }
        try {
            Method createMethod = Class.forName("sun.misc.Cleaner").getDeclaredMethod("create", Object.class, Runnable.class);
            createMethod.setAccessible(true);
            return createMethod.invoke(null, ob, thunk);
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("unchecked", e);
        }
    }

    /**
     * java16 need: --add-opens=java.base/java.lang=ALL-UNNAMED
     * If java version> 16 it is recommended to use {@link Platform#defineClass(Class, byte[])}
     */
    public static Class<?> defineClass(byte[] classBytes, ClassLoader classLoader)
            throws PlatFormUnsupportedOperation, IllegalAccessException
    {
        try {
            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineClass.setAccessible(true);
            return (Class<?>) defineClass.invoke(classLoader, null, classBytes, 0, classBytes.length);
        }
        catch (InvocationTargetException | NoSuchMethodException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    /**
     * Converts the class byte code to a java.lang.Class object
     * This method is available in Java 9 or later
     */
    public static Class<?> defineClass(Class<?> buddyClass, byte[] classBytes)
            throws IllegalAccessException
    {
        checkState(getJavaVersion() > 8, "This method is available in Java 9 or later");
        return platformBase.get().defineClass(buddyClass, classBytes);
    }

    public static long reallocateMemory(long address, long oldSize, long newSize)
    {
        long newMemory = unsafe.allocateMemory(newSize);
        copyMemory(null, address, null, newMemory, oldSize);
        unsafe.freeMemory(address);
        return newMemory;
    }

    /**
     * Uses internal JDK APIs to allocate a DirectByteBuffer while ignoring the JVM's
     * MaxDirectMemorySize limit (the default limit is too low and we do not want to require users
     * to increase it).
     * <p>
     * jdk9+ need: --add-opens=java.base/java.nio=ALL-UNNAMED
     *
     * @param size allocate mem size
     * @return ByteBuffer
     */
    public static ByteBuffer allocateDirectBuffer(int size)
            throws PlatFormUnsupportedOperation
    {
        try {
            Class<?> cls = Class.forName("java.nio.DirectByteBuffer");
            Constructor<?> constructor = cls.getDeclaredConstructor(Long.TYPE, Integer.TYPE);
            constructor.setAccessible(true);
            Field cleanerField = cls.getDeclaredField("cleaner");
            cleanerField.setAccessible(true);
            long memory = unsafe.allocateMemory(size);
            ByteBuffer buffer = (ByteBuffer) constructor.newInstance(memory, size);
            Object cleaner = createCleaner(buffer, (Runnable) () -> unsafe.freeMemory(memory));
            //Cleaner cleaner = Cleaner.create(buffer, () -> _UNSAFE.freeMemory(memory));
            cleanerField.set(buffer, cleaner);
            return buffer;
        }
        catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    /**
     * Free DirectBuffer
     *
     * @param buffer DirectBuffer waiting to be released
     */
    public static void freeDirectBuffer(ByteBuffer buffer)
    {
        checkState(buffer.isDirect(), "buffer not direct");
        sun.nio.ch.DirectBuffer directBuffer = (DirectBuffer) buffer;
        if (getJavaVersion() > 8) {
            platformBase.get().freeDirectBuffer(directBuffer);
        }
        else {
            try {
                Object cleaner = sun.nio.ch.DirectBuffer.class.getMethod("cleaner")
                        .invoke(directBuffer);
                Class.forName("sun.misc.Cleaner").getMethod("clean").invoke(cleaner);
            }
            catch (IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("unchecked", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> tClass)
            throws PlatFormUnsupportedOperation
    {
        try {
            return (T) unsafe.allocateInstance(tClass);
        }
        catch (InstantiationException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance2(Class<T> tClass)
            throws PlatFormUnsupportedOperation
    {
        try {
            Constructor<T> superCons = (Constructor<T>) Object.class.getConstructor();
            ReflectionFactory reflFactory = ReflectionFactory.getReflectionFactory();
            Constructor<T> c = (Constructor<T>) reflFactory.newConstructorForSerialization(tClass, superCons);
            return c.newInstance();
        }
        catch (InvocationTargetException | InstantiationException | NoSuchMethodException | IllegalAccessException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    public static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length)
    {
        // Check if dstOffset is before or after srcOffset to determine if we should copy
        // forward or backwards. This is necessary in case src and dst overlap.
        if (dstOffset < srcOffset) {
            while (length > 0) {
                long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
                unsafe.copyMemory(src, srcOffset, dst, dstOffset, size);
                length -= size;
                srcOffset += size;
                dstOffset += size;
            }
        }
        else {
            srcOffset += length;
            dstOffset += length;
            while (length > 0) {
                long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
                srcOffset -= size;
                dstOffset -= size;
                unsafe.copyMemory(src, srcOffset, dst, dstOffset, size);
                length -= size;
            }
        }
    }

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     *
     * @param t Throwable
     */
    public static void throwException(Throwable t)
    {
        unsafe.throwException(t);
    }

    /**
     * get system classLoader ucp jars
     *
     * @return system classLoader ucp jars
     */
    public static List<URL> getSystemClassLoaderJars()
            throws PlatFormUnsupportedOperation
    {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        if (classLoader instanceof URLClassLoader) {
            return java.util.Arrays.asList(((URLClassLoader) classLoader).getURLs());
        }
        //java11+
        try {
            Field field = classLoader.getClass().getDeclaredField("ucp");
            field.setAccessible(true);
            Object ucp = field.get(classLoader);
            Method getURLs = ucp.getClass().getMethod("getURLs");
            URL[] urls = (URL[]) getURLs.invoke(ucp);
            return java.util.Arrays.asList(urls);
        }
        catch (NoSuchMethodException | NoSuchFieldException | InvocationTargetException | IllegalAccessException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }

    /**
     * load other jar to system classLoader
     *
     * @param urls jars
     */
    public static void loadExtJarToSystemClassLoader(List<URL> urls)
            throws PlatFormUnsupportedOperation
    {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try {
            if (classLoader instanceof URLClassLoader) {
                Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURLMethod.setAccessible(true);
                for (URL uri : urls) {
                    addURLMethod.invoke(classLoader, uri);
                }
                return;
            }
            checkState(getJavaVersion() > 8, "check java version > 8 failed");
            //java11+
            Field field = classLoader.getClass().getDeclaredField("ucp");
            field.setAccessible(true);
            Object ucp = field.get(classLoader);
            Method addURLMethod = ucp.getClass().getMethod("addURL", URL.class);
            for (URL url : urls) {
                addURLMethod.invoke(ucp, url);
            }
        }
        catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new PlatFormUnsupportedOperation(e);
        }
    }
}

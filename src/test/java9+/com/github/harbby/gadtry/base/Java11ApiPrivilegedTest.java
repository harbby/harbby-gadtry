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

import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Java11ApiPrivilegedTest
{
    private static final Unsafe unsafe = Platform.getUnsafe();

    /**
     * see: {@link Java11ApiPrivilegedTest}
     */
    @Test
    public void should_not_throw_InaccessibleObjectException_doPrivilegedTest()
            throws Exception
    {
        Runnable thunk = () -> {};
        Platform.addOpenJavaModules(Class.forName("jdk.internal.ref.Cleaner"), Java11ApiPrivilegedTest.class);
        Object out;
        try {
            Method method = Class.forName("jdk.internal.ref.Cleaner").getDeclaredMethod("create", Object.class, Runnable.class);
            method.setAccessible(true);
            out = method.invoke(null, null, thunk);
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
        Assert.assertEquals("jdk.internal.ref.Cleaner", out.getClass().getName());
    }

    /**
     * 这个例子在java9+ jdk上不会打印 Warring
     */
    @Test
    public void should_not_printWarring_doPrivilegedTest()
            throws Exception
    {
        String log = "hello;";
        Platform.addOpenJavaModules(FilterOutputStream.class, Java11ApiPrivilegedTest.class);
        OutputStream out;
        try {
            System.out.println(log);
            if (!"hello;".equals(log)) {
                throw new IllegalStateException("check failed");
            }

            Field field = FilterOutputStream.class.getDeclaredField("out");
            field.setAccessible(true);
            out = (OutputStream) field.get(System.out);
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
        Assert.assertNotNull(out);
    }
}

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
package com.github.harbby.gadtry.aop.event;

import com.github.harbby.gadtry.aop.MethodSignature;
import com.github.harbby.gadtry.aop.codegen.ProxyAccess;

import java.lang.reflect.Method;

import static com.github.harbby.gadtry.base.JavaTypes.getClassInitValue;
import static java.util.Objects.requireNonNull;

/**
 * around
 */
public interface JoinPoint
        extends Before
{
    Object mock();

    default Object proceed()
            throws Throwable
    {
        return proceed(getArgs());
    }

    Object proceed(Object[] args)
            throws Throwable;

    public static JoinPoint of(Object mock, Method method, Object[] args, Object instance)
    {
        requireNonNull(instance, "instance is null");
        MethodSignature methodSignature = MethodSignature.of(method);
        return new JoinPoint()
        {
            @Override
            public MethodSignature getMethod()
            {
                return methodSignature;
            }

            @Override
            public Object mock()
            {
                return mock;
            }

            @Override
            public Object proceed(Object[] args)
                    throws Exception
            {
                if (mock instanceof ProxyAccess) {
                    return ((ProxyAccess) mock).callRealMethod(method, instance, args);
                }
                //jdk proxy
                return method.invoke(instance, args);
            }

            @Override
            public Object[] getArgs()
            {
                return args;
            }
        };
    }

    public static JoinPoint of(Object mock, Method method, Object[] args)
    {
        MethodSignature methodSignature = MethodSignature.of(method);
        return new JoinPoint()
        {
            @Override
            public MethodSignature getMethod()
            {
                return methodSignature;
            }

            @Override
            public Object mock()
            {
                return mock;
            }

            @Override
            public Object proceed(Object[] args)
                    throws Exception
            {
                return getClassInitValue(method.getReturnType());
            }

            @Override
            public Object[] getArgs()
            {
                return args;
            }
        };
    }
}

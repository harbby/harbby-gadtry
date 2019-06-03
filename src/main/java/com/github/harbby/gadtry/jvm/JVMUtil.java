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
package com.github.harbby.gadtry.jvm;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JVMUtil
{
    private JVMUtil() {}

    /**
     * get this jvm class.path all jars
     *
     * @return Set File
     */
    public static Set<File> systemJars()
    {
        String[] jars = System.getProperty("java.class.path")
                .split(Pattern.quote(File.pathSeparator));
        Set<File> res = Arrays.stream(jars).map(File::new).filter(File::isFile)
                .collect(Collectors.toSet());
        return res;
    }
}

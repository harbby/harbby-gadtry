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
package com.github.harbby.gadtry.jcodec.codecs;

import com.github.harbby.gadtry.jcodec.InputView;
import com.github.harbby.gadtry.jcodec.Jcodec;
import com.github.harbby.gadtry.jcodec.OutputView;
import com.github.harbby.gadtry.jcodec.Serializer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapSerializer<K, V>
        implements Serializer<Map<K, V>>
{
    @Override
    public void write(Jcodec jcodec, OutputView output, Map<K, V> value)
    {
        if (value == null) {
            output.writeVarInt(0, true);
            return;
        }
        final int size = value.size();
        //write size on the head
        output.writeVarInt(size + 1, true);
        //write key and value
        for (Map.Entry<K, V> entry : value.entrySet()) {
            jcodec.writeClassAndObject(output, entry.getKey());
            jcodec.writeClassAndObject(output, entry.getValue());
        }
    }

    @Override
    public Map<K, V> read(Jcodec jcodec, InputView input, Class<? extends Map<K, V>> typeClass)
    {
        int size = input.readVarInt(true);
        if (size == 0) {
            return null;
        }
        size--;
        Class<?> mapClass = typeClass;
        Map<K, V> map = mapClass == LinkedHashMap.class ? new LinkedHashMap<>(size) : new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            K key = jcodec.readClassAndObject(input);
            V value = jcodec.readClassAndObject(input);
            map.put(key, value);
        }
        return map;
    }

    @Override
    public boolean isNullable()
    {
        return true;
    }

    @Override
    public Comparator<Map<K, V>> comparator()
    {
        throw new UnsupportedOperationException("map obj not support comparator");
    }
}

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

import java.util.Comparator;

public class AnyArrayJcodec<E>
        implements Jcodec<E[]>
{
    private final Jcodec<E> jcodec;
    private final Class<E> classTag;

    public AnyArrayJcodec(Jcodec<E> jcodec, Class<E> classTag)
    {
        this.jcodec = jcodec;
        this.classTag = classTag;
    }

    @Override
    public void encoder(E[] values, OutputView output)
    {
        if (values == null) {
            output.writeVarInt(0, false);
            return;
        }
        output.writeVarInt(values.length + 1, false);
        for (E e : values) {
            jcodec.encoder(e, output);
        }
    }

    @Override
    public E[] decoder(InputView input)
    {
        int len = input.readVarInt(false) - 1;
        if (len == -1) {
            return null;
        }
        @SuppressWarnings("unchecked")
        E[] values = (E[]) java.lang.reflect.Array.newInstance(classTag, len);
        for (int i = 0; i < len; i++) {
            values[i] = jcodec.decoder(input);
        }
        return values;
    }

    @Override
    public Comparator<E[]> comparator()
    {
        return comparator(jcodec.comparator());
    }

    public static <E> Comparator<E[]> comparator(Comparator<E> comparator)
    {
        return (arr1, arr2) -> {
            int len1 = arr1.length;
            int len2 = arr2.length;
            int lim = Math.min(len1, len2);

            int k = 0;
            while (k < lim) {
                E c1 = arr1[k];
                E c2 = arr2[k];
                if (c1 != c2) {
                    return comparator.compare(c1, c2);
                }
                k++;
            }
            return len1 - len2;
        };
    }
}

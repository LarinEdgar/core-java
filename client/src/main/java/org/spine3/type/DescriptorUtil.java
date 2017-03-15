/*
 * Copyright 2017, TeamDev Ltd. All rights reserved.
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spine3.type;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.spine3.util.Exceptions.newIllegalArgumentException;
import static org.spine3.util.Exceptions.wrappedCause;

/**
 * Utility class for working with Protobuf descriptors.
 *
 * @author Alexander Yevsyukov
 */
class DescriptorUtil {
    /* This class is named with `Util` suffix instead of being `Descriptors` to avoid
       the name clash with Descriptors class from Protobuf. */

    private static final String METHOD_GET_DESCRIPTOR = "getDescriptor";

    private DescriptorUtil() {
        // Prevent instantiation of this utility class.
    }

    /** Returns descriptor for the passed message class. */
    static GenericDescriptor getDescriptorForClass(Class<? extends Message> cls) {
        checkNotNull(cls);
        try {
            final Method method = cls.getMethod(METHOD_GET_DESCRIPTOR);
            final GenericDescriptor result = (GenericDescriptor) method.invoke(null);
            return result;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw wrappedCause(e);
        }
    }

    /**
     * Retrieve {@link Descriptors proto descriptor} from the type name.
     *
     * @param typeName <b>valid</b> name of the desired type
     * @return {@link Descriptors proto descriptor} for given type
     * @see TypeName
     * @throws IllegalArgumentException if the name does not correspond to any known type
     */
    static Descriptor getDescriptorForType(String typeName) {
        final TypeUrl typeUrl = KnownTypes.getTypeUrl(typeName);
        checkArgument(typeUrl != null, "Given type name is invalid");

        final Class<?> cls = KnownTypes.getJavaClass(typeUrl);

        final Descriptor descriptor;
        try {
            final Method descriptorGetter = cls.getDeclaredMethod(METHOD_GET_DESCRIPTOR);
            descriptor = (Descriptor) descriptorGetter.invoke(null);
        } catch (NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw newIllegalArgumentException(e, "Type %s is not a protobuf Message", typeName);
        }
        return descriptor;
    }
}

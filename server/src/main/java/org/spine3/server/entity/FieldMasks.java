/*
 * Copyright 2016, TeamDev Ltd. All rights reserved.
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

package org.spine3.server.entity;

import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolStringList;
import org.spine3.protobuf.AnyPacker;
import org.spine3.protobuf.KnownTypes;
import org.spine3.protobuf.TypeUrl;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * // TODO:19-09-16:dmytro.dashenkov: Add javadoc.
 * @author Dmytro Dashenkov
 */
public class FieldMasks {

    private FieldMasks() {
    }

    /**
     * // TODO:19-09-16:dmytro.dashenkov: Add javadoc.
     * @param mask
     * @param entities
     * @param typeUrl
     * @param <B>
     * @return
     */
    @SuppressWarnings("MethodWithMultipleLoops")
    public static <B extends Message.Builder> Collection<? extends Message> applyMask(@SuppressWarnings("TypeMayBeWeakened") FieldMask mask, Collection<? extends Message> entities, TypeUrl typeUrl) {
        final List<Message> filtered = new ArrayList<>();
        final ProtocolStringList filter = mask.getPathsList();

        final Class<B> builderClass = getBuilderForType(typeUrl);

        if (filter.isEmpty() || builderClass == null) {
            return Collections.unmodifiableCollection(entities);
        }

        try {
            final Constructor<B> builderConstructor = builderClass.getDeclaredConstructor();
            builderConstructor.setAccessible(true);

            for (Message wholeMessage : entities) {
                final B builder = builderConstructor.newInstance();

                for (Descriptors.FieldDescriptor field : wholeMessage.getDescriptorForType().getFields()) {
                    if (filter.contains(field.getFullName())) {
                        builder.setField(field, wholeMessage.getField(field));
                    }
                }

                filtered.add(AnyPacker.pack(builder.build()));
            }

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
            // If any reflection failure happens, return all the data without any mask applied.
            return Collections.unmodifiableCollection(entities);
        }

        return Collections.unmodifiableList(filtered);
    }

    // TODO:19-09-16:dmytro.dashenkov: Add javadoc.
    public static Collection<? extends Message> applyMask(@SuppressWarnings("TypeMayBeWeakened") FieldMask.Builder maskBuilder, Collection<? extends Message> entities, TypeUrl typeUrl) {
        return applyMask(maskBuilder.build(), entities, typeUrl);
    }

    // TODO:19-09-16:dmytro.dashenkov: Add javadoc.
    public static <B extends Message.Builder> Message applyMask(@SuppressWarnings("TypeMayBeWeakened") FieldMask mask, Message entity, TypeUrl typeUrl) {
        final ProtocolStringList filter = mask.getPathsList();

        final Class<B> builderClass = getBuilderForType(typeUrl);

        if (filter.isEmpty() || builderClass == null) {
            return entity;
        }

        try {
            final Constructor<B> builderConstructor = builderClass.getDeclaredConstructor();
            builderConstructor.setAccessible(true);

            final B builder = builderConstructor.newInstance();

            for (Descriptors.FieldDescriptor field : entity.getDescriptorForType().getFields()) {
                if (filter.contains(field.getFullName())) {
                    builder.setField(field, entity.getField(field));
                }
            }

            return builder.build();

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
            return entity;
        }


    }

    @Nullable
    private static <B extends Message.Builder> Class<B> getBuilderForType(TypeUrl typeUrl) {
        Class<B> builderClass;
        try {
            //noinspection unchecked
            builderClass = (Class<B>) Class.forName(KnownTypes.getClassName(typeUrl).value())
                                           .getClasses()[0];
        } catch (ClassNotFoundException | ClassCastException e) {
            builderClass = null;
        }

        return builderClass;

    }
}
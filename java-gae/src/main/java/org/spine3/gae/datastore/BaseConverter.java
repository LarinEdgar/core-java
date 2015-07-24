/*
 * Copyright 2015, TeamDev Ltd. All rights reserved.
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

package org.spine3.gae.datastore;

import com.google.protobuf.Message;
import org.spine3.util.ClassName;
import org.spine3.util.TypeName;
import org.spine3.util.TypeToClassMap;

/**
 * Provides base implementation for common EntityConverter part.
 *
 * @author Mikhail Mikhaylov
 */
abstract class BaseConverter<T extends Message> implements Converter<T> {

    private final TypeName typeName;

    protected BaseConverter(TypeName typeName) {
        this.typeName = typeName;
    }

    protected String getTypeName() {
        return typeName.toString();
    }

    protected String getEntityKind() {
        //TODO:2015-07-24:alexander.yevsyukov: Why do we use Java class name here and not a Proto type?
        // What if we read this store from another language like Go?
        // Also notice that this lookup is going to be done every time we create an entity.
        final ClassName className = TypeToClassMap.get(typeName);
        return className.toString();
    }
}
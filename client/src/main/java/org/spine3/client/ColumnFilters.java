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

package org.spine3.client;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.spine3.client.ColumnFilter.Operator;
import static org.spine3.client.ColumnFilter.Operator.EQUAL;
import static org.spine3.client.ColumnFilter.Operator.GREATER_OR_EQUAL;
import static org.spine3.client.ColumnFilter.Operator.GREATER_THAN;
import static org.spine3.client.ColumnFilter.Operator.LESS_OR_EQUAL;
import static org.spine3.client.ColumnFilter.Operator.LESS_THAN;
import static org.spine3.protobuf.TypeConverter.toAny;

/**
 * A parameter of a {@link Query}.
 * // TODO:2017-05-17:dmytro.dashenkov: Fix the Javadoc.
 *
 * <p>This class may be considered a filter for the query. An instance contains the name of
 * the Entity Column to filter by, the value of the Column and
 * the {@linkplain Operator comparison operator}.
 *
 * <p>The supported types for querying are {@linkplain Message Message types} and Protobuf
 * primitives.
 *
 * @see org.spine3.protobuf.TypeConverter for the list of supported types
 */
public final class ColumnFilters {

    private ColumnFilters() {
        // Prevent this utility class initialization.
    }

    /**
     * Creates new equality {@code QueryParameter}.
     *
     * @param columnName the name of the Entity Column to query by, expressed in a single field
     *                   name with no type info
     * @param value      the requested value of the Entity Column
     * @return new instance of QueryParameter
     */
    public static ColumnFilter eq(String columnName, Object value) {
        checkNotNull(columnName);
        checkNotNull(value);
        return createFilter(columnName, value, EQUAL);
    }


    public static ColumnFilter gt(String columnName, Timestamp value) {
        checkNotNull(columnName);
        checkNotNull(value);
        return createFilter(columnName, value, GREATER_THAN);
    }

    public static ColumnFilter lt(String columnName, Timestamp value) {
        checkNotNull(columnName);
        checkNotNull(value);
        return createFilter(columnName, value, LESS_THAN);
    }

    public static ColumnFilter ge(String columnName, Timestamp value) {
        checkNotNull(columnName);
        checkNotNull(value);
        return createFilter(columnName, value, GREATER_OR_EQUAL);
    }

    public static ColumnFilter le(String columnName, Timestamp value) {
        checkNotNull(columnName);
        checkNotNull(value);
        return createFilter(columnName, value, LESS_OR_EQUAL);
    }

    private static ColumnFilter createFilter(String columnName, Object value, Operator operator) {
        final Any wrappedValue = toAny(value);
        final ColumnFilter filter = ColumnFilter.newBuilder()
                                                .setColumnName(columnName)
                                                .setValue(wrappedValue)
                                                .setOperator(operator)
                                                .build();
        return filter;
    }
}

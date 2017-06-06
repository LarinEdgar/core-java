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

package io.spine.server.entity.storage;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import io.spine.server.entity.EntityRecord;
import io.spine.testdata.Sample;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static io.spine.test.Verify.assertContainsKeyValue;
import static io.spine.test.Verify.assertEmpty;
import static io.spine.test.Verify.assertMapsEqual;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dmytro Dashenkov
 */
public class EntityRecordWithColumnsShould {

    @Test
    public void initialize_with_record_and_storage_fields() {
        final EntityRecordWithColumns record =
                EntityRecordWithColumns.of(EntityRecord.getDefaultInstance(),
                                           Collections.<String, Column.MemoizedValue<?>>emptyMap());
        assertNotNull(record);
    }

    @Test
    public void initialize_with_record_only() {
        final EntityRecordWithColumns record =
                EntityRecordWithColumns.of(EntityRecord.getDefaultInstance());
        assertNotNull(record);
    }

    @Test
    public void not_accept_nulls_in_ctor() {
        new NullPointerTester()
                .setDefault(EntityRecord.class, EntityRecord.getDefaultInstance())
                .testAllPublicConstructors(EntityRecordWithColumns.class);
    }

    @Test
    public void store_record() {
        final EntityRecordWithColumns recordWithFields = newRecord();
        final EntityRecord record = recordWithFields.getRecord();
        assertNotNull(record);
    }

    @Test
    public void store_column_values() {
        final Column.MemoizedValue<?> mockValue = mock(Column.MemoizedValue.class);
        final Map<String, Column.MemoizedValue<?>> columnsExpected =
                Collections.<String, Column.MemoizedValue<?>>singletonMap("some-key", mockValue);

        final EntityRecordWithColumns record =
                EntityRecordWithColumns.of(Sample.messageOfType(EntityRecord.class),
                                           columnsExpected);
        assertTrue(record.hasColumns());

        final Map<String, Column.MemoizedValue<?>> columnsActual =
                record.getColumnValues();
        assertMapsEqual(columnsExpected, columnsActual, "column values");
    }

    @Test
    public void store_column_definitions() {
        final Column.MemoizedValue<?> mockValue = mock(Column.MemoizedValue.class);
        final Column mockColumn = mock(Column.class);
        when(mockValue.getSourceColumn()).thenReturn(mockColumn);

        final String key = "arbitrary";
        final Map<String, Column.MemoizedValue<?>> columnsExpected =
                Collections.<String, Column.MemoizedValue<?>>singletonMap(key, mockValue);

        final EntityRecordWithColumns record =
                EntityRecordWithColumns.of(Sample.messageOfType(EntityRecord.class),
                                           columnsExpected);
        assertTrue(record.hasColumns());
        final Map<String, Column<?>> columnsActual = record.getColumns();
        assertContainsKeyValue(key, mockColumn, columnsActual);
    }


    @Test
    public void return_empty_map_if_no_storage_fields() {
        final EntityRecordWithColumns record =
                EntityRecordWithColumns.of(EntityRecord.getDefaultInstance());
        assertFalse(record.hasColumns());
        final Map<String, Column.MemoizedValue<?>> fields = record.getColumnValues();
        assertEmpty(fields);
    }

    @Test
    public void have_equals() {
        final Column.MemoizedValue<?> mockValue = mock(Column.MemoizedValue.class);
        final EntityRecordWithColumns noFieldsEnvelope =
                EntityRecordWithColumns.of(EntityRecord.getDefaultInstance()
                );
        final EntityRecordWithColumns emptyFieldsEnvelope =
                EntityRecordWithColumns.of(
                        EntityRecord.getDefaultInstance(),
                        Collections.<String, Column.MemoizedValue<?>>emptyMap()
                );
        final EntityRecordWithColumns notEmptyFieldsEnvelope =
                EntityRecordWithColumns.of(
                        EntityRecord.getDefaultInstance(),
                        Collections.<String, Column.MemoizedValue<?>>singletonMap("key", mockValue)
                );
        new EqualsTester()
                .addEqualityGroup(noFieldsEnvelope, emptyFieldsEnvelope, notEmptyFieldsEnvelope)
                .addEqualityGroup(newRecord())
                .addEqualityGroup(newRecord()) // Each one has different EntityRecord
                .testEquals();
    }

    private static EntityRecordWithColumns newRecord() {
        return EntityRecordWithColumns.of(Sample.messageOfType(EntityRecord.class),
                                          Collections.<String, Column.MemoizedValue<?>>emptyMap());
    }
}

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

package io.spine.server.storage;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import io.spine.server.entity.Entity;
import io.spine.test.Tests;
import io.spine.test.Verify;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Abstract storage tests.
 *
 * @param <I> the type of IDs of storage records
 * @param <R> the type of records kept in the storage
 * @author Alexander Litus
 */
@SuppressWarnings("ClassWithTooManyMethods")
public abstract class AbstractStorageShould<I,
                                            R extends Message,
                                            S extends AbstractStorage<I, R>> {

    private S storage;

    @Before
    public void setUpAbstractStorageTest() {
        storage = getDefaultStorage();
    }

    @After
    public void tearDownAbstractStorageTest() {
        close(storage);
    }

    protected final S getStorage() {
        return storage;
    }

    /**
     * Creates the default instance of {@link Storage} for this test suite.
     *
     * <p>NOTE: the storage is closed after each test.
     *
     * @return an empty storage instance
     * @see #getDefaultStorage() for a storage instance for a specific {@link Entity}
     */
    protected abstract S getDefaultStorage();

    /**
     * Used to initialize the storage before each test.
     *
     * <p>NOTE: the storage is closed after each test.
     *
     * @return an empty storage instance
     * @see AbstractStorage#close()
     */
    protected abstract S getStorage(Class<? extends Entity> cls);

    /** Creates a new storage record. */
    protected abstract R newStorageRecord();

    /** Creates a new unique storage record ID. */
    protected abstract I newId();

    /**
     * Closes the storage and propagates an exception if any occurs.
     */
    protected void close(AbstractStorage storage) {
        if (storage.isOpen()) {
            try {
                storage.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Closes the storage and fails the test if any exception occurs. */
    @SuppressWarnings("CallToPrintStackTrace")
    protected void closeAndFailIfException(AbstractStorage<I, R> storage) {
        try {
            storage.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("An unexpected exception: " + e.getClass() + "; " + e.getMessage());
        }
    }

    /** Writes a record, reads it and asserts it is the same as the expected one. */
    @SuppressWarnings("OptionalGetWithoutIsPresent") // We do check.
    protected void writeAndReadRecordTest(I id) {
        final R expected = writeRecord(id);

        final Optional<R> actual = storage.read(id);

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    private R writeRecord(I id) {
        final R expected = newStorageRecord();
        storage.write(id, expected);
        return expected;
    }

    @Test
    public void handle_absence_of_record_with_passed_id() {
        final Optional<R> record = storage.read(newId());

        assertResultForMissingId(record);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // This is the purpose of the method.
    protected void assertResultForMissingId(Optional<R> record) {
        assertFalse(record.isPresent());
    }

    @Test(expected = NullPointerException.class)
    public void throw_exception_if_read_by_null_id() {
        storage.read(Tests.<I>nullRef());
    }

    @Test(expected = NullPointerException.class)
    public void throw_exception_if_write_by_null_id() {
        storage.write(Tests.<I>nullRef(), newStorageRecord());
    }

    @Test(expected = NullPointerException.class)
    public void throw_exception_if_write_null_record() {
        storage.write(newId(), Tests.<R>nullRef());
    }

    @Test
    public void write_and_read_record() {
        writeAndReadRecordTest(newId());
    }

    @Test
    public void write_and_read_several_records_by_different_ids() {
        writeAndReadRecordTest(newId());
        writeAndReadRecordTest(newId());
        writeAndReadRecordTest(newId());
    }

    @Test
    public void rewrite_record_if_write_by_the_same_id() {
        final I id = newId();
        writeAndReadRecordTest(id);
        writeAndReadRecordTest(id);
    }

    @Test
    public void have_index_on_ID() {
        final Iterator<I> index = storage.index();
        assertNotNull(index);
    }

    @Test
    public void index_all_IDs() {
        final int recordCount = 10;
        final Set<I> ids = new HashSet<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            final I id = newId();
            writeRecord(id);
            ids.add(id);
        }

        final Iterator<I> index = storage.index();
        final Collection<I> indexValues = Sets.newHashSet(index);

        assertEquals(ids.size(), indexValues.size());
        Verify.assertContainsAll(indexValues, (I[]) ids.toArray());
    }

    @Test
    public void have_immutable_index() {
        writeRecord(newId());
        final Iterator<I> index = storage.index();
        assertTrue(index.hasNext());
        try {
            index.remove();
            fail("Storage#index is mutable");

            // One of collections used in in-memory implementation throws IllegalStateException
            // but default behavior is UnsupportedOperationException
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            // One of valid exceptions was thrown
        }
    }

    @Test(expected = IllegalStateException.class)
    public void assure_it_is_closed() throws Exception {
        closeAndFailIfException(storage);

        storage.checkNotClosed();
    }

    @Test
    public void not_throw_exception_if_it_is_not_closed_on_check() {
        storage.checkNotClosed();
    }

    @Test
    public void return_true_if_it_is_opened() {
        assertTrue(storage.isOpen());
    }

    @Test
    public void return_false_if_it_not_opened() throws Exception {
        storage.close();

        assertFalse(storage.isOpen());
    }

    @Test
    public void return_true_if_it_is_closed() throws Exception {
        storage.close();

        assertTrue(storage.isClosed());
    }

    @Test
    public void return_false_if_it_not_closed() throws Exception {
        assertFalse(storage.isClosed());
    }

    @Test(expected = IllegalStateException.class)
    public void close_itself_and_throw_exception_if_read_after() throws Exception {
        closeAndFailIfException(storage);

        storage.read(newId());
    }

    @Test(expected = IllegalStateException.class)
    public void close_itself_and_throw_exception_if_write_after() throws Exception {
        closeAndFailIfException(storage);

        storage.write(newId(), newStorageRecord());
    }

    @Test(expected = IllegalStateException.class)
    public void throw_exception_if_close_twice() throws Exception {
        storage.close();
        storage.close();
    }
}

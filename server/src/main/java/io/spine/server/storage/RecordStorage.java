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
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.FieldMasks;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.storage.EntityQuery;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.server.stand.AggregateStateId;
import io.spine.type.TypeUrl;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Identifiers.idToString;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A storage keeping messages with identity.
 *
 * @param <I> the type of entity IDs
 * @author Alexander Yevsyukov
 */
public abstract class RecordStorage<I> extends AbstractStorage<I, EntityRecord>
        implements StorageWithLifecycleFlags<I, EntityRecord>,
                   BulkStorageOperationsMixin<I, EntityRecord> {

    protected RecordStorage(boolean multitenant) {
        super(multitenant);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<EntityRecord> read(I id) {
        checkNotClosed();
        checkNotNull(id);

        final Optional<EntityRecord> record = readRecord(id);
        return record;
    }

    /**
     * Reads a single item from the storage and applies a {@link FieldMask} to it.
     *
     * @param id        ID of the item to read.
     * @param fieldMask fields to read.
     * @return the item with the given ID and with the {@code FieldMask} applied.
     * @see #read(Object)
     */
    public Optional<EntityRecord> read(I id, FieldMask fieldMask) {
        final Optional<EntityRecord> rawResult = read(id);

        if (!rawResult.isPresent()) {
            return Optional.absent();
        }

        final EntityRecord.Builder builder = EntityRecord.newBuilder(rawResult.get());
        final Any state = builder.getState();
        final TypeUrl type = TypeUrl.parse(state.getTypeUrl());
        final Message stateAsMessage = AnyPacker.unpack(state);

        final Message maskedState = FieldMasks.applyMask(fieldMask, stateAsMessage, type);

        final Any packedState = AnyPacker.pack(maskedState);
        builder.setState(packedState);
        return Optional.of(builder.build());
    }

    /**
     * Writes a record and its {@link io.spine.server.entity.storage.Column Columns} into
     * the storage.
     *
     * <p>Rewrites it if a record with this ID already exists in the storage.
     *
     * @param id     the ID for the record
     * @param record a record to store
     * @throws IllegalStateException if the storage is closed
     * @see #write(Object, EntityRecord)
     */
    public void write(I id, EntityRecordWithColumns record) {
        checkNotNull(id);
        checkArgument(record.getRecord().hasState(), "Record does not have state field.");
        checkNotClosed();

        writeRecord(id, record);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(I id, EntityRecord record) {
        final EntityRecordWithColumns recordWithStorageFields =
                EntityRecordWithColumns.of(record);
        write(id, recordWithStorageFields);
    }

    /**
     * Writes a bulk of records into the storage.
     *
     * <p>Rewrites it if a record with this ID already exists in the storage.
     *
     * @param records an ID to record map with the entries to store
     * @throws IllegalStateException if the storage is closed
     */
    public void write(Map<I, EntityRecordWithColumns> records) {
        checkNotNull(records);
        checkNotClosed();

        writeRecords(records);
    }

    @Override
    public Optional<LifecycleFlags> readLifecycleFlags(I id) {
        final Optional<EntityRecord> optional = read(id);
        if (optional.isPresent()) {
            return Optional.of(optional.get()
                                       .getLifecycleFlags());
        }
        return Optional.absent();
    }

    @Override
    public void writeLifecycleFlags(I id, LifecycleFlags flags) {
        final Optional<EntityRecord> optional = read(id);
        if (optional.isPresent()) {
            final EntityRecord record = optional.get();
            final EntityRecord updated = record.toBuilder()
                                               .setLifecycleFlags(flags)
                                               .build();
            write(id, updated);
        } else {
            // The AggregateStateId is a special case, which is not handled by the Identifier class.
            final String idStr = id instanceof AggregateStateId
                                 ? id.toString()
                                 : idToString(id);
            throw newIllegalStateException("Unable to load record for entity with ID: %s",
                                                      idStr);
        }
    }

    /**
     * Deletes the record with the passed ID.
     *
     * @param id the record to delete
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public abstract boolean delete(I id);

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<EntityRecord> readMultiple(Iterable<I> ids) {
        checkNotClosed();
        checkNotNull(ids);

        return readMultipleRecords(ids);
    }

    /**
     * Reads multiple items from the storage and apply {@link FieldMask} to each of the results.
     *
     * @param ids       the IDs of the items to read
     * @param fieldMask the mask to apply
     * @return the items with the given IDs and with the given {@code FieldMask} applied
     */
    public Iterable<EntityRecord> readMultiple(Iterable<I> ids, FieldMask fieldMask) {
        checkNotClosed();
        checkNotNull(ids);

        return readMultipleRecords(ids, fieldMask);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<I, EntityRecord> readAll() {
        checkNotClosed();

        return readAllRecords();
    }

    /**
     * Reads all items from the storage and apply {@link FieldMask} to each of the results.
     *
     * @param fieldMask the {@code FieldMask} to apply
     * @return all items from this repository with the given {@code FieldMask} applied
     */
    public Map<I, EntityRecord> readAll(FieldMask fieldMask) {
        checkNotClosed();

        return readAllRecords(fieldMask);
    }

    /**
     * Reads all the records matching the given {@link EntityQuery} and applies the given
     * {@link FieldMask} to the resulting record states.
     *
     * @param query     the query to execute
     * @param fieldMask the fields to retrieve
     * @return the matching records mapped upon their IDs
     */
    public Map<I, EntityRecord> readAll(EntityQuery<I> query, FieldMask fieldMask) {
        checkNotClosed();
        checkNotNull(query);
        checkNotNull(fieldMask);

        return readAllRecords(query, fieldMask);
    }

    //
    // Internal storage methods
    //---------------------------

    /**
     * Reads a record from the storage by the passed ID.
     *
     * @param id the ID of the record to load
     * @return a record instance or {@code null} if there is no record with this ID
     */
    protected abstract Optional<EntityRecord> readRecord(I id);

    /** @see BulkStorageOperationsMixin#readMultiple(java.lang.Iterable) */
    protected abstract Iterable<EntityRecord> readMultipleRecords(Iterable<I> ids);

    /** @see BulkStorageOperationsMixin#readMultiple(java.lang.Iterable) */
    protected abstract Iterable<EntityRecord> readMultipleRecords(Iterable<I> ids,
                                                                  FieldMask fieldMask);

    /** @see BulkStorageOperationsMixin#readAll() */
    protected abstract Map<I, EntityRecord> readAllRecords();

    /** @see BulkStorageOperationsMixin#readAll() */
    protected abstract Map<I, EntityRecord> readAllRecords(FieldMask fieldMask);

    /**
     * @see #readAll(EntityQuery, FieldMask)
     */
    protected abstract Map<I, EntityRecord> readAllRecords(EntityQuery<I> query,
                                                           FieldMask fieldMask);

    /**
     * Writes a record and the associated
     * {@link io.spine.server.entity.storage.Column Column values} into the storage.
     *
     * <p>Rewrites it if a record with this ID already exists in the storage.
     *
     * @param id     an ID of the record
     * @param record a record to store
     */
    protected abstract void writeRecord(I id, EntityRecordWithColumns record);

    /**
     * Writes a bulk of records into the storage.
     *
     * <p>Rewrites it if a record with this ID already exists in the storage.
     *
     * @param records an ID to record map with the entries to store
     */
    protected abstract void writeRecords(Map<I, EntityRecordWithColumns> records);
}

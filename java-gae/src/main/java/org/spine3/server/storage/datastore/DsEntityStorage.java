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

package org.spine3.server.storage.datastore;

import com.google.api.services.datastore.DatastoreV1;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import org.spine3.server.storage.EntityStorage;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.spine3.util.Identifiers.idToString;

/**
 * {@link EntityStorage} implementation based on Google App Engine Datastore.
 *
 * @see DatastoreStorageFactory
 * @see LocalDatastoreStorageFactory
 * @author Alexander Litus
 */
class DsEntityStorage<I, M extends Message> extends EntityStorage<I, M> {

    private final DsStorage<M> storage;

    private DsEntityStorage(DsStorage<M> storage) {
        this.storage = storage;
    }

    protected static <I, M extends Message> DsEntityStorage<I, M> newInstance(DsStorage<M> storage) {
        return new DsEntityStorage<>(storage);
    }

    @Override
    public M read(I id) {

        final String idString = idToString(id);

        final Message message = readEntity(idString);

        @SuppressWarnings("unchecked") // save because messages of only this type are written
        final M result = (M) message;
        return result;
    }

    @Override
    public void write(I id, M message) {

        checkNotNull(id);
        checkNotNull(message);

        final String idString = idToString(id);

        storeEntity(idString, message);
    }

    /**
     * Reads the first element by the {@code id}.
     */
    private M readEntity(String id) {

        final DatastoreV1.Key.Builder key = storage.makeCommonKey(id);
        DatastoreV1.LookupRequest request = DatastoreV1.LookupRequest.newBuilder().addKey(key).build();

        final DatastoreV1.LookupResponse response = storage.lookup(request);

        if (response == null || response.getFoundCount() == 0) {
            @SuppressWarnings("unchecked") // cast is save because Any is Message
            final M empty = (M) Any.getDefaultInstance();
            return empty;
        }

        DatastoreV1.EntityResult entity = response.getFound(0);
        final M message = storage.entityToMessage(entity);

        return message;
    }

    /**
     * Stores the {@code message} by the {@code id}. Only one message could be stored by given id.
     */
    private void storeEntity(String id, M message) {

        DatastoreV1.Entity.Builder entity = DsStorage.messageToEntity(message, storage.makeCommonKey(id));

        final DatastoreV1.Mutation.Builder mutation = DatastoreV1.Mutation.newBuilder().addInsert(entity);
        storage.commit(mutation);
    }
}

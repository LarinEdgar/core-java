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

package io.spine.server.storage.memory;

import com.google.protobuf.Message;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateStorage;
import io.spine.server.entity.Entity;
import io.spine.server.entity.storage.ColumnTypeRegistry;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionStorage;
import io.spine.server.stand.StandStorage;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.type.TypeUrl;

import static io.spine.server.entity.Entity.TypeInfo.getIdClass;
import static io.spine.server.entity.Entity.TypeInfo.getStateClass;

/**
 * A factory for in-memory storages.
 *
 * @author Alexander Yevsyukov
 */
public class InMemoryStorageFactory implements StorageFactory {

    private final String boundedContextName;
    private final boolean multitenant;

    public static InMemoryStorageFactory newInstance(String boundedContextName,
                                                     boolean multitenant) {
        return new InMemoryStorageFactory(boundedContextName, multitenant);
    }

    private InMemoryStorageFactory(String boundedContextName, boolean multitenant) {
        this.boundedContextName = boundedContextName;
        this.multitenant = multitenant;
    }

    @Override
    public boolean isMultitenant() {
        return this.multitenant;
    }

    /**
     * {@inheritDoc}
     *
     * <p>In-memory implementation stores no values separately
     * ({@link io.spine.server.entity.storage.Column Columns}), therefore
     * returns an empty ColumnTypeRegistry.
     */
    @Override
    public ColumnTypeRegistry getTypeRegistry() {
        return ColumnTypeRegistry.newBuilder()
                                 .build();
    }

    @Override
    public StandStorage createStandStorage() {
        final InMemoryStandStorage result =
                InMemoryStandStorage.newBuilder()
                                    .setBoundedContextName(boundedContextName)
                                    .setMultitenant(isMultitenant())
                                    .build();
        return result;
    }

    /** NOTE: the parameter is unused. */
    @Override
    public <I> AggregateStorage<I> createAggregateStorage(
            Class<? extends Aggregate<I, ?, ?>> unused) {
        return new InMemoryAggregateStorage<>(isMultitenant());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I> RecordStorage<I>
    createRecordStorage(Class<? extends Entity<I, ?>> entityClass) {
        final Class<? extends Message> stateClass = getStateClass(entityClass);
        final TypeUrl typeUrl = TypeUrl.of(stateClass);
        final Class<I> idClass = getIdClass(entityClass);
        final StorageSpec<I> spec = StorageSpec.of(boundedContextName, typeUrl, idClass);
        return InMemoryRecordStorage.newInstance(spec, isMultitenant());
    }

    @Override
    public <I> ProjectionStorage<I> createProjectionStorage(
            Class<? extends Projection<I, ?, ?>> projectionClass) {
        final Class<Message> stateClass = getStateClass(projectionClass);
        final Class<I> idClass = getIdClass(projectionClass);
        final TypeUrl stateUrl = TypeUrl.of(stateClass);
        final StorageSpec<I> spec = StorageSpec.of(boundedContextName, stateUrl, idClass);

        final boolean multitenant = isMultitenant();
        final InMemoryRecordStorage<I> entityStorage =
                InMemoryRecordStorage.newInstance(spec, multitenant);
        return InMemoryProjectionStorage.newInstance(entityStorage);
    }

    @Override
    public void close() {
        // NOP
    }

    @Override
    public StorageFactory toSingleTenant() {
        if (!isMultitenant()) {
            return this;
        }
        return newInstance(this.boundedContextName, false);
    }
}

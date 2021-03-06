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

package io.spine.server.aggregate;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.protobuf.Message;
import io.spine.server.BoundedContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.aggregate.AggregatePartRepositoryLookup.createLookup;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;

/**
 * A root object for a larger aggregate.
 *
 * @param <I> the type for IDs of this class of aggregates
 * @author Alexander Yevsyukov
 */
public class AggregateRoot<I> {

    /** The {@code BoundedContext} to which the aggregate belongs. */
    private final BoundedContext boundedContext;

    /** The aggregate ID. */
    private final I id;

    /** The cache of part repositories obtained from {@code boundedContext}. */
    private final LoadingCache<Class<? extends Message>,
            AggregatePartRepository<I, ? extends AggregatePart<I, ?, ?, ?>, ?>>
            cache = createCache();

    /**
     * Creates an new instance.
     *
     * @param boundedContext the bounded context to which the aggregate belongs
     * @param id             the ID of the aggregate
     */
    protected AggregateRoot(BoundedContext boundedContext, I id) {
        checkNotNull(boundedContext);
        checkNotNull(id);
        this.boundedContext = boundedContext;
        this.id = id;
    }

    /**
     * Creates a new {@code AggregateRoot}.
     *
     * @param <I>            the type of entity IDs
     * @param boundedContext the {@code BoundedContext} to use
     * @param rootClass      the class of the {@code AggregateRoot}
     * @param aggregateId    the ID of the aggregate
     * @return new instance
     */
    static <I, R extends AggregateRoot<I>> R create(BoundedContext boundedContext,
                                                    Class<R> rootClass,
                                                    I aggregateId) {
        checkNotNull(aggregateId);
        checkNotNull(boundedContext);
        checkNotNull(rootClass);

        try {
            final Constructor<R> ctor =
                    rootClass.getDeclaredConstructor(boundedContext.getClass(),
                                                     aggregateId.getClass());
            ctor.setAccessible(true);
            final R root = ctor.newInstance(boundedContext, aggregateId);
            return root;
        } catch (NoSuchMethodException | InvocationTargetException |
                InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Obtains the aggregate ID.
     */
    public I getId() {
        return this.id;
    }

    /**
     * Obtains the {@code BoundedContext} to which the aggregate belongs.
     */
    private BoundedContext getBoundedContext() {
        return boundedContext;
    }

    /**
     * Obtains a part state by its class.
     *
     * @param partStateClass the class of the state of the part
     * @param <S>            the type of the part state
     * @return the state of the part or a default state if the state was not found
     * @throws IllegalStateException if a repository was not found,
     *                               or the ID type of the part state does not match
     *                               the ID type of this {@code AggregateRoot}
     */
    protected <S extends Message, A extends AggregatePart<I, S, ?, ?>>
    S getPartState(Class<S> partStateClass) {
        final AggregatePartRepository<I, A, ?> repo = getRepository(partStateClass);
        final AggregatePart<I, S, ?, ?> aggregatePart = repo.loadOrCreate(getId());
        final S partState = aggregatePart.getState();
        return partState;
    }

    /**
     * Obtains a repository for the passed state class.
     *
     * @throws IllegalStateException if a repository was not found,
     *                               or the repository ID type does not match
     *                               the ID type of this {@code AggregateRoot}
     */
    @SuppressWarnings("unchecked") // We ensure ID type when adding to the map.
    private <S extends Message, A extends AggregatePart<I, S, ?, ?>>
    AggregatePartRepository<I, A, ?> getRepository(Class<S> stateClass) {

        final AggregatePartRepository<I, A, ?> result;
        try {
            result = (AggregatePartRepository<I, A, ?>) cache.get(stateClass);
        } catch (ExecutionException e) {
            throw illegalStateWithCauseOf(e);
        }
        return result;
    }

    /** Creates a cache for remembering aggregate part repositories. */
    private LoadingCache<Class<? extends Message>,
            AggregatePartRepository<I, ? extends AggregatePart<I, ?, ?, ?>, ?>> createCache() {
        return CacheBuilder.newBuilder()
                           .build(newLoader());
    }

    /** Creates a loader which calls {@link #lookup(Class)}. */
    private CacheLoader<Class<? extends Message>,
            AggregatePartRepository<I, ? extends AggregatePart<I, ?, ?, ?>, ?>> newLoader() {
        return new CacheLoader<Class<? extends Message>,
                       AggregatePartRepository<I, ? extends AggregatePart<I, ?, ?, ?>, ?>>() {
                   @Override
                   public AggregatePartRepository<I, ? extends AggregatePart<I, ?, ?, ?>, ?>
                   load(Class<? extends Message> key) throws Exception {
                       return AggregateRoot.this.lookup(key);
                   }
               };
    }

    /** Finds an aggregate part repository in the Bounded Context. */
    private <S extends Message, A extends AggregatePart<I, S, ?, ?>>
    AggregatePartRepository<I, A, ?> lookup(Class<S> stateClass) {
        @SuppressWarnings("unchecked") // The type is ensured by getId() result.
        final Class<I> idClass = (Class<I>) getId().getClass();
        final AggregatePartRepositoryLookup<I, S> lookup = createLookup(getBoundedContext(),
                                                                        idClass,
                                                                        stateClass);
        final AggregatePartRepository<I, A, ?> result = lookup.find();
        return result;
    }
}

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

package org.spine3.server.projection;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spine3.Internal;
import org.spine3.base.Event;
import org.spine3.base.EventContext;
import org.spine3.base.Events;
import org.spine3.protobuf.AnyPacker;
import org.spine3.server.BoundedContext;
import org.spine3.server.entity.EntityRepository;
import org.spine3.server.entity.IdSetFunction;
import org.spine3.server.event.EventDispatcher;
import org.spine3.server.event.EventFilter;
import org.spine3.server.event.EventStore;
import org.spine3.server.event.EventStreamQuery;
import org.spine3.server.stand.StandFunnel;
import org.spine3.server.storage.ProjectionStorage;
import org.spine3.server.storage.RecordStorage;
import org.spine3.server.storage.Storage;
import org.spine3.server.storage.StorageFactory;
import org.spine3.server.type.EventClass;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base for repositories managing {@link Projection}s.
 *
 * @param <I> the type of IDs of projections
 * @param <P> the type of projections
 * @param <M> the type of projection state messages
 * @author Alexander Yevsyukov
 */
public abstract class ProjectionRepository<I, P extends Projection<I, M>, M extends Message>
        extends EntityRepository<I, P, M> implements EventDispatcher {

    /** The enumeration of statuses in which a Projection Repository can be during its lifecycle. */
    protected enum Status {

        /**
         * The repository instance has been created.
         *
         * <p>In this status the storage is not yet assigned.
         */
        CREATED,

        /** A storage has been assigned to the repository. */
        STORAGE_ASSIGNED,

        /** The repository is getting events from EventStore and builds projections. */
        CATCHING_UP,

        /** The repository completed the catch-up process. */
        ONLINE,

        /** The repository is closed and no longer accept events. */
        CLOSED
    }

    /** The current status of the repository. */
    private Status status = Status.CREATED;

    /** An underlying entity storage used to store projections. */
    private RecordStorage<I> recordStorage;

    /** An instance of {@link StandFunnel} to be informed about state updates */
    private final StandFunnel standFunnel;

    /** If {@code true} the projection will {@link #catchUp()} after initialization. */
    private final boolean catchUpAfterStorageInit;

    private final IdSetFunctions<I> idSetFunctions = new IdSetFunctions<>();

    /**
     * Creates a {@code ProjectionRepository} for the given {@link BoundedContext} instance and enables catching up
     * after the storage initialization.
     *
     * <p>NOTE: The {@link #catchUp()} will be called automatically after the {@link #initStorage(StorageFactory)} call
     * is performed. To override this behavior, please use
     * {@link ProjectionRepository#ProjectionRepository(BoundedContext, boolean)} constructor.
     *
     * @param boundedContext the target {@code BoundedContext}
     */
    protected ProjectionRepository(BoundedContext boundedContext) {
        this(boundedContext, true);
    }

    /**
     * Creates a {@code ProjectionRepository} for the given {@link BoundedContext} instance.
     *
     * <p>If {@code catchUpAfterStorageInit} is set to {@code true}, the {@link #catchUp()} will be called
     * automatically after the {@link #initStorage(StorageFactory)} call is performed.
     *
     * @param boundedContext the target {@code BoundedContext}
     * @param catchUpAfterStorageInit whether the automatic catch-up should be performed after storage initialization
     */
    @SuppressWarnings("MethodParameterNamingConvention")
    protected ProjectionRepository(BoundedContext boundedContext, boolean catchUpAfterStorageInit) {
        super(boundedContext);
        this.standFunnel = boundedContext.getStandFunnel();
        this.catchUpAfterStorageInit = catchUpAfterStorageInit;
    }

    protected Status getStatus() {
        return status;
    }

    protected void setStatus(Status status) {
        this.status = status;
    }

    protected boolean isOnline() {
        return this.status == Status.ONLINE;
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod" /* We do not call super.createStorage() because
                       we create a specific type of a storage, not a regular entity storage created in the parent. */)
    protected Storage createStorage(StorageFactory factory) {
        final Class<P> projectionClass = getEntityClass();
        final ProjectionStorage<I> projectionStorage = factory.createProjectionStorage(projectionClass);
        this.recordStorage = projectionStorage.getRecordStorage();
        return projectionStorage;
    }

    /**
     * Adds {@code IdSetFunction} for the repository.
     *
     * <p>Typical usage for this method would be in a constructor of a {@code ProjectionRepository}
     * (derived from this class) to provide mapping between events to projection identifiers.
     *
     * <p>Such a mapping may be required when...
     * <ul>
     *     <li>An event should be matched to more than one projection.</li>
     *     <li>The type of an event producer ID (stored in {@code EventContext}) differs from {@code <I>}.</li>
     * </ul>
     *
     * <p>If there is no function for the class of the passed event message,
     * the repository will use the event producer ID from an {@code EventContext} passed with the event message.
     *
     * @param func the function instance
     * @param <E> the type of the event message handled by the function
     */
    public <E extends Message> void addIdSetFunction(Class<E> eventClass,
                                                     IdSetFunction<I, E, EventContext> func) {
        idSetFunctions.put(eventClass, func);
    }

    /**
     * Removes {@code IdSetFunction} from the repository.
     *
     * @param eventClass the class of the event message
     * @param <E> the type of the event message handled by the function we want to remove
     */
    public <E extends Message> void removeIdSetFunction(Class<E> eventClass) {
        idSetFunctions.remove(eventClass);
    }

    public <E extends Message> Optional<IdSetFunction<I, E, EventContext>> getIdSetFunction(Class<E> eventClass) {
        return idSetFunctions.get(eventClass);
    }

    /** {@inheritDoc} */
    @Override
    public void initStorage(StorageFactory factory) {
        super.initStorage(factory);
        setStatus(Status.STORAGE_ASSIGNED);

        if(catchUpAfterStorageInit) {
            if(log().isDebugEnabled()) {
                log().debug("Storage assigned. {} is starting to catch-up", getClass());
            }
            catchUp();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws Exception {
        super.close();
        setStatus(Status.CLOSED);
    }

    /**
     * Ensures that the repository has the storage.
     *
     * @return storage instance
     * @throws IllegalStateException if the storage is null
     */
    @Override
    @Nonnull
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    protected RecordStorage<I> recordStorage() {
        return checkStorage(recordStorage);
    }

    /**
     * Ensures that the repository has the storage.
     *
     * @return storage instance
     * @throws IllegalStateException if the storage is null
     */
    @Nonnull
    protected ProjectionStorage<I> projectionStorage() {
        @SuppressWarnings("unchecked") // It is safe to cast as we control the creation in createStorage().
        final ProjectionStorage<I> storage = (ProjectionStorage<I>) getStorage();
        return checkStorage(storage);
    }

    /** {@inheritDoc} */
    @Override
    public Set<EventClass> getEventClasses() {
        final Class<? extends Projection> projectionClass = getEntityClass();
        final Set<Class<? extends Message>> eventClasses = Projection.getEventClasses(projectionClass);
        final Set<EventClass> result = EventClass.setOf(eventClasses);
        return result;
    }

    /**
     * Obtains the set of IDs of projections to which apply the passed event.
     *
     * <p>The default implementation obtains the ID of the event producer from the
     * passed event context and casts it to the type of index used by this repository.
     *
     * @param event the event message. This parameter is not used by default implementation.
     *              Override to provide custom logic of ID generation.
     * @param context the event context
     */
    @SuppressWarnings("UnusedParameters") // Overriding methods may want to use the `event` parameter.
    protected Set<I> getProjectionIds(Message event, EventContext context) {
        return idSetFunctions.findAndApply(event, context);
    }

    /**
     * Loads or creates a projection by the passed ID.
     *
     * <p>The projection is created if there was no projection with such ID stored before.
     *
     * @param id the ID of the projection to load
     * @return loaded or created projection instance
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod") // we do call it, but IDEA somehow doesn't get it because
        // the signature of the parent class uses another letter for the generic type.
    @Nonnull
    @Override
    public P load(I id) {
        P projection = super.load(id);
        if (projection == null) {
            projection = create(id);
        }
        return projection;
    }

    /**
     * Dispatches the passed event to corresponding {@link Projection} if the repository is
     * in {@link Status#ONLINE}.
     *
     * <p>If the repository in another status the event is not dispatched. This is needed to
     * preserve the chronological sequence of events delivered to the repository, while it's
     * updating projections using the {@link #catchUp()} call.
     *
     * <p>The ID of the projection must be specified as the first property of the passed event.
     *
     * <p>If there is no stored projection with the ID from the event, a new projection is created
     * and stored after it handles the passed event.
     *
     * @param event the event to dispatch
     * @see #catchUp()
     * @see Projection#handle(Message, EventContext)
     */
    @Override
    public void dispatch(Event event) {
        if (!isOnline()) {
            if (log().isTraceEnabled()) {
                log().trace("Ignoring event {} while repository is not in {} status", event, Status.ONLINE);
            }
            return;
        }

        internalDispatch(event);
    }

    /**
     * Dispatches event to a projection without checking the status of the repository.
     *
     * <p>Also posts an update to the {@code StandFunnel} instance for this repository.
     *
     * @param event the event to dispatch
     */
    @Internal
    /* package */ void internalDispatch(Event event) {
        final Message eventMessage = Events.getMessage(event);
        final EventContext context = event.getContext();
        final Set<I> ids = getProjectionIds(eventMessage, context);
        for (I id : ids) {
            handleForProjection(id, eventMessage, context);
        }
    }

    private void handleForProjection(I id, Message eventMessage, EventContext context) {
        final P projection = load(id);
        projection.handle(eventMessage, context);
        store(projection);
        final M state = projection.getState();
        final Any packedState = AnyPacker.pack(state);
        standFunnel.post(id, packedState, projection.getVersion());
        final ProjectionStorage<I> storage = projectionStorage();
        final Timestamp eventTime = context.getTimestamp();
        storage.writeLastHandledEventTime(eventTime);
    }

    /**
     * Updates projections from the event stream obtained from {@code EventStore}.
     */
    public void catchUp() {
        // Get the timestamp of the last event. This also ensures we have the storage.
        final Timestamp timestamp = projectionStorage().readLastHandledEventTime();
        final EventStore eventStore = getBoundedContext().getEventBus().getEventStore();

        final Set<EventFilter> eventFilters = getEventFilters();

        final EventStreamQuery query = EventStreamQuery.newBuilder()
               .setAfter(timestamp == null ? Timestamp.getDefaultInstance() : timestamp)
               .addAllFilter(eventFilters)
               .build();

        setStatus(Status.CATCHING_UP);
        eventStore.read(query, new EventStreamObserver(this));
    }

    /**
     * Obtains event filters for event classes handled by projections of this repository.
     */
    private Set<EventFilter> getEventFilters() {
        final ImmutableSet.Builder<EventFilter> builder = ImmutableSet.builder();
        final Set<EventClass> eventClasses = getEventClasses();
        for (EventClass eventClass : eventClasses) {
            builder.add(EventFilter.newBuilder()
                                   .setEventType(eventClass.toTypeUrl().getTypeName())
                                   .build());
        }
        return builder.build();
    }

    /**
     * Sets the repository online bypassing the catch-up from the {@code EventStore}.
     */
    public void setOnline() {
        setStatus(Status.ONLINE);
    }

    private enum LogSingleton {
        INSTANCE;

        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Logger value = LoggerFactory.getLogger(ProjectionRepository.class);
    }

    private static Logger log() {
        return LogSingleton.INSTANCE.value;
    }

    /**
     * The stream observer which redirects events from {@code EventStore} to
     * the associated {@code ProjectionRepository}.
     */
    private static class EventStreamObserver implements StreamObserver<Event> {

        private final ProjectionRepository projectionRepository;

        /* package */ EventStreamObserver(ProjectionRepository projectionRepository) {
            this.projectionRepository = projectionRepository;
        }

        @Override
        public void onNext(Event event) {
            projectionRepository.internalDispatch(event);
        }

        @Override
        public void onError(Throwable throwable) {
            log().error("Error obtaining events from EventStore.", throwable);
        }

        @Override
        public void onCompleted() {
            projectionRepository.setStatus(Status.ONLINE);
            if (log().isInfoEnabled()) {
                final Class<? extends ProjectionRepository> repositoryClass = projectionRepository.getClass();
                log().info("{} catch-up complete", repositoryClass.getName());
            }
        }
    }

    /**
     * The {@code IdSetFunction} that obtains event producer ID from an {@code EventContext}
     * and returns it as a sole element of the {@code ImmutableSet}.
     *
     * @param <I> the type of the project IDs managed by the repository
     */
    private static class DefaultIdSetFunction<I> implements IdSetFunction<I, Message, EventContext> {
        @Override
        public Set<I> apply(Message message, EventContext context) {
            final I id = Events.getProducer(context);
            return ImmutableSet.of(id);
        }
    }

    /**
     * Helper class for managing {@link IdSetFunction}s associated with the projection repository.
     *
     * @param <I> the type of the projection IDs of this repository
     */
    private static class IdSetFunctions<I> {

        /** The map from event class to a function that generates a set of project IDs for the corresponding event. */
        private final Map<EventClass, IdSetFunction<I, Message, EventContext>> map = Maps.newHashMap();

        /** The function used when there's no matching entry in the map. */
        private final IdSetFunction<I, Message, EventContext> defaultFunction = new DefaultIdSetFunction<>();

        private <E extends Message> void remove(Class<E> eventClass) {
            final EventClass clazz = EventClass.of(eventClass);
            map.remove(clazz);
        }

        private Set<I> findAndApply(Message event, EventContext context) {
            final EventClass eventClass = EventClass.of(event);
            final IdSetFunction<I, Message, EventContext> func = map.get(eventClass);
            if (func != null) {
                final Set<I> result = func.apply(event, context);
                return result;
            }

            final Set<I> result = defaultFunction.apply(event, context);
            return result;
        }

        private <E extends Message> void put(Class<E> eventClass, IdSetFunction<I, E, EventContext> func) {
            final EventClass clazz = EventClass.of(eventClass);

            @SuppressWarnings("unchecked")    // since we want to store {@code IdSetFunction}s for various event types.
            final IdSetFunction<I, Message, EventContext> casted = (IdSetFunction<I, Message, EventContext>) func;
            map.put(clazz, casted);
        }

        private <E extends Message> Optional<IdSetFunction<I, E, EventContext>> get(Class<E> eventClass) {
            final EventClass clazz = EventClass.of(eventClass);
            final IdSetFunction<I, Message, EventContext> func = map.get(clazz);

            @SuppressWarnings("unchecked")  // we ensure the type when we put into the map.
            final IdSetFunction<I, E, EventContext> result = (IdSetFunction<I, E, EventContext>) func;
            return Optional.fromNullable(result);
        }
    }
}

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

package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.annotation.Subscribe;
import io.spine.base.Command;
import io.spine.base.CommandContext;
import io.spine.base.Enrichment;
import io.spine.base.Event;
import io.spine.base.EventContext;
import io.spine.base.Identifiers;
import io.spine.base.Version;
import io.spine.protobuf.Wrapper;
import io.spine.server.BoundedContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.command.EventFactory;
import io.spine.server.entity.idfunc.IdSetEventFunction;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.test.TestActorRequestFactory;
import io.spine.test.Tests;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.test.projection.ProjectValidatingBuilder;
import io.spine.test.projection.command.CreateProject;
import io.spine.test.projection.event.ProjectCreated;
import io.spine.validate.StringValueValidatingBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmytro Dashenkov
 */
class Given {

    static final int THREADS_COUNT_IN_POOL_EXECUTOR = 10;
    static final int SEVERAL = THREADS_COUNT_IN_POOL_EXECUTOR;
    static final int AWAIT_SECONDS = 6;
    private static final String PROJECT_UUID = Identifiers.newUuid();

    private Given() {
    }

    static Command validCommand() {
        final TestActorRequestFactory requestFactory =
                TestActorRequestFactory.newInstance(Given.class);
        return requestFactory.command().create(CreateProject.getDefaultInstance());
    }

    static Event validEvent() {
        final Command cmd = validCommand();
        final ProjectCreated eventMessage = ProjectCreated.newBuilder()
                                                          .setProjectId(ProjectId.newBuilder()
                                                                                 .setId("12345AD0"))
                                                          .build();
        final StringValue producerId = Wrapper.forString(Given.class.getSimpleName());
        final EventFactory eventFactory = EventFactory.newBuilder()
                                                      .setCommandId(cmd.getId())
                                                      .setProducerId(producerId)
                                                      .setCommandContext(cmd.getContext())
                                                      .build();
        final Event event = eventFactory.createEvent(eventMessage, Tests.<Version>nullRef());
        final Event result = event.toBuilder()
                                  .setContext(event.getContext()
                                               .toBuilder()
                                               .setEnrichment(Enrichment.newBuilder()
                                                                        .setDoNotEnrich(true))
                                               .build())
                                  .build();
        return result;
    }

    static ProjectionRepository<?, ?, ?> projectionRepo(BoundedContext context) {
        return new StandTestProjectionRepository(context);
    }

    static AggregateRepository<ProjectId, StandTestAggregate>
    aggregateRepo(BoundedContext context) {
        return new StandTestAggregateRepository(context);
    }

    static AggregateRepository<ProjectId, StandTestAggregate> aggregateRepo() {
        final BoundedContext boundedContext = BoundedContext.newBuilder()
                                                            .build();
        return aggregateRepo(boundedContext);
    }

    static BoundedContext boundedContext(Stand.Builder stand, StandUpdateDelivery delivery) {
        return boundedContextBuilder(stand)
                .setStand(Stand.newBuilder()
                               .setDelivery(delivery))
                .build();
    }

    private static BoundedContext.Builder boundedContextBuilder(Stand.Builder stand) {
        return BoundedContext.newBuilder()
                             .setStand(stand);
    }

    static class StandTestProjectionRepository
            extends ProjectionRepository<ProjectId, StandTestProjection, Project> {
        StandTestProjectionRepository(BoundedContext boundedContext) {
            super();
            addIdSetFunction(ProjectCreated.class,
                             new IdSetEventFunction<ProjectId, ProjectCreated>() {
                                 @Override
                                 public Set<ProjectId> apply(ProjectCreated message,
                                                             EventContext context) {
                                     return ImmutableSet.of(ProjectId.newBuilder()
                                                                     .setId(PROJECT_UUID)
                                                                     .build());
                                 }
                             });
        }
    }

    static class StandTestAggregateRepository
            extends AggregateRepository<ProjectId, StandTestAggregate> {

        /**
         * Creates a new repository instance.
         *
         * @param boundedContext the bounded context to which this repository belongs
         */
        StandTestAggregateRepository(BoundedContext boundedContext) {
            super();
        }
    }

    static class StandTestAggregate
            extends Aggregate<ProjectId, StringValue, StringValueValidatingBuilder> {

        /**
         * Creates a new aggregate instance.
         *
         * @param id the ID for the new aggregate
         * @throws IllegalArgumentException if the ID is not of one of the supported types
         */
        public StandTestAggregate(ProjectId id) {
            super(id);
        }

        @Assign
        List<? extends Message> handle(CreateProject createProject, CommandContext context) {
            // In real life we would return a list with at least one element
            // populated with real data.
            return Collections.emptyList();
        }

        @Apply
        public void handle(ProjectCreated event) {
            // Do nothing
        }
    }

    static class StandTestProjection
            extends Projection<ProjectId, Project, ProjectValidatingBuilder> {

        public StandTestProjection(ProjectId id) {
            super(id);
        }

        @SuppressWarnings("unused") // OK for test class.
        @Subscribe
        public void handle(ProjectCreated event, EventContext context) {
            getBuilder().setId(event.getProjectId());
        }
    }
}

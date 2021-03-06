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

import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import io.spine.core.CommandClass;
import io.spine.core.Event;
import io.spine.core.EventClass;
import io.spine.server.BoundedContext;
import io.spine.server.aggregate.given.AggregateRepositoryTestEnv.GivenAggregate;
import io.spine.server.aggregate.given.AggregateRepositoryTestEnv.ProjectAggregate;
import io.spine.server.aggregate.given.AggregateRepositoryTestEnv.ProjectAggregateRepository;
import io.spine.server.command.TestEventFactory;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.test.aggregate.Project;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.event.ProjectArchived;
import io.spine.test.aggregate.event.ProjectDeleted;
import io.spine.testdata.Sample;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

import static io.spine.core.given.GivenTenantId.newUuid;
import static io.spine.validate.Validate.isDefault;
import static io.spine.validate.Validate.isNotDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AggregateRepositoryShould {

    private BoundedContext boundedContext;
    private AggregateRepository<ProjectId, ProjectAggregate> repository;

    /**
     * Use spy only when it is required to avoid problems,
     * make tests faster and make it easier to debug.
     */
    private AggregateRepository<ProjectId, ProjectAggregate> repositorySpy;

    @Before
    public void setUp() {
        boundedContext = BoundedContext.newBuilder()
                                                            .build();
        repository = new ProjectAggregateRepository();
        boundedContext.register(repository);
        repositorySpy = spy(repository);
    }

    @After
    public void tearDown() throws Exception {
        repository.close();
    }

    @Test
    public void call_get_aggregate_constructor_method_only_once() {
        final ProjectId id = Sample.messageOfType(ProjectId.class);
        repositorySpy.create(id);
        repositorySpy.create(id);

        verify(repositorySpy, times(1)).findEntityConstructor();
    }

    @Test
    public void create_aggregate_with_default_state_if_no_aggregate_found() {
        final ProjectAggregate aggregate = repository.find(Sample.messageOfType(ProjectId.class))
                                                     .get();
        final Project state = aggregate.getState();

        assertTrue(isDefault(state));
    }

    @Test
    public void store_and_load_aggregate() {
        final ProjectId id = Sample.messageOfType(ProjectId.class);
        final ProjectAggregate expected = GivenAggregate.withUncommittedEvents(id);

        repository.store(expected);
        final ProjectAggregate actual = repository.find(id)
                                                  .get();

        assertTrue(isNotDefault(actual.getState()));
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getState(), actual.getState());
    }

    @Test
    public void restore_aggregate_using_snapshot() {
        final ProjectId id = Sample.messageOfType(ProjectId.class);
        final ProjectAggregate expected = GivenAggregate.withUncommittedEvents(id);

        repository.setSnapshotTrigger(expected.uncommittedEventsCount());
        repository.store(expected);

        final ProjectAggregate actual = repository.find(id)
                                                  .get();

        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getState(), actual.getState());
    }

    @Test
    public void store_snapshot_and_set_event_count_to_zero_if_needed() {
        final ProjectAggregate aggregate = GivenAggregate.withUncommittedEvents();
        // This should make the repository write the snapshot.
        repository.setSnapshotTrigger(aggregate.uncommittedEventsCount());

        repository.store(aggregate);
        final AggregateStateRecord record = repository.aggregateStorage()
                                                      .read(aggregate.getId())
                                                      .get();
        assertTrue(record.hasSnapshot());
        assertEquals(0, repository.aggregateStorage()
                                  .readEventCountAfterLastSnapshot(aggregate.getId()));
    }

    @Test
    public void not_store_snapshot_if_not_needed() {
        final ProjectAggregate aggregate = GivenAggregate.withUncommittedEvents();

        repository.store(aggregate);
        final AggregateStateRecord record = repository.aggregateStorage()
                                                      .read(aggregate.getId())
                                                      .get();
        assertFalse(record.hasSnapshot());
    }

    @Test
    public void return_aggregate_class() {
        assertEquals(ProjectAggregate.class, repository.getAggregateClass());
    }

    @Test
    public void have_default_value_for_snapshot_trigger() {
        assertEquals(AggregateRepository.DEFAULT_SNAPSHOT_TRIGGER, repository.getSnapshotTrigger());
    }

    @Test(expected = IllegalArgumentException.class)
    public void do_not_accept_negative_snapshot_trigger() {
        repository.setSnapshotTrigger(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void do_not_accept_zero_snapshot_trigger() {
        repository.setSnapshotTrigger(0);
    }

    @Test
    public void allow_to_change_snapshot_trigger() {
        final int newSnapshotTrigger = 1000;

        repository.setSnapshotTrigger(newSnapshotTrigger);

        assertEquals(newSnapshotTrigger, repository.getSnapshotTrigger());
    }

    @Test
    public void expose_classes_of_commands_of_its_aggregate() {
        final Set<CommandClass> aggregateCommands =
                Aggregate.TypeInfo.getCommandClasses(ProjectAggregate.class);
        final Set<CommandClass> exposedByRepository = repository.getMessageClasses();

        assertTrue(exposedByRepository.containsAll(aggregateCommands));
    }

    @Test
    public void not_find_archived_aggregates() {
        final ProjectAggregate aggregate = givenStoredAggregate();

        final AggregateTransaction tx = AggregateTransaction.start(aggregate);
        aggregate.setArchived(true);
        tx.commit();
        repository.store(aggregate);

        assertFalse(repository.find(aggregate.getId())
                              .isPresent());
    }

    @Test
    public void not_find_deleted_aggregates() {
        final ProjectAggregate aggregate = givenStoredAggregate();

        final AggregateTransaction tx = AggregateTransaction.start(aggregate);
        aggregate.setDeleted(true);
        tx.commit();

        repository.store(aggregate);

        assertFalse(repository.find(aggregate.getId())
                              .isPresent());
    }

    @Test(expected = IllegalStateException.class)
    public void throw_ISE_if_unable_to_load_entity_by_id_from_storage_index() {
        // Store a valid aggregate.
        givenStoredAggregate();

        // Store a troublesome entity, which cannot be loaded.
        final TenantAwareOperation op = new TenantAwareOperation(newUuid()) {
            @Override
            public void run() {
                givenStoredAggregateWithId(ProjectAggregateRepository.troublesome.getId());
            }
        };
        op.execute();

        final Iterator<ProjectAggregate> iterator = repository.iterator(
                Predicates.<ProjectAggregate>alwaysTrue());

        // This should iterate through all and fail.
        Lists.newArrayList(iterator);
    }

    @Test
    public void expose_event_classes_on_which_aggregates_react() {
        final Set<EventClass> eventClasses = repository.getEventClasses();
        assertTrue(eventClasses.contains(EventClass.of(ProjectArchived.class)));
        assertTrue(eventClasses.contains(EventClass.of(ProjectDeleted.class)));
    }

    @Test
    public void route_events_to_aggregates() {
        final ProjectAggregate parent = givenStoredAggregate();
        final ProjectAggregate child = givenStoredAggregate();

        assertTrue(repository.find(parent.getId())
                             .isPresent());
        assertTrue(repository.find(child.getId())
                             .isPresent());

        final TestEventFactory factory = TestEventFactory.newInstance(getClass());
        final ProjectArchived msg = ProjectArchived.newBuilder()
                                                   .setProjectId(parent.getId())
                                                   .addChildProjectId(child.getId())
                                                   .build();
        final Event event = factory.createEvent(msg);

        boundedContext.getEventBus()
                      .post(event);

        // Check that the child aggregate was archived.
        assertFalse(repository.find(child.getId())
                              .isPresent());

        // The parent should not be archived since the dispatch route uses only child aggregates
        // from the `ProjectArchived` event.
        assertTrue(repository.find(parent.getId())
                             .isPresent());
    }

    /*
     * Test environment methods that use internals of this test suite.
     ******************************************************************/

    private ProjectAggregate givenStoredAggregate() {
        final ProjectId id = Sample.messageOfType(ProjectId.class);
        final ProjectAggregate aggregate = GivenAggregate.withUncommittedEvents(id);

        repository.store(aggregate);
        return aggregate;
    }

    private void givenStoredAggregateWithId(String id) {
        final ProjectId projectId = ProjectId.newBuilder()
                                             .setId(id)
                                             .build();
        final ProjectAggregate aggregate = GivenAggregate.withUncommittedEvents(projectId);

        repository.store(aggregate);
    }
}

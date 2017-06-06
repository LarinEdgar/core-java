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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.base.Command;
import io.spine.base.CommandContext;
import io.spine.base.Commands;
import io.spine.base.Event;
import io.spine.envelope.CommandEnvelope;
import io.spine.server.command.Assign;
import io.spine.server.entity.InvalidEntityStateException;
import io.spine.test.TestActorRequestFactory;
import io.spine.test.TestEventFactory;
import io.spine.test.TimeTests;
import io.spine.test.aggregate.Project;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.ProjectValidatingBuilder;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.command.AddTask;
import io.spine.test.aggregate.command.CreateProject;
import io.spine.test.aggregate.command.ImportEvents;
import io.spine.test.aggregate.command.StartProject;
import io.spine.test.aggregate.event.ProjectCreated;
import io.spine.test.aggregate.event.ProjectStarted;
import io.spine.test.aggregate.event.TaskAdded;
import io.spine.test.aggregate.user.User;
import io.spine.test.aggregate.user.UserValidatingBuilder;
import io.spine.time.Time;
import io.spine.type.CommandClass;
import io.spine.validate.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.aggregate.AggregateCommandDispatcher.dispatch;
import static io.spine.server.aggregate.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.Given.EventMessage.projectStarted;
import static io.spine.server.aggregate.Given.EventMessage.taskAdded;
import static io.spine.test.Given.aggregateOfClass;
import static io.spine.test.Tests.assertHasPrivateParameterlessCtor;
import static io.spine.test.Tests.newVersionWithNumber;
import static io.spine.test.Verify.assertSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Alexander Litus
 * @author Alexander Yevsyukkov
 */
@SuppressWarnings({"ClassWithTooManyMethods", "OverlyCoupledClass"})
public class AggregateShould {

    private static final TestActorRequestFactory requestFactory =
            TestActorRequestFactory.newInstance(AggregateShould.class);
    private static final ProjectId ID = ProjectId.newBuilder()
                                                 .setId("prj-01")
                                                 .build();

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(pack(ID), requestFactory);

    private static final CreateProject createProject = Given.CommandMessage.createProject(ID);
    private static final AddTask addTask = Given.CommandMessage.addTask(ID);
    private static final StartProject startProject = Given.CommandMessage.startProject(ID);

    private TestAggregate aggregate;

    @Before
    public void setUp() {
        aggregate = newAggregate(ID);
    }

    private static TestAggregate newAggregate(ProjectId id) {
        final TestAggregate result = new TestAggregate(id);
        result.init();
        return result;
    }

    private static CommandEnvelope env(Message commandMessage) {
        return CommandEnvelope.of(requestFactory.command().create(commandMessage));
    }

    @Test
    public void handle_one_command_and_apply_appropriate_event() {
        dispatch(aggregate, env(createProject));

        assertTrue(aggregate.isCreateProjectCommandHandled);
        assertTrue(aggregate.isProjectCreatedEventApplied);
    }

    @Test
    public void advances_the_version_by_one_upon_handling_command_with_one_event() {
        final int version = aggregate.versionNumber();

        dispatch(aggregate, env(createProject));

        assertEquals(version + 1, aggregate.versionNumber());
    }

    @Test
    public void write_its_version_into_event_context() {
        dispatch(aggregate, env(createProject));

        // Get the first event since the command handler produces only one event message.
        final Event event = aggregate.getUncommittedEvents()
                                     .get(0);

        assertEquals(aggregate.getVersion(), event.getContext()
                                                  .getVersion());
    }

    @Test
    public void handle_only_dispatched_command() {
        dispatch(aggregate, env(createProject));

        assertTrue(aggregate.isCreateProjectCommandHandled);
        assertTrue(aggregate.isProjectCreatedEventApplied);

        assertFalse(aggregate.isAddTaskCommandHandled);
        assertFalse(aggregate.isTaskAddedEventApplied);

        assertFalse(aggregate.isStartProjectCommandHandled);
        assertFalse(aggregate.isProjectStartedEventApplied);
    }

    @Test
    public void invoke_applier_after_command_handler() {
        dispatch(aggregate, env(createProject));
        assertTrue(aggregate.isCreateProjectCommandHandled);
        assertTrue(aggregate.isProjectCreatedEventApplied);

        dispatch(aggregate, env(addTask));
        assertTrue(aggregate.isAddTaskCommandHandled);
        assertTrue(aggregate.isTaskAddedEventApplied);

        dispatch(aggregate, env(startProject));
        assertTrue(aggregate.isStartProjectCommandHandled);
        assertTrue(aggregate.isProjectStartedEventApplied);
    }

    @Test(expected = IllegalStateException.class)
    public void throw_exception_if_missing_command_handler() {
        final TestAggregateForCaseMissingHandlerOrApplier aggregate =
                new TestAggregateForCaseMissingHandlerOrApplier(ID);

        dispatch(aggregate, env(addTask));
    }

    @Test(expected = IllegalStateException.class)
    public void throw_exception_if_missing_event_applier_for_non_state_neutral_event() {
        final TestAggregateForCaseMissingHandlerOrApplier aggregate =
                new TestAggregateForCaseMissingHandlerOrApplier(ID);
        try {
            dispatch(aggregate, env(createProject));
        } catch (IllegalStateException e) { // expected exception
            assertTrue(aggregate.isCreateProjectCommandHandled);
            throw e;
        }
    }

    @Test
    public void return_command_classes_which_are_handled_by_aggregate() {
        final Set<CommandClass> commandClasses =
                Aggregate.TypeInfo.getCommandClasses(TestAggregate.class);

        assertTrue(commandClasses.size() == 4);
        assertTrue(commandClasses.contains(CommandClass.of(CreateProject.class)));
        assertTrue(commandClasses.contains(CommandClass.of(AddTask.class)));
        assertTrue(commandClasses.contains(CommandClass.of(StartProject.class)));
        assertTrue(commandClasses.contains(CommandClass.of(ImportEvents.class)));
    }

    @Test
    public void return_default_state_by_default() {
        final Project state = aggregate.getState();

        assertEquals(aggregate.getDefaultState(), state);
    }

    @Test
    public void update_state_when_the_command_is_handled() {
        dispatch(aggregate, env(createProject));

        final Project state = aggregate.getState();

        assertEquals(ID, state.getId());
        assertEquals(Status.CREATED, state.getStatus());
    }

    @Test
    public void return_current_state_after_several_dispatches() {
        dispatch(aggregate, env(createProject));
        assertEquals(Status.CREATED, aggregate.getState()
                                              .getStatus());

        dispatch(aggregate, env(startProject));
        assertEquals(Status.STARTED, aggregate.getState()
                                              .getStatus());
    }

    @Test
    public void return_non_null_time_when_was_last_modified() {
        final Timestamp creationTime = new TestAggregate(ID).whenModified();
        assertNotNull(creationTime);
    }

    @Test
    public void record_modification_time_when_command_handled() {
        try {
            final Timestamp frozenTime = Time.getCurrentTime();
            Time.setProvider(new TimeTests.FrozenMadHatterParty(frozenTime));

            dispatch(aggregate, env(createProject));

            assertEquals(frozenTime, aggregate.whenModified());
        } finally {
            Time.resetProvider();
        }
    }

    @Test
    public void advance_version_on_command_handled() {
        final int version = aggregate.versionNumber();

        dispatch(aggregate, env(createProject));
        dispatch(aggregate, env(startProject));
        dispatch(aggregate, env(addTask));

        assertEquals(version + 3, aggregate.versionNumber());
    }

    @Test
    public void play_events() {
        final List<Event> events = generateProjectEvents();
        final AggregateStateRecord aggregateStateRecord =
                AggregateStateRecord.newBuilder()
                                    .addAllEvent(events)
                                    .build();

        final AggregateTransaction tx = AggregateTransaction.start(aggregate);
        aggregate.play(aggregateStateRecord);
        tx.commit();

        assertTrue(aggregate.isProjectCreatedEventApplied);
        assertTrue(aggregate.isTaskAddedEventApplied);
        assertTrue(aggregate.isProjectStartedEventApplied);
    }

    @Test
    public void restore_snapshot_during_play() {
        dispatch(aggregate, env(createProject));

        final Snapshot snapshot = aggregate.toSnapshot();

        final TestAggregate anotherAggregate = newAggregate(aggregate.getId());

        final AggregateTransaction tx = AggregateTransaction.start(anotherAggregate);
        anotherAggregate.play(AggregateStateRecord.newBuilder()
                                                  .setSnapshot(snapshot)
                                                  .build());
        tx.commit();

        assertEquals(aggregate, anotherAggregate);
    }

    @Test
    public void not_return_any_uncommitted_event_records_by_default() {
        final List<Event> events = aggregate.getUncommittedEvents();

        assertTrue(events.isEmpty());
    }

    @Test
    public void return_uncommitted_event_records_after_dispatch() {
        aggregate.dispatchCommands(command(createProject),
                                   command(addTask),
                                   command(startProject));

        final List<Event> events = aggregate.getUncommittedEvents();

        assertContains(eventsToClasses(events),
                       ProjectCreated.class, TaskAdded.class, ProjectStarted.class);
    }

    @Test
    public void not_return_any_event_records_when_commit_by_default() {
        final List<Event> events = aggregate.commitEvents();

        assertTrue(events.isEmpty());
    }

    @Test
    public void return_events_when_commit_after_dispatch() {
        aggregate.dispatchCommands(command(createProject),
                                   command(addTask),
                                   command(startProject));

        final List<Event> events = aggregate.commitEvents();

        assertContains(eventsToClasses(events),
                       ProjectCreated.class, TaskAdded.class, ProjectStarted.class);
    }

    private static Command command(Message commandMessage) {
        return requestFactory.command().create(commandMessage);
    }

    @Test
    public void clear_event_records_when_commit_after_dispatch() {
        aggregate.dispatchCommands(command(createProject),
                                   command(addTask),
                                   command(startProject));

        final List<Event> events = aggregate.commitEvents();
        assertFalse(events.isEmpty());

        final List<Event> emptyList = aggregate.commitEvents();
        assertTrue(emptyList.isEmpty());
    }

    @Test
    public void transform_current_state_to_snapshot_event() {

        dispatch(aggregate, env(createProject));

        final Snapshot snapshot = aggregate.toSnapshot();
        final Project state = unpack(snapshot.getState());

        assertEquals(ID, state.getId());
        assertEquals(Status.CREATED, state.getStatus());
    }

    @Test
    public void restore_state_from_snapshot() {

        dispatch(aggregate, env(createProject));

        final Snapshot snapshotNewProject = aggregate.toSnapshot();

        final TestAggregate anotherAggregate = newAggregate(aggregate.getId());

        final AggregateTransaction tx = AggregateTransaction.start(anotherAggregate);
        anotherAggregate.restore(snapshotNewProject);
        tx.commit();

        assertEquals(aggregate.getState(), anotherAggregate.getState());
        assertEquals(aggregate.getVersion(), anotherAggregate.getVersion());
        assertEquals(aggregate.getLifecycleFlags(), anotherAggregate.getLifecycleFlags());
    }

    @Test
    public void import_events() {
        final String projectName = getClass().getSimpleName();
        final ProjectId id = aggregate.getId();
        final ImportEvents importCmd =
                ImportEvents.newBuilder()
                            .setProjectId(id)
                            .addEvent(event(projectCreated(id, projectName), 1))
                            .addEvent(event(taskAdded(id), 2))
                            .build();
        aggregate.dispatchCommands(command(importCmd));

        assertTrue(aggregate.isProjectCreatedEventApplied);
        assertTrue(aggregate.isTaskAddedEventApplied);
    }

    @SuppressWarnings("unused")
    private static class TestAggregate
            extends Aggregate<ProjectId, Project, ProjectValidatingBuilder> {

        private boolean isCreateProjectCommandHandled = false;
        private boolean isAddTaskCommandHandled = false;
        private boolean isStartProjectCommandHandled = false;

        private boolean isProjectCreatedEventApplied = false;
        private boolean isTaskAddedEventApplied = false;
        private boolean isProjectStartedEventApplied = false;

        protected TestAggregate(ProjectId id) {
            super(id);
        }

        /**
         * Overrides to expose the method to the text.
         */
        @VisibleForTesting
        @Override
        protected void init() {
            super.init();
        }

        @Assign
        ProjectCreated handle(CreateProject cmd, CommandContext ctx) {
            isCreateProjectCommandHandled = true;
            final ProjectCreated event = projectCreated(cmd.getProjectId(),
                                                                           cmd.getName());
            return event;
        }

        @Assign
        TaskAdded handle(AddTask cmd, CommandContext ctx) {
            isAddTaskCommandHandled = true;
            final TaskAdded event = taskAdded(cmd.getProjectId());
            return event.toBuilder()
                        .setTask(cmd.getTask())
                        .build();
        }

        @Assign
        List<ProjectStarted> handle(StartProject cmd, CommandContext ctx) {
            isStartProjectCommandHandled = true;
            final ProjectStarted message = projectStarted(cmd.getProjectId());
            return newArrayList(message);
        }

        @Assign
        List<Event> handle(ImportEvents command, CommandContext ctx) {
            return command.getEventList();
        }

        @Apply
        private void event(ProjectCreated event) {
            getBuilder()
                    .setId(event.getProjectId())
                    .setStatus(Status.CREATED);

            isProjectCreatedEventApplied = true;
        }

        @Apply
        private void event(TaskAdded event) {
            isTaskAddedEventApplied = true;
            getBuilder().addTask(event.getTask());
        }

        @Apply
        private void event(ProjectStarted event) {
            getBuilder()
                    .setId(event.getProjectId())
                    .setStatus(Status.STARTED);

            isProjectStartedEventApplied = true;
        }

        private void dispatchCommands(Command... commands) {
            for (Command cmd : commands) {
                final Message commandMessage = Commands.getMessage(cmd);
                dispatch(this, env(commandMessage));
            }
        }
    }

    /** Class only for test cases: exception if missing command handler or missing event applier. */
    private static class TestAggregateForCaseMissingHandlerOrApplier
            extends Aggregate<ProjectId, Project, ProjectValidatingBuilder> {

        private boolean isCreateProjectCommandHandled = false;

        public TestAggregateForCaseMissingHandlerOrApplier(ProjectId id) {
            super(id);
        }

        /** There is no event applier for ProjectCreated event (intentionally). */
        @Assign
        ProjectCreated handle(CreateProject cmd, CommandContext ctx) {
            isCreateProjectCommandHandled = true;
            return projectCreated(cmd.getProjectId(), cmd.getName());
        }
    }

    private static class TestAggregateWithIdInteger
            extends Aggregate<Integer, Project, ProjectValidatingBuilder> {
        private TestAggregateWithIdInteger(Integer id) {
            super(id);
        }
    }

    @Test
    public void increment_version_when_applying_state_changing_event() {
        final int version = aggregate.getVersion()
                                     .getNumber();
        // Dispatch two commands that cause events that modify aggregate state.
        aggregate.dispatchCommands(command(createProject), command(startProject));

        assertEquals(version + 2, aggregate.getVersion()
                                           .getNumber());
    }

    @Test
    public void record_modification_timestamp() throws InterruptedException {
        try {
            final TimeTests.BackToTheFuture provider = new TimeTests.BackToTheFuture();
            Time.setProvider(provider);

            Timestamp currentTime = Time.getCurrentTime();

            aggregate.dispatchCommands(command(createProject));

            assertEquals(currentTime, aggregate.whenModified());

            currentTime = provider.forward(10);

            aggregate.dispatchCommands(command(startProject));

            assertEquals(currentTime, aggregate.whenModified());
        } finally {
            Time.resetProvider();
        }
    }

    /** The class to check raising and catching exceptions. */
    @SuppressWarnings("unused")
    private static class FaultyAggregate
            extends Aggregate<ProjectId, Project, ProjectValidatingBuilder> {

        private static final String BROKEN_HANDLER = "broken_handler";
        private static final String BROKEN_APPLIER = "broken_applier";

        private final boolean brokenHandler;
        private final boolean brokenApplier;

        private FaultyAggregate(ProjectId id, boolean brokenHandler, boolean brokenApplier) {
            super(id);
            this.brokenHandler = brokenHandler;
            this.brokenApplier = brokenApplier;
        }

        @Assign
        ProjectCreated handle(CreateProject cmd, CommandContext ctx) {
            if (brokenHandler) {
                throw new IllegalStateException(BROKEN_HANDLER);
            }
            return projectCreated(cmd.getProjectId(), cmd.getName());
        }

        @Apply
        private void event(ProjectCreated event) {
            if (brokenApplier) {
                throw new IllegalStateException(BROKEN_APPLIER);
            }

            getBuilder().setStatus(Status.CREATED);
        }
    }

    @Test
    public void propagate_RuntimeException_when_handler_throws() {
        final FaultyAggregate faultyAggregate =
                new FaultyAggregate(ID, true, false);

        final Command command = Given.ACommand.createProject();
        try {
            dispatch(faultyAggregate, env(command.getMessage()));
        } catch (RuntimeException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored") // We need it for checking.
            final Throwable cause = getRootCause(e);
            assertTrue(cause instanceof IllegalStateException);
            assertEquals(FaultyAggregate.BROKEN_HANDLER, cause.getMessage());
        }
    }

    @Test
    public void propagate_RuntimeException_when_applier_throws() {
        final FaultyAggregate faultyAggregate =
                new FaultyAggregate(ID, false, true);

        final Command command = Given.ACommand.createProject();
        try {
            dispatch(aggregate, env(command.getMessage()));
        } catch (RuntimeException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            // because we need it for checking.
            final Throwable cause = getRootCause(e);
            assertTrue(cause instanceof IllegalStateException);
            assertEquals(FaultyAggregate.BROKEN_APPLIER, cause.getMessage());
        }
    }

    @Test
    public void propagate_RuntimeException_when_play_raises_exception() {
        final FaultyAggregate faultyAggregate =
                new FaultyAggregate(ID, false, true);
        try {
            final Event event = event(projectCreated(ID, getClass().getSimpleName()), 1);

            final AggregateTransaction tx = AggregateTransaction.start(faultyAggregate);
            faultyAggregate.play(AggregateStateRecord.newBuilder()
                                                     .addEvent(event)
                                                     .build());
            tx.commit();
        } catch (RuntimeException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            // because we need it for checking.
            final Throwable cause = getRootCause(e);
            assertTrue(cause instanceof IllegalStateException);
            assertEquals(FaultyAggregate.BROKEN_APPLIER, cause.getMessage());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void do_not_allow_getting_state_builder_from_outside_the_event_applier() {
        new TestAggregateWithIdInteger(100).getBuilder();
    }

    @Test
    public void have_TypeInfo_utility_class() {
        assertHasPrivateParameterlessCtor(Aggregate.TypeInfo.class);
    }

    @Test
    public void throw_InvalidEntityStateException_if_state_is_invalid() {
        final User user = User.newBuilder()
                              .setFirstName("|")
                              .setLastName("|")
                              .build();
        try {
            aggregateOfClass(UserAggregate.class).withId(getClass().getName())
                                                 .withVersion(1)
                                                 .withState(user)
                                                 .build();
            fail();
        } catch (InvalidEntityStateException e) {
            final List<ConstraintViolation> violations = e.getError()
                                                          .getValidationError()
                                                          .getConstraintViolationList();
            assertSize(user.getAllFields()
                           .size(), violations);
        }
    }

    @Test
    public void update_valid_entity_state() {
        final User user = User.newBuilder()
                              .setFirstName("Fname")
                              .setLastName("Lname")
                              .build();
        aggregateOfClass(UserAggregate.class).withId(getClass().getName())
                                             .withVersion(1)
                                             .withState(user)
                                             .build();
    }

    private static class UserAggregate extends Aggregate<String, User, UserValidatingBuilder> {
        private UserAggregate(String id) {
            super(id);
        }
    }

    /*
     * Utility methods.
     ********************************/

    private static Collection<Class<? extends Message>> eventsToClasses(Collection<Event> events) {
        return transform(events, new Function<Event, Class<? extends Message>>() {
            @Nullable // return null because an exception won't be propagated in this case
            @Override
            public Class<? extends Message> apply(@Nullable Event record) {
                if (record == null) {
                    return null;
                }
                return unpack(record.getMessage()).getClass();
            }
        });
    }

    private static void assertContains(Collection<Class<? extends Message>> actualClasses,
                                       Class... expectedClasses) {
        assertTrue(actualClasses.containsAll(newArrayList(expectedClasses)));
        assertEquals(expectedClasses.length, actualClasses.size());
    }

    private static Event event(Message eventMessage, int versionNumber) {
        return eventFactory.createEvent(eventMessage, newVersionWithNumber(versionNumber));
    }

    private static List<Event> generateProjectEvents() {
        final String projectName = AggregateShould.class.getSimpleName();
        final List<Event> events = ImmutableList.<Event>builder()
                .add(event(projectCreated(ID, projectName), 1))
                .add(event(taskAdded(ID), 3))
                .add(event(projectStarted(ID), 4))
                .build();
        return events;
    }
}

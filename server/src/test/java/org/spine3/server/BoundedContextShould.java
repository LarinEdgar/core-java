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

package org.spine3.server;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.*;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spine3.base.*;
import org.spine3.client.CommandRequest;
import org.spine3.client.UserUtil;
import org.spine3.server.aggregate.Aggregate;
import org.spine3.server.aggregate.AggregateRepository;
import org.spine3.server.aggregate.Apply;
import org.spine3.server.error.UnsupportedCommandException;
import org.spine3.server.procman.ProcessManager;
import org.spine3.server.procman.ProcessManagerRepository;
import org.spine3.server.storage.StorageFactory;
import org.spine3.server.storage.memory.InMemoryStorageFactory;
import org.spine3.server.stream.EventStore;
import org.spine3.server.stream.StreamProjection;
import org.spine3.server.stream.StreamProjectionRepository;
import org.spine3.test.project.Project;
import org.spine3.test.project.ProjectId;
import org.spine3.test.project.command.AddTask;
import org.spine3.test.project.command.CreateProject;
import org.spine3.test.project.command.StartProject;
import org.spine3.test.project.event.ProjectCreated;
import org.spine3.test.project.event.ProjectStarted;
import org.spine3.test.project.event.TaskAdded;
import org.spine3.testdata.TestAggregateIdFactory;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.protobuf.util.TimeUtil.add;
import static com.google.protobuf.util.TimeUtil.getCurrentTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.spine3.protobuf.Durations.seconds;
import static org.spine3.protobuf.Messages.fromAny;
import static org.spine3.test.project.Project.getDefaultInstance;
import static org.spine3.test.project.Project.newBuilder;
import static org.spine3.testdata.TestCommandFactory.*;
import static org.spine3.testdata.TestEventFactory.*;

/**
 * @author Alexander Litus
 */
@SuppressWarnings({"InstanceMethodNamingConvention", "ClassWithTooManyMethods", "OverlyCoupledClass"})
public class BoundedContextShould {

    private final UserId userId = UserUtil.newUserId("test_user");
    private final ProjectId projectId = TestAggregateIdFactory.createProjectId("test_project_id");
    private final EmptyHandler handler = new EmptyHandler();

    private StorageFactory storageFactory;
    private BoundedContext boundedContext;
    private boolean handlersRegistered = false;

    @Before
    public void setUp() {
        storageFactory = InMemoryStorageFactory.getInstance();
        boundedContext = BoundedContextTestStubs.create(storageFactory);
    }

    private static EventBus newEventBus(StorageFactory storageFactory) {
        return EventBus.newInstance(EventStore.newBuilder()
            .setStreamExecutor(MoreExecutors.directExecutor())
            .setStorage(storageFactory.createEventStorage())
            .build());
    }

    private static CommandBus newCommandDispatcher(StorageFactory storageFactory) {
        return CommandBus.create(new CommandStore(storageFactory.createCommandStorage()));
    }

    @After
    public void tearDown() throws Exception {
        if (handlersRegistered) {
            boundedContext.getEventBus().unsubscribe(handler);
        }
        boundedContext.close();
    }

    /**
     * Registers all test repositories, handlers etc.
     */
    private void registerAll() {
        final ProjectAggregateRepository repository = new ProjectAggregateRepository(boundedContext);
        repository.initStorage(InMemoryStorageFactory.getInstance());
        boundedContext.register(repository);
        boundedContext.getEventBus().subscribe(handler);
        handlersRegistered = true;
    }

    //TODO:2016-01-25:alexander.yevsyukov: Move the command result verification tests into AggregateRepositoryShould.

    private List<CommandResult> processRequests(Iterable<CommandRequest> requests) {

        final List<CommandResult> results = newLinkedList();
        for (CommandRequest request : requests) {
            final CommandResult result = boundedContext.process(request);
            results.add(result);
        }
        return results;
    }

    private List<CommandRequest> generateRequests() {

        final Duration delta = seconds(10);
        final Timestamp time1 = getCurrentTime();
        final Timestamp time2 = add(time1, delta);
        final Timestamp time3 = add(time2, delta);

        final CommandRequest createProject = createProject(userId, projectId, time1);
        final CommandRequest addTask = addTask(userId, projectId, time2);
        final CommandRequest startProject = startProject(userId, projectId, time3);

        return newArrayList(createProject, addTask, startProject);
    }

    @Test
    public void return_EventBus() {
        assertNotNull(boundedContext.getEventBus());
    }

    @Test
    public void return_CommandDispatcher() {
        assertNotNull(boundedContext.getCommandBus());
    }

    @Test(expected = NullPointerException.class)
    public void throw_NPE_on_null_CommandRequest() {
        // noinspection ConstantConditions
        boundedContext.process(null);
    }

    @Test(expected = UnsupportedCommandException.class)
    public void throw_exception_if_not_register_any_repositories_and_try_to_process_command() {
        boundedContext.post(createProject());
    }

    @Test
    public void register_AggregateRepository() {
        final ProjectAggregateRepository repository = new ProjectAggregateRepository(boundedContext);
        repository.initStorage(storageFactory);
        boundedContext.register(repository);
    }

    @Test
    public void register_ProcessManagerRepository() {
        final ProjectPmRepo repository = new ProjectPmRepo(boundedContext);
        repository.initStorage(storageFactory);
        boundedContext.register(repository);
    }

    @Test
    public void register_ProjectionRepository() {
        final ProjectReportRepository repository = new ProjectReportRepository(boundedContext);
        repository.initStorage(storageFactory);
        boundedContext.register(repository);
    }

    @Test
    public void post_CommandRequest() {
        registerAll();
        final CommandRequest request = createProject(userId, projectId, getCurrentTime());

        boundedContext.post(request);
    }

    private void assertCommandResultsAreValid(List<CommandRequest> requests, List<CommandResult> results) {
        assertEquals(requests.size(), results.size());

        for (int i = 0; i < requests.size(); i++) {
            assertRequestAndResultMatch(requests.get(i), results.get(i));
        }
    }

    private void assertRequestAndResultMatch(CommandRequest request, CommandResult result) {
        final Timestamp expectedTime = request.getContext().getTimestamp();

        final List<EventRecord> records = result.getEventRecordList();
        assertEquals(1, records.size());
        final EventRecord actualRecord = records.get(0);
        final ProjectId actualProjectId = fromAny(actualRecord.getContext().getAggregateId());

        assertEquals(projectId, actualProjectId);
        assertEquals(userId, actualRecord.getContext().getCommandContext().getActor());
        assertEquals(expectedTime, actualRecord.getContext().getCommandContext().getTimestamp());
    }


    private static class ResponseObserver implements StreamObserver<Response> {

        private Response response;

        @Override
        public void onNext(Response commandResponse) {
            this.response = commandResponse;
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }

        public Response getResponse() {
            return response;
        }
    }

    @Test
    public void verify_namespace_attribute_if_multitenant() {
        final BoundedContext bc = BoundedContext.newBuilder()
                .setStorageFactory(InMemoryStorageFactory.getInstance())
                .setCommandBus(newCommandDispatcher(storageFactory))
                .setEventBus(newEventBus(storageFactory))
                .setMultitenant(true)
                .build();

        final ResponseObserver observer = new ResponseObserver();

        final CommandRequest request = CommandRequest.newBuilder()
                // Pass empty command so that we have something valid to unpack in the context.
                .setCommand(Any.pack(StringValue.getDefaultInstance()))
                .build();
        bc.post(request, observer);

        assertEquals(CommandValidationError.NAMESPACE_UNKNOWN.getNumber(), observer.getResponse().getError().getCode());
    }

    @SuppressWarnings("unused")
    private static class ProjectAggregate extends Aggregate<ProjectId, Project> {

        private static final String STATUS_NEW = "STATUS_NEW";
        private static final String STATUS_STARTED = "STATUS_STARTED";

        private boolean isCreateProjectCommandHandled = false;
        private boolean isAddTaskCommandHandled = false;
        private boolean isStartProjectCommandHandled = false;

        private boolean isProjectCreatedEventApplied = false;
        private boolean isTaskAddedEventApplied = false;
        private boolean isProjectStartedEventApplied = false;

        public ProjectAggregate(ProjectId id) {
            super(id);
        }

        @Override
        protected Project getDefaultState() {
            return getDefaultInstance();
        }

        @Assign
        public ProjectCreated handle(CreateProject cmd, CommandContext ctx) {
            isCreateProjectCommandHandled = true;
            return projectCreatedEvent(cmd.getProjectId());
        }

        @Assign
        public TaskAdded handle(AddTask cmd, CommandContext ctx) {
            isAddTaskCommandHandled = true;
            return taskAddedEvent(cmd.getProjectId());
        }

        @Assign
        public List<ProjectStarted> handle(StartProject cmd, CommandContext ctx) {
            isStartProjectCommandHandled = true;
            final ProjectStarted message = projectStartedEvent(cmd.getProjectId());
            return newArrayList(message);
        }

        @Apply
        private void event(ProjectCreated event) {

            final Project newState = newBuilder(getState())
                    .setProjectId(event.getProjectId())
                    .setStatus(STATUS_NEW)
                    .build();

            incrementState(newState);

            isProjectCreatedEventApplied = true;
        }

        @Apply
        private void event(TaskAdded event) {
            isTaskAddedEventApplied = true;
        }

        @Apply
        private void event(ProjectStarted event) {

            final Project newState = newBuilder(getState())
                    .setProjectId(event.getProjectId())
                    .setStatus(STATUS_STARTED)
                    .build();

            incrementState(newState);

            isProjectStartedEventApplied = true;
        }
    }


    private static class ProjectAggregateRepository extends AggregateRepository<ProjectId, ProjectAggregate> {
        private ProjectAggregateRepository(BoundedContext boundedContext) {
            super(boundedContext);
        }
    }

    @SuppressWarnings("UnusedParameters") // It is intended in this empty handler class.
    private static class EmptyHandler implements EventHandler {

        @Subscribe
        public void on(ProjectCreated event, EventContext context) {
        }

        @Subscribe
        public void on(TaskAdded event, EventContext context) {
        }

        @Subscribe
        public void on(ProjectStarted event, EventContext context) {
        }
    }

    private static class ProjectProcessManager extends ProcessManager<ProjectId, Empty> {

        public ProjectProcessManager(ProjectId id) {
            super(id);
        }

        @Override
        protected Empty getDefaultState() {
            return Empty.getDefaultInstance();
        }

        @SuppressWarnings("UnusedParameters") // OK for test method
        @Assign
        public void handle(CreateProject command, CommandContext ctx) {
            // Do nothing, just watch.
        }

        @SuppressWarnings("UnusedParameters") // OK for test method
        @Subscribe
        public void on(ProjectCreated event, EventContext ctx) {
            // Do nothing, just watch.
        }
    }

    private static class ProjectPmRepo extends ProcessManagerRepository<ProjectId, ProjectProcessManager, Empty> {
        private ProjectPmRepo(BoundedContext boundedContext) {
            super(boundedContext);
        }
    }

    private static class ProjectReport extends StreamProjection<ProjectId, Empty> {

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        // Public constructor is a part of projection public API. It's called by a repository.
        public ProjectReport(ProjectId id) {
            super(id);
        }

        @Override
        protected Empty getDefaultState() {
            return Empty.getDefaultInstance();
        }

        @SuppressWarnings("UnusedParameters") // OK for test method.
        @Subscribe
        public void on(ProjectCreated event, EventContext context) {
            // Do nothing. We have the method so that there's one event class exposed by the repository.
        }
    }

    private static class ProjectReportRepository extends StreamProjectionRepository<ProjectId, ProjectReport, Empty> {
        protected ProjectReportRepository(BoundedContext boundedContext) {
            super(boundedContext);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void check_repository_has_storage_assigned_upon_registration() {
        boundedContext.register(new ProjectAggregateRepository(boundedContext));
    }
}
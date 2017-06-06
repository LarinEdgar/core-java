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

package io.spine.server.procman.given;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.protobuf.Message;
import io.spine.annotation.Subscribe;
import io.spine.base.CommandContext;
import io.spine.base.EventContext;
import io.spine.server.command.Assign;
import io.spine.server.entity.TestEntityWithStringColumn;
import io.spine.server.procman.CommandRouted;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.test.procman.Project;
import io.spine.test.procman.ProjectId;
import io.spine.test.procman.ProjectValidatingBuilder;
import io.spine.test.procman.Task;
import io.spine.test.procman.command.AddTask;
import io.spine.test.procman.command.CreateProject;
import io.spine.test.procman.command.StartProject;
import io.spine.test.procman.event.ProjectCreated;
import io.spine.test.procman.event.ProjectStarted;
import io.spine.test.procman.event.TaskAdded;
import io.spine.testdata.Sample;

public class ProcessManagerRepositoryTestEnv {

    private ProcessManagerRepositoryTestEnv() {
    }

    public static class TestProcessManagerRepository
            extends ProcessManagerRepository<ProjectId, TestProcessManager, Project> {

        public TestProcessManagerRepository() {
            super();
        }
    }

    @SuppressWarnings("OverlyCoupledClass")
    public static class TestProcessManager
            extends ProcessManager<ProjectId, Project, ProjectValidatingBuilder>
            implements TestEntityWithStringColumn {

        /** The event message we store for inspecting in delivery tests. */
        private static final Multimap<ProjectId, Message> messagesDelivered = HashMultimap.create();

        public TestProcessManager(ProjectId id) {
            super(id);
        }

        public static boolean processed(Message eventMessage) {
            final boolean result = messagesDelivered.containsValue(eventMessage);
            return result;
        }

        public static void clearMessageDeliveryHistory() {
            messagesDelivered.clear();
        }

        private void keep(Message commandOrEventMsg) {
            messagesDelivered.put(getState().getId(), commandOrEventMsg);
        }

        @SuppressWarnings("UnusedParameters")
            /* The parameter left to show that a projection subscriber can have two parameters. */
        @Subscribe
        public void on(ProjectCreated event, EventContext ignored) {
            // Keep the event message for further inspection in tests.
            keep(event);

            handleProjectCreated(event.getProjectId());
        }

        private void handleProjectCreated(ProjectId projectId) {
            final Project newState = getState().toBuilder()
                                               .setId(projectId)
                                               .setStatus(Project.Status.CREATED)
                                               .build();
            getBuilder().mergeFrom(newState);
        }

        @Subscribe
        public void on(TaskAdded event) {
            keep(event);

            final Task task = event.getTask();
            handleTaskAdded(task);
        }

        private void handleTaskAdded(Task task) {
            final Project newState = getState().toBuilder()
                                               .addTask(task)
                                               .build();
            getBuilder().mergeFrom(newState);
        }

        @Subscribe
        public void on(ProjectStarted event) {
            keep(event);

            handleProjectStarted();
        }

        private void handleProjectStarted() {
            final Project newState = getState().toBuilder()
                                               .setStatus(Project.Status.STARTED)
                                               .build();
            getBuilder().mergeFrom(newState);
        }

        @SuppressWarnings("UnusedParameters")
            /* The parameter left to show that a command subscriber can have two parameters. */
        @Assign
        ProjectCreated handle(CreateProject command, CommandContext ignored) {
            keep(command);

            handleProjectCreated(command.getProjectId());
            final ProjectCreated event = ((ProjectCreated.Builder) Sample.builderForType(
                    ProjectCreated.class))
                    .setProjectId(command.getProjectId())
                    .build();
            return event;
        }

        @SuppressWarnings("UnusedParameters")
            /* The parameter left to show that a command subscriber can have two parameters. */
        @Assign
        TaskAdded handle(AddTask command, CommandContext ignored) {
            keep(command);

            handleTaskAdded(command.getTask());
            final TaskAdded event = ((TaskAdded.Builder) Sample.builderForType(TaskAdded.class))
                    .setProjectId(command.getProjectId())
                    .build();
            return event;
        }

        @Assign
        CommandRouted handle(StartProject command, CommandContext context) {
            keep(command);

            handleProjectStarted();
            final Message addTask = ((AddTask.Builder) Sample.builderForType(AddTask.class))
                    .setProjectId(command.getProjectId())
                    .build();
            return newRouterFor(command, context)
                    .add(addTask)
                    .routeAll();
        }

        @Override
        public String getIdString() {
            return getId().toString();
        }
    }
}

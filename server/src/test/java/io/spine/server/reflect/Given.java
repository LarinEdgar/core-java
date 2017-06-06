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

package io.spine.server.reflect;

import io.spine.test.reflect.ProjectId;
import io.spine.test.reflect.ReflectFailures.InvalidProjectName;
import io.spine.test.reflect.command.CreateProject;
import io.spine.test.reflect.command.StartProject;
import io.spine.test.reflect.event.ProjectCreated;

import static io.spine.base.Identifiers.newUuid;

class Given {

    private Given() {}

    static ProjectId newProjectId() {
        final String uuid = newUuid();
        return ProjectId.newBuilder()
                        .setId(uuid)
                        .build();
    }

    static class EventMessage {

        private static final ProjectId DUMMY_PROJECT_ID = newProjectId();
        private static final ProjectCreated PROJECT_CREATED = projectCreated(DUMMY_PROJECT_ID);

        private EventMessage() {
        }

        public static ProjectCreated projectCreated() {
            return PROJECT_CREATED;
        }

        public static ProjectCreated projectCreated(ProjectId id) {
            return ProjectCreated.newBuilder()
                                 .setProjectId(id)
                                 .build();
        }
    }

    static class CommandMessage {

        private CommandMessage() {
        }

        public static CreateProject createProject() {
            return CreateProject.newBuilder()
                                .setProjectId(newProjectId())
                                .build();
        }

        public static StartProject startProject() {
            return StartProject.newBuilder()
                               .setProjectId(newProjectId())
                               .build();
        }
    }

    static class FailureMessage {

        private static final ProjectId DUMMY_PROJECT_ID = newProjectId();
        private static final InvalidProjectName INVALID_PROJECT_NAME =
                invalidProjectName(DUMMY_PROJECT_ID);

        private FailureMessage() {
        }

        public static InvalidProjectName invalidProjectName() {
            return INVALID_PROJECT_NAME;
        }

        public static InvalidProjectName  invalidProjectName(ProjectId id) {
            final InvalidProjectName invalidProjectName =
                    InvalidProjectName.newBuilder()
                                                      .setProjectId(id)
                                                      .build();
            return invalidProjectName;
        }
    }
}

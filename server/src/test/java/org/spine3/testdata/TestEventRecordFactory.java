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

package org.spine3.testdata;

import org.spine3.base.EventContext;
import org.spine3.base.EventRecord;
import org.spine3.base.UserId;
import org.spine3.test.project.ProjectId;
import org.spine3.test.project.event.ProjectCreated;
import org.spine3.test.project.event.ProjectStarted;
import org.spine3.test.project.event.TaskAdded;

import static org.spine3.client.ClientUtil.newUserId;
import static org.spine3.protobuf.Messages.toAny;
import static org.spine3.testdata.TestAggregateIdFactory.createProjectId;
import static org.spine3.testdata.TestContextFactory.createEventContext;
import static org.spine3.testdata.TestEventFactory.*;

/**
 * The utility class which is used for creating EventRecords for tests.
 *
 * @author Mikhail Mikhaylov
 */
public class TestEventRecordFactory {

    private static final ProjectId STUB_PROJECT_ID = createProjectId("dummy_project_id_456");
    private static final UserId STUB_USER_ID = newUserId("test_user_id_147");
    private static final EventContext STUB_EVENT_CONTEXT = createEventContext();


    private TestEventRecordFactory() {}


    /**
     * Creates a new {@link EventRecord} with default properties.
     */
    public static EventRecord projectCreated() {
        return projectCreated(STUB_PROJECT_ID, STUB_EVENT_CONTEXT);
    }

    /**
     * Creates a new {@link EventRecord} with default properties.
     */
    public static EventRecord taskAdded() {
        return taskAdded(STUB_PROJECT_ID, STUB_EVENT_CONTEXT);
    }

    /**
     * Creates a new {@link EventRecord} with default properties.
     */
    public static EventRecord projectStarted() {
        return projectStarted(STUB_PROJECT_ID, STUB_EVENT_CONTEXT);
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId.
     */
    public static EventRecord projectCreated(ProjectId projectId) {
        return projectCreated(projectId, createEventContext(projectId));
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId.
     */
    public static EventRecord taskAdded(ProjectId projectId) {
        return taskAdded(projectId, createEventContext(projectId));
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId.
     */
    public static EventRecord projectStarted(ProjectId projectId) {
        return projectStarted(projectId, createEventContext(projectId));
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId and eventContext.
     */
    public static EventRecord projectCreated(ProjectId projectId, EventContext eventContext) {

        final ProjectCreated event = projectCreatedEvent(projectId);
        final EventRecord.Builder builder = EventRecord.newBuilder().setContext(eventContext).setEvent(toAny(event));
        return builder.build();
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId and eventContext.
     */
    public static EventRecord taskAdded(ProjectId projectId, EventContext eventContext) {

        final TaskAdded event = taskAddedEvent(projectId);
        final EventRecord.Builder builder = EventRecord.newBuilder().setContext(eventContext).setEvent(toAny(event));
        return builder.build();
    }

    /**
     * Creates a new {@link EventRecord} with the given projectId and eventContext.
     */
    public static EventRecord projectStarted(ProjectId projectId, EventContext eventContext) {

        final ProjectStarted event = projectStartedEvent(projectId);
        final EventRecord.Builder builder = EventRecord.newBuilder().setContext(eventContext).setEvent(toAny(event));
        return builder.build();
    }
}

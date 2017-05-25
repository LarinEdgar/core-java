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

package org.spine3.server.aggregate;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.spine3.envelope.CommandEnvelope;
import org.spine3.server.BoundedContext;
import org.spine3.server.command.Assign;
import org.spine3.server.entity.InvalidEntityStateException;
import org.spine3.test.TestActorRequestFactory;
import org.spine3.test.aggregate.ProjectId;
import org.spine3.test.aggregate.Task;
import org.spine3.test.aggregate.TaskValidatingBuilder;
import org.spine3.test.aggregate.command.AddTask;
import org.spine3.test.aggregate.command.CreateProject;
import org.spine3.test.aggregate.event.ProjectCreated;
import org.spine3.test.aggregate.event.TaskAdded;
import org.spine3.test.aggregate.user.User;
import org.spine3.test.aggregate.user.UserValidatingBuilder;
import org.spine3.testdata.Sample;
import org.spine3.validate.ConstraintViolation;
import org.spine3.validate.StringValueValidatingBuilder;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.spine3.base.Identifiers.newUuid;
import static org.spine3.server.aggregate.AggregatePart.create;
import static org.spine3.server.aggregate.AggregatePart.getConstructor;
import static org.spine3.server.aggregate.CommandTestDispatcher.dispatch;
import static org.spine3.test.Given.aggregatePartOfClass;
import static org.spine3.test.Tests.assertHasPrivateParameterlessCtor;
import static org.spine3.test.Verify.assertSize;

/**
 * @author Illia Shepilov
 */
public class AggregatePartShould {

    private static final String TASK_DESCRIPTION = "Description";
    private static final TestActorRequestFactory factory =
            TestActorRequestFactory.newInstance(AggregatePartShould.class);
    private BoundedContext boundedContext;
    private AnAggregateRoot root;
    private TaskPart taskPart;
    private TaskDescriptionPart taskDescriptionPart;
    private TaskRepository taskRepository;

    @Before
    public void setUp() {
        boundedContext = BoundedContext.newBuilder()
                                       .build();
        root = new AnAggregateRoot(boundedContext, newUuid());
        taskPart = new TaskPart(root);
        prepareAggregatePart();
        taskDescriptionPart = new TaskDescriptionPart(root);
        taskRepository = new TaskRepository(boundedContext);
        final TaskDescriptionRepository taskDescriptionRepository =
                new TaskDescriptionRepository(boundedContext);
        boundedContext.register(taskRepository);
        boundedContext.register(taskDescriptionRepository);
    }

    @Test
    public void not_accept_nulls_as_parameter_values() throws NoSuchMethodException {
        createNullPointerTester()
                .testStaticMethods(AggregatePart.class, NullPointerTester.Visibility.PACKAGE);
    }

    @Test
    public void not_accept_nulls_as_parameter_values_for_instance_methods()
            throws NoSuchMethodException {
        createNullPointerTester().testAllPublicInstanceMethods(taskPart);
    }

    @Test
    public void create_aggregate_part_entity() throws NoSuchMethodException {
        final Constructor<AnAggregatePart> constructor =
                AnAggregatePart.class.getDeclaredConstructor(AnAggregateRoot.class);
        final AggregatePart aggregatePart = create(constructor, root);
        assertNotNull(aggregatePart);
    }

    @Test
    public void return_aggregate_part_state_by_class() {
        taskRepository.store(taskPart);
        final Task task = taskDescriptionPart.getPartState(Task.class);
        assertEquals(TASK_DESCRIPTION, task.getDescription());
    }

    @Test(expected = IllegalStateException.class)
    public void throw_exception_when_aggregate_part_does_not_have_appropriate_constructor() {
        getConstructor(WrongAggregatePart.class);
    }

    @Test(expected = IllegalStateException.class)
    public void throw_exc_during_aggregate_part_creation_when_it_does_not_have_appropriate_ctor()
            throws NoSuchMethodException {
        final Constructor<WrongAggregatePart> constructor =
                WrongAggregatePart.class.getDeclaredConstructor();
        create(constructor, root);
    }

    @Test
    public void obtain_aggregate_part_constructor() {
        final Constructor<AnAggregatePart> constructor =
                getConstructor(AnAggregatePart.class);
        assertNotNull(constructor);
    }

    @Test
    public void have_TypeInfo_utility_class() {
        assertHasPrivateParameterlessCtor(AggregatePart.TypeInfo.class);
    }

    @Test
    public void throw_InvalidEntityStateException_if_state_is_invalid() {
        final User user = User.newBuilder()
                              .setFirstName("|")
                              .setLastName("|")
                              .build();
        try {
            aggregatePartOfClass(AnAggregatePart.class).withRoot(root)
                                                       .withId(getClass().getName())
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
                              .setFirstName("Firstname")
                              .setLastName("Lastname")
                              .build();
        aggregatePartOfClass(AnAggregatePart.class).withRoot(root)
                                                   .withId(getClass().getName())
                                                   .withVersion(1)
                                                   .withState(user)
                                                   .build();
    }

    private static CommandEnvelope env(Message commandMessage) {
        return CommandEnvelope.of(factory.command().create(commandMessage));
    }

    private NullPointerTester createNullPointerTester() throws NoSuchMethodException {
        final Constructor constructor =
                AnAggregateRoot.class
                        .getDeclaredConstructor(BoundedContext.class, String.class);
        final NullPointerTester tester = new NullPointerTester();
        tester.setDefault(Constructor.class, constructor)
              .setDefault(BoundedContext.class, boundedContext)
              .setDefault(AggregateRoot.class, root);
        return tester;
    }

    private void prepareAggregatePart() {
        final AddTask addTask =
                ((AddTask.Builder) Sample.builderForType(AddTask.class))
                        .setProjectId(ProjectId.getDefaultInstance())
                        .build();
        dispatch(taskPart, env(addTask));
    }

    /*
     Test environment classes
    ***************************/

    private static class AnAggregateRoot extends AggregateRoot<String> {
        protected AnAggregateRoot(BoundedContext boundedContext, String id) {
            super(boundedContext, id);
        }
    }

    private static class WrongAggregatePart extends AggregatePart<String,
            StringValue,
            StringValueValidatingBuilder,
            AnAggregateRoot> {
        @SuppressWarnings("ConstantConditions")
        // Supply a "wrong" parameters on purpose to cause the validation failure
        protected WrongAggregatePart() {
            super(null);
        }
    }

    private static class AnAggregatePart extends AggregatePart<String,
            User,
            UserValidatingBuilder,
            AnAggregateRoot> {

        protected AnAggregatePart(AnAggregateRoot root) {
            super(root);
        }
    }

    private static class TaskPart
            extends AggregatePart<String, Task, TaskValidatingBuilder, AnAggregateRoot> {

        private TaskPart(AnAggregateRoot root) {
            super(root);
        }

        @Assign
        TaskAdded handle(AddTask msg) {
            final TaskAdded result = TaskAdded.newBuilder()
                                              .build();
            //This command can be empty since we use apply method to setup aggregate part.
            return result;
        }

        @Apply
        private void apply(TaskAdded event) {
            getBuilder().setDescription(TASK_DESCRIPTION);
        }
    }

    private static class TaskDescriptionPart extends AggregatePart<String,
                                                                   StringValue,
                                                                   StringValueValidatingBuilder,
                                                                   AnAggregateRoot> {

        protected TaskDescriptionPart(AnAggregateRoot root) {
            super(root);
        }

        @Assign
        ProjectCreated handle(CreateProject msg) {
            final ProjectCreated result = ProjectCreated.newBuilder()
                                                        .setProjectId(msg.getProjectId())
                                                        .setName(msg.getName())
                                                        .build();
            return result;
        }

        @Apply
        private void apply(TaskAdded event) {
            getBuilder().setValue("Description value");
        }
    }

    private static class TaskRepository
            extends AggregatePartRepository<String, TaskPart, AnAggregateRoot> {

        private TaskRepository(BoundedContext boundedContext) {
            super(boundedContext);
        }
    }

    private static class TaskDescriptionRepository
            extends AggregatePartRepository<String, TaskDescriptionPart, AnAggregateRoot> {

        private TaskDescriptionRepository(BoundedContext boundedContext) {
            super(boundedContext);
        }
    }
}

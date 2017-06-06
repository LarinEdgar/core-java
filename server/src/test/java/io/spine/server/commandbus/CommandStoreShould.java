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

package io.spine.server.commandbus;

import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.base.Command;
import io.spine.base.CommandContext;
import io.spine.base.CommandId;
import io.spine.base.CommandStatus;
import io.spine.base.Error;
import io.spine.base.FailureThrowable;
import io.spine.envelope.CommandEnvelope;
import io.spine.protobuf.Wrapper;
import io.spine.server.command.Assign;
import io.spine.server.command.CommandHandler;
import io.spine.server.tenant.TenantAwareFunction;
import io.spine.test.TimeTests;
import io.spine.test.command.AddTask;
import io.spine.test.command.CreateProject;
import io.spine.test.command.StartProject;
import io.spine.test.command.event.ProjectCreated;
import io.spine.type.CommandClass;
import io.spine.users.TenantId;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static io.spine.base.Commands.getMessage;
import static io.spine.server.commandbus.Given.Command.addTask;
import static io.spine.server.commandbus.Given.Command.createProject;
import static io.spine.server.commandbus.Given.Command.startProject;
import static io.spine.server.commandbus.Given.CommandMessage.createProjectMessage;
import static io.spine.time.Durations2.fromMinutes;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

public abstract class CommandStoreShould extends AbstractCommandBusTestSuite {

    CommandStoreShould(boolean multitenant) {
        super(multitenant);
    }

    @Test
    public void set_command_status_to_OK_when_handler_returns() {
        commandBus.register(createProjectHandler);

        final Command command = requestFactory.command()
                                              .create(createProjectMessage());
        commandBus.post(command, responseObserver);

        final TenantId tenantId = command.getContext()
                                         .getActorContext()
                                         .getTenantId();
        final CommandId commandId = command.getId();
        final ProcessingStatus status = getStatus(commandId, tenantId);

        assertEquals(CommandStatus.OK, status.getCode());
    }

    @Test
    public void set_command_status_to_error_when_dispatcher_throws() {
        final ThrowingDispatcher dispatcher = new ThrowingDispatcher();
        commandBus.register(dispatcher);
        final Command command = requestFactory.command()
                                              .create(createProjectMessage());

        commandBus.post(command, responseObserver);

        // Check that the logging was called.
        final CommandEnvelope envelope = CommandEnvelope.of(command);
        verify(log).errorHandling(dispatcher.exception,
                                  envelope.getMessage(),
                                  envelope.getCommandId());

        // Check that the command status has the correct code,
        // and the error matches the thrown exception.
        assertHasErrorStatusWithMessage(envelope, dispatcher.exception.getMessage());
    }

    @Test
    public void set_command_status_to_failure_when_handler_throws_failure() {
        final TestFailure failure = new TestFailure();
        final Command command = givenThrowingHandler(failure);
        final CommandId commandId = command.getId();
        final Message commandMessage = getMessage(command);

        commandBus.post(command, responseObserver);

        // Check that the logging was called.
        verify(log).failureHandling(eq(failure), eq(commandMessage), eq(commandId));

        // Check that the status has the correct code,
        // and the failure matches the thrown failure.
        final TenantId tenantId = command.getContext()
                                         .getActorContext()
                                         .getTenantId();
        final ProcessingStatus status = getStatus(commandId, tenantId);

        assertEquals(CommandStatus.FAILURE, status.getCode());
        assertEquals(failure.toFailure(command)
                            .getMessage(), status.getFailure()
                                                 .getMessage());
    }

    private ProcessingStatus getStatus(CommandId commandId, final TenantId tenantId) {
        final TenantAwareFunction<CommandId, ProcessingStatus> func =
                new TenantAwareFunction<CommandId, ProcessingStatus>(tenantId) {
                    @Nullable
                    @Override
                    public ProcessingStatus apply(@Nullable CommandId input) {
                        return commandStore.getStatus(checkNotNull(input));
                    }
                };
        return func.execute(commandId);
    }

    @Test
    public void set_command_status_to_error_when_handler_throws_exception() {
        final RuntimeException exception = new IllegalStateException("handler throws");
        final Command command = givenThrowingHandler(exception);
        final CommandEnvelope envelope = CommandEnvelope.of(command);

        commandBus.post(command, responseObserver);

        // Check that the logging was called.
        verify(log).errorHandling(eq(exception),
                                  eq(envelope.getMessage()),
                                  eq(envelope.getCommandId()));

        final String errorMessage = exception.getMessage();
        assertHasErrorStatusWithMessage(envelope, errorMessage);
    }

    /**
     * Checks that the command status has the correct code, and the stored error message matches the
     * passed message.
     *
     * <p>The check is performed as a tenant-aware function using the tenant ID from the command.
     */
    private void assertHasErrorStatusWithMessage(CommandEnvelope commandEnvelope,
                                                 String errorMessage) {
        final ProcessingStatus status = getProcessingStatus(commandEnvelope);
        assertEquals(CommandStatus.ERROR, status.getCode());
        assertEquals(errorMessage, status.getError()
                                         .getMessage());
    }

    private ProcessingStatus getProcessingStatus(CommandEnvelope commandEnvelope) {
        final TenantId tenantId = commandEnvelope.getCommandContext()
                                                 .getActorContext()
                                                 .getTenantId();
        final TenantAwareFunction<CommandId, ProcessingStatus> func =
                new TenantAwareFunction<CommandId, ProcessingStatus>(tenantId) {
                    @Override
                    public ProcessingStatus apply(@Nullable CommandId input) {
                        return commandStore.getStatus(checkNotNull(input));
                    }
                };
        final ProcessingStatus result = func.execute(commandEnvelope.getCommandId());
        return result;
    }

    @Test
    public void set_command_status_to_error_when_handler_throws_unknown_Throwable()
            throws TestFailure, TestThrowable {
        final Throwable throwable = new TestThrowable("Unexpected Throwable");
        final Command command = givenThrowingHandler(throwable);
        final CommandEnvelope envelope = CommandEnvelope.of(command);

        commandBus.post(command, responseObserver);

        // Check that the logging was called.
        verify(log).errorHandlingUnknown(eq(throwable),
                                         eq(envelope.getMessage()),
                                         eq(envelope.getCommandId()));

        // Check that the status and message.
        assertHasErrorStatusWithMessage(envelope, throwable.getMessage());
    }

    @Test
    public void set_expired_scheduled_command_status_to_error_if_time_to_post_them_passed() {
        final List<Command> commands = newArrayList(createProject(),
                                                    addTask(),
                                                    startProject());
        final Duration delay = fromMinutes(5);
        final Timestamp schedulingTime = TimeTests.Past.minutesAgo(10); // time to post passed
        storeAsScheduled(commands, delay, schedulingTime);

        commandBus.rescheduleCommands();

        for (Command cmd : commands) {
            final CommandEnvelope envelope = CommandEnvelope.of(cmd);
            final Message msg = envelope.getMessage();
            final CommandId id = envelope.getCommandId();

            // Check the expired status error was set.
            final ProcessingStatus status = getProcessingStatus(envelope);

            // Check that the logging was called.
            verify(log).errorExpiredCommand(msg, id);

            final Error expected = CommandExpiredException.commandExpiredError(msg);
            assertEquals(expected, status.getError());
        }
    }

    /**
     * A stub handler that throws passed `Throwable` in the command handler method.
     *
     * @see #set_command_status_to_failure_when_handler_throws_failure
     * @see #set_command_status_to_error_when_handler_throws_exception
     * @see #set_command_status_to_error_when_handler_throws_unknown_Throwable
     */
    private class ThrowingCreateProjectHandler extends CommandHandler {

        @Nonnull
        private final Throwable throwable;

        protected ThrowingCreateProjectHandler(@Nonnull Throwable throwable) {
            super(eventBus);
            this.throwable = throwable;
        }

        @Assign
        @SuppressWarnings({"unused", "ProhibitedExceptionThrown"})
            // Throwing is the purpose of this method.
        ProjectCreated handle(CreateProject msg, CommandContext context) throws Throwable {
            throw throwable;
        }
    }

    private <E extends Throwable> Command givenThrowingHandler(E throwable) {
        final CommandHandler handler = new ThrowingCreateProjectHandler(throwable);
        commandBus.register(handler);
        final CreateProject msg = createProjectMessage();
        final Command command = requestFactory.command()
                                              .create(msg);
        return command;
    }

    /*
     * Throwables
     ********************/

    private static class TestFailure extends FailureThrowable {
        private static final long serialVersionUID = 1L;

        private TestFailure() {
            super(Wrapper.forString(TestFailure.class.getName()));
        }
    }

    @SuppressWarnings("serial")
    private static class TestThrowable extends Throwable {
        private TestThrowable(String message) {
            super(message);
        }
    }

    private static class ThrowingDispatcher implements CommandDispatcher {

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        private final RuntimeException exception = new RuntimeException("Dispatching exception.");

        @Override
        public Set<CommandClass> getMessageClasses() {
            return CommandClass.setOf(CreateProject.class, StartProject.class, AddTask.class);
        }

        @Override
        public void dispatch(CommandEnvelope envelope) {
            throw exception;
        }
    }
}

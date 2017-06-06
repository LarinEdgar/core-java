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

package io.spine.server.procman;

import com.google.common.collect.Iterators;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.base.ActorContext;
import io.spine.base.Command;
import io.spine.base.CommandContext;
import io.spine.base.Response;
import io.spine.client.ActorRequestFactory;
import io.spine.client.CommandFactory;
import io.spine.server.commandbus.CommandBus;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for command routers.
 *
 * @author Alexander Yevsyukov
 */
abstract class AbstractCommandRouter<T extends AbstractCommandRouter> {

    /**
     * The command that we route.
     */
    private final Command source;

    /**
     * The {@code CommandBus} to which we post commands.
     */
    private final CommandBus commandBus;

    /**
     * Command messages for commands that we post during routing.
     */
    private final Queue<Message> queue;

    /**
     * The future for waiting until the {@link CommandBus#post posting of the command} completes.
     */
    private final SettableFuture<Void> finishFuture = SettableFuture.create();

    /**
     * The observer for posting commands.
     */
    private final StreamObserver<Response> responseObserver = newResponseObserver(finishFuture);

    AbstractCommandRouter(CommandBus commandBus,
                          Message commandMessage,
                          CommandContext commandContext) {
        this.commandBus = checkNotNull(commandBus);
        checkNotNull(commandMessage);
        checkNotNull(commandContext);
        this.source = asCommand(commandMessage, commandContext);
        this.queue = Queues.newConcurrentLinkedQueue();
    }

    /**
     * Returns typed reference to {@code this}.
     *
     * <p>This method provides return type covariance in fluent API methods.
     *
     * @see #add(Message)
     * @see #addAll(Iterable)
     */
    protected abstract T getThis();

    /**
     * Obtains the source command to be routed.
     */
    protected Command getSource() {
        return source;
    }

    /**
     * Adds {@code commandMessage} to be routed.
     */
    public T add(Message commandMessage) {
        queue.add(commandMessage);
        return getThis();
    }

    /**
     * Adds all command messages from the passed iterable.
     */
    protected T addAll(Iterable<Message> iterable) {
        for (Message message : iterable) {
            add(message);
        }
        return getThis();
    }

    /**
     * Tests whether the queue of command messages to be routed is empty.
     *
     * @return {@code true} if the queue is empty, {@code false} otherwise
     */
    protected boolean hasNext() {
        final boolean result = !queue.isEmpty();
        return result;
    }

    protected Iterator<Message> commandMessages() {
        return Iterators.unmodifiableIterator(queue.iterator());
    }

    /**
     * Creates a new command from the passed message and posts it
     * to the {@code CommandBus}.
     *
     * <p>This method waits till the posting of the command is finished.
     *
     * @param message the command message for the new command
     * @return the created and posted {@code Command}
     */
    protected Command route(Message message) {
        final Command command = produceCommand(message);
        commandBus.post(command, responseObserver);
        // Wait till the call is completed.
        try {
            finishFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
        return command;
    }

    private Command produceCommand(Message commandMessage) {
        final CommandContext sourceContext = source.getContext();
        final CommandFactory commandFactory = commandFactory(sourceContext);
        final Command result = commandFactory
                .createBasedOnContext(commandMessage, sourceContext);
        return result;
    }

    /**
     * Gets and removes the next command message from the queue.
     *
     * @return the command message
     * @throws NoSuchElementException if the queue is already empty
     */
    protected Message next() throws NoSuchElementException {
        final Message result = queue.remove();
        return result;
    }

    private static StreamObserver<Response> newResponseObserver(
            final SettableFuture<Void> finishFuture) {
        return new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                // Do nothing. It's just a confirmation of successful post to Command Bus.
            }

            @Override
            public void onError(Throwable throwable) {
                finishFuture.setException(throwable);
            }

            @Override
            public void onCompleted() {
                finishFuture.set(null);
            }
        };
    }

    /**
     * Creates a {@code CommandFactory} using the {@linkplain io.spine.users.UserId actor},
     * {@linkplain io.spine.users.TenantId tenant ID} and {@linkplain io.spine.time.ZoneOffset
     * zone offset} from the given command context.
     */
    private static CommandFactory commandFactory(CommandContext sourceContext) {
        final ActorContext actorContext = sourceContext.getActorContext();
        final ActorRequestFactory factory =
                ActorRequestFactory.newBuilder()
                                   .setActor(actorContext.getActor())
                                   .setTenantId(actorContext.getTenantId())
                                   .setZoneOffset(actorContext.getZoneOffset())
                                   .build();
        return factory.command();
    }

    private static Command asCommand(Message message, CommandContext context) {
        final Command command = commandFactory(context).createWithContext(message, context);
        return command;
    }
}

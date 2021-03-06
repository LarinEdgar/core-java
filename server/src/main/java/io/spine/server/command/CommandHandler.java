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

package io.spine.server.command;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.core.CommandClass;
import io.spine.core.CommandEnvelope;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.event.EventBus;
import io.spine.server.reflect.CommandHandlerMethod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static io.spine.protobuf.TypeConverter.toMessage;

/**
 * The abstract base for non-aggregate classes that expose command handling methods
 * and post their results to {@link EventBus}.
 *
 * <p>A command handler is responsible for:
 * <ol>
 *     <li>Changing the state of the business model in response to a command.
 *     This is done by one of the command handling methods to which the handler dispatches
 *     the command.
 *     <li>Producing corresponding events.
 *     <li>Posting events to {@code EventBus}.
 * </ol>
 *
 * <p>Event messages are returned as values of command handling methods.
 *
 * <p>A command handler does not have own state. So the state of the business
 * model it changes is external to it. Even though such a behaviour may be needed in
 * some rare cases, using {@linkplain io.spine.server.aggregate.Aggregate aggregates}
 * is a preferred way of handling commands.
 *
 * <p>This class implements {@code CommandDispatcher} dispatching messages
 * to methods declared in the derived classes.
 *
 * @author Alexander Yevsyukov
 * @see io.spine.server.aggregate.Aggregate Aggregate
 * @see CommandDispatcher
 */
public abstract class CommandHandler implements CommandDispatcher<String> {

    /**
     * The {@code EventBut} to which the handler posts events it produces.
     */
    private final EventBus eventBus;

    /**
     * Fully qualified name of the class wrapped into {@code Any}.
     */
    private final Any producerId;

    /**
     * Cached set of the command classes this handler can handle.
     */
    @Nullable
    private Set<CommandClass> commandClasses;

    /**
     * Creates a new instance of the command handler.
     *
     * @param eventBus the {@code EventBus} to post events generated by this handler
     */
    protected CommandHandler(EventBus eventBus) {
        this.eventBus = eventBus;
        final StringValue className = toMessage(getClass().getName());
        this.producerId = AnyPacker.pack(className);
    }

    /**
     * Obtains identity string of the handler.
     *
     * <p>Default implementation returns the result of {@link #toString()}.
     *
     * @return the string with the handler identity
     */
    protected String getId() {
        return toString();
    }

    /**
     * Dispatches the command to the handler method and
     * posts resulting events to the {@link EventBus}.
     *
     * @param envelope the command to dispatch
     * @return the handler identity as the result of {@link #toString()}
     * @throws IllegalStateException if an exception occurred during command dispatching
     *                               with this exception as the cause
     */
    @Override
    public String dispatch(CommandEnvelope envelope) {
        final List<? extends Message> eventMessages =
                CommandHandlerMethod.invokeFor(this,
                                               envelope.getMessage(),
                                               envelope.getCommandContext());
        final List<Event> events = toEvents(eventMessages, envelope);
        postEvents(events);
        return getId();
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField") // OK as we return immutable impl.
    @Override
    public Set<CommandClass> getMessageClasses() {
        if (commandClasses == null) {
            commandClasses = ImmutableSet.copyOf(
                    CommandHandlerMethod.inspect(getClass()));
        }
        return commandClasses;
    }

    private List<Event> toEvents(List<? extends Message> eventMessages, CommandEnvelope ce) {
        return CommandHandlerMethod.toEvents(producerId, null, eventMessages, ce);
    }

    /** Posts passed events to {@link EventBus}. */
    private void postEvents(Iterable<Event> events) {
        eventBus.post(events);
    }

    /**
     * Indicates whether some another command handler is "equal to" this one.
     *
     * <p>Two command handlers are equal if they handle the same set of commands.
     *
     * @return if the passed {@code CommandHandler} handles the same
     * set of command classes.
     * @see #getMessageClasses()
     */
    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        }
        if (otherObj == null ||
            getClass() != otherObj.getClass()) {
            return false;
        }
        final CommandHandler otherHandler = (CommandHandler) otherObj;
        final boolean equals = getMessageClasses().equals(otherHandler.getMessageClasses());
        return equals;
    }

    @Override
    public int hashCode() {
        final int result = getMessageClasses().hashCode();
        return result;
    }
}

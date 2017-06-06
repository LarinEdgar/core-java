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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.base.CommandContext;
import io.spine.base.Event;
import io.spine.base.Version;
import io.spine.envelope.CommandEnvelope;
import io.spine.protobuf.AnyPacker;
import io.spine.server.command.Assign;
import io.spine.server.command.EventFactory;
import io.spine.type.CommandClass;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * The wrapper for a command handler method.
 *
 * @author Alexander Yevsyukov
 */
public class CommandHandlerMethod extends HandlerMethod<CommandContext> {

    /** The instance of the predicate to filter command handler methods of a class. */
    private static final MethodPredicate PREDICATE = new FilterPredicate();

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param method subscriber method
     */
    private CommandHandlerMethod(Method method) {
        super(method);
    }

    /**
     * Obtains handler method for the command message.
     *
     * @param cls the class that handles the message
     * @param commandMessage the message
     * @return handler method
     * @throws IllegalStateException if the passed class does not handle messages of this class
     */
    private static CommandHandlerMethod forMessage(Class<?> cls, Message commandMessage)  {
        final Class<? extends Message> commandClass = commandMessage.getClass();
        final CommandHandlerMethod method = MethodRegistry.getInstance()
                                                          .get(cls, commandClass, factory());
        if (method == null) {
            throw missingCommandHandler(cls, commandClass);
        }
        return method;
    }

    static CommandHandlerMethod from(Method method) {
        return new CommandHandlerMethod(method);
    }

    private static IllegalStateException missingCommandHandler(Class<?> cls,
                        Class<? extends Message> commandClass) {
        throw newIllegalStateException("No handler for the command class %s found in the class %s.",
                                        commandClass.getName(), cls.getName());
    }

    static MethodPredicate predicate() {
        return PREDICATE;
    }

    /**
     * Returns the set of the command classes handled by the passed class.
     *
     * @param cls the class of objects that handle commands
     * @return immutable set of command classes
     */
    @CheckReturnValue
    public static Set<CommandClass> getCommandClasses(Class<?> cls) {
        final Set<CommandClass> result = CommandClass.setOf(
                getHandledMessageClasses(cls, predicate()));
        return result;
    }

    /**
     * Ensures that the passed instance of {@code Message} is not an {@code Any},
     * and unwraps the command message if {@code Any} is passed.
     */
    private static Message ensureCommandMessage(Message msgOrAny) {
        Message commandMessage;
        if (msgOrAny instanceof Any) {
            /* It looks that we're getting the result of `command.getMessage()`
               because the calling code did not bother to unwrap it.
               Extract the wrapped message (instead of treating this as an error).
               There may be many occasions of such a call especially from the
               testing code. */
            final Any any = (Any) msgOrAny;
            commandMessage = AnyPacker.unpack(any);
        } else {
            commandMessage = msgOrAny;
        }
        return commandMessage;
    }

    /**
     * Invokes the handler method in the passed object.
     *
     * @return the list of events produced by the handler method
     */
    public static List<? extends Message> invokeHandler(Object object,
                                                        Message command,
                                                        CommandContext context) {
        checkNotNull(object);
        checkNotNull(command);
        checkNotNull(context);

        final Message commandMessage = ensureCommandMessage(command);

        try {
            final CommandHandlerMethod method = forMessage(object.getClass(),
                                                           commandMessage);
            final List<? extends Message> eventMessages = method.invoke(object,
                                                                        commandMessage,
                                                                        context);
            return eventMessages;
        } catch (InvocationTargetException e) {
            throw illegalStateWithCauseOf(e);
        }
    }

    public static List<Event> toEvents(final Any producerId,
                                       @Nullable final Version version,
                                       final List<? extends Message> eventMessages,
                                       final CommandEnvelope envelope) {
        checkNotNull(producerId);
        checkNotNull(eventMessages);
        checkNotNull(envelope);

        final EventFactory eventFactory = EventFactory.newBuilder()
                .setCommandId(envelope.getCommandId())
                .setProducerId(producerId)
                .setMaxEventCount(eventMessages.size())
                .setCommandContext(envelope.getCommandContext())
                .build();

        return Lists.transform(eventMessages, new Function<Message, Event>() {
            @Override
            public Event apply(@Nullable Message eventMessage) {
                if (eventMessage == null) {
                    return Event.getDefaultInstance();
                }
                final Event result = eventFactory.createEvent(eventMessage, version);
                return result;
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @return the list of event messages (or an empty list if the handler returns nothing)
     */
    @Override
    public <R> R invoke(Object target, Message message, CommandContext context)
            throws InvocationTargetException {
        final R handlingResult = super.invoke(target, message, context);

        final List<? extends Message> events = toList(handlingResult);
        // The list of event messages/records is the return type expected.
        @SuppressWarnings("unchecked")
        final R result = (R) events;
        return result;
    }

    /**
     * Casts a command handling result to a list of event messages.
     *
     * @param output the command handler method return value.
     *               Could be a {@link Message}, a list of messages, or {@code null}.
     * @return the list of event messages or an empty list if {@code null} is passed
     */
    private static <R> List<? extends Message> toList(@Nullable R output) {
        if (output == null) {
            return emptyList();
        }
        if (output instanceof List) {
            // Cast to the list of messages as it is the one of the return types
            // we expect by methods we call.
            @SuppressWarnings("unchecked")
            final List<? extends Message> result = (List<? extends Message>) output;
            return result;
        } else {
            // Another type of result is single event message (as Message).
            final List<Message> result = singletonList((Message) output);
            return result;
        }
    }

    private static HandlerMethod.Factory<CommandHandlerMethod> factory() {
        return Factory.getInstance();
    }

    /**
     * The factory for filtering methods that match {@code CommandHandlerMethod} specification.
     */
    private static class Factory implements HandlerMethod.Factory<CommandHandlerMethod> {

        @Override
        public Class<CommandHandlerMethod> getMethodClass() {
            return CommandHandlerMethod.class;
        }

        @Override
        public CommandHandlerMethod create(Method method) {
            return from(method);
        }

        @Override
        public Predicate<Method> getPredicate() {
            return predicate();
        }

        @Override
        public void checkAccessModifier(Method method) {
            final int modifiers = method.getModifiers();
            final boolean nonDefaultModifier =
                    Modifier.isPublic(modifiers)
                    || Modifier.isProtected(modifiers)
                    || Modifier.isPrivate(modifiers);
            if (nonDefaultModifier) {
                warnOnWrongModifier(
                   "Command handler method {} should be declared with the default access modifier.",
                   method);
            }
        }

        private enum Singleton {
            INSTANCE;
            @SuppressWarnings("NonSerializableFieldInSerializableClass")
            private final Factory value = new Factory();
        }

        private static Factory getInstance() {
            return Singleton.INSTANCE.value;
        }
    }

    /**
     * The predicate class that allows to filter command handling methods.
     *
     * <p>See {@link Assign} annotation for more info about such methods.
     */
    private static class FilterPredicate extends HandlerMethodPredicate<CommandContext> {

        private FilterPredicate() {
            super(Assign.class, CommandContext.class);
        }

        @Override
        protected boolean verifyReturnType(Method method) {
            final Class<?> returnType = method.getReturnType();
            final boolean isMessage = Message.class.isAssignableFrom(returnType);
            if (isMessage) {
                return true;
            }
            final boolean isList = List.class.isAssignableFrom(returnType);
            return isList;
        }
    }
}

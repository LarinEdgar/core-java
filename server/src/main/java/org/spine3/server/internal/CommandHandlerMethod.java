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

package org.spine3.server.internal;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spine3.Internal;
import org.spine3.base.CommandContext;
import org.spine3.internal.MessageHandlerMethod;
import org.spine3.server.Assign;
import org.spine3.server.CommandHandler;
import org.spine3.server.util.MethodMap;
import org.spine3.server.util.Methods;
import org.spine3.type.CommandClass;

import javax.annotation.CheckReturnValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * The wrapper for a command handler method.
 *
 * @author Alexander Yevsyukov
 */
@Internal
public abstract class CommandHandlerMethod extends MessageHandlerMethod<Object, CommandContext> {

    /**
     * A command must be the first parameter of a handling method.
     */
    private static final int MESSAGE_PARAM_INDEX = 0;

    /**
     * A {@code CommandContext} must be the second parameter of the handling method.
     */
    private static final int COMMAND_CONTEXT_PARAM_INDEX = 1;

    /**
     * A command handling method accepts two parameters.
     */
    private static final int COMMAND_HANDLER_PARAM_COUNT = 2;

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param target object to which the method applies
     * @param method subscriber method
     */
    protected CommandHandlerMethod(Object target, Method method) {
        super(target, method);
    }

    public static boolean isAnnotatedCorrectly(Method method) {
        final boolean isAnnotated = method.isAnnotationPresent(Assign.class);
        return isAnnotated;
    }

    public static boolean acceptsCorrectParams(Method method) {
        final Class<?>[] paramTypes = method.getParameterTypes();
        final boolean paramCountIsCorrect = paramTypes.length == COMMAND_HANDLER_PARAM_COUNT;
        if (!paramCountIsCorrect) {
            return false;
        }
        final boolean acceptsCorrectParams =
                Message.class.isAssignableFrom(paramTypes[MESSAGE_PARAM_INDEX]) &&
                        CommandContext.class.equals(paramTypes[COMMAND_CONTEXT_PARAM_INDEX]);
        return acceptsCorrectParams;
    }

    public static boolean returnsMessageListOrVoid(Method method) {
        final Class<?> returnType = method.getReturnType();

        if (Message.class.isAssignableFrom(returnType)) {
            return true;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return true;
        }
        //noinspection RedundantIfStatement
        if (Void.TYPE.equals(returnType)) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return the list of event messages/records (or an empty list if the handler returns nothing)
     */
    @Override
    public <R> R invoke(Message message, CommandContext context) throws InvocationTargetException {
        final R handlingResult = super.invoke(message, context);

        final List<? extends Message> events = commandHandlingResultToEvents(handlingResult);
        // The list of event messages/records is the return type expected.
        @SuppressWarnings("unchecked")
        final R result = (R) events;
        return result;
    }

    /**
     * Casts a command handling result to a list of event messages.
     *
     * @param handlingResult the command handler method return value. Could be a {@link Message} or a list of messages.
     * @return the list of events as messages
     */
    protected <R> List<? extends Message> commandHandlingResultToEvents(R handlingResult) {
        final Class<?> resultClass = handlingResult.getClass();
        if (List.class.isAssignableFrom(resultClass)) {
            // Cast to the list of messages as it is the one of the return types we expect by methods we call.
            @SuppressWarnings("unchecked")
            final List<? extends Message> result = (List<? extends Message>) handlingResult;
            return result;
        } else if (Void.class.equals(resultClass)) {
            return Collections.emptyList();
        } else {
            // Another type of result is single event (as Message).
            final List<Message> result = singletonList((Message) handlingResult);
            return result;
        }
    }

    /**
     * Returns a map of the command handler methods from the passed instance.
     *
     * @param object the object that keeps command handler methods
     * @return immutable map
     */
    @Internal
    @CheckReturnValue
    public static Map<CommandClass, CommandHandlerMethod> scan(CommandHandler object) {
        final ImmutableMap.Builder<CommandClass, CommandHandlerMethod> result = ImmutableMap.builder();

        final Map<CommandClass, CommandHandlerMethod> regularHandlers = getHandlers(object);
        result.putAll(regularHandlers);

        return result.build();
    }

    private static Map<CommandClass, CommandHandlerMethod> getHandlers(CommandHandler object) {
        final ImmutableMap.Builder<CommandClass, CommandHandlerMethod> result = ImmutableMap.builder();

        final Predicate<Method> isHandlerPredicate = object.getHandlerMethodPredicate();
        final MethodMap handlers = new MethodMap(object.getClass(), isHandlerPredicate);
        checkModifiers(handlers.values());
        for (Map.Entry<Class<? extends Message>, Method> entry : handlers.entrySet()) {
            final CommandClass commandClass = CommandClass.of(entry.getKey());
            final CommandHandlerMethod handler = object.createMethod(entry.getValue());
            result.put(commandClass, handler);
        }
        return result.build();
    }

    /**
     * Verifiers modifiers in the methods in the passed map to be 'public'.
     *
     * <p>Logs warning for the methods with a non-public modifier.
     *
     * @param methods the map of methods to check
     */
    public static void checkModifiers(Iterable<Method> methods) {
        for (Method method : methods) {
            final boolean isPublic = Modifier.isPublic(method.getModifiers());
            if (!isPublic) {
                log().warn(String.format("Command handler %s must be declared 'public'.",
                        Methods.getFullMethodName(method)));
            }
        }
    }

    private enum LogSingleton {
        INSTANCE;
        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Logger value = LoggerFactory.getLogger(CommandHandlerMethod.class);
    }

    private static Logger log() {
        return LogSingleton.INSTANCE.value;
    }
}
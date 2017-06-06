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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.base.Command;
import io.spine.base.CommandContext;
import io.spine.base.Commands;
import io.spine.envelope.CommandEnvelope;
import io.spine.protobuf.AnyPacker;
import io.spine.protobuf.Wrapper;
import io.spine.server.BoundedContext;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.test.TestActorRequestFactory;
import io.spine.type.CommandClass;
import org.junit.Before;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static io.spine.base.Commands.sameActorAndTenant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexander Yevsyukov
 */
public abstract class AbstractCommandRouterShould<T extends AbstractCommandRouter> {

    private final TestActorRequestFactory requestFactory =
            TestActorRequestFactory.newInstance(getClass());

    /** The command message we route. */
    private Message sourceMessage;

    /** The context of the command that we route. */
    private CommandContext sourceContext;

    /** The object we test. */
    private T router;

    /** Command messages to be sent. */
    private final List<Message> messages = ImmutableList.<Message>of(
            Wrapper.forString("uno"),
            Wrapper.forString("dos"),
            Wrapper.forString("tres"),
            Wrapper.forString("cuatro")
    );

    abstract T createRouter(CommandBus commandBus, Message sourceMessage, CommandContext commandContext);

    T router() {
        return router;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField") // OK as we return immutable impl.
    List<Message> messages() {
        return messages;
    }

    @Before
    public void setUp() {
        final BoundedContext boundedContext = BoundedContext.newBuilder()
                                                            .build();
        final CommandBus commandBus = boundedContext.getCommandBus();

        // Register dispatcher for `StringValue` message type.
        // Otherwise we won't be able to post.
        commandBus.register(new CommandDispatcher() {
            @Override
            public Set<CommandClass> getMessageClasses() {
                return CommandClass.setOf(StringValue.class);
            }

            @Override
            public void dispatch(CommandEnvelope envelope) {
                // Do nothing.
            }
        });

        sourceMessage = Wrapper.forString(getClass().getSimpleName());
        sourceContext = requestFactory.createCommandContext();

        router = createRouter(commandBus, sourceMessage, sourceContext);
        router.addAll(messages);
    }

    /**
     * Asserts that the {@code CommandRouted} instance has correct source command.
     */
    protected void assertSource(CommandRouted commandRouted) {
        // Check that the source command is stored.
        final Command source = commandRouted.getSource();
        assertEquals(sourceMessage, Commands.getMessage(source));
        assertEquals(sourceContext, source.getContext());
    }

    /**
     * Asserts that the produced command context has correct fields.
     */
    protected void assertActorAndTenant(Command produced) {
        assertTrue(sameActorAndTenant(sourceContext, produced.getContext()));
    }

    static List<StringValue> unpackAll(List<Any> anyList) {
        return Lists.transform(anyList, new Function<Any, StringValue>() {
            @Nullable
            @Override
            public StringValue apply(@Nullable Any input) {
                if (input == null) {
                    return null;
                }
                return AnyPacker.unpack(input);
            }
        });
    }
}

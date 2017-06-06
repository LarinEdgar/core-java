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

package io.spine.envelope;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Message;
import io.spine.base.Command;
import io.spine.test.TestActorRequestFactory;
import io.spine.time.Time;
import io.spine.type.CommandClass;
import org.junit.Before;
import org.junit.Test;

import static io.spine.test.Tests.newUuidValue;
import static io.spine.validate.Validate.isDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Alexander Yevsyukov
 */
public class CommandEnvelopeShould {

    private final TestActorRequestFactory requestFactory =
            TestActorRequestFactory.newInstance(CommandEnvelopeShould.class);

    private Command command;
    private CommandEnvelope envelope;

    @Before
    public void setUp() {
        command = requestFactory.command().create(newUuidValue());
        envelope = CommandEnvelope.of(command);
    }

    @Test
    public void pass_null_tolerance_check() {
        new NullPointerTester()
                .setDefault(Command.class, Command.getDefaultInstance())
                .testAllPublicStaticMethods(CommandEnvelope.class);
    }

    @Test
    public void obtain_command() {
        assertEquals(command, envelope.getCommand());
    }

    @Test
    public void obtain_command_context() {
        assertEquals(command.getContext(), envelope.getCommandContext());
    }

    @Test
    public void extract_command_message() {
        final Message commandMessage = envelope.getMessage();
        assertNotNull(commandMessage);
        assertFalse(isDefault(commandMessage));
    }

    @Test
    public void obtain_command_class() {
        assertEquals(CommandClass.of(command), envelope.getMessageClass());
    }

    @Test
    public void obtain_command_id() {
        assertEquals(command.getId(), envelope.getCommandId());
    }

    @Test
    public void have_equals() {
        final Command anotherCommand = requestFactory.command()
                                                     .create(Time.getCurrentTime());

        new EqualsTester().addEqualityGroup(envelope)
                          .addEqualityGroup(CommandEnvelope.of(anotherCommand))
                          .testEquals();
    }

    @Test
    public void have_hashCode() {
        assertNotEquals(System.identityHashCode(envelope), envelope.hashCode());
    }
}

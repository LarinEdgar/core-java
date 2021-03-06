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

import io.spine.core.CommandClass;
import io.spine.core.CommandEnvelope;
import io.spine.server.bus.UnicastDispatcher;

import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Delivers commands to their handlers.
 *
 * <p>A dispatcher can deliver more than one class of commands.
 *
 * @author Alexander Yevsyukov
 */
public interface CommandDispatcher<I> extends UnicastDispatcher<CommandClass, CommandEnvelope, I> {

    /**
     * Utility class for reporting command dispatching errors.
     */
    class Error {
        private Error() {
            // Prevent instantiation of this utility class.
        }

        /**
         * Throws {@link IllegalArgumentException} to report unexpected command class.
         *
         * @param cls the command class which name will be used in the exception message
         * @return nothing ever
         * @throws IllegalArgumentException always
         */
        public static IllegalArgumentException unexpectedCommandEncountered(CommandClass cls)
                throws IllegalArgumentException {
            final String eventClassName = cls.value()
                                             .getName();
            throw newIllegalArgumentException("Unexpected command of class: %s", eventClassName);
        }
    }
}

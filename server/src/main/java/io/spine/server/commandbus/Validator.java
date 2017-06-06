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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.base.CommandContext;
import io.spine.base.CommandId;
import io.spine.envelope.CommandEnvelope;
import io.spine.server.entity.idfunc.GetTargetIdFromCommand;
import io.spine.validate.ConstraintViolation;
import io.spine.validate.MessageValidator;

import java.util.List;

import static io.spine.base.Identifiers.EMPTY_ID;
import static io.spine.base.Identifiers.idToString;
import static io.spine.validate.Validate.isDefault;

/**
 * The validator for {@code Command} instances.
 *
 * @author Alexander Yevsyukov
 */
class Validator {

    private static final String COMMAND_TARGET_ENTITY_ID_CANNOT_BE_EMPTY_OR_BLANK =
            "Command target entity ID cannot be empty or blank.";

    private Validator() {
    }

    /** Returns a validator instance. */
    static Validator getInstance() {
        return Singleton.INSTANCE.value;
    }

    /**
     * Validates a command checking that its required fields are valid and
     * validates a command message according to Spine custom protobuf options.
     *
     * @param envelope a command to validate
     * @return constraint violations found
     */
    List<ConstraintViolation> validate(CommandEnvelope envelope) {
        final ImmutableList.Builder<ConstraintViolation> result = ImmutableList.builder();
        final Message message = envelope.getMessage();
        final CommandContext context = envelope.getCommandContext();
        final CommandId id = envelope.getCommandId();
        validateCommandId(id, result);
        validateMessage(message, result);
        validateContext(context, result);
        validateTargetId(message, result);
        return result.build();
    }

    private static void validateMessage(Message message,
                                        ImmutableList.Builder<ConstraintViolation> result) {
        if (isDefault(message)) {
            result.add(newConstraintViolation("Non-default command message must be set."));
        }
        final List<ConstraintViolation> messageViolations = MessageValidator.newInstance()
                                                                            .validate(message);
        result.addAll(messageViolations);
    }

    private static void validateContext(CommandContext context,
                                        ImmutableList.Builder<ConstraintViolation> result) {
        if (isDefault(context)) {
            result.add(newConstraintViolation("Non-default command context must be set."));
        }
    }

    private static void validateCommandId(CommandId id,
                                          ImmutableList.Builder<ConstraintViolation> result) {
        final String commandId = idToString(id);
        if (commandId.equals(EMPTY_ID)) {
            result.add(newConstraintViolation("Command ID cannot be empty or blank."));
        }
    }

    private static void validateTargetId(Message message,
                                         ImmutableList.Builder<ConstraintViolation> result) {
        final Optional targetId = GetTargetIdFromCommand.asOptional(message);
        if (targetId.isPresent()) {
            final String targetIdString = idToString(targetId.get());
            if (targetIdString.equals(EMPTY_ID)) {
                final ConstraintViolation violation = newConstraintViolation(
                        COMMAND_TARGET_ENTITY_ID_CANNOT_BE_EMPTY_OR_BLANK);
                result.add(violation);
            }
        }
    }

    private static ConstraintViolation newConstraintViolation(String msgFormat) {
        final ConstraintViolation violation = ConstraintViolation.newBuilder()
                .setMsgFormat(msgFormat)
                .build();
        return violation;
    }

    private enum Singleton {
        INSTANCE;
        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Validator value = new Validator();
    }
}

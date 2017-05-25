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

import com.google.protobuf.Message;
import org.spine3.envelope.CommandEnvelope;

import java.util.List;

/**
 * A test utility to dispatch the commands to an Aggregate in test purposes.
 *
 * @author Alex Tymchenko
 */
class CommandTestDispatcher {

    private CommandTestDispatcher() {
    }

    /**
     * Dispatches the given {@code Command} envelope and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of {@code Event} messages.
     */
    static List<? extends Message> dispatch(Aggregate<?, ?, ?> aggregate,
                                            CommandEnvelope envelope) {
        final List<? extends Message> eventMessages = aggregate.dispatchCommand(envelope);

        final AggregateTransaction tx = AggregateTransaction.start(aggregate);
        aggregate.apply(eventMessages, envelope);
        tx.commit();

        return eventMessages;
    }
}

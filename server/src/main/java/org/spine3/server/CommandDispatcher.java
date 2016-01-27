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

package org.spine3.server;

import com.google.protobuf.Message;
import org.spine3.base.CommandContext;
import org.spine3.base.EventRecord;
import org.spine3.type.CommandClass;

import java.util.List;
import java.util.Set;

/**
 * {@code CommandDispatcher} delivers commands to handlers and returns results of the command processing.
 *
 * @author Alexander Yevsyukov
 */
public interface CommandDispatcher {

    /**
     * Returns the set of command classes this dispatcher can dispatch.
     *
     * @return non-empty set of command classes
     */
    Set<CommandClass> getCommandClasses();

    /**
     * Dispatches the command for processing and returns the generated events.
     *
     * @param command the command to dispatch
     * @param context context info of the command
     * @return a list of event records generated during the command execution, or
     *         an empty list if no events were generated
     */
    List<EventRecord> dispatch(Message command, CommandContext context) throws Exception;
    //TODO:2016-01-24:alexander.yevsyukov: Do not return results to the CommandBus.
    //TODO:2016-01-25:alexander.yevsyukov: Dispatch CommandRequest, not the couple of parameters.

    //TODO:2016-01-24:alexander.yevsyukov: Do handle exceptions that can be thrown at CommandBus side.

}
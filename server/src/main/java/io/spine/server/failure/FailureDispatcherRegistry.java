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
package io.spine.server.failure;

import io.spine.server.outbus.OutputDispatcherRegistry;
import io.spine.type.FailureClass;

import java.util.Set;

/**
 * The registry of objects dispatching the business failures to their subscribers.
 *
 * <p>There can be multiple dispatchers per failure class.
 *
 * @author Alex Tymchenko
 */
public class FailureDispatcherRegistry extends OutputDispatcherRegistry<FailureClass,
                                                                        FailureDispatcher> {
    /**
     * {@inheritDoc}
     *
     * Overrides to expose this method to
     * {@linkplain FailureBus#getDispatchers(FailureClass) failureBus}.
     */
    @Override
    protected Set<FailureDispatcher> getDispatchers(FailureClass messageClass) {
        return super.getDispatchers(messageClass);
    }

    /**
     * {@inheritDoc}
     *
     * Overrides to expose this method to
     * {@linkplain FailureBus#hasDispatchers(FailureClass) failureBus}.
     */
    @Override
    protected boolean hasDispatchersFor(FailureClass eventClass) {
        return super.hasDispatchersFor(eventClass);
    }
}

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

package io.spine.core;

import com.google.common.base.Optional;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.type.TypeName;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * Utility class for working with event enrichments.
 *
 * @author Alexander Yevsyukov
 */
public final class Enrichments {

    private Enrichments() {
        // Prevent instantiation of this utility class.
    }

    /**
     * Verifies if the enrichment is not disabled in the passed event.
     */
    public static boolean isEnrichmentEnabled(Event event) {
        checkNotNull(event);
        final EventContext context = event.getContext();
        final boolean isEnabled =
                context.getEnrichment()
                       .getModeCase() != Enrichment.ModeCase.DO_NOT_ENRICH;
        return isEnabled;
    }

    /**
     * Returns all enrichments from the context.
     *
     * @param context a context to get enrichments from
     * @return an optional of enrichments
     */
    public static Optional<Enrichment.Container> getEnrichments(EventContext context) {
        checkNotNull(context);
        if (context.getEnrichment()
                   .getModeCase() == Enrichment.ModeCase.CONTAINER) {
            return Optional.of(context.getEnrichment()
                                      .getContainer());
        }
        return Optional.absent();
    }

    /**
     * Return a specific enrichment from the context.
     *
     * @param enrichmentClass a class of the event enrichment
     * @param context         a context to get an enrichment from
     * @param <E>             a type of the event enrichment
     * @return an optional of the enrichment
     */
    public static <E extends Message> Optional<E> getEnrichment(Class<E> enrichmentClass,
                                                                EventContext context) {
        checkNotNull(enrichmentClass);
        checkNotNull(context);
        final Optional<Enrichment.Container> value = getEnrichments(context);
        if (!value.isPresent()) {
            return Optional.absent();
        }
        final Enrichment.Container enrichments = value.get();
        final String typeName = TypeName.of(enrichmentClass)
                                        .value();
        final Any any = enrichments.getItemsMap()
                                   .get(typeName);
        if (any == null) {
            return Optional.absent();
        }
        final E result = unpack(any);
        return Optional.fromNullable(result);
    }
}

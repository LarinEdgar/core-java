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

package org.spine3.server.event.enrich;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import org.spine3.annotations.EventAnnotationsProto;
import org.spine3.protobuf.KnownTypes;
import org.spine3.protobuf.TypeUrl;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.Descriptors.FieldDescriptor;
import static org.spine3.io.IoUtil.loadAllProperties;

/**
 * A map from an event enrichment Protobuf type name to the corresponding type name(s) of event(s) to enrich.
 *
 * <p>Example:
 * <p>{@code proto.type.MyEventEnrichment} - {@code proto.type.FirstEvent},{@code proto.type.SecondEvent}
 *
 * @author Alexander Litus
 * @author Dmytro Dashenkov
 */
class EventEnrichmentsMap {

    /**
     * A path to the file which contains enrichment and event Protobuf type names.
     * Is generated by Gradle during the build process.
     */
    private static final String PROPS_FILE_PATH = "enrichments.properties";

    /** A separator between event types in the `.properties` file. */
    private static final String EVENT_TYPE_SEPARATOR = ",";

    private static final ImmutableMultimap<String, String> enrichmentsMap = buildEnrichmentsMap();

    private EventEnrichmentsMap() {
    }

    /** Returns the immutable map instance. */
    static ImmutableMultimap<String, String> getInstance() {
        return enrichmentsMap;
    }

    private static ImmutableMultimap<String, String> buildEnrichmentsMap() {
        final ImmutableSet<Properties> propertiesSet = loadAllProperties(PROPS_FILE_PATH);
        final Builder builder = new Builder(propertiesSet);
        final ImmutableMultimap<String, String> result = builder.build();
        return result;
    }

    private static class Builder {

        /**
         * Constant indicating a package qualifier.
         */
        private static final String PACKAGE_WILDCARD_INDICATOR = ".*";

        private static final String WILDCARD_TYPE_UPON_FIELD_INDICATOR = "*.";

        private final Iterable<Properties> properties;
        private final ImmutableMultimap.Builder<String, String> builder;

        Builder(Iterable<Properties> properties) {
            this.properties = properties;
            this.builder = ImmutableMultimap.builder();
        }

        ImmutableMultimap<String, String> build() {
            for (Properties props : this.properties) {
                put(props);
            }
            return builder.build();
        }

        private void put(Properties props) {
            final Set<String> enrichmentTypes = props.stringPropertyNames();
            for (String enrichmentType : enrichmentTypes) {
                final String eventTypesStr = props.getProperty(enrichmentType);
                final Iterable<String> eventQualifier = FluentIterable.from(eventTypesStr.split(EVENT_TYPE_SEPARATOR));
                put(enrichmentType, eventQualifier);
            }
        }

        private void put(String enrichmentType, Iterable<String> eventQualifiers) {
            for (String eventQualifier : eventQualifiers) {
                if (isPackage(eventQualifier)) {
                    putAllTypesFromPackage(enrichmentType, eventQualifier);
                } else {
                    builder.put(enrichmentType, eventQualifier);
                }
            }
        }

        /**
         * Puts all the events from the given package into the map to match the given enrichment type.
         *
         * @param enrichmentType type of the enrichment for the given events
         * @param eventsPackage  package qualifier representing the protobuf package containing the event to enrich
         */
        private void putAllTypesFromPackage(String enrichmentType, String eventsPackage) {
            final int lastSignificantCharPos = eventsPackage.length() - PACKAGE_WILDCARD_INDICATOR.length();
            final String packageName = eventsPackage.substring(0, lastSignificantCharPos);
            final Set<String> boundFields = getBoundFields(enrichmentType);
            final Collection<TypeUrl> eventTypes = KnownTypes.getTypesFromPackage(packageName);
            for (TypeUrl type : eventTypes) {
                final String typeQualifier = type.getTypeName();
                if (hasTargetFields(typeQualifier, boundFields)) {
                    builder.put(enrichmentType, typeQualifier);
                }
            }
        }

        private static Set<String> getBoundFields(String enrichmentType) {
            final Descriptor enrichmentDescriptor = KnownTypes.getDescriptorForType(enrichmentType);

            final Set<String> result = new HashSet<>();
            for (FieldDescriptor field : enrichmentDescriptor.getFields()) {
                final String extension = field.getOptions()
                                              .getExtension(EventAnnotationsProto.by);
                final String fieldName = extension.substring(WILDCARD_TYPE_UPON_FIELD_INDICATOR.length());
                result.add(fieldName);
            }

            return result;
        }

        private static boolean hasTargetFields(String eventType, Collection<String> targetFields) {
            final Descriptor eventDescriptor = KnownTypes.getDescriptorForType(eventType);

            final List<FieldDescriptor> fields = eventDescriptor.getFields();
            final Collection<String> fieldNames = Collections2.transform(
                    fields,
                    new Function<FieldDescriptor, String>() {
                        @Override
                        public String apply(@Nullable FieldDescriptor input) {
                            checkNotNull(input);
                            return input.getName();
                        }
                    });
            final boolean result = fieldNames.containsAll(targetFields);
            return result;
        }

        /**
         * @return {@code true} if the given qualifier is a package according to the contract
         * of {@code "enrichment_for") option notation
         */
        private static boolean isPackage(String qualifier) {
            checkNotNull(qualifier);
            checkArgument(!qualifier.isEmpty());

            final int indexOfWildcardChar = qualifier.indexOf(PACKAGE_WILDCARD_INDICATOR);
            final int qualifierLength = qualifier.length();

            final boolean result = indexOfWildcardChar == (qualifierLength - PACKAGE_WILDCARD_INDICATOR.length());
            return result;
        }
    }
}

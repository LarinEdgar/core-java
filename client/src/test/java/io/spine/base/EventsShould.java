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
package io.spine.base;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.BoolValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.protobuf.Wrapper;
import io.spine.server.command.EventFactory;
import io.spine.string.Stringifiers;
import io.spine.test.EventTests;
import io.spine.test.TestActorRequestFactory;
import io.spine.test.TestEventFactory;
import io.spine.test.Tests;
import io.spine.time.Time;
import org.junit.Before;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.spine.base.Events.checkValid;
import static io.spine.base.Events.getActor;
import static io.spine.base.Events.getMessage;
import static io.spine.base.Events.getProducer;
import static io.spine.base.Events.getTimestamp;
import static io.spine.base.Events.sort;
import static io.spine.base.Identifiers.newUuid;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.Wrapper.forBoolean;
import static io.spine.test.Tests.assertHasPrivateParameterlessCtor;
import static io.spine.test.Tests.newUuidValue;
import static io.spine.test.TimeTests.Past.minutesAgo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexander Litus
 * @author Alexander Yevsyukov
 */
public class EventsShould {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(Wrapper.forString()
                                                .pack(EventsShould.class.getSimpleName()),
                                         EventsShould.class);
    private Event event;
    private EventContext context;

    private final StringValue stringValue = Wrapper.forString(newUuid());
    private final BoolValue boolValue = forBoolean(true);
    @SuppressWarnings("MagicNumber")
    private final DoubleValue doubleValue = Wrapper.forDouble(10.1);

    static EventContext newEventContext() {
        final Event event = eventFactory.createEvent(Time.getCurrentTime(),
                                                     Tests.<Version>nullRef());
        return event.getContext();
    }

    static Event createEventOccurredMinutesAgo(int minutesAgo) {
        final Event result = eventFactory.createEvent(newUuidValue(),
                                                      null,
                                                      minutesAgo(minutesAgo));
        return result;
    }

    private Event createEventWithContext(Message eventMessage) {
        return EventTests.createEvent(eventMessage, context);
    }

    @Before
    public void setUp() {
        final TestActorRequestFactory requestFactory =
                TestActorRequestFactory.newInstance(getClass());
        final Command cmd = requestFactory.command().create(Time.getCurrentTime());
        final StringValue producerId = Wrapper.forString(getClass().getSimpleName());
        EventFactory eventFactory = EventFactory.newBuilder()
                                                .setCommandId(Commands.generateId())
                                                .setProducerId(producerId)
                                                .setCommandContext(cmd.getContext())
                                                .build();
        event = eventFactory.createEvent(Time.getCurrentTime(),
                                                     Tests.<Version>nullRef());
        context = event.getContext();
    }

    @Test
    public void have_private_ctor() {
        assertHasPrivateParameterlessCtor(Events.class);
    }

    @Test
    public void return_actor_from_EventContext() {
        assertEquals(context.getCommandContext()
                            .getActorContext()
                            .getActor(), getActor(context));
    }

    @Test
    public void sort_events_by_time() {
        final Event event1 = createEventOccurredMinutesAgo(30);
        final Event event2 = createEventOccurredMinutesAgo(20);
        final Event event3 = createEventOccurredMinutesAgo(10);
        final List<Event> sortedEvents = newArrayList(event1, event2, event3);
        final List<Event> eventsToSort = newArrayList(event2, event1, event3);

        sort(eventsToSort);

        assertEquals(sortedEvents, eventsToSort);
    }

    @Test
    public void have_event_comparator() {
        final Event event1 = createEventOccurredMinutesAgo(120);
        final Event event2 = createEventOccurredMinutesAgo(2);

        final Comparator<Event> comparator = Events.eventComparator();
        assertTrue(comparator.compare(event1, event2) < 0);
        assertTrue(comparator.compare(event2, event1) > 0);
        assertTrue(comparator.compare(event1, event1) == 0);
    }

   @Test
    public void get_message_from_event() {
        createEventAndAssertReturnedMessageFor(stringValue);
        createEventAndAssertReturnedMessageFor(boolValue);
        createEventAndAssertReturnedMessageFor(doubleValue);
    }

    private static void createEventAndAssertReturnedMessageFor(Message msg) {
        final Event event = EventTests.createContextlessEvent(msg);

        assertEquals(msg, getMessage(event));
    }

    @Test
    public void get_timestamp_from_event() {
        final Event event = createEventWithContext(stringValue);

        assertEquals(context.getTimestamp(), getTimestamp(event));
    }

    @Test
    public void get_producer_from_event_context() {
        final StringValue msg = unpack(context.getProducerId());

        final String id = getProducer(context);

        assertEquals(msg.getValue(), id);
    }


    @Test
    public void pass_the_null_tolerance_check() {
        new NullPointerTester()
                .setDefault(StringValue.class, StringValue.getDefaultInstance())
                .setDefault(EventContext.class, newEventContext())
                .testAllPublicStaticMethods(Events.class);
    }

    @Test
    public void provide_EventId_stringifier() {
        final EventId id = event.getId();
        
        final String str = Stringifiers.toString(id);
        final EventId convertedBack = Stringifiers.fromString(str, EventId.class);

        assertEquals(id, convertedBack);
    }

    @Test(expected = IllegalArgumentException.class)
    public void reject_empty_event_id() {
        checkValid(EventId.getDefaultInstance());
    }

    @Test
    public void accept_generated_event_id() {
        final EventId eventId = event.getId();
        assertEquals(eventId, checkValid(eventId));
    }
}

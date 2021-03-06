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

package io.spine.server.aggregate.given;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.BoolValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.util.Timestamps;
import io.spine.core.EventContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.Apply;
import io.spine.server.aggregate.React;
import io.spine.server.command.Assign;
import io.spine.server.route.EventRoute;
import io.spine.server.route.EventRouting;
import io.spine.string.Stringifiers;
import io.spine.time.Time;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * @author Alexander Yevsyukov
 */
public class AggregateEventRoutingTestEnv {

    /**
     * An aggregate that accepts some standard Protobuf types as commands and events.
     */
    public static class OptimisticLog extends Aggregate<Long, Log, LogVBuilder> {

        private OptimisticLog(Long id) {
            super(id);
        }

        private static MessageReceived ack(Message msg) {
            return MessageReceived.newBuilder()
                                  .setTimestamp(Time.getCurrentTime())
                                  .setMessage(pack(msg))
                                  .build();
        }

        @Assign
        MessageReceived handle(BoolValue cmd) {
            return ack(cmd);
        }

        @Assign
        MessageReceived handle(StringValue cmd) {
            return ack(cmd);
        }

        @Apply
        void event(MessageReceived event) {
            final Map<String, String> map = getBuilder().getRecords();
            map.put(Timestamps.toString(event.getTimestamp()),
                    Stringifiers.toString(unpack(event.getMessage())));
        }

        /**
         * Reacts with {@link MessageReceived} only if the passed value is positive.
         */
        @React
        List<Message> thinkPositive(FloatValue event) {
            final Number number = event.getValue();
            return doReact(event, number);
        }

        /**
         * Reacts with {@link MessageReceived} only if the passed value is positive.
         */
        @React
        List<Message> thinkPositive(Int64Value event) {
            final Number number = event.getValue();
            return doReact(event, number);
        }

        private static List<Message> doReact(Message event, Number asNumber) {
            if (asNumber.longValue() > 0) {
                return ImmutableList.<Message>of(ack(event));
            }
            return ImmutableList.of();
        }
    }

    @SuppressWarnings("SerializableInnerClassWithNonSerializableOuterClass")
    // OK as the routes do not refer to the instance of the repository.
    public static class LogRepository extends AggregateRepository<Long, OptimisticLog> {

        private static final EventRoute<Long, Message> defaultRoute =
                new EventRoute<Long, Message>() {
                    private static final long serialVersionUID = 0L;

                    @Override
                    public Set<Long> apply(Message message, EventContext context) {
                        return ImmutableSet.of((long) message.hashCode());
                    }
                };

        public LogRepository() {
            super();
            final EventRouting<Long> eventRouting = getEventRouting();
            eventRouting.replaceDefault(defaultRoute);

            //TODO:2017-07-18:alexander.yevsyukov: Finish setup
        }
    }

}

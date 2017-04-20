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

package org.spine3.time;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.spine3.test.Tests.assertHasPrivateParameterlessCtor;
import static org.spine3.test.Tests.random;
import static org.spine3.time.Calendars.at;
import static org.spine3.time.Calendars.getHours;
import static org.spine3.time.Calendars.getMinutes;
import static org.spine3.time.Calendars.getSeconds;
import static org.spine3.time.Calendars.getZoneOffset;
import static org.spine3.time.OffsetTimes.addHours;
import static org.spine3.time.OffsetTimes.addMillis;
import static org.spine3.time.OffsetTimes.addMinutes;
import static org.spine3.time.OffsetTimes.addSeconds;
import static org.spine3.time.OffsetTimes.subtractHours;
import static org.spine3.time.OffsetTimes.subtractMillis;
import static org.spine3.time.OffsetTimes.subtractMinutes;
import static org.spine3.time.OffsetTimes.subtractSeconds;
import static org.spine3.time.Timestamps2.HOURS_PER_DAY;
import static org.spine3.time.Timestamps2.MILLIS_PER_SECOND;
import static org.spine3.time.Timestamps2.MINUTES_PER_HOUR;
import static org.spine3.time.Timestamps2.NANOS_PER_MILLISECOND;
import static org.spine3.time.Timestamps2.SECONDS_PER_MINUTE;
import static org.spine3.time.Timestamps2.getCurrentTime;

/**
 * @author Alexander Aleksandrov
 * @author Alexander Yevsyukov
 */
public class OffsetTimesShould extends AbstractZonedTimeTest {

    private Timestamp gmtNow;
    private OffsetTime now;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        gmtNow = getCurrentTime();
        now = OffsetTimes.timeAt(gmtNow, zoneOffset);
    }

    @Test
    public void have_utility_constructor() {
        assertHasPrivateParameterlessCtor(OffsetTimes.class);
    }

    @Test
    public void obtain_current() {
        final OffsetTime now = OffsetTimes.now(zoneOffset);
        final Calendar cal = at(zoneOffset);

        final LocalTime time = now.getTime();
        assertEquals(getHours(cal), time.getHours());
        assertEquals(getMinutes(cal), time.getMinutes());
        assertEquals(getSeconds(cal), time.getSeconds());
        assertEquals(getZoneOffset(cal), now.getOffset().getAmountSeconds());
        /* We cannot check milliseconds and nanos due to time gap between object creation */
    }

    @Test
    public void create_instance_on_local_time_at_offset() {
        final ZoneOffset delhiOffset = ZoneOffsets.ofHoursMinutes(3, 30);
        final LocalTime localTime = generateLocalTime();
        final OffsetTime delhiTime = OffsetTimes.of(localTime, delhiOffset);

        assertEquals(localTime, delhiTime.getTime());
        assertEquals(delhiOffset, delhiTime.getOffset());
    }

    private static LocalTime generateLocalTime() {
        int hours = random(HOURS_PER_DAY);
        int minutes = random(MINUTES_PER_HOUR);
        int seconds = random(SECONDS_PER_MINUTE);
        int millis = random(MILLIS_PER_SECOND);
        int nanos = random(NANOS_PER_MILLISECOND);
        return LocalTimes.of(hours, minutes, seconds, millis, nanos);
    }

    @Test
    public void add_hours() {
        final int hoursDelta = random(1, 100);
        final Duration deltaDuration = Durations2.hours(hoursDelta);

        final Timestamp gmtFuture = Timestamps.add(gmtNow, deltaDuration);
        final LocalTime expectedFuture = LocalTimes.timeAt(gmtFuture, zoneOffset);

        final LocalTime actualFuture = addHours(now, hoursDelta).getTime();

        assertEquals(expectedFuture, actualFuture);
    }

    @Test
    public void subtract_hours() {
        final int hoursDelta = random(1, 500);
        final Duration deltaDuration = Durations2.hours(hoursDelta);

        final Timestamp gmtPast = Timestamps.subtract(gmtNow, deltaDuration);
        final LocalTime expectedPast = LocalTimes.timeAt(gmtPast, zoneOffset);

        final LocalTime actualPast = subtractHours(now, hoursDelta).getTime();

        assertEquals(expectedPast, actualPast);
    }

    @Test
    public void add_minutes() {
        final int minutesDelta = random(1, 300);
        final Duration deltaDuration = Durations2.minutes(minutesDelta);

        final Timestamp gmtFuture = Timestamps.add(gmtNow, deltaDuration);
        final LocalTime expectedFuture = LocalTimes.timeAt(gmtFuture, zoneOffset);

        final LocalTime actualFuture = addMinutes(now, minutesDelta).getTime();

        assertEquals(expectedFuture, actualFuture);
    }

    @Test
    public void subtract_minutes() {
        final int minutesDelta = random(1, 1024);
        final Duration deltaDuration = Durations2.minutes(minutesDelta);

        final Timestamp gmtPast = Timestamps.subtract(gmtNow, deltaDuration);
        final LocalTime expectedPast = LocalTimes.timeAt(gmtPast, zoneOffset);

        final LocalTime actualPast = subtractMinutes(now, minutesDelta).getTime();

        assertEquals(expectedPast, actualPast);
    }

    @Test
    public void add_seconds() {
        final int secondsDelta = random(1, 300);
        final Duration deltaDuration = Durations2.seconds(secondsDelta);

        final Timestamp gmtFuture = Timestamps.add(gmtNow, deltaDuration);
        final LocalTime expectedFuture = LocalTimes.timeAt(gmtFuture, zoneOffset);

        final LocalTime actualFuture = addSeconds(now, secondsDelta).getTime();

        assertEquals(expectedFuture, actualFuture);
    }

    @Test
    public void subtract_seconds() {
        final int secondsDelta = random(1, 1024);
        final Duration deltaDuration = Durations2.seconds(secondsDelta);

        final Timestamp gmtPast = Timestamps.subtract(gmtNow, deltaDuration);
        final LocalTime expectedPast = LocalTimes.timeAt(gmtPast, zoneOffset);

        final LocalTime actualPast = subtractSeconds(now, secondsDelta).getTime();

        assertEquals(expectedPast, actualPast);
    }

    @Test
    public void add_millis() {
        final int millisDelta = random(1, 100_000_000);
        final Duration deltaDuration = Durations.fromMillis(millisDelta);

        final Timestamp gmtFuture = Timestamps.add(gmtNow, deltaDuration);
        final LocalTime expectedFuture = LocalTimes.timeAt(gmtFuture, zoneOffset);

        final LocalTime actualFuture = addMillis(now, millisDelta).getTime();

        assertEquals(expectedFuture, actualFuture);
    }

    @Test
    public void subtract_millis() {
        final int millisDelta = random(1, 999_999);
        final Duration deltaDuration = Durations.fromMillis(millisDelta);

        final Timestamp gmtPast = Timestamps.subtract(gmtNow, deltaDuration);
        final LocalTime expectedPast = LocalTimes.timeAt(gmtPast, zoneOffset);

        final LocalTime actualPast = subtractMillis(now, millisDelta).getTime();

        assertEquals(expectedPast, actualPast);
    }

    @Test
    public void pass_null_tolerance_test() {
        new NullPointerTester()
                .setDefault(Timestamp.class, getCurrentTime())
                .setDefault(OffsetTime.class, OffsetTimes.now(zoneOffset))
                .setDefault(ZoneOffset.class, zoneOffset)
                .setDefault(LocalTime.class, LocalTimes.now())
                .testAllPublicStaticMethods(OffsetTimes.class);
    }

    //
    // Illegal args. check for math with hours.
    //------------------------------------------
    
    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_hours_to_add() {
        addHours(now, -5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_hours_to_add() {
        addHours(now, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_hours_to_subtract() {
        subtractHours(now, -6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_hours_to_subtract() {
        subtractHours(now, 0);
    }

    //
    // Illegal args. check for math with minutes.
    //------------------------------------------
    
    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_minutes_to_add() {
        addMinutes(now, -7);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_minutes_to_add() {
        addMinutes(now, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_minutes_to_subtract() {
        subtractMinutes(now, -8);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_minutes_to_subtract() {
        subtractMinutes(now, 0);
    }

    //
    // Illegal args. check for math with seconds.
    //-------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_seconds_to_add() {
        addSeconds(now, -25);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_seconds_to_add() {
        addSeconds(now, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_seconds_to_subtract() {
        subtractSeconds(now, -27);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_seconds_to_subtract() {
        subtractSeconds(now, 0);
    }

    //
    // Illegal args. check for math with millis.
    //-------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_millis_to_add() {
        addMillis(now, -500);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_millis_to_add() {
        addMillis(now, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_negative_millis_to_subtract() {
        subtractMillis(now, -270);
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_accept_zero_millis_to_subtract() {
        subtractMillis(now, 0);
    }

    //
    // Stringification
    //-------------------------

    @Test
    public void convert_to_string_and_back_at_UTC() throws ParseException {
        final OffsetTime nowAtUTC = OffsetTimes.now(ZoneOffsets.UTC);

        final String str = OffsetTimes.toString(nowAtUTC);
        assertTrue(str.contains("Z"));

        final OffsetTime parsed = OffsetTimes.parse(str);
        assertEquals(nowAtUTC, parsed);
    }

    @Test
    public void convert_values_at_current_time_zone() throws ParseException {
        // Get current zone offset and strip ID value because it's not stored into date/time.
        final ZoneOffset zoneOffset = ZoneOffsets.getDefault()
                                                 .toBuilder()
                                                 .clearId()
                                                 .build();
        final OffsetTime now = OffsetTimes.now(zoneOffset);

        final String value = OffsetTimes.toString(now);
        final OffsetTime parsed = OffsetTimes.parse(value);
        assertEquals(now, parsed);
    }
}

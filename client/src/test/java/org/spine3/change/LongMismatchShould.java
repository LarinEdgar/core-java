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

package org.spine3.change;

import com.google.protobuf.Int64Value;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.spine3.protobuf.AnyPacker.unpack;
import static org.spine3.test.Tests.hasPrivateUtilityConstructor;

public class LongMismatchShould {

    private static final int VERSION = 7;

    @Test
    public void have_private_constructor() {
        assertTrue(hasPrivateUtilityConstructor(LongMismatch.class));
    }

    @Test
    public void return_mismatch_object_with_int64_values() {
        final long expected = 0L;
        final long actual = 1L;
        final long newValue = 5L;
        final ValueMismatch mismatch = LongMismatch.of(expected, actual, newValue, VERSION);
        final Int64Value expectedWrapped = unpack(mismatch.getExpected());
        final Int64Value actualWrapped = unpack(mismatch.getActual());

        assertEquals(expected, expectedWrapped.getValue());
        assertEquals(actual, actualWrapped.getValue());
    }
}
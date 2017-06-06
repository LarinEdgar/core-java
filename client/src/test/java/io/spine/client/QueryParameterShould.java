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

package io.spine.client;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.protobuf.StringValue;
import org.junit.Test;

import static io.spine.client.QueryParameter.eq;
import static java.lang.String.valueOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author Dmytro Dashenkov
 */
public class QueryParameterShould {

    @Test
    public void not_accept_nulls() {
        new NullPointerTester().testAllPublicStaticMethods(QueryParameter.class);
    }

    @Test
    public void support_equality() {
        final String param1 = "param1";
        final String param2 = "param2";
        final String foobar = "foobar";
        final String baz = "baz";
        final StringValue foobarValue = StringValue.newBuilder()
                                                   .setValue(foobar)
                                                   .build();

        final QueryParameter parameter1 = eq(param1, foobar);
        final QueryParameter parameter2 = eq(param1, foobarValue);
        final QueryParameter parameter3 = eq(param1, baz);
        final QueryParameter parameter4 = eq(param2, foobar);

        new EqualsTester()
                .addEqualityGroup(parameter1, parameter2)
                .addEqualityGroup(parameter3)
                .addEqualityGroup(parameter4)
                .testEquals();
    }

    @Test
    public void support_toString() {
        final String name = "myColumn";
        final int value = 42;

        final QueryParameter param = eq(name, value);
        final String stringRepr = param.toString();

        assertThat(stringRepr, containsString(name));
        assertThat(stringRepr, containsString(valueOf(value)));
    }
}

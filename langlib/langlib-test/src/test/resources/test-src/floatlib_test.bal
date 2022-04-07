// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/lang.'float as floats;
import ballerina/lang.test;

function testIsFinite() returns [boolean, boolean] {
    float f = 12.34;
    float inf = 1.0/0.0;
    return [f.isFinite(), inf.isFinite()];
}

function testIsInfinite() returns [boolean, boolean] {
    float f = 12.34;
    float inf = 1.0/0.0;
    return [f.isInfinite(), inf.isInfinite()];
}

function testSum() returns float {
    return floats:sum(12.34, 23.45, 34.56);
}

function testFloatConsts() returns [float,float] {
    return [floats:NaN, floats:Infinity];
}

type Floats 12f|21.0;

function testLangLibCallOnFiniteType() {
    Floats x = 21;
    float y = x.sum(1, 2.3);
    test:assertValueEqual(24.3, y);
}

function testFloatEquality() {
    test:assertTrue(42.0 == 42.0);
    test:assertFalse(1.0 == 12.0);
    test:assertTrue(float:NaN == float:NaN);
    test:assertTrue(-0.0 == 0.0);
}

function testFloatNotEquality() {
    test:assertFalse(42.0 != 42.0);
    test:assertTrue(1.0 != 12.0);
    test:assertFalse(float:NaN != float:NaN);
    test:assertFalse(-0.0 != 0.0);
}

function testFloatExactEquality() {
    test:assertTrue(42.0 === 42.0);
    test:assertFalse(1.0 === 12.0);
    test:assertTrue(float:NaN === float:NaN);
    test:assertFalse(-0.0 === 0.0);
}

function testFloatNotExactEquality() {
    test:assertFalse(42.0 !== 42.0);
    test:assertTrue(1.0 !== 12.0);
    test:assertFalse(float:NaN !== float:NaN);
    test:assertTrue(-0.0 !== 0.0);
}

function testFromHexString() {
    float|error v1 = float:fromHexString("0xa.bp1");
    test:assertValueEqual(checkpanic v1, 21.375);

    v1 = float:fromHexString("+0xa.bp1");
    test:assertValueEqual(checkpanic v1, 21.375);

    v1 = float:fromHexString("-0xa.bp1");
    test:assertValueEqual(checkpanic v1, -21.375);

    v1 = float:fromHexString("0Xa2c.b32p2");
    test:assertValueEqual(checkpanic v1, 10418.798828125);

    v1 = float:fromHexString("0Xa.b32P-5");
    test:assertValueEqual(checkpanic v1, 0.3343658447265625);

    v1 = float:fromHexString("-0x123.fp-5");
    test:assertValueEqual(checkpanic v1, -9.123046875);

    v1 = float:fromHexString("0x123fp-5");
    test:assertValueEqual(checkpanic v1, 145.96875);

    v1 = float:fromHexString("0x.ab5p2");
    test:assertValueEqual(checkpanic v1, 2.6767578125);

    v1 = float:fromHexString("0x1a");
    error err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "For input string: \"0x1a\"");

    v1 = float:fromHexString("NaN");
    test:assertValueEqual(checkpanic v1, float:NaN);

    v1 = float:fromHexString("+Infinity");
    test:assertValueEqual(checkpanic v1, float:Infinity);

    v1 = float:fromHexString("-Infinity");
    test:assertValueEqual(checkpanic v1, -float:Infinity);

    v1 = float:fromHexString("Infinity");
    test:assertValueEqual(checkpanic v1, float:Infinity);

    v1 = float:fromHexString("AInvalidNum");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: 'AInvalidNum'");

    v1 = float:fromHexString("12.3");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '12.3'");

    v1 = float:fromHexString("1");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '1'");

    v1 = float:fromHexString("+1");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '+1'");

    v1 = float:fromHexString("-1");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '-1'");

    v1 = float:fromHexString("2A");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '2A'");

    v1 = float:fromHexString("0x");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '0x'");

    v1 = float:fromHexString("0i");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '0i'");

    v1 = float:fromHexString("0i123");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '0i123'");

    v1 = float:fromHexString("+inf");
    err = <error> v1;
    test:assertValueEqual(err.message(), "{ballerina/lang.float}NumberParsingError");
    test:assertValueEqual(<string> checkpanic err.detail()["message"], "invalid hex string: '+inf'");
}

function testMinAndMaxWithNaN() {
    float a = float:max(1, float:NaN);
    test:assertTrue(a === float:NaN);

    float b = float:min(5, float:NaN);
    test:assertTrue(b === float:NaN);
}

function testFromStringPositive() {
    float|error a1 = float:fromString("123");
    assertEquality(true, a1 is float);
    assertEquality(123.0, a1);

    a1 = float:fromString("-123");
    assertEquality(true, a1 is float);
    assertEquality(-123.0, a1);

    a1 = float:fromString("123.0");
    assertEquality(true, a1 is float);
    assertEquality(123.0, a1);

    a1 = float:fromString("-123.0");
    assertEquality(true, a1 is float);
    assertEquality(-123.0, a1);

    a1 = float:fromString("12E+2");
    assertEquality(true, a1 is float);
    assertEquality(1200.0, a1);

    a1 = float:fromString("-12E+2");
    assertEquality(true, a1 is float);
    assertEquality(-1200.0, a1);

    a1 = float:fromString("12e-2");
    assertEquality(true, a1 is float);
    assertEquality(0.12, a1);

    a1 = float:fromString("-12e-2");
    assertEquality(true, a1 is float);
    assertEquality(-0.12, a1);

    a1 = float:fromString("12.23E+2");
    assertEquality(true, a1 is float);
    assertEquality(1223.0, a1);

    a1 = float:fromString("-12.23E+2");
    assertEquality(true, a1 is float);
    assertEquality(-1223.0, a1);

    a1 = float:fromString("12.23e-2");
    assertEquality(true, a1 is float);
    assertEquality(0.1223, a1);

    a1 = float:fromString("-12.23e-2");
    assertEquality(true, a1 is float);
    assertEquality(-0.1223, a1);

    a1 = float:fromString("+12.23E+2");
    assertEquality(true, a1 is float);
    assertEquality(1223.0, a1);

    a1 = float:fromString("+12.23e-2");
    assertEquality(true, a1 is float);
    assertEquality(0.1223, a1);

    a1 = float:fromString("+123.0");
    assertEquality(true, a1 is float);
    assertEquality(123.0, a1);

    a1 = float:fromString("NaN");
    assertEquality(true, a1 is float);
    assertEquality(float:NaN, a1);

    a1 = float:fromString("+Infinity");
    assertEquality(true, a1 is float);
    assertEquality(float:Infinity, a1);

    a1 = float:fromString("-Infinity");
    assertEquality(true, a1 is float);
    assertEquality(-float:Infinity, a1);

    a1 = float:fromString("Infinity");
    assertEquality(true, a1 is float);
    assertEquality(float:Infinity, a1);
}

function testFromStringNegative() {
    float|error a1 = float:fromString("123f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '123f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("123F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '123F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("123.67f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '123.67f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("123.67F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '123.67F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("12E+2f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12E+2f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-12E+2F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-12E+2F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("12e-2F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12e-2F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-12e-2f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-12e-2f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("12.23E+2F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12.23E+2F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-12.23E+2f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-12.23E+2f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("12.23e-2f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12.23e-2f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-12.23e-2F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-12.23e-2F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("+12.23E+2F");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '+12.23E+2F' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("+12.23e-2f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '+12.23e-2f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("+123.0f");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '+123.0f' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("12d");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12d' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

     a1 = float:fromString("12D");
     assertEquality(true, a1 is error);
     if (a1 is error) {
         assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
         assertEquality("'string' value '12D' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
     }

    a1 = float:fromString("12.23E+2D");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '12.23E+2D' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-12.23e-2d");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-12.23e-2d' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("0xabcf");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '0xabcf' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-0xabcf");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-0xabcf' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("0Xabcf");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '0Xabcf' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }

    a1 = float:fromString("-0Xabcf");
    assertEquality(true, a1 is error);
    if (a1 is error) {
        assertEquality("{ballerina/lang.float}NumberParsingError", a1.message());
        assertEquality("'string' value '-0Xabcf' cannot be converted to 'float'", <string> checkpanic a1.detail()["message"]);
    }
}

const float f1 = 5.7;

type FloatType 5.7;

type FloatType2 5.7|2.8;

type FloatType3 float;

function testToFixedStringWithPositiveFloat() {
    float a = 5.7;
    string b = float:toFixedString(a, 16);
    assertEquality("5.7000000000000002", b);
    assertEquality("5.70", float:toFixedString(a, 2));
    assertEquality("5.700000000000000", float:toFixedString(a, 15));
    assertEquality("5.70000000", float:toFixedString(a, 8));
    assertEquality("5.7000", float:toFixedString(a, 4));

    string c = a.toFixedString(16);
    assertEquality("5.7000000000000002", c);
    assertEquality("5.70", a.toFixedString(2));
    assertEquality("5.700000000000000", a.toFixedString(15));
    assertEquality("5.70000000", a.toFixedString(8));
    assertEquality("5.7000", a.toFixedString(4));

    5.7 d = 5.7;
    assertEquality("5.7000000000000002", d.toFixedString(16));
    assertEquality("5.70", d.toFixedString(2));
    assertEquality("5.700000000000000", d.toFixedString(15));
    assertEquality("5.70000000", d.toFixedString(8));
    assertEquality("5.7000", d.toFixedString(4));

    assertEquality("5.7000000000000002", f1.toFixedString(16));
    assertEquality("5.70", f1.toFixedString(2));
    assertEquality("5.700000000000000", f1.toFixedString(15));
    assertEquality("5.70000000", f1.toFixedString(8));
    assertEquality("5.7000", f1.toFixedString(4));

    FloatType e = 5.7;
    assertEquality("5.7000000000000002", e.toFixedString(16));
    assertEquality("5.70", e.toFixedString(2));
    assertEquality("5.700000000000000", e.toFixedString(15));
    assertEquality("5.70000000", e.toFixedString(8));
    assertEquality("5.7000", e.toFixedString(4));

    FloatType2 f = 5.7;
    assertEquality("5.7000000000000002", f.toFixedString(16));
    assertEquality("5.70", f.toFixedString(2));
    assertEquality("5.700000000000000", f.toFixedString(15));
    assertEquality("5.70000000", f.toFixedString(8));
    assertEquality("5.7000", f.toFixedString(4));

    FloatType3 g = 5.7;
    assertEquality("5.7000000000000002", float:toFixedString(g, 16));
    assertEquality("5.70", float:toFixedString(g, 2));
    assertEquality("5.700000000000000", float:toFixedString(g, 15));
    assertEquality("5.70000000", float:toFixedString(g, 8));
    assertEquality("5.7000", float:toFixedString(g, 4));
}

const float f2 = -5.7;

type FloatType4 -5.7;

type FloatType5 -5.7|2.8;

function testToFixedStringWithNegativeFloat() {
    float a = -5.7;
    string b = float:toFixedString(a, 16);
    assertEquality("-5.7000000000000002", b);
    assertEquality("-5.70", float:toFixedString(a, 2));
    assertEquality("-5.700000000000000", float:toFixedString(a, 15));
    assertEquality("-5.70000000", float:toFixedString(a, 8));
    assertEquality("-5.7000", float:toFixedString(a, 4));

    string c = a.toFixedString(16);
    assertEquality("-5.7000000000000002", c);
    assertEquality("-5.70", a.toFixedString(2));
    assertEquality("-5.700000000000000", a.toFixedString(15));
    assertEquality("-5.70000000", a.toFixedString(8));
    assertEquality("-5.7000", a.toFixedString(4));

    -5.7 d = -5.7;
    assertEquality("-5.7000000000000002", d.toFixedString(16));
    assertEquality("-5.70", d.toFixedString(2));
    assertEquality("-5.700000000000000", d.toFixedString(15));
    assertEquality("-5.70000000", d.toFixedString(8));
    assertEquality("-5.7000", d.toFixedString(4));

    assertEquality("-5.7000000000000002", f2.toFixedString(16));
    assertEquality("-5.70", f2.toFixedString(2));
    assertEquality("-5.700000000000000", f2.toFixedString(15));
    assertEquality("-5.70000000", f2.toFixedString(8));
    assertEquality("-5.7000", f2.toFixedString(4));

    FloatType4 e = -5.7;
    assertEquality("-5.7000000000000002", e.toFixedString(16));
    assertEquality("-5.70", e.toFixedString(2));
    assertEquality("-5.700000000000000", e.toFixedString(15));
    assertEquality("-5.70000000", e.toFixedString(8));
    assertEquality("-5.7000", e.toFixedString(4));

    FloatType5 f = -5.7;
    assertEquality("-5.7000000000000002", f.toFixedString(16));
    assertEquality("-5.70", f.toFixedString(2));
    assertEquality("-5.700000000000000", f.toFixedString(15));
    assertEquality("-5.70000000", f.toFixedString(8));
    assertEquality("-5.7000", f.toFixedString(4));

    FloatType3 g = -5.7;
    assertEquality("-5.7000000000000002", float:toFixedString(g, 16));
    assertEquality("-5.70", float:toFixedString(g, 2));
    assertEquality("-5.700000000000000", float:toFixedString(g, 15));
    assertEquality("-5.70000000", float:toFixedString(g, 8));
    assertEquality("-5.7000", float:toFixedString(g, 4));
}

function testToFixedStringWithInfinity() {
    assertEquality("Infinity", float:toFixedString(float:Infinity, 16));
    assertEquality("Infinity", float:toFixedString(float:Infinity, 8));
    assertEquality("Infinity", float:toFixedString(float:Infinity, 4));
    assertEquality("Infinity", float:toFixedString(float:Infinity, 2));

    assertEquality("Infinity", float:Infinity.toFixedString(16));
    assertEquality("Infinity", float:Infinity.toFixedString(8));
    assertEquality("Infinity", float:Infinity.toFixedString(4));
    assertEquality("Infinity", float:Infinity.toFixedString(2));
}

function testToFixedStringWithNaN() {
    assertEquality("NaN", float:toFixedString(float:NaN, 16));
    assertEquality("NaN", float:toFixedString(float:NaN, 8));
    assertEquality("NaN", float:toFixedString(float:NaN, 4));
    assertEquality("NaN", float:toFixedString(float:NaN, 2));

    assertEquality("NaN", float:NaN.toFixedString(16));
    assertEquality("NaN", float:NaN.toFixedString(8));
    assertEquality("NaN", float:NaN.toFixedString(4));
    assertEquality("NaN", float:NaN.toFixedString(2));
}

function testToFixedStringWhenFractionDigitsIsLessThanZero() {
    float a = 5.7;

    string|error b = trap a.toFixedString(-2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toFixedString(a, -2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toFixedString(a, -(+2));
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toFixedString(a, ~2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }
}

function testToFixedStringWhenFractionDigitsIsZero() {
    float a = 5.7;
    string b = float:toFixedString(a, 0);
    assertEquality("6", b);
    assertEquality("6", a.toFixedString(0));

    float c = -5.7;
    string d = float:toFixedString(c, 0);
    assertEquality("-6", d);
    assertEquality("-6", c.toFixedString(0));
}

function testToFixedStringWhenFractionDigitsIsNil() {
    float a = 5.7;
    string b = float:toFixedString(a, ());
    assertEquality("5.7", b);
    assertEquality("5.7", a.toFixedString(()));

    float c = -5.7;
    string d = float:toFixedString(c, ());
    assertEquality("-5.7", d);
    assertEquality("-5.7", c.toFixedString(()));
}

function testToFixedStringWhenFractionDigitsIsVeryLargeInt() {
    float a = 5.7;
    string b = float:toFixedString(a, 2147483648);
    assertEquality("5.7", b);
    assertEquality("5.7", a.toFixedString(2147483647 + 1));

    float c = -5.7;
    string d = float:toFixedString(c, 2147483648 + 1);
    assertEquality("-5.7", d);
    assertEquality("-5.7", c.toFixedString(2147483647 + 1));
}

function testToFixedStringWhenFractionDigitsIsIntMax() {
    float a = 5.7;
    string b = float:toFixedString(a, 9223372036854775807);
    assertEquality("5.7", b);
    assertEquality("5.7", a.toFixedString(int:MAX_VALUE));

    float c = -5.7;
    string d = float:toFixedString(c, 9223372036854775807);
    assertEquality("-5.7", d);
    assertEquality("-5.7", c.toFixedString(int:MAX_VALUE));
}

function testToFixedStringWithMorePositiveFloats() {
    assertEquality("45362.1233399999982794", float:toFixedString(45362.12334, 16));
    assertEquality("0.1237469219000000", float:toFixedString(0.1237469219, 16));
    assertEquality("0.12374692", float:toFixedString(0.1237469219, 8));
    assertEquality("0.0124", float:toFixedString(0.0123654, 4));
    assertEquality("0.01236540", float:toFixedString(0.0123654, 8));
    assertEquality("0.01237", float:toFixedString(0.0123654, 5));
    assertEquality("0.00000006", float:toFixedString(0.0000000564, 8));
    assertEquality("0.0000001", float:toFixedString(0.0000000564, 7));
    assertEquality("0.00000", float:toFixedString(0, 5));
    assertEquality("0.000000000000000", float:toFixedString(0, 15));

    assertEquality("45362.1233399999982794", float:toFixedString(45362.12334f, 16));
    assertEquality("0.1237469219000000", float:toFixedString(0.1237469219f, 16));
    assertEquality("0.12374692", float:toFixedString(0.1237469219F, 8));
    assertEquality("0.0124", float:toFixedString(0.0123654f, 4));
    assertEquality("0.01236540", float:toFixedString(0.0123654F, 8));
    assertEquality("0.01237", float:toFixedString(0.0123654f, 5));
    assertEquality("0.00000006", float:toFixedString(0.0000000564f, 8));
    assertEquality("0.0000001", float:toFixedString(0.0000000564F, 7));
    assertEquality("0.00000", float:toFixedString(0f, 5));
    assertEquality("0.000000000000000", float:toFixedString(0F, 15));
}

function testToFixedStringWithVerySmallAndLargePositiveFloats() {
    float a = 2.5E-17;
    assertEquality("0.00000000000000002", float:toFixedString(a, 17));
    assertEquality("0.0000000000", float:toFixedString(a, 10));
    assertEquality("0.0000", float:toFixedString(a, 4));

    float b = 2.5E+17;
    assertEquality("250000000000000000.00000000000000000", float:toFixedString(b, 17));
    assertEquality("250000000000000000.0000000000", float:toFixedString(b, 10));
    assertEquality("250000000000000000.0000", float:toFixedString(b, 4));

    float c = 1.423223E6;
    assertEquality("1423223.00000000000000000", c.toFixedString(17));
    assertEquality("1423223.0000000000", c.toFixedString(10));
    assertEquality("1423223.0000", c.toFixedString(4));

    float d = 0.9E-10f;
    assertEquality("0.00000000009000000", d.toFixedString(17));
    assertEquality("0.000000000090", d.toFixedString(12));
    assertEquality("0.0000", d.toFixedString(4));
}

function testToFixedStringWithMoreNegativeFloats() {
    assertEquality("-45362.1233399999982794", float:toFixedString(-45362.12334, 16));
    assertEquality("-0.1237469219000000", float:toFixedString(-0.1237469219, 16));
    assertEquality("-0.12374692", float:toFixedString(-0.1237469219, 8));
    assertEquality("-0.0124", float:toFixedString(-0.0123654, 4));
    assertEquality("-0.01236540", float:toFixedString(-0.0123654, 8));
    assertEquality("-0.01237", float:toFixedString(-0.0123654, 5));
    assertEquality("-0.00000006", float:toFixedString(-0.0000000564, 8));
    assertEquality("-0.0000001", float:toFixedString(-0.0000000564, 7));
    assertEquality("0.00000", float:toFixedString(-0, 5));
    assertEquality("0.000000000000000", float:toFixedString(-0, 15));

    assertEquality("-45362.1233399999982794", float:toFixedString(-45362.12334f, 16));
    assertEquality("-0.1237469219000000", float:toFixedString(-0.1237469219f, 16));
    assertEquality("-0.12374692", float:toFixedString(-0.1237469219F, 8));
    assertEquality("-0.0124", float:toFixedString(-0.0123654F, 4));
    assertEquality("-0.01236540", float:toFixedString(-0.0123654f, 8));
    assertEquality("-0.01237", float:toFixedString(-0.0123654f, 5));
    assertEquality("-0.00000006", float:toFixedString(-0.0000000564F, 8));
    assertEquality("-0.0000001", float:toFixedString(-0.0000000564F, 7));
    assertEquality("0.00000", float:toFixedString(-0f, 5));
    assertEquality("0.000000000000000", float:toFixedString(-0F, 15));
}

function testToFixedStringWithVerySmallAndLargeNegativeFloats() {
    float a = -2.5E-17;
    assertEquality("-0.00000000000000002", float:toFixedString(a, 17));
    assertEquality("0.0000000000", float:toFixedString(a, 10));
    assertEquality("0.0000", float:toFixedString(a, 4));

    float b = -2.5E+17;
    assertEquality("-250000000000000000.00000000000000000", float:toFixedString(b, 17));
    assertEquality("-250000000000000000.0000000000", float:toFixedString(b, 10));
    assertEquality("-250000000000000000.0000", float:toFixedString(b, 4));

    float c = -1.423223E6;
    assertEquality("-1423223.00000000000000000", c.toFixedString(17));
    assertEquality("-1423223.0000000000", c.toFixedString(10));
    assertEquality("-1423223.0000", c.toFixedString(4));

    float d = -0.9E-10f;
    assertEquality("-0.00000000009000000", d.toFixedString(17));
    assertEquality("-0.000000000090", d.toFixedString(12));
    assertEquality("0.0000", d.toFixedString(4));
}

function testToFixedStringWithHexaDecimalFloatingPoints() {
    float a = 0xabcff;
    assertEquality("703743.00000000000000000", a.toFixedString(17));
    assertEquality("703743.0000000000", a.toFixedString(10));
    assertEquality("703743.0000", a.toFixedString(4));

    float b = 0xAB126fa;
    assertEquality("179382010.00000000000000000", b.toFixedString(17));
    assertEquality("179382010.0000000000", b.toFixedString(10));
    assertEquality("179382010.0000", b.toFixedString(4));

    a = -0xabcff;
    assertEquality("-703743.00000000000000000", a.toFixedString(17));
    assertEquality("-703743.0000000000", a.toFixedString(10));
    assertEquality("-703743.0000", a.toFixedString(4));

    b = -0xAB126fa;
    assertEquality("-179382010.00000000000000000", b.toFixedString(17));
    assertEquality("-179382010.0000000000", b.toFixedString(10));
    assertEquality("-179382010.0000", b.toFixedString(4));

    a = 0x1.e412904862198p4;
    assertEquality("30.25453213000000119", a.toFixedString(17));
    assertEquality("30.2545321300", a.toFixedString(10));
    assertEquality("30.2545", a.toFixedString(4));

    b = 0x1.cbe3p17;
    assertEquality("235462.00000000000000000", b.toFixedString(17));
    assertEquality("235462.0000000000", b.toFixedString(10));
    assertEquality("235462.0000", b.toFixedString(4));

    a = -0x1.e412904862198p4;
    assertEquality("-30.25453213000000119", a.toFixedString(17));
    assertEquality("-30.2545321300", a.toFixedString(10));
    assertEquality("-30.2545", a.toFixedString(4));

    b = -0x1.cbe3p17;
    assertEquality("-235462.00000000000000000", b.toFixedString(17));
    assertEquality("-235462.0000000000", b.toFixedString(10));
    assertEquality("-235462.0000", b.toFixedString(4));
}

const float f3 = 45362.12334;

type FloatType6 45362.12334;

type FloatType7 45362.12334|2.85743;

function testToExpStringWithPositiveFloat() {
    float a = 45362.12334;
    string b = float:toExpString(a, 16);
    assertEquality("4.5362123339999998e+4", b);
    assertEquality("4.54e+4", float:toExpString(a, 2));
    assertEquality("4.536212334e+4", float:toExpString(a, 15));
    assertEquality("4.53621233e+4", float:toExpString(a, 8));
    assertEquality("4.5362e+4", float:toExpString(a, 4));

    string c = a.toExpString(16);
    assertEquality("4.5362123339999998e+4", c);
    assertEquality("4.54e+4", a.toExpString(2));
    assertEquality("4.536212334e+4", a.toExpString(15));
    assertEquality("4.53621233e+4", a.toExpString(8));
    assertEquality("4.5362e+4", a.toExpString(4));

    45362.12334 d = 45362.12334;
    assertEquality("4.5362123339999998e+4", d.toExpString(16));
    assertEquality("4.54e+4", d.toExpString(2));
    assertEquality("4.536212334e+4", d.toExpString(15));
    assertEquality("4.53621233e+4", d.toExpString(8));
    assertEquality("4.5362e+4", d.toExpString(4));

    assertEquality("4.5362123339999998e+4", f3.toExpString(16));
    assertEquality("4.54e+4", f3.toExpString(2));
    assertEquality("4.536212334e+4", f3.toExpString(15));
    assertEquality("4.53621233e+4", f3.toExpString(8));
    assertEquality("4.5362e+4", f3.toExpString(4));

    FloatType6 e = 45362.12334;
    assertEquality("4.5362123339999998e+4", f3.toExpString(16));
    assertEquality("4.54e+4", f3.toExpString(2));
    assertEquality("4.536212334e+4", f3.toExpString(15));
    assertEquality("4.53621233e+4", f3.toExpString(8));
    assertEquality("4.5362e+4", f3.toExpString(4));

    FloatType7 f = 45362.12334;
    assertEquality("4.5362123339999998e+4", f.toExpString(16));
    assertEquality("4.54e+4", f.toExpString(2));
    assertEquality("4.536212334e+4", f.toExpString(15));
    assertEquality("4.53621233e+4", f.toExpString(8));
    assertEquality("4.5362e+4", f.toExpString(4));

    FloatType3 g = 45362.12334;
    assertEquality("4.5362123339999998e+4", float:toExpString(g, 16));
    assertEquality("4.54e+4", float:toExpString(g, 2));
    assertEquality("4.536212334e+4", float:toExpString(g, 15));
    assertEquality("4.53621233e+4", float:toExpString(g, 8));
    assertEquality("4.5362e+4", float:toExpString(g, 4));
}

const float f4 = -45362.12334;

type FloatType8 -45362.12334;

type FloatType9 -45362.12334|-2.85743;

function testToExpStringWithNegativeFloat() {
    float a = -45362.12334;
    string b = float:toExpString(a, 16);
    assertEquality("-4.5362123339999998e+4", b);
    assertEquality("-4.54e+4", float:toExpString(a, 2));
    assertEquality("-4.536212334e+4", float:toExpString(a, 15));
    assertEquality("-4.53621233e+4", float:toExpString(a, 8));
    assertEquality("-4.5362e+4", float:toExpString(a, 4));

    string c = a.toExpString(16);
    assertEquality("-4.5362123339999998e+4", c);
    assertEquality("-4.54e+4", a.toExpString(2));
    assertEquality("-4.536212334e+4", a.toExpString(15));
    assertEquality("-4.53621233e+4", a.toExpString(8));
    assertEquality("-4.5362e+4", a.toExpString(4));

    -45362.12334 d = -45362.12334;
    assertEquality("-4.5362123339999998e+4", d.toExpString(16));
    assertEquality("-4.54e+4", d.toExpString(2));
    assertEquality("-4.536212334e+4", d.toExpString(15));
    assertEquality("-4.53621233e+4", d.toExpString(8));
    assertEquality("-4.5362e+4", d.toExpString(4));

    assertEquality("-4.5362123339999998e+4", f4.toExpString(16));
    assertEquality("-4.54e+4", f4.toExpString(2));
    assertEquality("-4.536212334e+4", f4.toExpString(15));
    assertEquality("-4.53621233e+4", f4.toExpString(8));
    assertEquality("-4.5362e+4", f4.toExpString(4));

    FloatType8 e = -45362.12334;
    assertEquality("-4.5362123339999998e+4", f4.toExpString(16));
    assertEquality("-4.54e+4", f4.toExpString(2));
    assertEquality("-4.536212334e+4", f4.toExpString(15));
    assertEquality("-4.53621233e+4", f4.toExpString(8));
    assertEquality("-4.5362e+4", f4.toExpString(4));

    FloatType9 f = -45362.12334;
    assertEquality("-4.5362123339999998e+4", f.toExpString(16));
    assertEquality("-4.54e+4", f.toExpString(2));
    assertEquality("-4.536212334e+4", f.toExpString(15));
    assertEquality("-4.53621233e+4", f.toExpString(8));
    assertEquality("-4.5362e+4", f.toExpString(4));

    FloatType3 g = -45362.12334;
    assertEquality("-4.5362123339999998e+4", float:toExpString(g, 16));
    assertEquality("-4.54e+4", float:toExpString(g, 2));
    assertEquality("-4.536212334e+4", float:toExpString(g, 15));
    assertEquality("-4.53621233e+4", float:toExpString(g, 8));
    assertEquality("-4.5362e+4", float:toExpString(g, 4));
}

function testToExpStringWithInfinity() {
    assertEquality("Infinity", float:toExpString(float:Infinity, 16));
    assertEquality("Infinity", float:toExpString(float:Infinity, 8));
    assertEquality("Infinity", float:toExpString(float:Infinity, 4));
    assertEquality("Infinity", float:toExpString(float:Infinity, 2));

    assertEquality("Infinity", float:Infinity.toExpString(16));
    assertEquality("Infinity", float:Infinity.toExpString(8));
    assertEquality("Infinity", float:Infinity.toExpString(4));
    assertEquality("Infinity", float:Infinity.toExpString(2));
}

function testToExpStringWithNaN() {
    assertEquality("NaN", float:toExpString(float:NaN, 16));
    assertEquality("NaN", float:toExpString(float:NaN, 8));
    assertEquality("NaN", float:toExpString(float:NaN, 4));
    assertEquality("NaN", float:toExpString(float:NaN, 2));

    assertEquality("NaN", float:NaN.toExpString(16));
    assertEquality("NaN", float:NaN.toExpString(8));
    assertEquality("NaN", float:NaN.toExpString(4));
    assertEquality("NaN", float:NaN.toExpString(2));
}

function testToExpStringWhenFractionDigitsIsLessThanZero() {
    float a = 5.7;

    string|error b = trap a.toExpString(-2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toExpString(a, -2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toExpString(a, -(+2));
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }

    b = trap float:toExpString(a, ~2);
    assertEquality(true, b is error);
    if (b is error) {
        assertEquality("{ballerina/lang.float}InvalidFractionDigits", b.message());
        assertEquality("fraction digits cannot be less than 0", <string> checkpanic b.detail()["message"]);
    }
}

function testToExpStringWhenFractionDigitsIsZero() {
    float a = 45362.12334;
    string b = float:toExpString(a, 0);
    assertEquality("5e+4", b);
    assertEquality("5e+4", a.toExpString(0));

    float c = -45362.12334;
    string d = float:toExpString(c, 0);
    assertEquality("-5e+4", d);
    assertEquality("-5e+4", c.toExpString(0));
}

function testToExpStringWhenFractionDigitsIsNil() {
    float a = 45362.12334;
    string b = float:toExpString(a, ());
    assertEquality("4.536212334e+4", b);
    assertEquality("4.536212334e+4", a.toExpString(()));

    float c = -45362.12334;
    string d = float:toExpString(c, ());
    assertEquality("-4.536212334e+4", d);
    assertEquality("-4.536212334e+4", c.toExpString(()));
}

function testToExpStringWhenFractionDigitsIsVeryLargeInt() {
    float a = 45362.12334;
    string b = float:toExpString(a, 2147483648);
    assertEquality("4.53621233399999982793815433979034423828125e+4", b);
    assertEquality("4.53621233399999982793815433979034423828125e+4", a.toExpString(2147483647 + 1));

    float c = -45362.12334;
    string d = float:toExpString(c, 2147483648 + 1);
    assertEquality("-4.53621233399999982793815433979034423828125e+4", d);
    assertEquality("-4.53621233399999982793815433979034423828125e+4", c.toExpString(2147483647 + 1));
}

function testToExpStringWhenFractionDigitsIsIntMax() {
    float a = 45362.12334;
    string b = float:toExpString(a, 9223372036854775807);
    assertEquality("4.53621233399999982793815433979034423828125e+4", b);
    assertEquality("4.53621233399999982793815433979034423828125e+4", a.toExpString(int:MAX_VALUE));

    float c = -45362.12334;
    string d = float:toExpString(c, 9223372036854775807);
    assertEquality("-4.53621233399999982793815433979034423828125e+4", d);
    assertEquality("-4.53621233399999982793815433979034423828125e+4", c.toExpString(int:MAX_VALUE));
}

function assertEquality(any|error expected, any|error actual) {
    if expected is anydata && actual is anydata && expected == actual {
        return;
    }

    if expected === actual {
        return;
    }

    string expectedValAsString = expected is error ? expected.toString() : expected.toString();
    string actualValAsString = actual is error ? actual.toString() : actual.toString();
    panic error("expected '" + expectedValAsString + "', found '" + actualValAsString + "'");
}

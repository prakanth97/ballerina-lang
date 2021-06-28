// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/jballerina.java;

class ClassWithExternalMethodOne {
    private int[] i = [];

    function f1() {
        lock {
            self.i.push(1);
        }
    }

    isolated function f2() = @java:Method {
        'class: "org.ballerinalang.test.isolation.IsolationInferenceTest"
    } external;
}

class ClassWithExternalMethodTwo {
    private int[] i = [];

    function f1() {
        lock {
            self.i.push(1);
        }
    }

    function f2() = @java:Method {
        'class: "org.ballerinalang.test.isolation.IsolationInferenceTest"
    } external;
}

class ClassWithObjectConstructorExprWithExternalMethod {
    private int[] i = [];

    function f1() returns object {} {
        lock {
            self.i.push(1);
            return object {
                final int x = 1;
            };
        }
    }

    function f3() returns object {} {
        _ = self.f1();
        var ob = object {
            private int i = 0;

            function f2() = @java:Method {
                'class: "org.ballerinalang.test.isolation.IsolationInferenceTest"
            } external;

            function f4() returns int {
                lock {
                    return self.i;
                }
            }
        };
        return ob;
    }
}

class ClassWithPotentiallyIsolatedFieldNegative {
    final (int[] & readonly)|NonIsolatedClass|string x = f1();

    function init() {
    }

    function func() {
        var s = self.x;
    }
}

class NonIsolatedClass {
    final int[] i = arr;

    function init() {
    }
}

int[] arr = [];

function f1() returns NonIsolatedClass {
    _ = arr;
    return new;
}

type Obj object {
    int i;
    int[] j;
};

class Class {
    final string k = "";
    string[] l = [];
}

class NonIsolatedClassIncludingObjectAndClassWithNonFinalNonPrivateOverridingField {
    *Obj;
    *Class;

    int i = 1;
    final readonly & int[] j = [];
    final string k;
    final string[] & readonly l;

    function init(string[] arg) {
        self.k = "default";
        self.l = arg.cloneReadOnly();
    }

    function access() returns anydata {
        lock {
            return self.k + self.l.toString();
        }
    }
}

class NonIsolatedClassIncludingObjectAndClassNotOverridingField {
    *Obj;
    *Class;

    final readonly & int[] j = [];
    final string k;
    final string[] & readonly l;

    function init(string[] arg) {
        self.i = 0;
        self.k = "default";
        self.l = arg.cloneReadOnly();
    }

    function access() returns anydata {
        lock {
            return self.k + self.l.toString();
        }
    }
}

function testIsolatedInference() {
    ClassWithExternalMethodOne a = new;
    assertFalse(<any> a is isolated object {});
    assertTrue(isMethodIsolated(a, "f1"));
    assertTrue(isMethodIsolated(a, "f2"));

    ClassWithExternalMethodTwo b = new;
    assertFalse(<any> b is isolated object {});
    assertTrue(isMethodIsolated(b, "f1"));
    assertFalse(isMethodIsolated(b, "f2"));

    ClassWithObjectConstructorExprWithExternalMethod c = new;
    assertTrue(<any> c is isolated object {});
    assertTrue(isMethodIsolated(c, "f1"));
    assertTrue(isMethodIsolated(c, "f3"));
    object {} ob1 = c.f1();
    assertTrue(ob1 is isolated object {});
    object {} ob2 = c.f3();
    assertFalse(ob2 is isolated object {});
    assertFalse(isMethodIsolated(ob2, "f2"));
    assertTrue(isMethodIsolated(ob2, "f4"));

    ClassWithPotentiallyIsolatedFieldNegative d = new;
    assertFalse(<any> d is isolated object {});
    assertFalse(isMethodIsolated(d, "init"));
    assertTrue(isMethodIsolated(d, "func"));

    NonIsolatedClass e = new;
    assertFalse(<any> e is isolated object {});
    assertFalse(isMethodIsolated(e, "init"));

    assertFalse(f1 is isolated function () returns NonIsolatedClass);

    NonIsolatedClassIncludingObjectAndClassWithNonFinalNonPrivateOverridingField f = new ([]);
    assertFalse(<any> f is isolated object {});
    assertTrue(isMethodIsolated(f, "init"));
    assertTrue(isMethodIsolated(f, "access"));

    NonIsolatedClassIncludingObjectAndClassNotOverridingField g = new ([]);
    assertFalse(<any> g is isolated object {});
}

isolated function assertTrue(any|error actual) {
    assertEquality(true, actual);
}

isolated function assertFalse(any|error actual) {
    assertEquality(false, actual);
}

isolated function assertEquality(any|error expected, any|error actual) {
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

isolated function isResourceIsolated(service object {}|typedesc val, string resourceMethodName,
     string resourcePath) returns boolean = @java:Method {
                        'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                        paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString",
                                        "io.ballerina.runtime.api.values.BString"]
                    } external;

isolated function isRemoteMethodIsolated(object {}|typedesc val, string methodName) returns boolean = @java:Method {
                                            'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                                             paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString"]
                                        } external;

isolated function isMethodIsolated(object {}|typedesc val, string methodName) returns boolean = @java:Method {
                                            'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                                            paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString"]
                                        } external;

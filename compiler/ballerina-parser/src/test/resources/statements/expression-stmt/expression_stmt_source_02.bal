function foo() {
    [...n];
    [...n, 1, 2];
    [1, 2, ...n];
    [1, 2, ...[3,4]];
    [1, 2, ...n, 5, 6];
    [1, 2, ...[4,5], 5, 6];
    [1, 2, ...n1, ...n2];
    [...[2, ...["bar", ...n1, [...n2], "baz", 6]], ...n3];
    {foo: [...n]};
    {foo: [...[1, 2]]};
}

function foo() {
    [(int)[], int] t1 = [[23, 700], 17];
    [(int|byte)[], int] t2 = [[23, 700], 17];
    [(int|byte)[]...] t3 = [[17], [23, 700]];
    [((int|byte)[])...] t4 = [[17], [23, 700]];
    [((((int|byte))[]))...] t5 = [[17], [23, 700]];
    [((int)[]), int] t6 = [[17], 700];
}

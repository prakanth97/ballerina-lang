/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.types.typeops;

import io.ballerina.types.Atom;
import io.ballerina.types.Bdd;
import io.ballerina.types.ComplexSemType;
import io.ballerina.types.Conjunction;
import io.ballerina.types.Context;
import io.ballerina.types.Core;
import io.ballerina.types.FixedLengthArray;
import io.ballerina.types.ListAtomicType;
import io.ballerina.types.SemType;
import io.ballerina.types.SubtypeData;
import io.ballerina.types.UniformTypeBitSet;
import io.ballerina.types.subtypedata.BddAllOrNothing;
import io.ballerina.types.subtypedata.BddNode;
import io.ballerina.types.subtypedata.IntSubtype;
import io.ballerina.types.subtypedata.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.ballerina.types.Common.isListBitsSet;
import static io.ballerina.types.Common.isNothingSubtype;
import static io.ballerina.types.Conjunction.and;
import static io.ballerina.types.Core.diff;
import static io.ballerina.types.Core.getComplexSubtypeData;
import static io.ballerina.types.Core.isEmpty;
import static io.ballerina.types.Core.isNever;
import static io.ballerina.types.Core.union;
import static io.ballerina.types.PredefinedType.NEVER;
import static io.ballerina.types.PredefinedType.TOP;
import static io.ballerina.types.UniformTypeCode.UT_LIST_RO;
import static io.ballerina.types.UniformTypeCode.UT_LIST_RW;
import static io.ballerina.types.subtypedata.IntSubtype.intSubtypeContains;
import static io.ballerina.types.typeops.ListCommonOps.fixedArrayAnyEmpty;
import static io.ballerina.types.typeops.ListCommonOps.fixedArrayShallowCopy;
import static io.ballerina.types.typeops.ListCommonOps.listIntersectWith;
import static io.ballerina.types.typeops.ListCommonOps.listMemberAt;

/**
 * Class to hold functions ported from `listProj.bal` file.
 *
 * @since 3.0.0
 */
public class ListProj {
    // Untested full implementation of list projection.

    // Based on listMemberType
    public static SemType listProj(Context cx, SemType t, SemType k) {
        if (t instanceof UniformTypeBitSet) {
            return isListBitsSet((UniformTypeBitSet) t) ? TOP : NEVER;
        } else {
            SubtypeData keyData = Core.intSubtype(k);
            if (isNothingSubtype(keyData)) {
                return NEVER;
            }
            return union(listProjBdd(cx,
                                     keyData,
                                     (Bdd) getComplexSubtypeData((ComplexSemType) t, UT_LIST_RO),
                                     null, null),
                         listProjBdd(cx,
                                     keyData,
                                     (Bdd) getComplexSubtypeData((ComplexSemType) t, UT_LIST_RW),
                                     null, null));
        }
    }

    // Based on bddEvery
    static SemType listProjBdd(Context cx, SubtypeData k, Bdd b, Conjunction pos, Conjunction neg) {
        if (b instanceof BddAllOrNothing) {
            return ((BddAllOrNothing) b).isAll() ? listProjPath(cx, k, pos, neg) : NEVER;
        } else {
            BddNode bddNode = (BddNode) b;
            return union(listProjBdd(cx, k, bddNode.left, and(bddNode.atom, pos), neg),
                         union(listProjBdd(cx, k, bddNode.middle, pos, neg),
                               listProjBdd(cx, k, bddNode.right, pos, and(bddNode.atom, neg))));
        }
    }

    // Based on listFormulaIsEmpty
    static SemType listProjPath(Context cx, SubtypeData k, Conjunction pos, Conjunction neg) {
        FixedLengthArray members;
        SemType rest;
        if (pos == null) {
            members = FixedLengthArray.empty();
            rest = TOP;
        } else {
            // combine all the positive tuples using intersection
            ListAtomicType lt = cx.listAtomType(pos.atom);
            members = lt.members;
            rest = lt.rest;
            Conjunction p = pos.next;
            // the neg case is in case we grow the array in listInhabited
            if (p != null || neg != null) {
                members = fixedArrayShallowCopy(members);
            }

            while (true) {
                if (p == null) {
                    break;
                } else {
                    Atom d = p.atom;
                    p = p.next;
                    lt = cx.listAtomType(d);
                    ListCommonOps.FixedLengthArraySemtypePair intersected = listIntersectWith(members, rest,
                            lt.members, lt.rest);
                    if (intersected == null) {
                        return NEVER;
                    }
                    members = intersected.members;
                    rest = intersected.rest;
                }
            }
            if (fixedArrayAnyEmpty(cx, members)) {
                return NEVER;
            }
            // Ensure that we can use isNever on rest in listInhabited
            if (rest != NEVER && isEmpty(cx, rest)) {
                rest = NEVER;
            }
        }
        // return listProjExclude(cx, k, members, rest, listConjunction(cx, neg));
        List<Integer> indices = ListCommonOps.listSamples(cx, members, rest, neg);
        int[] keyIndices;
        IntListPair projSamples = listProjSamples(indices, k);
        SemtypesIntPair sampleTypes = ListCommonOps.listSampleTypes(cx, members, rest, indices);
        return listProjExclude(cx, projSamples.indices.toArray(new Integer[0]),
                projSamples.keyIndices.toArray(new Integer[0]), sampleTypes.memberTypes.toArray(new SemType[0]),
                sampleTypes.nRequired, neg);
    }

    // In order to adapt listInhabited to do projection, we need
    // to know which samples correspond to keys and to ensure that
    // every equivalence class that overlaps with a key has a sample in the
    // intersection.
    // Here we add samples for both ends of each range. This doesn't handle the
    // case where the key is properly within a partition: but that is handled
    // because we already have a sample of the end of the partition.
    private static IntListPair listProjSamples(List<Integer> indices, SubtypeData k) {
        List<IntBooleanPair> v = new ArrayList<>();
        for (int i : indices) {
            v.add(IntBooleanPair.from(i, intSubtypeContains(k, i)));
        }
        if (k instanceof IntSubtype) {
            for (Range range : ((IntSubtype) k).ranges) {
                long max = range.max;
                if (range.max >= 0) {
                    v.add(new IntBooleanPair((int) max, true));
                    int min = Integer.max(0, (int) range.min);
                    if (min < max) {
                        v.add(IntBooleanPair.from(min, true));
                    }
                }
            }
        }
        Collections.sort(v, new SortIntBool());
        List<Integer> indices1 = new ArrayList<>();
        List<Integer> keyIndices = new ArrayList<>();
        for (var ib : v) {
            if (indices1.size() == 0 || ib.i != indices1.get(indices1.size() - 1)) {
                if (ib.inKey) {
                    keyIndices.add(indices1.size());
                }
                indices1.add(ib.i);
            }
        }
        return IntListPair.from(indices1, keyIndices);
    }

    // `keyIndices` are the indices in `memberTypes` of those samples that belong to the key type.
    // Based on listInhabited
    // Corresponds to phi^x in AMK tutorial generalized for list types.
    static SemType listProjExclude(Context cx, Integer[] indices, Integer[] keyIndices, SemType[] memberTypes,
                                   int nRequired, Conjunction neg) {
        SemType p = NEVER;
        if (neg == null) {
            int len = memberTypes.length;
            for (int k : keyIndices) {
                if (k < len) {
                    p = union(p, memberTypes[k]);
                }
            }
        } else {
            final ListAtomicType nt = cx.listAtomType(neg.atom);
            if (nRequired > 0 && isNever(listMemberAt(nt.members, nt.rest, indices[nRequired - 1]))) {
                return listProjExclude(cx, indices, keyIndices, memberTypes, nRequired, neg.next);
            }
            int negLen = nt.members.fixedLength;
            if (negLen > 0) {
                int len = memberTypes.length;
                if (len < indices.length && indices[len] < negLen) {
                    return listProjExclude(cx, indices, keyIndices, memberTypes, nRequired, neg.next);
                }
                for (int i = nRequired; i < memberTypes.length; i++) {
                    if (indices[i] >= negLen) {
                        break;
                    }
                    SemType[] t = Arrays.copyOfRange(memberTypes, 0, i);
                    p = union(p, listProjExclude(cx, indices, keyIndices, t, nRequired, neg.next));
                }
            }
            for (int i = 0; i < memberTypes.length; i++) {
                SemType d = diff(memberTypes[i], listMemberAt(nt.members, nt.rest, indices[i]));
                if (!Core.isEmpty(cx, d)) {
                    SemType[] t = memberTypes.clone();
                    t[i] = d;
                    // We need to make index i be required
                    p = union(p, listProjExclude(cx, indices, keyIndices, t, Integer.max(nRequired, i + 1),
                            neg.next));
                }
            }
        }
        return p;
    }
}

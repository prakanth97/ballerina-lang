/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.ballerinalang.compiler.semantics.model.symbols;

import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;

/**
 * {@link BServiceSymbol} represents a service symbol in a scope.
 *
 * @since 0.965.0
 */
public class BServiceSymbol extends BTypeSymbol {

    public BObjectTypeSymbol objectType;

    public BServiceSymbol(int flags, Name name, PackageID pkgID, BType type, BSymbol owner, BObjectTypeSymbol obtype) {
        super(SymTag.SERVICE, flags, name, pkgID, type, owner);
        this.objectType = obtype;
    }

    @Override
    public BServiceSymbol createLabelSymbol() {
        BServiceSymbol copy = Symbols
                .createServiceSymbol(flags, Names.EMPTY, pkgID, type, owner);
        copy.isLabel = true;
        return copy;
    }
}

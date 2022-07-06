package io.ballerina.compiler.api.impl.type.builders;

import io.ballerina.compiler.api.TypeBuilder;
import io.ballerina.compiler.api.impl.symbols.AbstractTypeSymbol;
import io.ballerina.compiler.api.impl.symbols.BallerinaSymbol;
import io.ballerina.compiler.api.impl.symbols.TypesFactory;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.ConstantSymbol;
import io.ballerina.compiler.api.symbols.EnumSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ballerinalang.model.symbols.SymbolOrigin.COMPILED_SOURCE;

public class BallerinaUnionTypeBuilder implements TypeBuilder.UNION {

    private final TypesFactory typesFactory;
    private final SymbolTable symTable;
    private final List<TypeSymbol> memberTypes = new ArrayList<>();

    public BallerinaUnionTypeBuilder(CompilerContext context) {
        typesFactory = TypesFactory.getInstance(context);
        symTable = SymbolTable.getInstance(context);
    }

    @Override
    public TypeBuilder.UNION withMemberTypes(TypeSymbol... memberTypes) {
        this.memberTypes.clear();
        this.memberTypes.addAll(Arrays.asList(memberTypes));

        return this;
    }

    @Override
    public UnionTypeSymbol build() {

        BTypeSymbol unionSymbol = Symbols.createTypeSymbol(SymTag.UNION_TYPE, Flags.PUBLIC, Names.EMPTY,
                symTable.rootPkgSymbol.pkgID, null, symTable.rootPkgSymbol, symTable.builtinPos, COMPILED_SOURCE);

        BUnionType unionType = BUnionType.create(unionSymbol, getMemberBTypes());
        UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) typesFactory.getTypeDescriptor(unionType);
        memberTypes.clear();

        return unionTypeSymbol;
    }

    private BType[] getMemberBTypes() {
        if (memberTypes.size() == 0) {
            throw new IllegalArgumentException("Member types can not be empty");
        }

        List<BType> bTypeList = new ArrayList<>();
        for (TypeSymbol memberType : memberTypes) {
            if (memberType instanceof AbstractTypeSymbol) {
                bTypeList.add(((AbstractTypeSymbol) memberType).getBType());
                continue;
            }

            if ((memberType instanceof ClassSymbol
                    || memberType instanceof ConstantSymbol
                    || memberType instanceof EnumSymbol)
                    && memberType instanceof BallerinaSymbol) {
                bTypeList.add(((BallerinaSymbol) memberType).getInternalSymbol().getType());
                continue;
            }

            throw new IllegalArgumentException("Invalid member type provided.");
        }

        return bTypeList.toArray(new BType[0]);
    }
}

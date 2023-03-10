package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.runtime.api.values.BString;
import io.ballerina.tools.diagnostics.DiagnosticCode;
import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.MarkdownDocAttachment;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.SymbolOrigin;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.tree.expressions.RecordLiteralNode;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.parser.BLangAnonymousModelHelper;
import org.wso2.ballerinalang.compiler.parser.BLangMissingNodesHelper;
import org.wso2.ballerinalang.compiler.parser.NodeCloner;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.TypeVisitor;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.*;
import org.wso2.ballerinalang.compiler.semantics.model.types.*;
import org.wso2.ballerinalang.compiler.tree.*;
import org.wso2.ballerinalang.compiler.tree.expressions.*;
import org.wso2.ballerinalang.compiler.tree.types.BLangRecordTypeNode;
import org.wso2.ballerinalang.compiler.util.*;
import org.wso2.ballerinalang.util.Flags;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.BiFunction;

import static org.ballerinalang.model.symbols.SymbolOrigin.SOURCE;
import static org.ballerinalang.model.symbols.SymbolOrigin.VIRTUAL;

/**
 * @since 2201.4.0
 */
public class ConstantTypeChecker extends SimpleBLangNodeAnalyzer<ConstantTypeChecker.AnalyzerData> {
    private static final CompilerContext.Key<ConstantTypeChecker> CONSTANT_TYPE_CHECKER_KEY = new CompilerContext.Key<>();

    private final SymbolTable symTable;
    private final Names names;
    private final NodeCloner nodeCloner;
    private final SymbolResolver symResolver;
    private final BLangDiagnosticLog dlog;
    private final BLangMissingNodesHelper missingNodesHelper;
    private final Types types;
    private final TypeChecker typeChecker;
    private final TypeResolver typeResolver;
    private final ConstantTypeChecker.FillMembers fillMembers;
    private BLangAnonymousModelHelper anonymousModelHelper;

    public ConstantTypeChecker(CompilerContext context) {
        context.put(CONSTANT_TYPE_CHECKER_KEY, this);

        this.symTable = SymbolTable.getInstance(context);
        this.names = Names.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.nodeCloner = NodeCloner.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.types = Types.getInstance(context);
        this.anonymousModelHelper = BLangAnonymousModelHelper.getInstance(context);
        this.typeChecker = TypeChecker.getInstance(context);
        this.typeResolver = TypeResolver.getInstance(context);
        this.missingNodesHelper = BLangMissingNodesHelper.getInstance(context);
        this.fillMembers = FillMembers.getInstance(context);
    }

    public static ConstantTypeChecker getInstance(CompilerContext context) {
        ConstantTypeChecker constTypeChecker = context.get(CONSTANT_TYPE_CHECKER_KEY);
        if (constTypeChecker == null) {
            constTypeChecker = new ConstantTypeChecker(context);
        }

        return constTypeChecker;
    }

    public BType checkConstExpr(BLangExpression expr, SymbolEnv env, AnalyzerData data) {
        return checkConstExpr(expr, env, symTable.noType, data);
    }

    public BType checkConstExpr(BLangExpression expr, AnalyzerData data) {
        return checkConstExpr(expr, data.env, symTable.noType, data);
    }

    public BType checkConstExpr(BLangExpression expr, SymbolEnv env, BType expType, AnalyzerData data) {
        return checkConstExpr(expr, env, expType, DiagnosticErrorCode.INCOMPATIBLE_TYPES, data);
    }

    public BType checkConstExpr(BLangExpression expr, BType expType, AnalyzerData data) {
        return checkConstExpr(expr, data.env, expType, DiagnosticErrorCode.INCOMPATIBLE_TYPES, data);
    }

    public BType checkConstExpr(BLangExpression expr, SymbolEnv env) {
        return checkConstExpr(expr, env, symTable.noType, new Stack<>());
    }

    public BType checkConstExpr(BLangExpression expr, SymbolEnv env, BType expType, Stack<SymbolEnv> prevEnvs) {
        final AnalyzerData data = new AnalyzerData();
        data.env = env;
        data.prevEnvs = prevEnvs;
        data.commonAnalyzerData.queryFinalClauses = new Stack<>();
        data.commonAnalyzerData.queryEnvs = new Stack<>();
        return checkConstExpr(expr, env, expType, DiagnosticErrorCode.INCOMPATIBLE_TYPES, data);
    }

    public BType checkConstExpr(BLangExpression expr, SymbolEnv env, BType expType, DiagnosticCode diagCode,
                                AnalyzerData data) {
        if (expr.typeChecked) {
            return expr.getBType();
        }

        SymbolEnv prevEnv = data.env;
        BType preExpType = data.expType;
        DiagnosticCode preDiagCode = data.diagCode;
        data.env = env;
        data.diagCode = diagCode;
        data.expType = expType;
        data.isTypeChecked = true;
        expr.expectedType = expType;

        expr.accept(this, data);

        expr.setTypeCheckedType(data.resultType);
        expr.typeChecked = data.isTypeChecked;
        data.env = prevEnv;
        data.expType = preExpType;
        data.diagCode = preDiagCode;

        validateAndSetExprExpectedType(expr, data);

        return data.resultType;
    }

    public void validateAndSetExprExpectedType(BLangExpression expr, AnalyzerData data) {
        if (data.resultType.tag == TypeTags.SEMANTIC_ERROR) {
            return;
        }

        // If the expected type is a map, but a record type is inferred due to the presence of `readonly` fields in
        // the mapping constructor expression, we don't override the expected type.
        if (expr.getKind() == NodeKind.RECORD_LITERAL_EXPR && expr.expectedType != null &&
                Types.getReferredType(expr.expectedType).tag == TypeTags.MAP
                && Types.getReferredType(expr.getBType()).tag == TypeTags.RECORD) {
            return;
        }

        expr.expectedType = data.resultType;
    }

    @Override
    public void analyzeNode(BLangNode node, AnalyzerData data) {
        // Temporarily Added.
        dlog.error(node.pos, DiagnosticErrorCode.CONSTANT_DECLARATION_NOT_YET_SUPPORTED, node);
        data.resultType = symTable.semanticError;
    }

    @Override
    public void visit(BLangPackage node, AnalyzerData data) {

    }

    @Override
    public void visit(BLangLiteral literalExpr, AnalyzerData data) {
        BType literalType = setLiteralValueAndGetType(literalExpr, data.expType, data);
        if (literalType == symTable.semanticError) {
            data.resultType = literalType;
            return;
        }

        if (literalExpr.isFiniteContext) {
            return;
        }

        BType finiteType = getFiniteType(literalExpr.value, data.constantSymbol, literalExpr.pos, literalType);
        if (data.compoundExprs.isEmpty() &&
                types.typeIncompatible(literalExpr.pos, finiteType, data.expType)) {
            data.resultType = symTable.semanticError;
            return;
        }
        data.resultType = finiteType;
    }

    @Override
    public void visit(BLangSimpleVarRef varRefExpr, AnalyzerData data) {
        // Set error type as the actual type.
        BType actualType = symTable.semanticError;

        Name varName = names.fromIdNode(varRefExpr.variableName);
        if (varName == Names.IGNORE) {
            varRefExpr.setBType(this.symTable.anyType);

            // If the variable name is a wildcard('_'), the symbol should be ignorable.
            varRefExpr.symbol = new BVarSymbol(0, true, varName,
                    names.originalNameFromIdNode(varRefExpr.variableName),
                    data.env.enclPkg.symbol.pkgID, varRefExpr.getBType(), data.env.scope.owner,
                    varRefExpr.pos, VIRTUAL);

            data.resultType = varRefExpr.getBType();
            return;
        }

        Name compUnitName = typeChecker.getCurrentCompUnit(varRefExpr);
        varRefExpr.pkgSymbol =
                symResolver.resolvePrefixSymbol(data.env, names.fromIdNode(varRefExpr.pkgAlias), compUnitName);
        if (varRefExpr.pkgSymbol == symTable.notFoundSymbol) {
            varRefExpr.symbol = symTable.notFoundSymbol;
            dlog.error(varRefExpr.pos, DiagnosticErrorCode.UNDEFINED_MODULE, varRefExpr.pkgAlias);
        }

        if (varRefExpr.pkgSymbol != symTable.notFoundSymbol) {
            BSymbol symbol = getSymbolOfVarRef(varRefExpr.pos, data.env, names.fromIdNode(varRefExpr.pkgAlias), varName, data);

            if (symbol == symTable.notFoundSymbol) {
                data.resultType = symTable.semanticError;
                varRefExpr.symbol = symbol; // Set notFoundSymbol
                logUndefinedSymbolError(varRefExpr.pos, varName.value);
                return;
            }

            if ((symbol.tag & SymTag.CONSTANT) == SymTag.CONSTANT) {
                // Check whether the referenced expr is a constant.
                BConstantSymbol constSymbol = (BConstantSymbol) symbol;
                varRefExpr.symbol = constSymbol;
                actualType = symbol.type;
            } else {
                varRefExpr.symbol = symbol;
                dlog.error(varRefExpr.pos, DiagnosticErrorCode.EXPRESSION_IS_NOT_A_CONSTANT_EXPRESSION);
            }
        }

        if (data.compoundExprs.isEmpty() &&
                types.typeIncompatible(varRefExpr.pos, actualType, data.expType)) {
            data.resultType = symTable.semanticError;
            return;
        }
        data.resultType = actualType;
    }

    public void visit(BLangListConstructorExpr listConstructor, AnalyzerData data) {
        BType expType = data.expType;
        if (expType.tag == TypeTags.NONE || expType.tag == TypeTags.READONLY) {
            data.resultType = defineInferredTupleType(listConstructor, expType, data);
            return;
        }
        data.resultType = checkListConstructorCompatibility(data.expType, listConstructor, data);
    }

    public void visit(BLangRecordLiteral.BLangRecordVarNameField varRefExpr, AnalyzerData data) {
        BType actualType = symTable.semanticError;

        Name varName = names.fromIdNode(varRefExpr.variableName);
        if (varName == Names.IGNORE) {
            varRefExpr.setBType(this.symTable.anyType);

            // If the variable name is a wildcard('_'), the symbol should be ignorable.
            varRefExpr.symbol = new BVarSymbol(0, true, varName,
                    names.originalNameFromIdNode(varRefExpr.variableName),
                    data.env.enclPkg.symbol.pkgID, varRefExpr.getBType(), data.env.scope.owner,
                    varRefExpr.pos, VIRTUAL);

            data.resultType = varRefExpr.getBType();
            return;
        }

        Name compUnitName = typeChecker.getCurrentCompUnit(varRefExpr);
        varRefExpr.pkgSymbol =
                symResolver.resolvePrefixSymbol(data.env, names.fromIdNode(varRefExpr.pkgAlias), compUnitName);
        if (varRefExpr.pkgSymbol == symTable.notFoundSymbol) {
            varRefExpr.symbol = symTable.notFoundSymbol;
            dlog.error(varRefExpr.pos, DiagnosticErrorCode.UNDEFINED_MODULE, varRefExpr.pkgAlias);
        }

        if (varRefExpr.pkgSymbol != symTable.notFoundSymbol) {
            BSymbol symbol = getSymbolOfVarRef(varRefExpr.pos, data.env, names.fromIdNode(varRefExpr.pkgAlias), varName, data);

            if (symbol == symTable.notFoundSymbol) {
                data.resultType = symTable.semanticError;
                return;
            }

            if ((symbol.tag & SymTag.CONSTANT) == SymTag.CONSTANT) {
                // Check whether the referenced expr is a constant.
                BConstantSymbol constSymbol = (BConstantSymbol) symbol;
                varRefExpr.symbol = constSymbol;
                actualType = symbol.type;
            } else {
                varRefExpr.symbol = symbol;
                dlog.error(varRefExpr.pos, DiagnosticErrorCode.EXPRESSION_IS_NOT_A_CONSTANT_EXPRESSION);
            }
        }

        data.resultType = types.checkType(varRefExpr, actualType, data.expType);
    }

    public void visit(BLangRecordLiteral recordLiteral, AnalyzerData data) {
        BType expType = data.expType;
        int expTypeTag = Types.getReferredType(expType).tag;

        if (expTypeTag == TypeTags.NONE || expTypeTag == TypeTags.READONLY) {
            data.resultType = defineInferredRecordType(recordLiteral, expType, data);
            return;
        }
        data.resultType = checkMappingConstructorCompatibility(data.expType, recordLiteral, data);
    }

    @Override
    public void visit(BLangBinaryExpr binaryExpr, AnalyzerData data) {
        BType expType = data.expType;

        data.compoundExprs.push(binaryExpr.lhsExpr);
        BType lhsType = checkConstExpr(binaryExpr.lhsExpr, expType, data);
        data.compoundExprs.pop();

        data.compoundExprs.push(binaryExpr.rhsExpr);
        BType rhsType = checkConstExpr(binaryExpr.rhsExpr, expType, data);
        data.compoundExprs.pop();

        Location pos = binaryExpr.pos;

        if (lhsType == symTable.semanticError || rhsType == symTable.semanticError) {
            data.resultType = symTable.semanticError;
            return;
        } else {
            // Resolve the operator symbol and corresponding result type according to different operand types.
            BSymbol opSymbol = symTable.notFoundSymbol;

            if (lhsType.tag == TypeTags.UNION && rhsType.tag == TypeTags.UNION) {
                // Both the operands are unions.
                opSymbol = getOpSymbolBothUnion(binaryExpr.opKind, (BUnionType) lhsType, (BUnionType) rhsType,
                        binaryExpr, data);
            }
            if (lhsType.tag == TypeTags.UNION && rhsType.tag != TypeTags.UNION) {
                // LHS is a union type.
                opSymbol = getOpSymbolLhsUnion(binaryExpr.opKind, (BUnionType) lhsType, rhsType, binaryExpr,
                        data, false);
            }
            if (lhsType.tag != TypeTags.UNION && rhsType.tag == TypeTags.UNION) {
                // RHS is a union type.
                opSymbol = getOpSymbolLhsUnion(binaryExpr.opKind, (BUnionType) rhsType, lhsType, binaryExpr,
                        data, true);
            }
            if (lhsType.tag != TypeTags.UNION && rhsType.tag != TypeTags.UNION) {
                // Both the operands are not unions.
                opSymbol = getOpSymbolBothNonUnion(lhsType, rhsType, binaryExpr, data);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                data.resultType = symTable.semanticError;
                return;
            }
            binaryExpr.opSymbol = (BOperatorSymbol) opSymbol;
        }
        BType resultType = data.resultType;
        BConstantSymbol constantSymbol = data.constantSymbol;

        Object resolvedValue = calculateSingletonValue((BFiniteType) lhsType, (BFiniteType) rhsType, binaryExpr.opKind,
                resultType, binaryExpr.pos);
        if (resolvedValue == null) {
            data.resultType = symTable.semanticError;
            dlog.error(pos, DiagnosticErrorCode.INVALID_CONST_EXPRESSION);
            return;
        }
        BType finiteType = getFiniteType(resolvedValue, constantSymbol, pos, resultType);
        if (data.compoundExprs.isEmpty() && types.typeIncompatible(pos, finiteType, expType)) {
            data.resultType = symTable.semanticError;
            return;
        }
        data.resultType = finiteType;
    }

    public void visit(BLangGroupExpr groupExpr, AnalyzerData data) {
        checkConstExpr(groupExpr.expression, data.expType, data);
    }

    public void visit(BLangUnaryExpr unaryExpr, AnalyzerData data) {
        BType resultType;

        data.compoundExprs.push(unaryExpr.expr);
        BType actualType = checkConstExpr(unaryExpr.expr, data.expType, data);
        data.compoundExprs.pop();

        if (actualType == symTable.semanticError) {
            data.resultType = symTable.semanticError;
            return;
        }

        BSymbol opSymbol = getUnaryOpSymbol(unaryExpr, actualType, data);
        resultType = getBroadType(data.resultType);

        if (opSymbol == symTable.notFoundSymbol) {
            data.resultType = symTable.semanticError;
            return;
        }

        BConstantSymbol constantSymbol = data.constantSymbol;
        Object resolvedValue = evaluateUnaryOperator((BFiniteType) actualType, resultType,
                unaryExpr.operator);
        if (resolvedValue == null) {
            data.resultType = symTable.semanticError;
            return;
        }

        BType finiteType = getFiniteType(resolvedValue, constantSymbol, unaryExpr.pos, resultType);
        if (data.compoundExprs.isEmpty() && types.typeIncompatible(unaryExpr.pos, finiteType, data.expType)) {
            data.resultType = symTable.semanticError;
            return;
        }
        data.resultType = finiteType;
    }

    private BType defineInferredRecordType(BLangRecordLiteral recordLiteral, BType expType, AnalyzerData data) {
        boolean containErrors = false;
        SymbolEnv env = data.env;
        PackageID pkgID = env.enclPkg.symbol.pkgID;
        BRecordTypeSymbol recordSymbol = createRecordTypeSymbol(pkgID, recordLiteral.pos, VIRTUAL, data);
        LinkedHashMap<String, BField> inferredFields = new LinkedHashMap<>();
        List<RecordLiteralNode.RecordField> computedFields = new ArrayList<>();

        for (RecordLiteralNode.RecordField field : recordLiteral.fields) {
            if (field.isKeyValueField()) {
                BLangRecordLiteral.BLangRecordKeyValueField keyValue =
                        (BLangRecordLiteral.BLangRecordKeyValueField) field;
                BLangRecordLiteral.BLangRecordKey key = keyValue.key;

                if (key.computedKey) {
                    // Computed fields can overwrite the existing field values.
                    // Therefore, Computed key fields should be evaluated at the end.
                    // Temporarily added them into a list.
                    computedFields.add(field);
                    continue;
                }

                BLangExpression keyValueExpr = keyValue.valueExpr;
                BType keyValueType = checkConstExpr(keyValueExpr, data);
                BLangExpression keyExpr = key.expr;

                // Add the resolved field.
                if (!addFields(inferredFields, keyValueType, getKeyName(keyExpr), keyExpr.pos, recordSymbol, false)) {
                    containErrors = true;
                }
            } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangRecordLiteral.BLangRecordVarNameField varNameField =
                        (BLangRecordLiteral.BLangRecordVarNameField) field;
                BType varRefType = checkConstExpr(varNameField, data.expType, data);
                if (!addFields(inferredFields, Types.getReferredType(varRefType), getKeyName(varNameField),
                        varNameField.pos, recordSymbol, false)) {
                    containErrors = true;
                }
            } else { // Spread Field
                BLangExpression fieldExpr = ((BLangRecordLiteral.BLangRecordSpreadOperatorField) field).expr;
                BType spreadOpType = checkConstExpr(fieldExpr, data);
                BType type = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOpType));
                if (type.tag != TypeTags.RECORD) {
                    containErrors = true;
                    continue;
                }

                BRecordType recordType = (BRecordType) type;
                for (BField recField : recordType.fields.values()) {
                    if (!addFields(inferredFields, Types.getReferredType(recField.type), recField.name.value,
                            fieldExpr.pos, recordSymbol, false)) {
                        containErrors = true;
                    }
                }
            }
        }

        for (RecordLiteralNode.RecordField field : computedFields) {
            BLangRecordLiteral.BLangRecordKeyValueField keyValue = (BLangRecordLiteral.BLangRecordKeyValueField) field;
            BLangRecordLiteral.BLangRecordKey key = keyValue.key;
            BType fieldName = checkConstExpr(key.expr, data);
            if (fieldName.tag == TypeTags.FINITE && ((BFiniteType) fieldName).getValueSpace().size() == 1) {
                BLangLiteral fieldNameLiteral = (BLangLiteral) ((BFiniteType) fieldName).getValueSpace().iterator().next();
                if (fieldNameLiteral.getBType().tag == TypeTags.STRING) {
                    BType keyValueType = checkConstExpr(keyValue.valueExpr, data);
                    if (!addFields(inferredFields, Types.getReferredType(keyValueType),
                            fieldNameLiteral.getValue().toString(), key.pos, recordSymbol, true)) {
                        containErrors = true;
                    }
                    continue;
                }
            }
            dlog.error(key.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, fieldName, symTable.stringType);
            containErrors = true;
        }

        if (containErrors) {
            return symTable.semanticError;
        }

        BRecordType recordType = new BRecordType(recordSymbol);
        recordType.restFieldType = symTable.noType;
        recordType.fields = inferredFields;
        recordSymbol.type = recordType;
        recordType.tsymbol = recordSymbol;
        recordType.sealed = true;
        if (expType.tag == TypeTags.READONLY) {
            recordType.flags |= Flags.READONLY;
        }
        createTypeDefinition(recordType, data.constantSymbol.pos, data.env);
        return recordType;
    }

    private BType checkMappingConstructorCompatibility(BType expType, BLangRecordLiteral mappingConstructor,
                                                       AnalyzerData data) {
        int tag = expType.tag;
        if (tag == TypeTags.UNION) {
            boolean prevNonErrorLoggingCheck = data.commonAnalyzerData.nonErrorLoggingCheck;
            data.commonAnalyzerData.nonErrorLoggingCheck = true;
            int errorCount = this.dlog.errorCount();
            this.dlog.mute();

            List<BType> compatibleTypes = new ArrayList<>();
            boolean erroredExpType = false;
            for (BType memberType : ((BUnionType) expType).getMemberTypes()) {
                if (memberType == symTable.semanticError) {
                    if (!erroredExpType) {
                        erroredExpType = true;
                    }
                    continue;
                }

                BType listCompatibleMemType = getMappingConstructorCompatibleNonUnionType(memberType, data);
                if (listCompatibleMemType == symTable.semanticError) {
                    continue;
                }

                dlog.resetErrorCount();
                BType memCompatibiltyType = checkMappingConstructorCompatibility(listCompatibleMemType,
                        mappingConstructor, data);

                if (memCompatibiltyType != symTable.semanticError && dlog.errorCount() == 0 &&
                        isUniqueType(compatibleTypes, listCompatibleMemType)) {
                    compatibleTypes.add(listCompatibleMemType);
                }
            }

            data.commonAnalyzerData.nonErrorLoggingCheck = prevNonErrorLoggingCheck;
            dlog.setErrorCount(errorCount);
            if (!prevNonErrorLoggingCheck) {
                this.dlog.unmute();
            }

            if (compatibleTypes.isEmpty()) {
                dlog.error(mappingConstructor.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, mappingConstructor, expType);
                return symTable.semanticError;
            } else if (compatibleTypes.size() != 1) {
                dlog.error(mappingConstructor.pos, DiagnosticErrorCode.AMBIGUOUS_TYPES, expType);
                return symTable.semanticError;
            }
            return checkMappingConstructorCompatibility(compatibleTypes.get(0), mappingConstructor, data);
        }

        if (tag == TypeTags.TYPEREFDESC) {
            BType refType = Types.getReferredType(expType);
            return checkMappingConstructorCompatibility(refType, mappingConstructor, data);
        }

        if (tag == TypeTags.INTERSECTION) {
            return checkMappingConstructorCompatibility(((BIntersectionType) expType).effectiveType,
                    mappingConstructor, data);
        }

        BType possibleType = getMappingConstructorCompatibleNonUnionType(expType, data);

        switch (possibleType.tag) {
            case TypeTags.MAP:
                return validateSpecifiedFieldsAndGetType(mappingConstructor, possibleType, data);
            case TypeTags.RECORD:
                boolean hasAllRequiredFields = validateRequiredFields((BRecordType) possibleType,
                        mappingConstructor.fields,
                        mappingConstructor.pos, data);
                return hasAllRequiredFields ? validateSpecifiedFieldsAndGetType(mappingConstructor, possibleType, data)
                        : symTable.semanticError;
            case TypeTags.READONLY:
                return checkConstExpr(mappingConstructor, possibleType, data);
        }
        return symTable.semanticError;
    }

    private BType getMappingConstructorCompatibleNonUnionType(BType type, AnalyzerData data) {
        switch (type.tag) {
            case TypeTags.MAP:
            case TypeTags.RECORD:
            case TypeTags.READONLY:
                return type;
            case TypeTags.JSON:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.mapJsonType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.mapJsonType, data.env,
                                symTable, anonymousModelHelper, names);
            case TypeTags.ANYDATA:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.mapAnydataType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.mapAnydataType,
                                data.env, symTable, anonymousModelHelper, names);
            case TypeTags.ANY:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.mapAllType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.mapAllType, data.env,
                                symTable, anonymousModelHelper, names);
            case TypeTags.INTERSECTION:
                return ((BIntersectionType) type).effectiveType;
            case TypeTags.TYPEREFDESC:
                BType refType = Types.getReferredType(type);
                BType compatibleType = getMappingConstructorCompatibleNonUnionType(refType, data);
                return compatibleType == refType ? type : compatibleType;
        }
        return symTable.semanticError;
    }

    private BType validateSpecifiedFieldsAndGetType(BLangRecordLiteral mappingConstructor, BType possibleType,
                                                    AnalyzerData data) {
        boolean containErrors = false;
        SymbolEnv env = data.env;
        PackageID pkgID = env.enclPkg.symbol.pkgID;
        BRecordTypeSymbol recordSymbol = createRecordTypeSymbol(pkgID, mappingConstructor.pos, VIRTUAL, data);
        LinkedHashMap<String, BField> inferredFields = new LinkedHashMap<>();
        List<RecordLiteralNode.RecordField> computedFields = new ArrayList<>();

        BType expType;
        BLangExpression exprToCheck;
        switch (possibleType.tag) {
            case TypeTags.MAP:
                expType = ((BMapType) possibleType).constraint;
                for (RecordLiteralNode.RecordField field : mappingConstructor.fields) {
                    if (field.isKeyValueField()) {
                        BLangRecordLiteral.BLangRecordKeyValueField keyValue =
                                (BLangRecordLiteral.BLangRecordKeyValueField) field;
                        BLangRecordLiteral.BLangRecordKey key = keyValue.key;

                        if (key.computedKey) {
                            // Computed fields can overwrite the existing field values.
                            // Therefore, Computed key fields should be evaluated at the end.
                            // Temporarily added them into a list.
                            computedFields.add(field);
                            continue;
                        }

                        exprToCheck = keyValue.valueExpr;
                        if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                            exprToCheck = nodeCloner.cloneNode(keyValue.valueExpr);
                        }
                        BType keyValueType = checkConstExpr(exprToCheck, expType, data);
                        if (keyValueType == symTable.semanticError) {
                            containErrors = true;
                            continue;
                        }
                        BLangExpression keyExpr = key.expr;

                        // Add the resolved field.
                        if (!addFields(inferredFields, keyValueType, getKeyName(keyExpr), keyExpr.pos, recordSymbol,
                                false)) {
                            containErrors = true;
                        }
                    } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                        BLangRecordLiteral.BLangRecordVarNameField varNameField =
                                (BLangRecordLiteral.BLangRecordVarNameField) field;
                        exprToCheck = varNameField;
                        if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                            exprToCheck = nodeCloner.cloneNode(varNameField);
                        }
                        BType varRefType = checkConstExpr(exprToCheck, expType, data);

                        if (varRefType == symTable.semanticError) {
                            containErrors = true;
                            continue;
                        }

                        if (!addFields(inferredFields, Types.getReferredType(varRefType), getKeyName(varNameField),
                                varNameField.pos, recordSymbol, false)) {
                            containErrors = true;
                        }
                    } else { // Spread Field
                        BLangExpression fieldExpr = ((BLangRecordLiteral.BLangRecordSpreadOperatorField) field).expr;
                        BType spreadOpType = checkConstExpr(fieldExpr, data);
                        BType type = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOpType));
                        if (type.tag != TypeTags.RECORD) {
                            containErrors = true;
                            continue;
                        }

                        if (types.checkType(fieldExpr, type, possibleType) == symTable.semanticError) {
                            containErrors = true;
                            continue;
                        }
                        BRecordType recordType = (BRecordType) type;
                        for (BField recField : recordType.fields.values()) {
                            if (!addFields(inferredFields, Types.getReferredType(recField.type), recField.name.value,
                                    fieldExpr.pos, recordSymbol, false)) {
                                containErrors = true;
                            }
                        }
                    }
                }
                for (RecordLiteralNode.RecordField field : computedFields) {
                    BLangRecordLiteral.BLangRecordKeyValueField keyValue = (BLangRecordLiteral.BLangRecordKeyValueField) field;
                    BLangRecordLiteral.BLangRecordKey key = keyValue.key;
                    BType fieldName = checkConstExpr(key.expr, data);
                    if (fieldName.tag == TypeTags.FINITE && ((BFiniteType) fieldName).getValueSpace().size() == 1) {
                        BLangLiteral fieldNameLiteral =
                                (BLangLiteral) ((BFiniteType) fieldName).getValueSpace().iterator().next();
                        if (fieldNameLiteral.getBType().tag == TypeTags.STRING) {
                            exprToCheck = keyValue.valueExpr;
                            if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                                exprToCheck = nodeCloner.cloneNode(keyValue.valueExpr);
                            }
                            BType keyValueType = checkConstExpr(exprToCheck, expType, data);
                            if (!addFields(inferredFields, Types.getReferredType(keyValueType),
                                    fieldNameLiteral.getValue().toString(), key.pos, recordSymbol, true)) {
                                containErrors = true;
                            }
                            continue;
                        }
                    }
                    dlog.error(key.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, fieldName, symTable.stringType);
                    containErrors = true;
                }
                break;
            case TypeTags.RECORD:
                BRecordType expRecordType = (BRecordType) possibleType;
                LinkedHashMap<String, BField> targetFields = expRecordType.fields;
                for (RecordLiteralNode.RecordField field : mappingConstructor.fields) {
                    if (field.isKeyValueField()) {
                        BLangRecordLiteral.BLangRecordKeyValueField keyValue =
                                (BLangRecordLiteral.BLangRecordKeyValueField) field;
                        BLangRecordLiteral.BLangRecordKey key = keyValue.key;
                        if (!targetFields.containsKey(key.toString())) {
                            if (expRecordType.sealed) {
                                dlog.error(keyValue.pos, DiagnosticErrorCode.UNDEFINED_STRUCTURE_FIELD_WITH_TYPE,
                                        key, possibleType.tsymbol.type.getKind().typeName(), possibleType);
                                containErrors = true;
                                continue;
                            } else {
                                expType = expRecordType.restFieldType;
                            }
                        } else {
                            expType = targetFields.get(key.toString()).type;
                        }

                        if (key.computedKey) {
                            // Computed fields can overwrite the existing field values.
                            // Therefore, Computed key fields should be evaluated at the end.
                            // Temporarily added them into a list.
                            computedFields.add(field);
                            continue;
                        }

                        exprToCheck = keyValue.valueExpr;
                        if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                            exprToCheck = nodeCloner.cloneNode(keyValue.valueExpr);
                        }
                        BType keyValueType = checkConstExpr(exprToCheck, expType, data);
                        BLangExpression keyExpr = key.expr;

                        // Add the resolved field.
                        if (!addFields(inferredFields, keyValueType, getKeyName(keyExpr), keyExpr.pos, recordSymbol,
                                false)) {
                            containErrors = true;
                        }
                    } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                        BLangRecordLiteral.BLangRecordVarNameField varNameField =
                                (BLangRecordLiteral.BLangRecordVarNameField) field;
                        String key = field.toString();
                        if (!targetFields.containsKey(key)) {
                            if (expRecordType.sealed) {
                                containErrors = true;
                                dlog.error(varNameField.pos, DiagnosticErrorCode.UNDEFINED_STRUCTURE_FIELD_WITH_TYPE,
                                        key, possibleType.tsymbol.type.getKind().typeName(), possibleType);
                                continue;
                            } else {
                                expType = expRecordType.restFieldType;
                            }
                        } else {
                            expType = targetFields.get(key).type;
                        }
                        exprToCheck = varNameField;
                        if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                            exprToCheck = nodeCloner.cloneNode(varNameField);
                        }
                        BType varRefType = checkConstExpr(exprToCheck, expType, data);
                        if (!addFields(inferredFields, Types.getReferredType(varRefType), getKeyName(varNameField),
                                varNameField.pos, recordSymbol, false)) {
                            containErrors = true;
                        }
                    } else { // Spread Field
                        BLangExpression fieldExpr = ((BLangRecordLiteral.BLangRecordSpreadOperatorField) field).expr;
                        BType spreadOpType = checkConstExpr(fieldExpr, data);
                        BType type = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOpType));
                        if (type.tag != TypeTags.RECORD) {
                            containErrors = true;
                            continue;
                        }

                        BRecordType recordType = (BRecordType) type;
                        for (BField recField : recordType.fields.values()) {
                            if (types.checkType(fieldExpr, recField.type, expRecordType.restFieldType)
                                    == symTable.semanticError) {
                                containErrors = true;
                                continue;
                            }
                            if (!addFields(inferredFields, Types.getReferredType(recField.type), recField.name.value,
                                    fieldExpr.pos, recordSymbol, false)) {
                                containErrors = true;
                            }
                        }
                    }
                }
                for (RecordLiteralNode.RecordField field : computedFields) {
                    BLangRecordLiteral.BLangRecordKeyValueField keyValue = (BLangRecordLiteral.BLangRecordKeyValueField) field;
                    BLangRecordLiteral.BLangRecordKey key = keyValue.key;
                    BType fieldName = checkConstExpr(key.expr, data);
                    if (fieldName.tag == TypeTags.FINITE && ((BFiniteType) fieldName).getValueSpace().size() == 1) {
                        BLangLiteral fieldNameLiteral =
                                (BLangLiteral) ((BFiniteType) fieldName).getValueSpace().iterator().next();
                        if (fieldNameLiteral.getBType().tag == TypeTags.STRING) {
                            if (!targetFields.containsKey(((BString) fieldNameLiteral).getValue())) {
                                if (expRecordType.sealed) {
                                    containErrors = true;
                                    dlog.error(keyValue.pos, DiagnosticErrorCode.UNDEFINED_STRUCTURE_FIELD_WITH_TYPE,
                                            key, possibleType.tsymbol.type.getKind().typeName(), possibleType);
                                    continue;
                                } else {
                                    expType = expRecordType.restFieldType;
                                }
                            } else {
                                expType = targetFields.get(key).type;
                            }
                            exprToCheck = keyValue.valueExpr;
                            if (data.commonAnalyzerData.nonErrorLoggingCheck) {
                                exprToCheck = nodeCloner.cloneNode(keyValue.valueExpr);
                            }
                            BType keyValueType = checkConstExpr(exprToCheck, expType, data);
                            if (!addFields(inferredFields, Types.getReferredType(keyValueType),
                                    fieldNameLiteral.getValue().toString(), key.pos, recordSymbol, true)) {
                                containErrors = true;
                            }
                            continue;
                        }
                    }
                    dlog.error(key.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, fieldName, symTable.stringType);
                    containErrors = true;
                }
                break;
        }

        if (containErrors) {
            return symTable.semanticError;
        }

        BRecordType recordType = new BRecordType(recordSymbol);
        recordType.restFieldType = symTable.noType;
        recordType.fields = inferredFields;
        recordSymbol.type = recordType;
        recordType.tsymbol = recordSymbol;
        recordType.sealed = true;
        createTypeDefinition(recordType, data.constantSymbol.pos, data.env);
        return recordType;
    }

    private void createTypeDefinition(BRecordType type, Location pos, SymbolEnv env) {
        BRecordTypeSymbol recordSymbol = (BRecordTypeSymbol) type.tsymbol;

        BTypeDefinitionSymbol typeDefinitionSymbol = Symbols.createTypeDefinitionSymbol(type.tsymbol.flags,
                type.tsymbol.name, env.scope.owner.pkgID, null, env.scope.owner, pos, VIRTUAL);
        typeDefinitionSymbol.scope = new Scope(typeDefinitionSymbol);
        typeDefinitionSymbol.scope.define(names.fromString(typeDefinitionSymbol.name.value), typeDefinitionSymbol);

        type.tsymbol.scope = new Scope(type.tsymbol);
        for (BField field : ((HashMap<String, BField>) type.fields).values()) {
            type.tsymbol.scope.define(field.name, field.symbol);
            field.symbol.owner = recordSymbol;
        }
        typeDefinitionSymbol.type = type;
        recordSymbol.type = type;
        recordSymbol.typeDefinitionSymbol = typeDefinitionSymbol;
        recordSymbol.markdownDocumentation = new MarkdownDocAttachment(0);

        BLangRecordTypeNode recordTypeNode = TypeDefBuilderHelper.createRecordTypeNode(new ArrayList<>(), type,
                pos);
        TypeDefBuilderHelper.populateStructureFields(types, symTable, null, names, recordTypeNode, type, type, pos,
                env, env.scope.owner.pkgID, null, 0, false);
        recordTypeNode.sealed = true;
        type.restFieldType = new BNoType(TypeTags.NONE);
        BLangTypeDefinition typeDefinition = TypeDefBuilderHelper.createTypeDefinitionForTSymbol(null,
                typeDefinitionSymbol, recordTypeNode, env);
        typeDefinition.symbol.scope = new Scope(typeDefinition.symbol);
        typeDefinition.symbol.type = type;
        typeDefinition.flagSet = new HashSet<>();
        typeDefinition.flagSet.add(Flag.PUBLIC);
        typeDefinition.flagSet.add(Flag.ANONYMOUS);
    }

    private boolean isUniqueType(Iterable<BType> typeList, BType type) {
        boolean isRecord = type.tag == TypeTags.RECORD;

        for (BType bType : typeList) {

            if (isRecord) {
                if (type == bType) {
                    return false;
                }
            } else if (types.isSameType(type, bType)) {
                return false;
            }
        }
        return true;
    }

    private boolean validateRequiredFields(BRecordType type, List<RecordLiteralNode.RecordField> specifiedFields,
                                           Location pos, TypeChecker.AnalyzerData data) {
        HashSet<String> specFieldNames = typeChecker.getFieldNames(specifiedFields, data);
        boolean hasAllRequiredFields = true;

        for (BField field : type.fields.values()) {
            String fieldName = field.name.value;
            if (!specFieldNames.contains(fieldName) && Symbols.isFlagOn(field.symbol.flags, Flags.REQUIRED)
                    && !types.isNeverTypeOrStructureTypeWithARequiredNeverMember(field.type)) {
                // Check if `field` is explicitly assigned a value in the record literal
                // If a required field is missing, it's a compile error
                dlog.error(pos, DiagnosticErrorCode.MISSING_REQUIRED_RECORD_FIELD, field.name);
                if (hasAllRequiredFields) {
                    hasAllRequiredFields = false;
                }
            }
        }
        return hasAllRequiredFields;
    }

    private BType defineInferredTupleType(BLangListConstructorExpr listConstructor, BType expType, AnalyzerData data) {
        boolean containErrors = false;
        List<BType> memberTypes = new ArrayList<>();
        for (BLangExpression expr : listConstructor.exprs) {
            if (expr.getKind() == NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                // Spread operator expr.
                BLangExpression spreadOpExpr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
                BType spreadOpExprType = checkConstExpr(spreadOpExpr, data);
                BType type = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOpExprType));
                if (type.tag != TypeTags.TUPLE) {
                    // Finalized constant type should be a tuple type (cannot be an array type or any other).
                    containErrors = true;
                    continue;
                }

                // Add the members from spread operator into current tuple type.
                for (BType memberType : ((BTupleType) type).getTupleTypes()) {
                    memberTypes.add(memberType);
                }
                continue;
            }

            BType tupleMemberType = checkConstExpr(expr, data);
            if (tupleMemberType == symTable.semanticError) {
                containErrors = true;
                continue;
            }
            memberTypes.add(tupleMemberType);
        }

        if (containErrors) {
            return symTable.semanticError;
        }

        // Create new tuple type using inferred members.
        BTupleType resultTupleType = createNewTupleType(listConstructor.pos, memberTypes, data);
        if (expType.tag == TypeTags.READONLY) {
            resultTupleType.flags |= Flags.READONLY;
        }
        return resultTupleType;
    }

    private BType checkListConstructorCompatibility(BType expType, BLangListConstructorExpr listConstructor,
                                                    AnalyzerData data) {
        int tag = expType.tag;
        if (tag == TypeTags.UNION) {
            boolean prevNonErrorLoggingCheck = data.commonAnalyzerData.nonErrorLoggingCheck;
            int errorCount = this.dlog.errorCount();
            data.commonAnalyzerData.nonErrorLoggingCheck = true;
            this.dlog.mute();

            List<BType> compatibleTypes = new ArrayList<>();
            boolean erroredExpType = false;
            for (BType memberType : ((BUnionType) expType).getMemberTypes()) {
                if (memberType == symTable.semanticError) {
                    if (!erroredExpType) {
                        erroredExpType = true;
                    }
                    continue;
                }

                BType listCompatibleMemType = getListConstructorCompatibleNonUnionType(memberType, data);
                if (listCompatibleMemType == symTable.semanticError) {
                    continue;
                }

                dlog.resetErrorCount();
                BType memCompatibiltyType = checkListConstructorCompatibility(listCompatibleMemType, listConstructor,
                        data);
                if (memCompatibiltyType != symTable.semanticError && dlog.errorCount() == 0 &&
                        isUniqueType(compatibleTypes, memCompatibiltyType)) {
                    compatibleTypes.add(memCompatibiltyType);
                }
            }

            data.commonAnalyzerData.nonErrorLoggingCheck = prevNonErrorLoggingCheck;
            this.dlog.setErrorCount(errorCount);
            if (!prevNonErrorLoggingCheck) {
                this.dlog.unmute();
            }

            if (compatibleTypes.isEmpty()) {
                dlog.error(listConstructor.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, listConstructor, expType);
                return symTable.semanticError;
            } else if (compatibleTypes.size() != 1) {
                dlog.error(listConstructor.pos, DiagnosticErrorCode.AMBIGUOUS_TYPES,
                        data.expType);
                return symTable.semanticError;
            }

            return checkListConstructorCompatibility(compatibleTypes.get(0), listConstructor, data);
        }

        if (tag == TypeTags.TYPEREFDESC) {
            return checkListConstructorCompatibility(Types.getReferredType(expType), listConstructor, data);
        }

        if (tag == TypeTags.INTERSECTION) {
            return checkListConstructorCompatibility(((BIntersectionType) expType).effectiveType, listConstructor, data);
        }

        BType possibleType = getListConstructorCompatibleNonUnionType(expType, data);

        switch (possibleType.tag) {
            case TypeTags.ARRAY:
                return checkArrayType((BArrayType) possibleType, listConstructor, data);
            case TypeTags.TUPLE:
                return checkTupleType((BTupleType) possibleType, listConstructor, data);
            case TypeTags.READONLY:
                return checkConstExpr(listConstructor, possibleType, data);
        }
        return symTable.semanticError;
    }

    private BType checkArrayType(BArrayType arrayType, BLangListConstructorExpr listConstructor, AnalyzerData data) {
        int listExprSize = 0;
        if (arrayType.state != BArrayState.OPEN) {
            for (BLangExpression expr: listConstructor.exprs) {
                if (expr.getKind() != NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                    listExprSize++;
                    continue;
                }

                BLangExpression spreadOpExpr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
                BType spreadOpType = checkConstExpr(spreadOpExpr, data);
                spreadOpType = Types.getReferredType(spreadOpType);

                switch (spreadOpType.tag) {
                    case TypeTags.ARRAY:
                        int arraySize = ((BArrayType) spreadOpType).size;
                        if (arraySize >= 0) {
                            listExprSize += arraySize;
                            continue;
                        }

                        dlog.error(spreadOpExpr.pos,
                                DiagnosticErrorCode.INVALID_SPREAD_OP_FIXED_LENGTH_LIST_EXPECTED);
                        return symTable.semanticError;
                    case TypeTags.TUPLE:
                        BTupleType tType = (BTupleType) spreadOpType;
                        if (types.isFixedLengthTuple(tType)) {
                            listExprSize += tType.getMembers().size();
                            continue;
                        }

                        dlog.error(spreadOpExpr.pos,
                                DiagnosticErrorCode.INVALID_SPREAD_OP_FIXED_LENGTH_LIST_EXPECTED);
                        return symTable.semanticError;
                }
            }
        }

        BType eType = arrayType.eType;
        if (arrayType.state == BArrayState.INFERRED) {
            arrayType.size = listExprSize;
            arrayType.state = BArrayState.CLOSED;
        } else if (arrayType.state != BArrayState.OPEN && arrayType.size != listExprSize) {
            if (arrayType.size < listExprSize) {
                dlog.error(listConstructor.pos, DiagnosticErrorCode.MISMATCHING_ARRAY_LITERAL_VALUES, arrayType.size,
                        listExprSize);
                return symTable.semanticError;
            }

            if (!types.hasFillerValue(eType)) {
                dlog.error(listConstructor.pos, DiagnosticErrorCode.INVALID_LIST_CONSTRUCTOR_ELEMENT_TYPE,
                        data.expType);
                return symTable.semanticError;
            }
        }

        boolean containErrors = false;
        List<BType> memberTypes = new ArrayList<>();
        for (BLangExpression expr : listConstructor.exprs) {
            if (expr.getKind() == NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                BLangExpression spreadOpExpr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
                BType spreadOpExprType = checkConstExpr(spreadOpExpr, data);
                BType type = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOpExprType));
                if (type.tag != TypeTags.TUPLE) {
                    // Finalized constant type should be a tuple type (cannot be an array type or any other).
                    containErrors = true;
                    continue;
                }

                // Add the members from spread operator into current tuple type.
                for (BType memberType : ((BTupleType) type).getTupleTypes()) {
                    if (types.checkType(expr.pos, memberType, eType, DiagnosticErrorCode.INCOMPATIBLE_TYPES)
                            == symTable.semanticError) {
                        containErrors = true;
                        continue;
                    }
                    memberTypes.add(memberType);
                }
                continue;
            }

            BType tupleMemberType = checkConstExpr(expr, eType, data);
            if (tupleMemberType == symTable.semanticError) {
                containErrors = true;
                continue;
            }
            memberTypes.add(tupleMemberType);
        }

        if (containErrors) {
            return symTable.semanticError;
        }

        // Create new tuple type using inferred members.
        BTupleType resultTupleType = createNewTupleType(listConstructor.pos, memberTypes, data);

        if (arrayType.state == BArrayState.CLOSED && arrayType.size > listExprSize) {
            if (!fillMembers.addFillMembers(resultTupleType, arrayType, data)) {
                return symTable.semanticError;
            }
        }
        return resultTupleType;
    }

    private BType checkTupleType(BTupleType tupleType, BLangListConstructorExpr listConstructor, AnalyzerData data) {
        List<BLangExpression> exprs = listConstructor.exprs;
        List<BTupleMember> members = tupleType.getMembers();
        int memberTypeSize = members.size();
        BType restType = tupleType.restType;

        if (types.isFixedLengthTuple(tupleType)) {
            int listExprSize = 0;
            for (BLangExpression expr : exprs) {
                if (expr.getKind() != NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                    listExprSize++;
                    continue;
                }

                BLangExpression spreadOpExpr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
                BType spreadOpType = checkConstExpr(spreadOpExpr, data);
                spreadOpType = Types.getReferredType(spreadOpType);

                switch (spreadOpType.tag) {
                    case TypeTags.ARRAY:
                        int arraySize = ((BArrayType) spreadOpType).size;
                        if (arraySize >= 0) {
                            listExprSize += arraySize;
                            continue;
                        }

                        dlog.error(spreadOpExpr.pos, DiagnosticErrorCode.INVALID_SPREAD_OP_FIXED_LENGTH_LIST_EXPECTED);
                        return symTable.semanticError;
                    case TypeTags.TUPLE:
                        BTupleType tType = (BTupleType) spreadOpType;
                        if (types.isFixedLengthTuple(tType)) {
                            listExprSize += tType.getMembers().size();
                            continue;
                        }

                        dlog.error(spreadOpExpr.pos, DiagnosticErrorCode.INVALID_SPREAD_OP_FIXED_LENGTH_LIST_EXPECTED);
                        return symTable.semanticError;
                }
            }

            if (listExprSize > memberTypeSize) {
                dlog.error(listConstructor.pos, DiagnosticErrorCode.TUPLE_AND_EXPRESSION_SIZE_DOES_NOT_MATCH);
                return symTable.semanticError;
            }
        }

        boolean containErrors = false;
        int nonRestTypeIndex = 0;
        List<BType> memberTypes = new ArrayList<>();

        for (BLangExpression expr : exprs) {
            BType memberType;
            int remainNonRestCount = memberTypeSize - nonRestTypeIndex;
            if (expr.getKind() != NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                if (remainNonRestCount > 0) {
                    memberType = checkExprIncompatible(members.get(nonRestTypeIndex).type, expr, data);
                    nonRestTypeIndex++;
                } else {
                    memberType = checkExprIncompatible(restType, expr, data);
                }
                if (memberType == symTable.semanticError) {
                    containErrors = true;
                    continue;
                }
                memberTypes.add(memberType);
                continue;
            }

            BLangExpression spreadOpExpr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
            BType spreadOpType = checkConstExpr(spreadOpExpr, data);
            BType spreadOpReferredType = Types.getReferredType(
                    types.getTypeWithEffectiveIntersectionTypes(spreadOpType));
            if (spreadOpReferredType.tag != TypeTags.TUPLE) {
                // Finalized constant type should be a tuple type (cannot be an array type or any other).
                containErrors = true;
                continue;
            }

            BTupleType spreadOpTuple = (BTupleType) spreadOpReferredType;
            List<BType> tupleMemberTypes = spreadOpTuple.getTupleTypes();
            int spreadOpMemberTypeSize = tupleMemberTypes.size();

            for (int i = 0; i < spreadOpMemberTypeSize && nonRestTypeIndex < memberTypeSize; i++, nonRestTypeIndex++) {
                BType tupleMemberType = tupleMemberTypes.get(i);
                if (types.typeIncompatible(spreadOpExpr.pos, tupleMemberType,
                        members.get(nonRestTypeIndex).type)) {
                    return symTable.semanticError;
                }
                memberTypes.add(tupleMemberType);
            }

            for (int i = remainNonRestCount; i < spreadOpMemberTypeSize; i++) {
                BType tupleMemberType = tupleMemberTypes.get(i);
                if (types.typeIncompatible(spreadOpExpr.pos, tupleMemberType,
                        restType)) {
                    return symTable.semanticError;
                }
                memberTypes.add(tupleMemberType);
            }
        }

        if (containErrors) {
            return symTable.semanticError;
        }

        // Create new tuple type using inferred members.
        BTupleType resultTupleType = createNewTupleType(listConstructor.pos, memberTypes, data);
        if (memberTypeSize - nonRestTypeIndex > 0) {
            if (!fillMembers.addFillMembers(resultTupleType, tupleType, data)) {
                return symTable.semanticError;
            }
        }

        return resultTupleType;
    }

    private BTupleType createNewTupleType(Location pos, List<BType> memberTypes, AnalyzerData data) {
        BTypeSymbol tupleTypeSymbol = Symbols.createTypeSymbol(SymTag.TUPLE_TYPE, 0, Names.EMPTY,
                data.env.enclPkg.symbol.pkgID, null, data.env.scope.owner, pos, SOURCE);
        List<BTupleMember> members = new ArrayList<>();
        memberTypes.forEach(m ->
                members.add(new BTupleMember(m, Symbols.createVarSymbolForTupleMember(m))));
        BTupleType tupleType = new BTupleType(tupleTypeSymbol, members);
        tupleType.tsymbol.type = tupleType;
        return tupleType;
    }

    private BType checkExprIncompatible(BType eType, BLangExpression expr, AnalyzerData data) {
        if (expr.typeChecked) {
            return expr.getBType();
        }

        BLangExpression exprToCheck = expr;

        if (data.commonAnalyzerData.nonErrorLoggingCheck) {
            expr.cloneAttempt++;
            exprToCheck = nodeCloner.cloneNode(expr);
        }

        return checkConstExpr(exprToCheck, eType, data);
    }

    private BType getListConstructorCompatibleNonUnionType(BType type, AnalyzerData data) {
        switch (type.tag) {
            case TypeTags.ARRAY:
            case TypeTags.TUPLE:
            case TypeTags.READONLY:
            case TypeTags.TYPEDESC:
                return type;
            case TypeTags.JSON:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.arrayJsonType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.arrayJsonType,
                                data.env, symTable, anonymousModelHelper, names);
            case TypeTags.ANYDATA:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.arrayAnydataType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.arrayAnydataType,
                                data.env, symTable, anonymousModelHelper, names);
            case TypeTags.ANY:
                return !Symbols.isFlagOn(type.flags, Flags.READONLY) ? symTable.arrayAllType :
                        ImmutableTypeCloner.getEffectiveImmutableType(null, types, symTable.arrayAllType, data.env,
                                symTable, anonymousModelHelper, names);
            case TypeTags.INTERSECTION:
                return ((BIntersectionType) type).effectiveType;
//            case TypeTags.TYPEREFDESC:
//                return type;
        }
        return symTable.semanticError;
    }

    private BType getBroadType(BType type) {
        if (type.tag != TypeTags.FINITE) {
            return type;
        }
        return ((BFiniteType) type).getValueSpace().iterator().next().getBType();
    }

    private BSymbol getUnaryOpSymbol(BLangUnaryExpr unaryExpr, BType type, AnalyzerData data) {
        if (type == symTable.semanticError) {
            return symTable.notFoundSymbol;
        }
        BType exprType = type;
        BSymbol symbol = symResolver.resolveUnaryOperator(unaryExpr.operator, exprType);
        if (symbol == symTable.notFoundSymbol) {
            symbol = symResolver.getUnaryOpsForTypeSets(unaryExpr.operator, exprType);
        }
        if (symbol == symTable.notFoundSymbol) {
//            dlog.error(unaryExpr.pos, DiagnosticErrorCode.UNARY_OP_INCOMPATIBLE_TYPES,
//                    unaryExpr.operator, exprType);
//            data.resultType = symTable.semanticError;
        } else {
            unaryExpr.opSymbol = (BOperatorSymbol) symbol;
            data.resultType = symbol.type.getReturnType();
        }
        if (symbol == symTable.notFoundSymbol) {
            exprType = ((BFiniteType) type).getValueSpace().iterator().next().getBType();
            symbol = symResolver.resolveUnaryOperator(unaryExpr.operator, exprType);
            if (symbol == symTable.notFoundSymbol) {
                symbol = symResolver.getUnaryOpsForTypeSets(unaryExpr.operator, exprType);
            }
            if (symbol == symTable.notFoundSymbol) {
//                dlog.error(unaryExpr.pos, DiagnosticErrorCode.UNARY_OP_INCOMPATIBLE_TYPES,
//                        unaryExpr.operator, exprType);
//                data.resultType = symTable.semanticError;
            } else {
                unaryExpr.opSymbol = (BOperatorSymbol) symbol;
                data.resultType = symbol.type.getReturnType();
            }
        }
        return symbol;
    }

    private Object calculateSingletonValue(BFiniteType lhs, BFiniteType rhs, OperatorKind kind,
                                                       BType type, Location currentPos) {
        // Calculate the value for the binary operation.
        // TODO - Handle overflows.
        if (lhs == null || rhs == null) {
            // This is a compilation error.
            // This is to avoid NPE exceptions in sub-sequent validations.
            return null;
        }

        BLangLiteral lhsLiteral = (BLangLiteral) lhs.getValueSpace().iterator().next();
        BLangLiteral rhsLiteral = (BLangLiteral) rhs.getValueSpace().iterator().next();

        // See Types.isAllowedConstantType() for supported types.
        Object lhsValue = getValue(lhsLiteral);
        Object rhsValue = getValue(rhsLiteral);
        try {
            switch (kind) {
                case ADD:
                    return calculateAddition(lhsValue, rhsValue, type);
                case SUB:
                    return calculateSubtract(lhsValue, rhsValue, type);
                case MUL:
                    return calculateMultiplication(lhsValue, rhsValue, type);
                case DIV:
                    return calculateDivision(lhsValue, rhsValue, type);
                case MOD:
                    return calculateMod(lhsValue, rhsValue, type);
                case BITWISE_AND:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a & b, type, currentPos);
                case BITWISE_OR:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a | b, type, currentPos);
                case BITWISE_LEFT_SHIFT:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a << b, type, currentPos);
                case BITWISE_RIGHT_SHIFT:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a >> b, type, currentPos);
                case BITWISE_UNSIGNED_RIGHT_SHIFT:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a >>> b, type, currentPos);
                case BITWISE_XOR:
                    return calculateBitWiseOp(lhsValue, rhsValue, (a, b) -> a ^ b, type, currentPos);
                default:
//                    dlog.error(currentPos, DiagnosticErrorCode.CONSTANT_EXPRESSION_NOT_SUPPORTED);
            }
        } catch (NumberFormatException nfe) {
            // Ignore. This will be handled as a compiler error.
        } catch (ArithmeticException ae) {
//            dlog.error(currentPos, DiagnosticErrorCode.INVALID_CONST_EXPRESSION, ae.getMessage());
        }
        // This is a compilation error already logged.
        // This is to avoid NPE exceptions in sub-sequent validations.
        return null;
    }

    private Object getValue(BLangLiteral lhsLiteral) {
        Object value = lhsLiteral.value;
        if (value instanceof BLangConstantValue) {
            return ((BLangConstantValue) value).value;
        }
        return value;
    }

    private Object evaluateUnaryOperator(BFiniteType finiteType, BType type, OperatorKind kind) {
        // Calculate the value for the unary operation.
        BLangLiteral lhsLiteral = (BLangLiteral) finiteType.getValueSpace().iterator().next();
        Object value = getValue(lhsLiteral);
        if (value == null) {
            // This is a compilation error.
            // This is to avoid NPE exceptions in sub-sequent validations.
            return null;
        }

        try {
            switch (kind) {
                case ADD:
                    return value;
                case SUB:
                    return calculateNegation(value, type);
                case BITWISE_COMPLEMENT:
                    return calculateBitWiseComplement(value, type);
                case NOT:
                    return calculateBooleanComplement(value, type);
            }
        } catch (ClassCastException ce) {
            // Ignore. This will be handled as a compiler error.
        }
        // This is a compilation error already logged.
        // This is to avoid NPE exceptions in sub-sequent validations.
        return null;
    }

    private Object calculateBitWiseOp(Object lhs, Object rhs,
                                                  BiFunction<Long, Long, Long> func, BType type, Location currentPos) {
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
                Long val = func.apply((Long) lhs, (Long) rhs);
                return val;
            default:
                dlog.error(currentPos, DiagnosticErrorCode.CONSTANT_EXPRESSION_NOT_SUPPORTED);

        }
        return null;
//        return new BLangConstantValue(null, type);
    }

    private Object calculateAddition(Object lhs, Object rhs, BType type) {
        Object result = null;
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                try {
                    result = Math.addExact((Long) lhs, (Long) rhs);
                } catch (ArithmeticException ae) {
                    return null;
                }
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs))
                        + Double.parseDouble(String.valueOf(rhs)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.add(rhsDecimal, MathContext.DECIMAL128);
                resultDecimal = types.getValidDecimalNumber(resultDecimal);
                result = resultDecimal != null ? resultDecimal.toPlainString() : null;
                break;
            case TypeTags.STRING:
                result = String.valueOf(lhs) + String.valueOf(rhs);
                break;
        }
        return result;
    }

    private Object calculateSubtract(Object lhs, Object rhs, BType type) {
        Object result = null;
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                try {
                    result = Math.subtractExact((Long) lhs, (Long) rhs);
                } catch (ArithmeticException ae) {
                    return null;
                }
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs))
                        - Double.parseDouble(String.valueOf(rhs)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.subtract(rhsDecimal, MathContext.DECIMAL128);
                resultDecimal = types.getValidDecimalNumber(resultDecimal);
                result = resultDecimal != null ? resultDecimal.toPlainString() : null;
                break;
        }
        return result;
    }

    private Object calculateMultiplication(Object lhs, Object rhs, BType type) {
        Object result = null;
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                try {
                    result = Math.multiplyExact((Long) lhs, (Long) rhs);
                } catch (ArithmeticException ae) {
                    return null;
                }
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs))
                        * Double.parseDouble(String.valueOf(rhs)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.multiply(rhsDecimal, MathContext.DECIMAL128);
                resultDecimal = types.getValidDecimalNumber(resultDecimal);
                result = resultDecimal != null ? resultDecimal.toPlainString() : null;
                break;
        }
        return result;
    }

    private Object calculateDivision(Object lhs, Object rhs, BType type) {
        Object result = null;
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                if ((Long) lhs == Long.MIN_VALUE && (Long) rhs == -1) {
                    return null;
                }
                result = (Long) ((Long) lhs / (Long) rhs);
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs))
                        / Double.parseDouble(String.valueOf(rhs)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.divide(rhsDecimal, MathContext.DECIMAL128);
                resultDecimal = types.getValidDecimalNumber(resultDecimal);
                result = resultDecimal != null ? resultDecimal.toPlainString() : null;
                break;
        }
        return result;
    }

    private Object calculateMod(Object lhs, Object rhs, BType type) {
        Object result = null;
        switch (Types.getReferredType(type).tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) ((Long) lhs % (Long) rhs);
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs))
                        % Double.parseDouble(String.valueOf(rhs)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.remainder(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return result;
    }


    private Object calculateNegationForInt(Object value) {
        return -1 * ((Long) (value));
    }

    private Object calculateNegationForFloat(Object value) {
        return String.valueOf(-1 * Double.parseDouble(String.valueOf(value)));
    }

    private Object calculateNegationForDecimal(Object value) {
        BigDecimal valDecimal = new BigDecimal(String.valueOf(value), MathContext.DECIMAL128);
        BigDecimal negDecimal = new BigDecimal(String.valueOf(-1), MathContext.DECIMAL128);
        BigDecimal resultDecimal = valDecimal.multiply(negDecimal, MathContext.DECIMAL128);
        return resultDecimal.toPlainString();
    }

    private Object calculateNegation(Object value, BType type) {
        Object result = null;

        switch (type.tag) {
            case TypeTags.INT:
                result = calculateNegationForInt(value);
                break;
            case TypeTags.FLOAT:
                result = calculateNegationForFloat(value);
                break;
            case TypeTags.DECIMAL:
                result = calculateNegationForDecimal(value);
                break;
        }

        return result;
    }

    private Object calculateBitWiseComplement(Object value, BType type) {
        Object result = null;
        if (Types.getReferredType(type).tag == TypeTags.INT) {
            result = ~((Long) (value));
        }
        return result;
    }

    private Object calculateBooleanComplement(Object value, BType type) {
        Object result = null;
        if (Types.getReferredType(type).tag == TypeTags.BOOLEAN) {
            result = !((Boolean) (value));
        }
        return result;
    }

    private BType setLiteralValueAndGetType(BLangLiteral literalExpr, BType expType, AnalyzerData data) {
        Object literalValue = literalExpr.value;
        BType expectedType = Types.getReferredType(expType);

        if (literalExpr.getKind() == NodeKind.NUMERIC_LITERAL) {
            NodeKind kind = ((BLangNumericLiteral) literalExpr).kind;
            if (kind == NodeKind.INTEGER_LITERAL) {
                return getIntegerLiteralType(literalExpr, literalValue, expectedType);
            } else if (kind == NodeKind.DECIMAL_FLOATING_POINT_LITERAL) {
                if (NumericLiteralSupport.isFloatDiscriminated(literalExpr.originalValue)) {
                    return getTypeOfLiteralWithFloatDiscriminator(literalExpr, literalValue);
                } else if (NumericLiteralSupport.isDecimalDiscriminated(literalExpr.originalValue)) {
                    return getTypeOfLiteralWithDecimalDiscriminator(literalExpr, literalValue);
                } else {
                    return getTypeOfDecimalFloatingPointLiteral(literalExpr, literalValue, expectedType);
                }
            } else {
                return getTypeOfHexFloatingPointLiteral(literalExpr, literalValue, data);
            }
        }

        // Get the type matching to the tag from the symbol table.
        BType literalType = symTable.getTypeFromTag(literalExpr.getBType().tag);
        if (literalType.tag == TypeTags.STRING && types.isCharLiteralValue((String) literalValue)) {
            boolean foundMember = types.isAssignableToFiniteType(symTable.noType, literalExpr);
            if (foundMember) {
                setLiteralValueForFiniteType(literalExpr, literalType, data);
                return literalType;
            }
        }

        // Byte arrays are not yet supported in constants.
        if (literalExpr.getBType().tag == TypeTags.BYTE_ARRAY) {
            // check whether this is a byte array
            literalType = new BArrayType(symTable.byteType);
        }

        return literalType;
    }

    private BType getTypeOfLiteralWithFloatDiscriminator(BLangLiteral literalExpr, Object literalValue) {
        String numericLiteral = NumericLiteralSupport.stripDiscriminator(String.valueOf(literalValue));
        if (!types.validateFloatLiteral(literalExpr.pos, numericLiteral)) {
            return symTable.semanticError;
        }
        literalExpr.value = Double.parseDouble(numericLiteral);
        return symTable.floatType;
    }

    public BType getIntegerLiteralType(BLangLiteral literalExpr, Object literalValue, BType expType) {
        BType literalType = getIntegerLiteralTypeUsingExpType(literalExpr, literalValue, expType);
        if (literalType != symTable.semanticError) {
            return literalType;
        }

        if (!(literalValue instanceof Long)) {
            dlog.error(literalExpr.pos, DiagnosticErrorCode.OUT_OF_RANGE, literalExpr.originalValue,
                    literalExpr.getBType());
            return symTable.semanticError;
        }
        return symTable.intType;
    }

    private BType getIntegerLiteralTypeUsingExpType(BLangLiteral literalExpr, Object literalValue, BType expType) {
        BType expectedType = Types.getReferredType(expType);
        int expectedTypeTag = expectedType.tag;
        switch (expectedTypeTag) {
            case TypeTags.BYTE:
            case TypeTags.INT:
            case TypeTags.SIGNED32_INT:
            case TypeTags.SIGNED16_INT:
            case TypeTags.SIGNED8_INT:
            case TypeTags.UNSIGNED32_INT:
            case TypeTags.UNSIGNED16_INT:
            case TypeTags.UNSIGNED8_INT:
            case TypeTags.ANY:
            case TypeTags.ANYDATA:
            case TypeTags.JSON:
                if (!(literalValue instanceof Long)) {
                    dlog.error(literalExpr.pos, DiagnosticErrorCode.OUT_OF_RANGE, literalExpr.originalValue, expType);
                    return symTable.semanticError;
                }
                return symTable.intType;
            case TypeTags.FLOAT:
                // The literalValue will be a string if it was not within the bounds of what is supported by Java Long
                // or Double when it was parsed in BLangNodeBuilder
                if (literalValue instanceof String) {
                    dlog.error(literalExpr.pos, DiagnosticErrorCode.OUT_OF_RANGE, literalExpr.originalValue,
                            expectedType);
                    return symTable.semanticError;
                }
                if (literalValue instanceof Double) {
                    literalExpr.value = (Double) literalValue;
                } else {
                    literalExpr.value = ((Long) literalValue).doubleValue();
                }
                return symTable.floatType;
            case TypeTags.DECIMAL:
                literalExpr.value = String.valueOf(literalValue);
                return symTable.decimalType;
            case TypeTags.FINITE:
                Set<BLangExpression> valueSpace = ((BFiniteType) expectedType).getValueSpace();
                if (valueSpace.size() > 1) {
                    LinkedHashSet<BType> memTypes = new LinkedHashSet<>();
                    valueSpace.forEach(memExpr -> memTypes.add(memExpr.getBType()));
                    BUnionType unionType = new BUnionType(null, memTypes, false, false);
                    return getIntegerLiteralTypeUsingExpType(literalExpr, literalValue, unionType);
                }
                BType expBroadType = ((BFiniteType) expectedType).getValueSpace().iterator().next().getBType();
                return getIntegerLiteralTypeUsingExpType(literalExpr, literalValue, expBroadType);
            case TypeTags.UNION:
                BUnionType expectedUnionType = (BUnionType) expectedType;
                List<BType> memberTypes = types.getAllReferredTypes(expectedUnionType);
                List<BType> validTypes = new ArrayList<>();
                dlog.mute();
                for (BType memType : memberTypes) {
                    BType validType = getIntegerLiteralTypeUsingExpType(literalExpr, literalValue, memType);
                    if (validType.tag != TypeTags.SEMANTIC_ERROR) {
                        validTypes.add(validType);
                    }
                }
                dlog.unmute();

                validTypes.sort(Comparator.comparingInt(t -> t.tag));
                for (BType validType : validTypes) {
                    if (validType.tag == TypeTags.INT) {
                        literalExpr.value = literalValue;
                        return symTable.intType;
                    } else if (validType.tag == TypeTags.FLOAT) {
                        if (literalValue instanceof Double) {
                            literalExpr.value = literalValue;
                        } else {
                            literalExpr.value = ((Long) literalValue).doubleValue();
                        }
                        return symTable.floatType;
                    } else if (validType.tag == TypeTags.DECIMAL) {
                        literalExpr.value = String.valueOf(literalValue);
                        return symTable.decimalType;
                    }
                }
                break;
        }
        return symTable.semanticError;
    }

    public void setLiteralValueForFiniteType(BLangLiteral literalExpr, BType type, AnalyzerData data) {
        types.setImplicitCastExpr(literalExpr, type, data.expType);
        data.resultType = type;
        literalExpr.isFiniteContext = true;
    }

    private BType getTypeOfLiteralWithDecimalDiscriminator(BLangLiteral literalExpr, Object literalValue) {
        literalExpr.value = NumericLiteralSupport.stripDiscriminator(String.valueOf(literalValue));
        if (!types.isValidDecimalNumber(literalExpr.pos, literalExpr.value.toString())) {
            return symTable.semanticError;
        }
        return symTable.decimalType;
    }

    private BType getTypeOfDecimalFloatingPointLiteral(BLangLiteral literalExpr, Object literalValue, BType expType) {
        String numericLiteral = String.valueOf(literalValue);
        BType literalType = getTypeOfDecimalFloatingPointLiteralUsingExpType(literalExpr, literalValue, expType);
        if (literalType != symTable.semanticError) {
            return literalType;
        }
        return types.validateFloatLiteral(literalExpr.pos, numericLiteral)
                ? symTable.floatType : symTable.semanticError;
    }

    private BType getTypeOfDecimalFloatingPointLiteralUsingExpType(BLangLiteral literalExpr, Object literalValue,
                                                                   BType expType) {
        BType expectedType = Types.getReferredType(expType);
        String numericLiteral = String.valueOf(literalValue);
        switch (expectedType.tag) {
            case TypeTags.FLOAT:
            case TypeTags.ANYDATA:
            case TypeTags.ANY:
            case TypeTags.JSON:
                if (!types.validateFloatLiteral(literalExpr.pos, numericLiteral)) {
                    return symTable.semanticError;
                }
                return symTable.floatType;
            case TypeTags.DECIMAL:
                if (types.isValidDecimalNumber(literalExpr.pos, literalExpr.value.toString())) {
                    return symTable.decimalType;
                }
                return symTable.semanticError;
            case TypeTags.FINITE:
                BType expBroadType = ((BFiniteType) expectedType).getValueSpace().iterator().next().getBType();
                return getTypeOfDecimalFloatingPointLiteralUsingExpType(literalExpr, literalValue, expBroadType);
            case TypeTags.UNION:
                BUnionType expectedUnionType = (BUnionType) expectedType;
                List<BType> memberTypes = types.getAllReferredTypes(expectedUnionType);
                List<BType> validTypes = new ArrayList<>();
                dlog.mute();
                for (BType memType : memberTypes) {
                    BType validType = getTypeOfDecimalFloatingPointLiteralUsingExpType(literalExpr, literalValue,
                            memType);
                    if (validType.tag != TypeTags.SEMANTIC_ERROR) {
                        validTypes.add(validType);
                    }
                }
                dlog.unmute();

                validTypes.sort(Comparator.comparingInt(t -> t.tag));
                for (BType validType : validTypes) {
                    if (validType.tag == TypeTags.FLOAT) {
                        return symTable.floatType;
                    } else if (validType.tag == TypeTags.DECIMAL) {
                        return symTable.decimalType;
                    }
                }
                break;
        }
        return symTable.semanticError;
    }

    public BType getTypeOfHexFloatingPointLiteral(BLangLiteral literalExpr, Object literalValue,
                                                  AnalyzerData data) {
        String numericLiteral = String.valueOf(literalValue);
        if (!types.validateFloatLiteral(literalExpr.pos, numericLiteral)) {
            data.resultType = symTable.semanticError;
            return symTable.semanticError;
        }
        literalExpr.value = Double.parseDouble(numericLiteral);
        return symTable.floatType;
    }

    private BType getFiniteType(Object value, BConstantSymbol constantSymbol, Location pos, BType type) {
        switch (type.tag) {
            case TypeTags.INT:
            case TypeTags.FLOAT:
            case TypeTags.DECIMAL:
//            case TypeTags.BYTE:
                BLangNumericLiteral numericLiteral = (BLangNumericLiteral) TreeBuilder.createNumericLiteralExpression();
                return createFiniteType(constantSymbol, updateLiteral(numericLiteral, value, type, pos));
            case TypeTags.BYTE:
                BLangNumericLiteral byteLiteral = (BLangNumericLiteral) TreeBuilder.createNumericLiteralExpression();
                return createFiniteType(constantSymbol, updateLiteral(byteLiteral, value, symTable.intType, pos));
            case TypeTags.STRING:
            case TypeTags.NIL:
            case TypeTags.BOOLEAN:
                BLangLiteral literal = (BLangLiteral) TreeBuilder.createLiteralExpression();
                return createFiniteType(constantSymbol, updateLiteral(literal, value, type, pos));
            case TypeTags.UNION:
                return createFiniteType(constantSymbol, value, (BUnionType) type, pos);
            default:
                return type;
        }
    }

    private BLangLiteral getLiteral(Object value, Location pos, BType type) {
        switch (type.tag) {
            case TypeTags.INT:
            case TypeTags.FLOAT:
            case TypeTags.DECIMAL:
                BLangNumericLiteral numericLiteral = (BLangNumericLiteral) TreeBuilder.createNumericLiteralExpression();
                return updateLiteral(numericLiteral, value, type, pos);
            case TypeTags.BYTE:
                BLangNumericLiteral byteLiteral = (BLangNumericLiteral) TreeBuilder.createNumericLiteralExpression();
                return updateLiteral(byteLiteral, value, symTable.byteType, pos);
            default:
                BLangLiteral literal = (BLangLiteral) TreeBuilder.createLiteralExpression();
                return updateLiteral(literal, value, type, pos);
        }
    }

    private BLangLiteral updateLiteral(BLangLiteral literal, Object value, BType type, Location pos) {
        literal.value = value;
        literal.isConstant = true;
        literal.setBType(type);
        literal.pos = pos;
        return literal;
    }

    private BFiniteType createFiniteType(BConstantSymbol constantSymbol, BLangExpression expr) {
        BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE, constantSymbol.flags, Names.EMPTY,
                constantSymbol.pkgID, null, constantSymbol.owner,
                constantSymbol.pos, VIRTUAL);
        BFiniteType finiteType = new BFiniteType(finiteTypeSymbol);
        finiteType.addValue(expr);
        finiteType.tsymbol.type = finiteType;
        return finiteType;
    }

    private BUnionType createFiniteType(BConstantSymbol constantSymbol, Object value, BUnionType type, Location pos) {
        LinkedHashSet<BType> memberTypes = new LinkedHashSet<>(3);
        for (BType memberType : type.getMemberTypes()) {
            BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE, constantSymbol.flags, Names.EMPTY,
                    constantSymbol.pkgID, null, constantSymbol.owner,
                    constantSymbol.pos, VIRTUAL);
            BFiniteType finiteType = new BFiniteType(finiteTypeSymbol);
//            BLangLiteral literal = (BLangLiteral) TreeBuilder.createLiteralExpression();
            Object memberValue;
            switch (memberType.tag) {
                case TypeTags.FLOAT:
                    memberValue =  value instanceof String? Double.parseDouble((String) value):((Long) value).doubleValue();
                    break;
                case TypeTags.DECIMAL:
                    memberValue = new BigDecimal(String.valueOf(value));
                    break;
                default:
                    memberValue = value;
            };
            finiteType.addValue(getLiteral(memberValue, pos, memberType));
            finiteType.tsymbol.type = finiteType;
            memberTypes.add(finiteType);
        }

        return BUnionType.create(null, memberTypes);
    }

    private BSymbol getSymbolOfVarRef(Location pos, SymbolEnv env, Name pkgAlias, Name varName, AnalyzerData data) {
        if (pkgAlias == Names.EMPTY && data.modTable.containsKey(varName.value)) {
            // modTable contains the available constants in current module.
            BLangNode node = data.modTable.get(varName.value);
            if (node.getKind() == NodeKind.CONSTANT) {
                if (!typeResolver.resolvedConstants.contains((BLangConstant) node)) {
                    typeResolver.resolveConstant(data.env, data.modTable, (BLangConstant) node);
                }
            } else {
                dlog.error(pos, DiagnosticErrorCode.EXPRESSION_IS_NOT_A_CONSTANT_EXPRESSION);
                return symTable.notFoundSymbol;
            }
        }

        // Search and get the referenced variable from different module.
        return symResolver.lookupMainSpaceSymbolInPackage(pos, env, pkgAlias, varName);
    }

    private boolean addFields(LinkedHashMap<String, BField> fields, BType keyValueType, String key, Location pos,
                           BRecordTypeSymbol recordSymbol, boolean overWriteValue) {
        Name fieldName = names.fromString(key);
        if (fields.containsKey(key)) {
            if (overWriteValue) {
                // This is used in computed field scenarios.
                BField field = fields.get(key);
                field.type = keyValueType;
                field.symbol.type = keyValueType;
                return true;
            }
            // Duplicate key.
            dlog.error(pos, DiagnosticErrorCode.DUPLICATE_KEY_IN_RECORD_LITERAL, key);
            return false;
        }
        Set<Flag> flags = new HashSet<>();
//        flags.add(Flag.REQUIRED); // All the fields in constant mapping types should be required fields.

        BVarSymbol fieldSymbol = new BVarSymbol(recordSymbol.flags, fieldName, recordSymbol.pkgID , keyValueType,
                recordSymbol, symTable.builtinPos, VIRTUAL);
        fields.put(fieldName.value, new BField(fieldName, null, fieldSymbol));
        return true;
    }

    private String getKeyName(BLangExpression key) {
        return key.getKind() == NodeKind.SIMPLE_VARIABLE_REF ?
                ((BLangSimpleVarRef) key).variableName.value : (String) ((BLangLiteral) key).value;
    }

    private BRecordTypeSymbol createRecordTypeSymbol(PackageID pkgID, Location location,
                                                     SymbolOrigin origin, AnalyzerData data) {
        SymbolEnv env = data.env;
        BRecordTypeSymbol recordSymbol =
                Symbols.createRecordSymbol(data.constantSymbol.flags,
                        names.fromString(anonymousModelHelper.getNextAnonymousTypeKey(pkgID)),
                        pkgID, null, env.scope.owner, location, origin);

        BInvokableType bInvokableType = new BInvokableType(new ArrayList<>(), symTable.nilType, null);
        BInvokableSymbol initFuncSymbol = Symbols.createFunctionSymbol(
                Flags.PUBLIC, Names.EMPTY, Names.EMPTY, env.enclPkg.symbol.pkgID, bInvokableType, env.scope.owner,
                false, symTable.builtinPos, VIRTUAL);
        initFuncSymbol.retType = symTable.nilType;
        recordSymbol.initializerFunc = new BAttachedFunction(Names.INIT_FUNCTION_SUFFIX, initFuncSymbol,
                bInvokableType, location);

        recordSymbol.scope = new Scope(recordSymbol);
        recordSymbol.scope.define(
                names.fromString(recordSymbol.name.value + "." + recordSymbol.initializerFunc.funcName.value),
                recordSymbol.initializerFunc.symbol);
        return recordSymbol;
    }

    private BFiniteType getCorrespondingFiniteType(BType type, BType expType) {
        // Here, type can be broad union type. This will return member type corresponding to expType.
        // type = 1|1.0f|1.0d, expType = float, Result = 1.0f
        if (type.tag == TypeTags.UNION) {
            for (BType memberType: ((BUnionType) type).getMemberTypes()) {
                BFiniteType result = getCorrespondingFiniteType(memberType, expType);
                if (result != null) {
                    return result;
                }
            }
        } else if (type.tag == TypeTags.FINITE && types.isAssignable(type, expType)) {
            return (BFiniteType) type;
        }
        return null;
    }

    private BSymbol getOpSymbolBothUnion(OperatorKind opKind, BUnionType lhsType, BUnionType rhsType, BLangBinaryExpr binaryExpr, AnalyzerData data) {
        BSymbol firstValidOpSymbol = symTable.notFoundSymbol;
        LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
        LinkedHashSet<BType> removableLhsMemberTypes = new LinkedHashSet<>();
        LinkedHashSet<BType> removableRhsMemberTypes = new LinkedHashSet<>();
        for (BType memberTypeRhs : rhsType.getMemberTypes()) {
            removableRhsMemberTypes.add(memberTypeRhs);
        }
        for (BType memberTypeLhs : lhsType.getMemberTypes()) {
            boolean isValidLhsMemberType = false;
            for (BType memberTypeRhs : rhsType.getMemberTypes()) {
                BSymbol resultantOpSymbol = getOpSymbol(memberTypeLhs, memberTypeRhs, binaryExpr, data);
                if (data.resultType != symTable.semanticError) {
                    memberTypes.add(data.resultType);
                    isValidLhsMemberType = true;
                    if (removableRhsMemberTypes.contains(memberTypeRhs)) {
                        removableRhsMemberTypes.remove(memberTypeRhs);
                    }
                }
                if (firstValidOpSymbol == symTable.notFoundSymbol && resultantOpSymbol != symTable.notFoundSymbol) {
                    firstValidOpSymbol = resultantOpSymbol;
                }
            }
            if (!isValidLhsMemberType) {
                removableLhsMemberTypes.add(memberTypeLhs);
            }
        }
        for (BType memberTypeRhs : removableRhsMemberTypes) {
            rhsType.remove(memberTypeRhs);
        }
        for (BType memberTypeLhs : removableLhsMemberTypes) {
            lhsType.remove(memberTypeLhs);
        }
        if (memberTypes.size() != 1) {
            data.resultType = BUnionType.create(null, memberTypes);
        } else if (memberTypes.size() == 1) {
            data.resultType = memberTypes.iterator().next();
        }
        return firstValidOpSymbol;
    }

    private BSymbol getOpSymbolLhsUnion(OperatorKind opKind, BUnionType lhsType, BType rhsType, BLangBinaryExpr binaryExpr, AnalyzerData data, boolean swap) {
        BSymbol firstValidOpSymbol = symTable.notFoundSymbol;
        LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
        LinkedHashSet<BType> removableLhsMemberTypes = new LinkedHashSet<>();
        for (BType memberTypeLhs : lhsType.getMemberTypes()) {
            boolean isValidLhsMemberType = false;
            BSymbol resultantOpSymbol;
            if (swap) {
                resultantOpSymbol = getOpSymbol(rhsType, memberTypeLhs, binaryExpr, data);
            } else {
                resultantOpSymbol = getOpSymbol(memberTypeLhs, rhsType, binaryExpr, data);
            }
            if (data.resultType != symTable.semanticError) {
                memberTypes.add(data.resultType);
                isValidLhsMemberType = true;
            }
            if (firstValidOpSymbol == symTable.notFoundSymbol && resultantOpSymbol != symTable.notFoundSymbol) {
                firstValidOpSymbol = resultantOpSymbol;
            }

            if (!isValidLhsMemberType) {
                removableLhsMemberTypes.add(memberTypeLhs);
            }
        }
        for (BType memberTypeLhs : removableLhsMemberTypes) {
            lhsType.remove(memberTypeLhs);
        }
        if (memberTypes.size() != 1) {
            data.resultType = BUnionType.create(null, memberTypes);
        } else if (memberTypes.size() == 1) {
            data.resultType = memberTypes.iterator().next();
        }
        return firstValidOpSymbol;
    }

    private BSymbol getOpSymbolBothNonUnion(BType lhsType, BType rhsType, BLangBinaryExpr binaryExpr, AnalyzerData data) {
        return getOpSymbol(lhsType, rhsType, binaryExpr, data);
    }

    private BSymbol getOpSymbol(BType lhsType, BType rhsType, BLangBinaryExpr binaryExpr, AnalyzerData data) {
        BSymbol opSymbol = symResolver.resolveBinaryOperator(binaryExpr.opKind, lhsType, rhsType);
        if (lhsType != symTable.semanticError && rhsType != symTable.semanticError) {
            // Look up operator symbol if both rhs and lhs types aren't error or xml types

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getBitwiseShiftOpsForTypeSets(binaryExpr.opKind, lhsType, rhsType);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getBinaryBitwiseOpsForTypeSets(binaryExpr.opKind, lhsType, rhsType);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getArithmeticOpsForTypeSets(binaryExpr.opKind, lhsType, rhsType);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getBinaryEqualityForTypeSets(binaryExpr.opKind, lhsType, rhsType,
                        binaryExpr, data.env);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getBinaryComparisonOpForTypeSets(binaryExpr.opKind, lhsType, rhsType);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                opSymbol = symResolver.getRangeOpsForTypeSets(binaryExpr.opKind, lhsType, rhsType);
            }

            if (opSymbol == symTable.notFoundSymbol) {
                DiagnosticErrorCode errorCode = DiagnosticErrorCode.BINARY_OP_INCOMPATIBLE_TYPES;

                if ((binaryExpr.opKind == OperatorKind.DIV || binaryExpr.opKind == OperatorKind.MOD) &&
                        lhsType.tag == TypeTags.INT &&
                        (rhsType.tag == TypeTags.DECIMAL || rhsType.tag == TypeTags.FLOAT)) {
                    errorCode = DiagnosticErrorCode.BINARY_OP_INCOMPATIBLE_TYPES_INT_FLOAT_DIVISION;
                }

                dlog.error(binaryExpr.pos, errorCode, binaryExpr.opKind, lhsType, rhsType);
                data.resultType = symTable.semanticError;
            } else {
                data.resultType = opSymbol.type.getReturnType();
            }
        }
        return opSymbol;
    }

    private void logUndefinedSymbolError(Location pos, String name) {
        if (!missingNodesHelper.isMissingNode(name)) {
            dlog.error(pos, DiagnosticErrorCode.UNDEFINED_SYMBOL, name);
        }
    }

    /**
     * @since 2201.4.0
     */
    public static class FillMembers implements TypeVisitor {

        private static final CompilerContext.Key<ConstantTypeChecker.FillMembers> FILL_MEMBERS_KEY =
                new CompilerContext.Key<>();

        private final SymbolTable symTable;
        private final Types types;
        private final ConstantTypeChecker constantTypeChecker;
        private final Names names;
        private final BLangDiagnosticLog dlog;

        private AnalyzerData data;

        public FillMembers(CompilerContext context) {
            context.put(FILL_MEMBERS_KEY, this);

            this.symTable = SymbolTable.getInstance(context);
            this.types = Types.getInstance(context);
            this.constantTypeChecker = ConstantTypeChecker.getInstance(context);
            this.names = Names.getInstance(context);
            this.dlog = BLangDiagnosticLog.getInstance(context);;
        }

        public static ConstantTypeChecker.FillMembers getInstance(CompilerContext context) {
            ConstantTypeChecker.FillMembers fillMembers = context.get(FILL_MEMBERS_KEY);
            if (fillMembers == null) {
                fillMembers = new ConstantTypeChecker.FillMembers(context);
            }

            return fillMembers;
        }

        public boolean addFillMembers(BTupleType type, BType expType, AnalyzerData data) {
            BType refType = Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(expType));
            List<BType> tupleTypes = type.getTupleTypes();
            int tupleMemberCount = tupleTypes.size();
            if (refType.tag == TypeTags.ARRAY) {
                BArrayType arrayType = (BArrayType) expType;
                int noOfFillMembers = arrayType.size - tupleMemberCount;
                BType fillMemberType = getFillMembers(arrayType.eType, data);
                if (fillMemberType == symTable.semanticError) {
                    return false;
                }
                for (int i = 0; i < noOfFillMembers; i++) {
                    type.addMembers(
                            new BTupleMember(fillMemberType, Symbols.createVarSymbolForTupleMember(fillMemberType)));
                }
            } else if (refType.tag == TypeTags.TUPLE) {
                List<BType> bTypeList = ((BTupleType) expType).getTupleTypes();
                for (int i = tupleMemberCount; i < bTypeList.size(); i++) {
                    BType fillMemberType = getFillMembers(bTypeList.get(i), data);
                    if (fillMemberType == symTable.semanticError) {
                        return false;
                    }
                    type.addMembers(
                            new BTupleMember(fillMemberType, Symbols.createVarSymbolForTupleMember(fillMemberType)));
                }
            }
            return true;
        }

        public BType getFillMembers(BType type, AnalyzerData data) {
            this.data = data;
            data.resultType = symTable.semanticError;
            type.accept(this);

            if (data.resultType == symTable.semanticError) {
                dlog.error(data.constantSymbol.pos, DiagnosticErrorCode.INVALID_LIST_CONSTRUCTOR_ELEMENT_TYPE,
                        data.expType);
            }
            return data.resultType;
        }

        @Override
        public void visit(BAnnotationType bAnnotationType) {

        }

        @Override
        public void visit(BArrayType arrayType) {
            BTypeSymbol tupleTypeSymbol = Symbols.createTypeSymbol(SymTag.TUPLE_TYPE, Flags.asMask(EnumSet.of(Flag.PUBLIC)),
                    Names.EMPTY, data.env.enclPkg.symbol.pkgID, null,
                    data.env.scope.owner, null, SOURCE);
            if (arrayType.state == BArrayState.OPEN) {
                BTupleType resultTupleType = new BTupleType(tupleTypeSymbol, new ArrayList<>());
                tupleTypeSymbol.type = resultTupleType;
                data.resultType = resultTupleType;
                return;
            } else if (arrayType.state == BArrayState.INFERRED) {
                data.resultType = symTable.semanticError;
                return;
            }
            BType fillMemberType = getFillMembers(arrayType.eType, data);
            if (fillMemberType == symTable.semanticError) {
                data.resultType = symTable.semanticError;
                return;
            }
            List<BType> tupleTypes = new ArrayList<>(arrayType.size);
            for (int i = 0; i < arrayType.size; i++) {
                tupleTypes.add(fillMemberType);
            }
            List<BTupleMember> members = new ArrayList<>();
            tupleTypes.forEach(m ->
                    members.add(new BTupleMember(m, Symbols.createVarSymbolForTupleMember(m))));
            BTupleType resultTupleType = new BTupleType(tupleTypeSymbol, members);
            tupleTypeSymbol.type = resultTupleType;
            data.resultType = resultTupleType;
        }

        @Override
        public void visit(BBuiltInRefType bBuiltInRefType) {

        }

        @Override
        public void visit(BAnyType bAnyType) {
            data.resultType = symTable.nilType;
        }

        @Override
        public void visit(BAnydataType bAnydataType) {
            data.resultType = symTable.nilType;
        }

        @Override
        public void visit(BErrorType bErrorType) {

        }

        @Override
        public void visit(BFiniteType finiteType) {
            Set<BLangExpression> valueSpace = finiteType.getValueSpace();
            if (valueSpace.size() > 1) {
                if (finiteType.isNullable()) { // Ex. 1|null
                    data.resultType = symTable.nilType;
                    return;
                }
                data.resultType = symTable.semanticError;
                return;
            }
            if (finiteType.isNullable()) {
                // Compiler takes () as nilType and null as a finite ().
                // Added this logic to keep the consistency.
                data.resultType = symTable.nilType;
                return;
            }
            data.resultType = finiteType;
        }

        @Override
        public void visit(BInvokableType bInvokableType) {

        }

        @Override
        public void visit(BJSONType bjsonType) {
            data.resultType = symTable.nilType;
        }

        @Override
        public void visit(BMapType bMapType) {
            data.resultType = symTable.mapType;
        }

        @Override
        public void visit(BStreamType bStreamType) {

        }

        @Override
        public void visit(BTypedescType bTypedescType) {

        }

        @Override
        public void visit(BTypeReferenceType bTypeReferenceType) {

        }

        @Override
        public void visit(BParameterizedType bTypedescType) {

        }

        @Override
        public void visit(BNeverType bNeverType) {

        }

        @Override
        public void visit(BNilType bNilType) {
            data.resultType = symTable.nilType;
        }

        @Override
        public void visit(BNoType bNoType) {

        }

        @Override
        public void visit(BPackageType bPackageType) {

        }

        @Override
        public void visit(BStructureType bStructureType) {

        }

        @Override
        public void visit(BTupleType tupleType) {
            List<BType> bTypeList = tupleType.getTupleTypes();
            BTypeSymbol tupleTypeSymbol = Symbols.createTypeSymbol(SymTag.TUPLE_TYPE, Flags.asMask(EnumSet.of(Flag.PUBLIC)),
                    Names.EMPTY, data.env.enclPkg.symbol.pkgID, null,
                    data.env.scope.owner, null, SOURCE);
            List<BType> tupleTypes = new ArrayList<>(bTypeList.size());
            for (int i = 0; i < bTypeList.size(); i++) {
                BType fillMemberType = getFillMembers(bTypeList.get(i), data);
                if (fillMemberType == symTable.semanticError) {
                    data.resultType = symTable.semanticError;
                    return;
                }
                tupleTypes.add(fillMemberType);
            }
            List<BTupleMember> members = new ArrayList<>();
            tupleTypes.forEach(m ->
                    members.add(new BTupleMember(m, Symbols.createVarSymbolForTupleMember(m))));
            BTupleType resultTupleType = new BTupleType(tupleTypeSymbol, members);
            tupleTypeSymbol.type = resultTupleType;
            data.resultType = resultTupleType;
            return;
        }

        @Override
        public void visit(BUnionType unionType) {
            LinkedHashSet<BType> memberTypes = unionType.getMemberTypes();
            if (memberTypes.size() == 1) {
                getFillMembers(memberTypes.iterator().next(), data);
                return;
            }
            if (memberTypes.size() > 2 || !unionType.isNullable()) {
                data.resultType = symTable.semanticError;
                return;
            }
            data.resultType = symTable.nilType;
        }

        @Override
        public void visit(BIntersectionType intersectionType) {
            data.resultType = getFillMembers(intersectionType.getEffectiveType(), data);
        }

        @Override
        public void visit(BXMLType bxmlType) {

        }

        @Override
        public void visit(BTableType bTableType) {
            data.resultType = symTable.tableType;
        }

        @Override
        public void visit(BRecordType recordType) {
            LinkedHashMap<String, BField> fields = recordType.fields;
            BRecordTypeSymbol recordSymbol = constantTypeChecker.createRecordTypeSymbol(data.constantSymbol.pkgID,
                    data.constantSymbol.pos, VIRTUAL, data);
            LinkedHashMap<String, BField> newFields = new LinkedHashMap<>();
            for (String key : fields.keySet()) {
                BField field = fields.get(key);
                if ((field.symbol.flags & Flags.REQUIRED) != Flags.REQUIRED) {
                    continue;
                }
                BType fillMemberType = getFillMembers(fields.get(key).type, data);
                if (fillMemberType == symTable.semanticError) {
                    data.resultType = symTable.semanticError;
                    return;
                }
                Name fieldName = names.fromString(key);
                Set<Flag> flags = new HashSet<>();
                flags.add(Flag.REQUIRED);
                BVarSymbol fieldSymbol = new BVarSymbol(Flags.asMask(flags), fieldName, recordSymbol.pkgID , fillMemberType,
                        recordSymbol, symTable.builtinPos, VIRTUAL);
                newFields.put(key, new BField(fieldName, null, fieldSymbol));
            }
            BRecordType resultRecordType = new BRecordType(recordSymbol);
            resultRecordType.fields = newFields;
            recordSymbol.type = resultRecordType;
            resultRecordType.tsymbol = recordSymbol;
            resultRecordType.sealed = true;
            data.resultType = resultRecordType;
            data.resultType = data.inferredType;
        }

        @Override
        public void visit(BObjectType bObjectType) {

        }

        @Override
        public void visit(BType type) {
            BType refType = Types.getReferredType(type);
            switch (refType.tag) {
                case TypeTags.BOOLEAN:
                    data.resultType = symTable.falseType;
                    return;
                case TypeTags.INT:
                case TypeTags.SIGNED8_INT:
                case TypeTags.SIGNED16_INT:
                case TypeTags.SIGNED32_INT:
                case TypeTags.UNSIGNED8_INT:
                case TypeTags.UNSIGNED16_INT:
                case TypeTags.UNSIGNED32_INT:
                case TypeTags.BYTE:
                    data.resultType = constantTypeChecker.getFiniteType(0l, data.constantSymbol, null, symTable.intType);
                    return;
                case TypeTags.FLOAT:
                    data.resultType = constantTypeChecker.getFiniteType(0.0d, data.constantSymbol, null, symTable.floatType);
                    return;
                case TypeTags.DECIMAL:
                    data.resultType = constantTypeChecker.getFiniteType(new BigDecimal(0), data.constantSymbol, null, symTable.decimalType);
                    return;
                case TypeTags.STRING:
                case TypeTags.CHAR_STRING:
                    data.resultType = constantTypeChecker.getFiniteType("", data.constantSymbol, null, symTable.stringType);
                    return;
                default:
                    data.resultType = symTable.semanticError;
            }
        }

        @Override
        public void visit(BFutureType bFutureType) {

        }

        @Override
        public void visit(BHandleType bHandleType) {

        }
    }
    
    public BType getNarrowedType(BType type) {
        switch (type.tag) {
            case TypeTags.FINITE:
                return type;
            case TypeTags.UNION:
                return ((BUnionType) type).getMemberTypes().iterator().next();
            case TypeTags.RECORD:
                for (String key : ((BRecordType) type).fields.keySet()) {
                    BField field = ((BRecordType) type).fields.get(key);
                    field.type = getNarrowedType(field.type);
                }
                return type;
            case TypeTags.ARRAY:
                ((BArrayType) type).eType = getNarrowedType(((BArrayType) type).eType);
                return type;
            case TypeTags.TUPLE:
                List<BType> tupleTypes = ((BTupleType) type).getTupleTypes();
                List<BType> newTupleTypes = new ArrayList<>(tupleTypes.size());
                for (BType memberType : tupleTypes) {
                    newTupleTypes.add(getNarrowedType(memberType));
                }
                List<BTupleMember> members = new ArrayList<>();
                newTupleTypes.forEach(m ->
                        members.add(new BTupleMember(m, Symbols.createVarSymbolForTupleMember(m))));
                ((BTupleType) type).setMembers(members);
                return type;
            default:
                return symTable.semanticError;
        }
    }

    public BLangConstantValue getConstantValue(BType type) {
        // Obtain the constant value using its type.
        switch (type.tag) {
            case TypeTags.FINITE:
                BLangExpression expr = ((BFiniteType) type).getValueSpace().iterator().next();
                if (expr.getBType().tag == TypeTags.DECIMAL) {
                    return new BLangConstantValue ((((BLangNumericLiteral) expr).value).toString(), expr.getBType());
                }
                return new BLangConstantValue (((BLangLiteral) expr).value, expr.getBType());
            case TypeTags.INTERSECTION:
                return getConstantValue(((BIntersectionType) type).getEffectiveType());
            case TypeTags.RECORD:
                Map<String, BLangConstantValue> fields = new HashMap<>();
                LinkedHashMap<String, BField> recordFields = ((BRecordType) type).fields;
                for (String key : recordFields.keySet()) {
                    BLangConstantValue constantValue = getConstantValue(recordFields.get(key).type);
                    fields.put(key, constantValue);
                }
                return new BLangConstantValue(fields, ((BRecordType) type).getIntersectionType().get());
            case TypeTags.TUPLE:
                List<BLangConstantValue> members = new ArrayList<>();
                List<BType> tupleTypes = ((BTupleType) type).getTupleTypes();
                for (BType memberType : tupleTypes) {
                    BLangConstantValue constantValue = getConstantValue(memberType);
                    members.add(constantValue);
                }
                return new BLangConstantValue(members, type);
            case TypeTags.NIL:
                return new BLangConstantValue (type.tsymbol.getType().toString(), type.tsymbol.getType());
            default:
                return null;
        }
    }

    /**
     * @since 2201.4.0
     */
    public static class ResolveConstantExpressionType extends SimpleBLangNodeAnalyzer<ConstantTypeChecker.AnalyzerData> {

        private static final CompilerContext.Key<ConstantTypeChecker.ResolveConstantExpressionType>
                RESOLVE_CONSTANT_EXPRESSION_TYPE = new CompilerContext.Key<>();

        private final SymbolTable symTable;
        private final Types types;
        private final ConstantTypeChecker constantTypeChecker;

        private List<BLangTypeDefinition> resolvingtypeDefinitions = new ArrayList<>();

        public ResolveConstantExpressionType(CompilerContext context) {
            context.put(RESOLVE_CONSTANT_EXPRESSION_TYPE, this);

            this.symTable = SymbolTable.getInstance(context);
            this.types = Types.getInstance(context);
            this.constantTypeChecker = ConstantTypeChecker.getInstance(context);
        }

        public static ResolveConstantExpressionType getInstance(CompilerContext context) {
            ResolveConstantExpressionType resolveConstantExpressionType = context.get(RESOLVE_CONSTANT_EXPRESSION_TYPE);
            if (resolveConstantExpressionType == null) {
                resolveConstantExpressionType = new ResolveConstantExpressionType(context);
            }

            return resolveConstantExpressionType;
        }

        public BType resolveConstExpr(BLangExpression expr, BType expType, AnalyzerData data) {
            return resolveConstExpr(expr, data.env, expType, DiagnosticErrorCode.INCOMPATIBLE_TYPES, data);
        }

        public BType resolveConstExpr(BLangExpression expr, SymbolEnv env, BType expType, DiagnosticCode diagCode,
                                    AnalyzerData data) {

            SymbolEnv prevEnv = data.env;
            BType preExpType = data.expType;
            DiagnosticCode preDiagCode = data.diagCode;
            data.env = env;
            data.diagCode = diagCode;
            if (expType.tag == TypeTags.INTERSECTION) {
                data.expType = ((BIntersectionType) expType).effectiveType;
            } else {
                data.expType = expType;
            }

            expr.expectedType = expType;

            expr.accept(this, data);

            data.env = prevEnv;
            data.expType = preExpType;
            data.diagCode = preDiagCode;

            return data.resultType;
        }

        @Override
        public void analyzeNode(BLangNode node, AnalyzerData data) {

        }

        @Override
        public void visit(BLangPackage node, AnalyzerData data) {

        }

        @Override
        public void visit(BLangLiteral literalExpr, AnalyzerData data) {
            updateBlangExprType(literalExpr, data);
        }

        private void updateBlangExprType(BLangExpression expression, AnalyzerData data) {
            BType expressionType = expression.getBType();
            if (expressionType.tag == TypeTags.FINITE) {
                expressionType = ((BFiniteType) expressionType).getValueSpace().iterator().next().getBType();
                expression.setBType(expressionType);
                types.setImplicitCastExpr(expression, data.expType, expressionType);
                return;
            }
            if (expressionType.tag != TypeTags.UNION) {
                return;
            }

            BType targetType = symTable.noType;
            BType expType = data.expType;
            if (expType.tag == TypeTags.FINITE) {
                targetType = ((BFiniteType) expType).getValueSpace().iterator().next().getBType();
            } else {
                targetType = expType;
            }

            for (BType memberType : ((BUnionType) expressionType).getMemberTypes()) {
                BType type = ((BFiniteType) memberType).getValueSpace().iterator().next().getBType();

                if (type.tag == targetType.tag || types.isAssignable(memberType, targetType)) {
                    expression.setBType(type);
                    types.setImplicitCastExpr(expression, type, memberType);
                    return;
                }
            }
        }

        @Override
        public void visit(BLangSimpleVarRef varRefExpr, AnalyzerData data) {

        }

        public void visit(BLangListConstructorExpr listConstructor, AnalyzerData data) {
            BType resolvedType = data.expType;
            BTupleType tupleType = (BTupleType) ((resolvedType.tag == TypeTags.INTERSECTION)?
                    ((BIntersectionType) resolvedType).effectiveType : resolvedType);
            List<BType> resolvedMemberType = tupleType.getTupleTypes();
            listConstructor.setBType(data.expType);
            int currentListIndex = 0;
            for (BLangExpression memberExpr : listConstructor.exprs) {
                if (memberExpr.getKind() == NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                    BLangListConstructorExpr.BLangListConstructorSpreadOpExpr spreadOp = (BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) memberExpr;
                    BTupleType type = (BTupleType) Types.getReferredType(types.getTypeWithEffectiveIntersectionTypes(spreadOp.expr.getBType()));
                    spreadOp.setBType(spreadOp.expr.getBType());
                    currentListIndex += type.getTupleTypes().size();
                    continue;
                }
                resolveConstExpr(memberExpr, resolvedMemberType.get(currentListIndex), data);
                currentListIndex++;
            }
        }

        public void visit(BLangRecordLiteral recordLiteral, AnalyzerData data) {
            BType expFieldType;
            BType resolvedType = data.expType;
            recordLiteral.setBType(data.expType);
            for (RecordLiteralNode.RecordField field : recordLiteral.fields) {
                if (field.isKeyValueField()) {
                    BLangRecordLiteral.BLangRecordKeyValueField keyValue =
                            (BLangRecordLiteral.BLangRecordKeyValueField) field;
                    BLangRecordLiteral.BLangRecordKey key = keyValue.key;
                    if (key.computedKey) {
                        BLangRecordLiteral.BLangRecordKeyValueField computedKeyValue = (BLangRecordLiteral.BLangRecordKeyValueField) field;
                        BLangRecordLiteral.BLangRecordKey computedKey = computedKeyValue.key;
                        BType fieldName = constantTypeChecker.checkConstExpr(computedKey.expr, data);
                        BLangLiteral fieldNameLiteral = (BLangLiteral) ((BFiniteType) fieldName).getValueSpace().iterator().next();
                        expFieldType = getResolvedFieldType(constantTypeChecker.getKeyName(fieldNameLiteral), resolvedType);
                        resolveConstExpr(computedKey.expr, expFieldType, data);
                        continue;
                    }
                    BLangExpression keyValueExpr = keyValue.valueExpr;
                    expFieldType = getResolvedFieldType(constantTypeChecker.getKeyName(key.expr), resolvedType);
                    resolveConstExpr(keyValueExpr, expFieldType, data);
                } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                    BLangRecordLiteral.BLangRecordVarNameField varNameField =
                            (BLangRecordLiteral.BLangRecordVarNameField) field;
                    expFieldType = getResolvedFieldType(constantTypeChecker.getKeyName(varNameField), resolvedType);
                    resolveConstExpr(varNameField, expFieldType, data);
                } else {
                    // Spread Field
                    // Spread fields are not required to resolve separately since they are constant references.
                    BLangRecordLiteral.BLangRecordSpreadOperatorField spreadField =
                            (BLangRecordLiteral.BLangRecordSpreadOperatorField) field;
                    spreadField.setBType(spreadField.expr.getBType());
                }
            }
        }

        private BType getResolvedFieldType(Object targetKey, BType resolvedType) {
            BRecordType recordType = (BRecordType) ((resolvedType.tag == TypeTags.INTERSECTION)?
                    ((BIntersectionType) resolvedType).effectiveType : resolvedType);
            for (String key : recordType.getFields().keySet()) {
                if (key.equals(targetKey)) {
                    BType type = recordType.getFields().get(key).type;
//                    if (type.tag == TypeTags.FINITE) {
//                        return ((BFiniteType) type).getValueSpace().iterator().next().getBType();
//                    }
                    return type;
                }
            }
            return null;
        }

        @Override
        public void visit(BLangBinaryExpr binaryExpr, AnalyzerData data) {
            switch (binaryExpr.opKind) {
                case OR:
                case AND:
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                case MOD:
                    resolveConstExpr(binaryExpr.lhsExpr, data.expType, data);
                    resolveConstExpr(binaryExpr.rhsExpr, data.expType, data);
                    updateBlangExprType(binaryExpr, data);
                    BInvokableType invokableType = (BInvokableType) binaryExpr.opSymbol.type;
                    ArrayList<BType> paramTypes = new ArrayList<>(2);
                    paramTypes.add(binaryExpr.lhsExpr.getBType());
                    paramTypes.add(binaryExpr.rhsExpr.getBType());
                    invokableType.paramTypes = paramTypes;
                    invokableType.retType = binaryExpr.getBType();
                    return;
            }
        }

        public void visit(BLangUnaryExpr unaryExpr, AnalyzerData data) {
            updateBlangExprType(unaryExpr.expr, data);
            updateBlangExprType(unaryExpr, data);
            BInvokableType invokableType = (BInvokableType) unaryExpr.opSymbol.type;
            ArrayList<BType> paramTypes = new ArrayList<>(1);
            paramTypes.add(unaryExpr.expr.getBType());
            invokableType.paramTypes = paramTypes;
            invokableType.retType = unaryExpr.getBType();
        }

        public void visit(BLangGroupExpr groupExpr, AnalyzerData data) {
            updateBlangExprType(groupExpr.expression, data);
            updateBlangExprType(groupExpr, data);
        }
    }

    /**
     * @since 2201.4.0
     */
    public static class AnalyzerData extends TypeChecker.AnalyzerData {
        public SymbolEnv env;
        boolean isTypeChecked;
        Stack<SymbolEnv> prevEnvs;
        Types.CommonAnalyzerData commonAnalyzerData = new Types.CommonAnalyzerData();
        DiagnosticCode diagCode;
        BType expType;
        BType inferredType;
        BType resultType;
        Map<String, BLangNode> modTable;
        BConstantSymbol constantSymbol;
        private Stack<BLangExpression> compoundExprs = new Stack<>();
    }
}

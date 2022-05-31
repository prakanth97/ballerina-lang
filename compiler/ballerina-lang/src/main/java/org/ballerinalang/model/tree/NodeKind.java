/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.ballerinalang.model.tree;

/**
 * @since 0.94
 */
public enum NodeKind {

    ANNOTATION,
    ANNOTATION_ATTACHMENT,
    ANNOTATION_ATTRIBUTE,
    COMPILATION_UNIT,
    DEPRECATED,
    DOCUMENTATION,
    MARKDOWN_DOCUMENTATION,
    ENDPOINT,
    FUNCTION,
    RESOURCE_FUNC,
    BLOCK_FUNCTION_BODY,
    EXPR_FUNCTION_BODY,
    EXTERN_FUNCTION_BODY,
    IDENTIFIER,
    IMPORT,
    PACKAGE,
    PACKAGE_DECLARATION,
    RECORD_LITERAL_KEY_VALUE,
    RECORD_LITERAL_SPREAD_OP,
    RESOURCE,
    SERVICE,
    TYPE_DEFINITION,
    VARIABLE,
    LET_VARIABLE,
    TUPLE_VARIABLE,
    RECORD_VARIABLE,
    ERROR_VARIABLE,
    WORKER,
    XMLNS,
    CHANNEL,
    WAIT_LITERAL_KEY_VALUE,
    TABLE_KEY_SPECIFIER,
    TABLE_KEY_TYPE_CONSTRAINT,
    RETRY_SPEC,
    CLASS_DEFN,

    /* Expressions */
    DOCUMENTATION_ATTRIBUTE,
    ARRAY_LITERAL_EXPR,
    TUPLE_LITERAL_EXPR,
    LIST_CONSTRUCTOR_EXPR,
    LIST_CONSTRUCTOR_SPREAD_OP,
    BINARY_EXPR,
    QUERY_EXPR,
    ELVIS_EXPR,
    GROUP_EXPR,
    TYPE_INIT_EXPR,
    FIELD_BASED_ACCESS_EXPR,
    INDEX_BASED_ACCESS_EXPR,
    INT_RANGE_EXPR,
    INVOCATION,
    LAMBDA,
    ARROW_EXPR,
    LITERAL,
    NUMERIC_LITERAL,
    HEX_FLOATING_POINT_LITERAL,
    INTEGER_LITERAL,
    DECIMAL_FLOATING_POINT_LITERAL,
    CONSTANT,
    RECORD_LITERAL_EXPR,
    SIMPLE_VARIABLE_REF,
    CONSTANT_REF,
    TUPLE_VARIABLE_REF,
    RECORD_VARIABLE_REF,
    ERROR_VARIABLE_REF,
    STRING_TEMPLATE_LITERAL,
    RAW_TEMPLATE_LITERAL,
    TERNARY_EXPR,
    WAIT_EXPR,
    TRAP_EXPR,
    TYPEDESC_EXPRESSION,
    ANNOT_ACCESS_EXPRESSION,
    TYPE_CONVERSION_EXPR,
    IS_ASSIGNABLE_EXPR,
    UNARY_EXPR,
    REST_ARGS_EXPR,
    NAMED_ARGS_EXPR,
    XML_QNAME,
    XML_ATTRIBUTE,
    XML_ATTRIBUTE_ACCESS_EXPR,
    XML_QUOTED_STRING,
    XML_ELEMENT_LITERAL,
    XML_TEXT_LITERAL,
    XML_COMMENT_LITERAL,
    XML_PI_LITERAL,
    XML_SEQUENCE_LITERAL,
    XML_ELEMENT_FILTER_EXPR,
    XML_ELEMENT_ACCESS,
    XML_NAVIGATION,
    STATEMENT_EXPRESSION,
    MATCH_EXPRESSION,
    MATCH_EXPRESSION_PATTERN_CLAUSE,
    CHECK_EXPR,
    CHECK_PANIC_EXPR,
    FAIL,
    ERROR_CONSTRUCTOR,
    TYPE_TEST_EXPR,
    IS_LIKE,
    IGNORE_EXPR,
    DOCUMENTATION_DESCRIPTION,
    DOCUMENTATION_PARAMETER,
    DOCUMENTATION_REFERENCE,
    DOCUMENTATION_DEPRECATION,
    DOCUMENTATION_DEPRECATED_PARAMETERS,
    SERVICE_CONSTRUCTOR,
    LET_EXPR,
    TABLE_CONSTRUCTOR_EXPR,
    TRANSACTIONAL_EXPRESSION,
    OBJECT_CTOR_EXPRESSION,
    ERROR_CONSTRUCTOR_EXPRESSION,
    DYNAMIC_PARAM_EXPR,
    INFER_TYPEDESC_EXPR,

    /* Statements */
    ABORT,
    DONE,
    RETRY,
    RETRY_TRANSACTION,
    ASSIGNMENT,
    COMPOUND_ASSIGNMENT,
    POST_INCREMENT,
    BLOCK,
    BREAK,
    NEXT,
    EXPRESSION_STATEMENT,
    FOREACH,
    FORK_JOIN,
    IF,
    MATCH,
    MATCH_STATEMENT,
    MATCH_TYPED_PATTERN_CLAUSE,
    MATCH_STATIC_PATTERN_CLAUSE,
    MATCH_STRUCTURED_PATTERN_CLAUSE,
    REPLY,
    RETURN,
    THROW,
    PANIC,
    TRANSACTION,
    TRANSFORM,
    TUPLE_DESTRUCTURE,
    RECORD_DESTRUCTURE,
    ERROR_DESTRUCTURE,
    VARIABLE_DEF,
    WHILE,
    LOCK,
    WORKER_RECEIVE,
    WORKER_SEND,
    WORKER_SYNC_SEND,
    WORKER_FLUSH,
    STREAM,
    SCOPE,
    COMPENSATE,
    CHANNEL_RECEIVE,
    CHANNEL_SEND,
    DO_ACTION,
    COMMIT,
    ROLLBACK,
    DO_STMT,

    /* Clauses */
    SELECT,
    FROM,
    JOIN,
    WHERE,
    DO,
    LET_CLAUSE,
    ON_CONFLICT,
    ON,
    LIMIT,
    ORDER_BY,
    ORDER_KEY,
    ON_FAIL,

    /* Match statement */
    MATCH_CLAUSE,
    MATCH_GUARD,

    /* Match patterns */
    CONST_MATCH_PATTERN,
    WILDCARD_MATCH_PATTERN,
    VAR_BINDING_PATTERN_MATCH_PATTERN,
    LIST_MATCH_PATTERN,
    REST_MATCH_PATTERN,
    MAPPING_MATCH_PATTERN,
    FIELD_MATCH_PATTERN,
    ERROR_MATCH_PATTERN,
    ERROR_MESSAGE_MATCH_PATTERN,
    ERROR_CAUSE_MATCH_PATTERN,
    ERROR_FIELD_MATCH_PATTERN,
    NAMED_ARG_MATCH_PATTERN,
    SIMPLE_MATCH_PATTERN,

    /* Binding patterns*/
    WILDCARD_BINDING_PATTERN,
    CAPTURE_BINDING_PATTERN,
    LIST_BINDING_PATTERN,
    REST_BINDING_PATTERN,
    FIELD_BINDING_PATTERN,
    MAPPING_BINDING_PATTERN,
    ERROR_BINDING_PATTERN,
    ERROR_MESSAGE_BINDING_PATTERN,
    ERROR_CAUSE_BINDING_PATTERN,
    ERROR_FIELD_BINDING_PATTERN,
    NAMED_ARG_BINDING_PATTERN,
    SIMPLE_BINDING_PATTERN,

    /* Types */
    ARRAY_TYPE,
    UNION_TYPE_NODE,
    INTERSECTION_TYPE_NODE,
    FINITE_TYPE_NODE,
    TUPLE_TYPE_NODE,
    BUILT_IN_REF_TYPE,
    CONSTRAINED_TYPE,
    FUNCTION_TYPE,
    USER_DEFINED_TYPE,
    VALUE_TYPE,
    RECORD_TYPE,
    OBJECT_TYPE,
    ERROR_TYPE,
    STREAM_TYPE,
    TABLE_TYPE,

    /* Internal */
    NODE_ENTRY

}

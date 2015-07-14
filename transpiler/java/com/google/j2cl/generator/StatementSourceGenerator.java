/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.generator;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.j2cl.ast.AbstractTransformer;
import com.google.j2cl.ast.ArrayAccess;
import com.google.j2cl.ast.ArrayLiteral;
import com.google.j2cl.ast.ArrayTypeDescriptor;
import com.google.j2cl.ast.AssertStatement;
import com.google.j2cl.ast.BinaryExpression;
import com.google.j2cl.ast.Block;
import com.google.j2cl.ast.BooleanLiteral;
import com.google.j2cl.ast.BreakStatement;
import com.google.j2cl.ast.CastExpression;
import com.google.j2cl.ast.CatchClause;
import com.google.j2cl.ast.CharacterLiteral;
import com.google.j2cl.ast.DoWhileStatement;
import com.google.j2cl.ast.EmptyStatement;
import com.google.j2cl.ast.Expression;
import com.google.j2cl.ast.ExpressionStatement;
import com.google.j2cl.ast.FieldAccess;
import com.google.j2cl.ast.FieldDescriptor;
import com.google.j2cl.ast.ForStatement;
import com.google.j2cl.ast.IfStatement;
import com.google.j2cl.ast.InstanceOfExpression;
import com.google.j2cl.ast.Member;
import com.google.j2cl.ast.MemberReference;
import com.google.j2cl.ast.MethodCall;
import com.google.j2cl.ast.MethodDescriptor;
import com.google.j2cl.ast.NewArray;
import com.google.j2cl.ast.NewInstance;
import com.google.j2cl.ast.Node;
import com.google.j2cl.ast.NullLiteral;
import com.google.j2cl.ast.NumberLiteral;
import com.google.j2cl.ast.ParenthesizedExpression;
import com.google.j2cl.ast.PostfixExpression;
import com.google.j2cl.ast.PrefixExpression;
import com.google.j2cl.ast.RegularTypeDescriptor;
import com.google.j2cl.ast.ReturnStatement;
import com.google.j2cl.ast.StringLiteral;
import com.google.j2cl.ast.TernaryExpression;
import com.google.j2cl.ast.ThisReference;
import com.google.j2cl.ast.ThrowStatement;
import com.google.j2cl.ast.TryStatement;
import com.google.j2cl.ast.TypeDescriptor;
import com.google.j2cl.ast.UnionTypeDescriptor;
import com.google.j2cl.ast.Variable;
import com.google.j2cl.ast.VariableDeclarationExpression;
import com.google.j2cl.ast.VariableDeclarationFragment;
import com.google.j2cl.ast.VariableDeclarationStatement;
import com.google.j2cl.ast.VariableReference;
import com.google.j2cl.ast.WhileStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Generate javascript source code for {@code Statement}.
 * TODO: Keep an eye on performance and if things get slow then replace these String operations with
 * a StringBuilder.
 */
public class StatementSourceGenerator {
  // TODO: static field may be a potential threading problem.
  private static TypeDescriptor inClinitForTypeDescriptor = null;

  public static String toSource(Node node) {
    class ToSourceTransformer extends AbstractTransformer<String> {
      @Override
      public String transformAssertStatement(AssertStatement statement) {
        if (statement.getMessage() == null) {
          return String.format(
              "Asserts.$enabled() && Asserts.$assert(%s);", toSource(statement.getExpression()));
        } else {
          return String.format(
              "Asserts.$enabled() && Asserts.$assertWithMessage(%s, %s);",
              toSource(statement.getExpression()),
              toSource(statement.getMessage()));
        }
      }

      @Override
      public String transformBinaryExpression(BinaryExpression expression) {
        if (TranspilerUtils.isAssignment(expression.getOperator())
            && expression.getLeftOperand() instanceof ArrayAccess) {
          return transformArrayAssignmentBinaryExpression(expression);
        } else {
          return transformRegularBinaryExpression(expression);
        }
      }

      @Override
      public String transformBreakStatement(BreakStatement statement) {
        return "break;";
      }

      private String transformRegularBinaryExpression(BinaryExpression expression) {
        Preconditions.checkState(
            !(TranspilerUtils.isAssignment(expression.getOperator())
                && expression.getLeftOperand() instanceof ArrayAccess));

        return String.format(
            "%s %s %s",
            toSource(expression.getLeftOperand()),
            expression.getOperator().toString(),
            toSource(expression.getRightOperand()));
      }

      // TODO: extend to handle long[].
      private String transformArrayAssignmentBinaryExpression(BinaryExpression expression) {
        Preconditions.checkState(
            TranspilerUtils.isAssignment(expression.getOperator())
                && expression.getLeftOperand() instanceof ArrayAccess);

        ArrayAccess arrayAccess = (ArrayAccess) expression.getLeftOperand();
        return String.format(
            "Arrays.%s(%s, %s, %s)",
            TranspilerUtils.getArrayAssignmentFunctionName(expression.getOperator()),
            toSource(arrayAccess.getArrayExpression()),
            toSource(arrayAccess.getIndexExpression()),
            toSource(expression.getRightOperand()));
      }

      @Override
      public String transformBlock(Block statement) {
        String statementsAsString =
            Joiner.on("\n").join(transformNodesToSource(statement.getStatements()));
        return "{\n" + statementsAsString + "}";
      }

      @Override
      public String transformBooleanLiteral(BooleanLiteral expression) {
        return expression.getValue() ? "true" : "false";
      }

      @Override
      public String transformCastExpression(CastExpression expression) {
        TypeDescriptor castTypeDescriptor = expression.getCastTypeDescriptor();
        if (castTypeDescriptor.isArray()) {
          return transformArrayCastExpression(expression);
        }
        return transformRegularCastExpression(expression);
      }

      private String transformRegularCastExpression(CastExpression castExpression) {
        Preconditions.checkArgument(
            castExpression.getCastTypeDescriptor() instanceof RegularTypeDescriptor);
        RegularTypeDescriptor castTypeDescriptor =
            (RegularTypeDescriptor) castExpression.getCastTypeDescriptor();

        if (castTypeDescriptor.isPrimitive()) {
          throw new RuntimeException("TODO: Implement toSource() for cast to primitive type");
        }

        String jsDocTypeName = TranspilerUtils.getJsDocName(castTypeDescriptor);
        String typeName = TranspilerUtils.getClassName(castTypeDescriptor);
        String expressionStr = toSource(castExpression.getExpression());
        String isInstanceCallStr = String.format("%s.$isInstance(%s)", typeName, expressionStr);
        return String.format(
            "/**@type {%s} */ (Casts.to(%s, %s))", jsDocTypeName, expressionStr, isInstanceCallStr);
      }

      private String transformArrayCastExpression(CastExpression castExpression) {
        Preconditions.checkArgument(
            castExpression.getCastTypeDescriptor() instanceof ArrayTypeDescriptor);
        ArrayTypeDescriptor arrayCastTypeDescriptor =
            (ArrayTypeDescriptor) castExpression.getCastTypeDescriptor();

        String jsDocTypeName = TranspilerUtils.getJsDocName(arrayCastTypeDescriptor);
        String leafTypeName =
            TranspilerUtils.getClassName(arrayCastTypeDescriptor.getLeafTypeDescriptor());
        String expressionStr = toSource(castExpression.getExpression());
        return String.format(
            "/**@type {%s} */ (Arrays.$castTo(%s, %s, %s))",
            jsDocTypeName,
            expressionStr,
            leafTypeName,
            arrayCastTypeDescriptor.getDimensions());
      }

      @Override
      public String transformCharacterLiteral(CharacterLiteral characterLiteral) {
        return String.format(
            "%s /* %s */",
            Integer.toString(characterLiteral.getValue()),
            characterLiteral.getEscapedValue());
      }

      @Override
      public String transformExpressionStatement(ExpressionStatement statement) {
        return toSource(statement.getExpression()) + ";";
      }

      @Override
      public String transformFieldAccess(FieldAccess fieldAccess) {
        FieldDescriptor targetFieldDescriptor = fieldAccess.getTarget();
        String fieldMangledName =
            ManglingNameUtils.getMangledName(
                targetFieldDescriptor,
                isInClinit(targetFieldDescriptor.getEnclosingClassTypeDescriptor()));

        // make 'this.' reference and static reference explicit.
        // TODO(rluble): We should probably make this explicit at the AST level, either by a
        // normalization pass or by construction.
        String qualifier = transformQualifier(fieldAccess);
        return String.format("%s.%s", qualifier, fieldMangledName);
      }

      @Override
      public String transformInstanceOfExpression(InstanceOfExpression expression) {
        TypeDescriptor checkTypeDescriptor = expression.getTestTypeDescriptor();
        if (checkTypeDescriptor.isArray()) {
          return transformArrayInstanceOfExpression(expression);
        }
        return String.format(
            "%s.$isInstance(%s)",
            TranspilerUtils.getClassName(checkTypeDescriptor),
            toSource(expression.getExpression()));
      }

      private String transformArrayInstanceOfExpression(
          InstanceOfExpression arrayInstanceOfExpression) {
        TypeDescriptor checkTypeDescriptor = arrayInstanceOfExpression.getTestTypeDescriptor();
        Preconditions.checkArgument(checkTypeDescriptor.isArray());

        String leafTypeName =
            TranspilerUtils.getClassName(checkTypeDescriptor.getLeafTypeDescriptor());
        String expressionStr = toSource(arrayInstanceOfExpression.getExpression());
        return String.format(
            "Arrays.$instanceIsOfType(%s, %s, %s)",
            expressionStr,
            leafTypeName,
            checkTypeDescriptor.getDimensions());
      }

      @Override
      public String transformMethodCall(MethodCall expression) {
        MethodDescriptor methodDescriptor = expression.getTarget();
        String qualifier = transformQualifier(expression);
        String argumentList =
            Joiner.on(", ").join(transformNodesToSource(expression.getArguments()));
        return String.format("%s.%s(%s)", qualifier, toSource(methodDescriptor), argumentList);
      }

      @Override
      public String transformMethodDescriptor(MethodDescriptor methodDescriptor) {
        if (methodDescriptor.isConstructor()) {
          return ManglingNameUtils.getCtorMangledName(methodDescriptor);
        } else if (methodDescriptor.isInit()) {
          return ManglingNameUtils.getInitMangledName(
              methodDescriptor.getEnclosingClassTypeDescriptor());
        } else {
          return ManglingNameUtils.getMangledName(methodDescriptor);
        }
      }

      @Override
      public String transformArrayAccess(ArrayAccess arrayAccess) {
        return String.format(
            "%s[%s]",
            toSource(arrayAccess.getArrayExpression()),
            toSource(arrayAccess.getIndexExpression()));
      }

      @Override
      public String transformArrayLiteral(ArrayLiteral arrayLiteral) {
        String valuesAsString =
            Joiner.on(", ").join(transformNodesToSource(arrayLiteral.getValueExpressions()));
        return "[ " + valuesAsString + " ]";
      }

      @Override
      public String transformNewArray(NewArray newArrayExpression) {
        if (newArrayExpression.getArrayLiteral() != null) {
          return transformArrayInit(newArrayExpression);
        }
        return transformArrayCreate(newArrayExpression);
      }

      private String transformArrayCreate(NewArray newArrayExpression) {
        Preconditions.checkArgument(newArrayExpression.getArrayLiteral() == null);

        String dimensionsList =
            Joiner.on(", ")
                .join(transformNodesToSource(newArrayExpression.getDimensionExpressions()));
        return String.format(
            "Arrays.$create([%s], %s)",
            dimensionsList,
            TranspilerUtils.getClassName(newArrayExpression.getLeafTypeDescriptor()));
      }

      private String transformArrayInit(NewArray newArrayExpression) {
        Preconditions.checkArgument(newArrayExpression.getArrayLiteral() != null);

        String leafTypeName =
            TranspilerUtils.getClassName(newArrayExpression.getLeafTypeDescriptor());
        int dimensionCount = newArrayExpression.getDimensionExpressions().size();
        String arrayLiteralAsString = toSource(newArrayExpression.getArrayLiteral());

        if (dimensionCount == 1) {
          // It's 1 dimensional.
          if (TypeDescriptor.OBJECT_TYPE_DESCRIPTOR.equals(
              newArrayExpression.getLeafTypeDescriptor())) {
            // And the leaf type is Object. All arrays are implicitly Array<Object> so leave out the
            // init.
            return arrayLiteralAsString;
          }
          // Number of dimensions defaults to 1 so we can leave that parameter out.
          return String.format("Arrays.$init(%s, %s)", arrayLiteralAsString, leafTypeName);
        } else {
          // It's multidimensional, make dimensions explicit.
          return String.format(
              "Arrays.$init(%s, %s, %s)", arrayLiteralAsString, leafTypeName, dimensionCount);
        }
      }

      @Override
      public String transformNewInstance(NewInstance expression) {
        String className =
            TranspilerUtils.getClassName(
                expression.getConstructorMethodDescriptor().getEnclosingClassTypeDescriptor());
        String constructorMangledName =
            ManglingNameUtils.getConstructorMangledName(
                expression.getConstructorMethodDescriptor());
        String argumentsList =
            Joiner.on(", ").join(transformNodesToSource(expression.getArguments()));
        return String.format("%s.%s(%s)", className, constructorMangledName, argumentsList);
      }

      @Override
      public String transformNullLiteral(NullLiteral expression) {
        return "null";
      }

      @Override
      public String transformNumberLiteral(NumberLiteral expression) {
        return expression.getToken();
      }

      @Override
      public String transformParenthesizedExpression(ParenthesizedExpression expression) {
        return String.format("(%s)", toSource(expression.getExpression()));
      }

      @Override
      public String transformPostfixExpression(PostfixExpression expression) {
        return String.format(
            "%s%s", toSource(expression.getOperand()), expression.getOperator().toString());
      }

      @Override
      public String transformPrefixExpression(PrefixExpression expression) {
        return String.format(
            "%s%s", expression.getOperator().toString(), toSource(expression.getOperand()));
      }

      @Override
      public String transformReturnStatement(ReturnStatement statement) {
        Expression expression = statement.getExpression();
        if (expression == null) {
          return "return;";
        } else {
          return "return " + toSource(expression) + ";";
        }
      }

      @Override
      public String transformStringLiteral(StringLiteral expression) {
        return expression.getEscapedValue();
      }

      @Override
      public String transformThisReference(ThisReference expression) {
        return "this";
      }

      @Override
      public String transformThrowStatement(ThrowStatement statement) {
        return "throw " + toSource(statement.getExpression()) + ";";
      }

      @Override
      public String transformTryStatement(TryStatement statement) {
        String tryBlock = String.format("try %s", toSource(statement.getBody()));
        String catchBlock;
        if (statement.getCatchClauses().isEmpty()) {
          catchBlock = "";
        } else {
          String exceptionVarName =
              statement
                  .getCatchClauses()
                  .get(0)
                  .getExceptionVar()
                  .getName();
          catchBlock =
              String.format(
                  "catch (%s) { %s }\n",
                  exceptionVarName,
                  transformCatchClauses(statement.getCatchClauses(), exceptionVarName));
        }
        String finallyBlock =
            statement.getFinallyBlock() == null
                ? ""
                : String.format("finally %s", toSource(statement.getFinallyBlock()));
        return tryBlock + catchBlock + finallyBlock;
      }

      /**
       * Translates multiple catch clauses to if-else statement.
       */
      private String transformCatchClauses(
          List<CatchClause> catchClauses, final String exceptionVarName) {
        List<String> transformedCatchClauses =
            Lists.transform(
                catchClauses,
                new Function<CatchClause, String>() {
                  @Override
                  public String apply(CatchClause catchClause) {
                    return transformCatchClause(catchClause, exceptionVarName);
                  }
                });
        String ifBranches = Joiner.on(" else ").join(transformedCatchClauses);
        String elseBranch = String.format("else { throw %s; }", exceptionVarName);
        return ifBranches + elseBranch;
      }

      /**
       * Translates a catch clause like catch (Exception e) { ...body... }
       * to a if statement like
       * if ($Exception.isInstance(e)) { ...body... }
       */
      private String transformCatchClause(
          CatchClause catchClause, final String globalExceptionVarName) {
        Variable localExceptionVar = catchClause.getExceptionVar();
        // If this catch clause uses a different exception variable name than the global one, then
        // re-expose the exception variable via the global exception variable name.
        String localVarDecl =
            localExceptionVar.getName().equals(globalExceptionVarName)
                ? ""
                : String.format(
                    "let %s = %s;", localExceptionVar.getName(), globalExceptionVarName);
        String blockStatementsAsString =
            Joiner.on("\n").join(transformNodesToSource(catchClause.getBody().getStatements()));
        return String.format(
            "if (%s) {%s %s}",
            transformExceptionVariable(
                localExceptionVar.getTypeDescriptor(), globalExceptionVarName),
            localVarDecl,
            blockStatementsAsString);
      }

      /**
       * Translates exception variable declaration in a catch clause like
       * (RuntimeException | NullPointerException e)
       * to the condition expression in a if statement like
       * (RuntimeException.$isInstance(e) || NullPointerException.$isInstance(e))
       */
      private String transformExceptionVariable(
          TypeDescriptor exceptionTypeDescriptor, final String globalExceptionVarName) {
        List<TypeDescriptor> exceptionTypeDescriptors = new ArrayList<>();
        if (exceptionTypeDescriptor instanceof UnionTypeDescriptor) {
          exceptionTypeDescriptors.addAll(
              ((UnionTypeDescriptor) exceptionTypeDescriptor).getTypes());
        } else {
          exceptionTypeDescriptors.add(exceptionTypeDescriptor);
        }
        List<String> isInstanceCalls =
            Lists.transform(
                exceptionTypeDescriptors,
                new Function<TypeDescriptor, String>() {
                  @Override
                  public String apply(TypeDescriptor typeDescriptor) {
                    return String.format(
                        "%s.$isInstance(%s)",
                        TranspilerUtils.getClassName(typeDescriptor),
                        globalExceptionVarName);
                  }
                });
        return Joiner.on(" || ").join(isInstanceCalls);
      }

      @Override
      public String transformTypeDescriptor(TypeDescriptor typeDescriptor) {
        return TranspilerUtils.getClassName(typeDescriptor);
      }

      @Override
      public String transformVariableReference(VariableReference expression) {
        return expression.getTarget().getName();
      }

      @Override
      public String transformIfStatement(IfStatement ifStatement) {
        if (ifStatement.getElseStatement() == null) {
          return String.format(
              "if (%s) %s",
              toSource(ifStatement.getConditionExpression()),
              toSource(ifStatement.getThenStatement()));
        }
        return String.format(
            "if (%s) %s else %s",
            toSource(ifStatement.getConditionExpression()),
            toSource(ifStatement.getThenStatement()),
            toSource(ifStatement.getElseStatement()));
      }

      @Override
      public String transformEmptyStatement(EmptyStatement emptyStatement) {
        return ";";
      }

      @Override
      public String transformTernaryExpression(TernaryExpression ternaryExpression) {
        String conditionExpressionAsString = toSource(ternaryExpression.getConditionExpression());
        String trueExpressionAsString = toSource(ternaryExpression.getTrueExpression());
        String falseExpressionAsString = toSource(ternaryExpression.getFalseExpression());
        return String.format(
            "%s ? %s : %s",
            conditionExpressionAsString,
            trueExpressionAsString,
            falseExpressionAsString);
      }

      @Override
      public String transformWhileStatement(WhileStatement whileStatement) {
        String conditionAsString = toSource(whileStatement.getConditionExpression());
        String blockAsString = toSource(whileStatement.getBody());
        return String.format("while (%s) %s", conditionAsString, blockAsString);
      }

      @Override
      public String transformDoWhileStatement(DoWhileStatement doWhileStatement) {
        String conditionAsString = toSource(doWhileStatement.getConditionExpression());
        String blockAsString = toSource(doWhileStatement.getBody());
        return String.format("do %s while(%s);", blockAsString, conditionAsString);
      }

      @Override
      public String transformForStatement(ForStatement forStatement) {

        List<String> initializers = new ArrayList<>();
        for (Expression e : forStatement.getInitializers()) {
          initializers.add(toSource(e));
        }
        String initializerAsString = Joiner.on(",").join(initializers);

        String conditionExpressionAsString =
            forStatement.getConditionExpression() == null
                ? ""
                : toSource(forStatement.getConditionExpression());

        List<String> updaters = new ArrayList<>();
        for (Expression e : forStatement.getUpdaters()) {
          updaters.add(toSource(e));
        }
        String updatersAsString = Joiner.on(",").join(updaters);

        String blockAsString = toSource(forStatement.getBody());

        return String.format(
            "for (%s; %s; %s) %s",
            initializerAsString,
            conditionExpressionAsString,
            updatersAsString,
            blockAsString);
      }

      @Override
      public String transformVariableDeclarationExpression(
          VariableDeclarationExpression variableDeclarationExpression) {
        List<String> fragmentsAsString =
            transformNodesToSource(variableDeclarationExpression.getFragments());
        return "let " + Joiner.on(", ").join(fragmentsAsString);
      }

      @Override
      public String transformVariableDeclarationStatement(
          VariableDeclarationStatement variableDeclarationStatement) {
        List<String> fragmentsAsString =
            transformNodesToSource(variableDeclarationStatement.getFragments());
        return "let " + Joiner.on(", ").join(fragmentsAsString) + ";";
      }

      @Override
      public String transformVariableDeclarationFragment(
          VariableDeclarationFragment variableDeclarationFragment) {

        String variableAsString = toSource(variableDeclarationFragment.getVariable());
        if (variableDeclarationFragment.getInitializer() == null) {
          return variableAsString;
        }

        String initializerAsString = toSource(variableDeclarationFragment.getInitializer());
        return String.format("%s = %s", variableAsString, initializerAsString);
      }

      @Override
      public String transformVariable(Variable variable) {
        return variable.getName();
      }

      private String transformQualifier(MemberReference memberRef) {
        Member member = memberRef.getTarget();
        String qualifier =
            memberRef.getQualifier() == null
                ? (member.isStatic()
                    ? TranspilerUtils.getClassName(member.getEnclosingClassTypeDescriptor())
                    : "this")
                : toSource(memberRef.getQualifier());
        return qualifier;
      }
    }
    return new ToSourceTransformer().process(node);
  }

  public static List<String> transformNodesToSource(List<? extends Node> nodes) {
    return Lists.transform(
        nodes,
        new Function<Node, String>() {
          @Override
          public String apply(Node node) {
            return toSource(node);
          }
        });
  }

  public static void setInClinit(TypeDescriptor typeDescriptor) {
    inClinitForTypeDescriptor = typeDescriptor;
  }

  public static void resetInClinit() {
    inClinitForTypeDescriptor = null;
  }

  private static boolean isInClinit(TypeDescriptor typeDescriptor) {
    return typeDescriptor.equals(inClinitForTypeDescriptor);
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.amazon.documentdb.jdbc.calcite.adapter;

import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.InvalidRelException;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.util.Bug;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;
import org.slf4j.Logger;
import software.amazon.documentdb.jdbc.metadata.DocumentDbMetadataTable;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static software.amazon.documentdb.jdbc.DocumentDbConnectionProperties.isNullOrWhitespace;

/**
 * Rules and relational operators for
 * {@link DocumentDbRel#CONVENTION MONGO}
 * calling convention.
 */
public final class DocumentDbRules {
    private DocumentDbRules() { }

    protected static final Logger LOGGER = CalciteTrace.getPlannerTracer();

    @SuppressWarnings("MutablePublicArray")
    public static final RelOptRule[] RULES = {
            DocumentDbSortRule.INSTANCE,
            DocumentDbFilterRule.INSTANCE,
            DocumentDbProjectRule.INSTANCE,
            DocumentDbAggregateRule.INSTANCE,
    };

    /** Returns 'string' if it is a call to item['string'], null otherwise. */
    static String isItem(final RexCall call) {
        if (call.getOperator() != SqlStdOperatorTable.ITEM) {
            return null;
        }
        final RexNode op0 = call.operands.get(0);
        final RexNode op1 = call.operands.get(1);
        if (op0 instanceof RexInputRef
                && ((RexInputRef) op0).getIndex() == 0
                && op1 instanceof RexLiteral
                && ((RexLiteral) op1).getValue2() instanceof String) {
            return (String) ((RexLiteral) op1).getValue2();
        }
        return null;
    }

    // DocumentDB: modified - start
    static List<String> mongoFieldNames(final RelDataType rowType,
            final DocumentDbMetadataTable metadataTable) {
        // DocumentDB: modified - end
        return SqlValidatorUtil.uniquify(
                new AbstractList<String>() {
                    @Override public String get(final int index) {
                        // DocumentDB: modified - start
                        final String name = rowType.getFieldList().get(index).getName();
                        final String path = metadataTable.getColumns().get(name).getPath();

                        if (isNullOrWhitespace(path)) {
                            return null;
                        }
                        return path;
                        // DocumentDB: modified - end
                    }

                    @Override public int size() {
                        return rowType.getFieldCount();
                    }
                },
                SqlValidatorUtil.EXPR_SUGGESTER, true);
    }

    static String maybeQuote(final String s) {
        if (!needsQuote(s)) {
            return s;
        }
        return quote(s);
    }

    static String quote(final String s) {
        return "'" + s + "'"; // TODO: handle embedded quotes
    }

    private static boolean needsQuote(final String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            final char c = s.charAt(i);
            if (!Character.isJavaIdentifierPart(c)
                    || c == '$') {
                return true;
            }
        }
        return false;
    }

    /** Translator from {@link RexNode} to strings in MongoDB's expression
     * language. */
    static class RexToMongoTranslator extends RexVisitorImpl<String> {
        private final JavaTypeFactory typeFactory;
        private final List<String> inFields;

        private static final Map<SqlOperator, String> MONGO_OPERATORS =
                new HashMap<>();

        static {
            // Arithmetic
            MONGO_OPERATORS.put(SqlStdOperatorTable.DIVIDE, "$divide");
            MONGO_OPERATORS.put(SqlStdOperatorTable.MULTIPLY, "$multiply");
            MONGO_OPERATORS.put(SqlStdOperatorTable.MOD, "$mod");
            MONGO_OPERATORS.put(SqlStdOperatorTable.PLUS, "$add");
            MONGO_OPERATORS.put(SqlStdOperatorTable.MINUS, "$subtract");
            // Boolean
            MONGO_OPERATORS.put(SqlStdOperatorTable.AND, "$and");
            MONGO_OPERATORS.put(SqlStdOperatorTable.OR, "$or");
            MONGO_OPERATORS.put(SqlStdOperatorTable.NOT, "$not");
            // Comparison
            MONGO_OPERATORS.put(SqlStdOperatorTable.EQUALS, "$eq");
            MONGO_OPERATORS.put(SqlStdOperatorTable.NOT_EQUALS, "$ne");
            MONGO_OPERATORS.put(SqlStdOperatorTable.GREATER_THAN, "$gt");
            MONGO_OPERATORS.put(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, "$gte");
            MONGO_OPERATORS.put(SqlStdOperatorTable.LESS_THAN, "$lt");
            MONGO_OPERATORS.put(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, "$lte");
        }

        protected RexToMongoTranslator(final JavaTypeFactory typeFactory,
                final List<String> inFields) {
            super(true);
            this.typeFactory = typeFactory;
            this.inFields = inFields;
        }

        @Override public String visitLiteral(final RexLiteral literal) {
            if (literal.getValue() == null) {
                return "null";
            }
            return "{$literal: "
                    + RexToLixTranslator.translateLiteral(literal, literal.getType(),
                    typeFactory, RexImpTable.NullAs.NOT_POSSIBLE)
                    + "}";
        }

        @Override public String visitInputRef(final RexInputRef inputRef) {
            return maybeQuote(
                    "$" + inFields.get(inputRef.getIndex()));
        }

        @Override public String visitCall(final RexCall call) {
            final String name = isItem(call);
            if (name != null) {
                return "'$" + name + "'";
            }
            final List<String> strings = visitList(call.operands);
            if (call.getKind() == SqlKind.CAST) {
                return strings.get(0);
            }
            final String stdOperator = MONGO_OPERATORS.get(call.getOperator());
            if (stdOperator != null) {
                return "{" + stdOperator + ": [" + Util.commaList(strings) + "]}";
            }
            if (call.getOperator() == SqlStdOperatorTable.ITEM) {
                final RexNode op1 = call.operands.get(1);
                if (op1 instanceof RexLiteral
                        && op1.getType().getSqlTypeName() == SqlTypeName.INTEGER) {
                    if (!Bug.CALCITE_194_FIXED) {
                        return "'" + stripQuotes(strings.get(0)) + "["
                                + ((RexLiteral) op1).getValue2() + "]'";
                    }
                    return strings.get(0) + "[" + strings.get(1) + "]";
                }
            }
            if (call.getOperator() == SqlStdOperatorTable.CASE) {
                final StringBuilder sb = new StringBuilder();
                final StringBuilder finish = new StringBuilder();
                // case(a, b, c)  -> $cond:[a, b, c]
                // case(a, b, c, d) -> $cond:[a, b, $cond:[c, d, null]]
                // case(a, b, c, d, e) -> $cond:[a, b, $cond:[c, d, e]]
                for (int i = 0; i < strings.size(); i += 2) {
                    sb.append("{$cond:[");
                    finish.append("]}");

                    sb.append(strings.get(i));
                    sb.append(',');
                    sb.append(strings.get(i + 1));
                    sb.append(',');
                    if (i == strings.size() - 3) {
                        sb.append(strings.get(i + 2));
                        break;
                    }
                    if (i == strings.size() - 2) {
                        sb.append("null");
                        break;
                    }
                }
                sb.append(finish);
                return sb.toString();
            }
            throw new IllegalArgumentException("Translation of " + call.toString()
                    + " is not supported by MongoProject");
        }

        private static String stripQuotes(final String s) {
            return s.startsWith("'") && s.endsWith("'")
                    ? s.substring(1, s.length() - 1)
                    : s;
        }
    }

    /** Base class for planner rules that convert a relational expression to
     * MongoDB calling convention. */
    abstract static class DocumentDbConverterRule extends ConverterRule {
        protected DocumentDbConverterRule(final Config config) {
            super(config);
        }
    }

    /**
     * Rule to convert a {@link Sort} to a
     * {@link DocumentDbSort}.
     */
    private static class DocumentDbSortRule extends DocumentDbConverterRule {
        static final DocumentDbSortRule INSTANCE = Config.INSTANCE
                .withConversion(Sort.class, Convention.NONE, DocumentDbRel.CONVENTION,
                        "DocumentDbSortRule")
                .withRuleFactory(DocumentDbSortRule::new)
                .toRule(DocumentDbSortRule.class);

        DocumentDbSortRule(final Config config) {
            super(config);
        }

        @Override public RelNode convert(final RelNode rel) {
            final Sort sort = (Sort) rel;
            final RelTraitSet traitSet =
                    sort.getTraitSet().replace(out)
                            .replace(sort.getCollation());
            return new DocumentDbSort(rel.getCluster(), traitSet,
                    convert(sort.getInput(), traitSet.replace(RelCollations.EMPTY)),
                    sort.getCollation(), sort.offset, sort.fetch);
        }
    }

    /**
     * Rule to convert a {@link LogicalFilter} to a
     * {@link DocumentDbFilter}.
     */
    private static class DocumentDbFilterRule extends DocumentDbConverterRule {
        static final DocumentDbFilterRule INSTANCE = Config.INSTANCE
                .withConversion(LogicalFilter.class, Convention.NONE,
                        DocumentDbRel.CONVENTION, "DocumentDbFilterRule")
                .withRuleFactory(DocumentDbFilterRule::new)
                .toRule(DocumentDbFilterRule.class);

        DocumentDbFilterRule(final Config config) {
            super(config);
        }

        @Override public RelNode convert(final RelNode rel) {
            final LogicalFilter filter = (LogicalFilter) rel;
            final RelTraitSet traitSet = filter.getTraitSet().replace(out);
            return new DocumentDbFilter(
                    rel.getCluster(),
                    traitSet,
                    convert(filter.getInput(), out),
                    filter.getCondition());
        }
    }

    /**
     * Rule to convert a {@link LogicalProject}
     * to a {@link DocumentDbProject}.
     */
    private static class DocumentDbProjectRule extends DocumentDbConverterRule {
        static final DocumentDbProjectRule INSTANCE = Config.INSTANCE
                .withConversion(LogicalProject.class, Convention.NONE,
                        DocumentDbRel.CONVENTION, "DocumentDbProjectRule")
                .withRuleFactory(DocumentDbProjectRule::new)
                .toRule(DocumentDbProjectRule.class);

        DocumentDbProjectRule(final Config config) {
            super(config);
        }

        @Override public RelNode convert(final RelNode rel) {
            final LogicalProject project = (LogicalProject) rel;
            final RelTraitSet traitSet = project.getTraitSet().replace(out);
            return new DocumentDbProject(project.getCluster(), traitSet,
                    convert(project.getInput(), out), project.getProjects(),
                    project.getRowType());
        }
    }

    /*

    /**
     * Rule to convert a {@link LogicalCalc} to an
     * {@link MongoCalcRel}.
     o/
    private static class MongoCalcRule
        extends DocumentDbConverterRule {
      private MongoCalcRule(MongoConvention out) {
        super(
            LogicalCalc.class,
            Convention.NONE,
            out,
            "MongoCalcRule");
      }

      public RelNode convert(RelNode rel) {
        final LogicalCalc calc = (LogicalCalc) rel;

        // If there's a multiset, let FarragoMultisetSplitter work on it
        // first.
        if (RexMultisetUtil.containsMultiset(calc.getProgram())) {
          return null;
        }

        return new MongoCalcRel(
            rel.getCluster(),
            rel.getTraitSet().replace(out),
            convert(
                calc.getChild(),
                calc.getTraitSet().replace(out)),
            calc.getProgram(),
            Project.Flags.Boxed);
      }
    }

    public static class MongoCalcRel extends SingleRel implements MongoRel {
      private final RexProgram program;

      /**
       * Values defined in {@link org.apache.calcite.rel.core.Project.Flags}.
       o/
      protected int flags;

      public MongoCalcRel(
          RelOptCluster cluster,
          RelTraitSet traitSet,
          RelNode child,
          RexProgram program,
          int flags) {
        super(cluster, traitSet, child);
        assert getConvention() instanceof MongoConvention;
        this.flags = flags;
        this.program = program;
        this.rowType = program.getOutputRowType();
      }

      public RelOptPlanWriter explainTerms(RelOptPlanWriter pw) {
        return program.explainCalc(super.explainTerms(pw));
      }

      public double getRows() {
        return LogicalFilter.estimateFilteredRows(
            getChild(), program);
      }

      public RelOptCost computeSelfCost(RelOptPlanner planner) {
        double dRows = RelMetadataQuery.getRowCount(this);
        double dCpu =
            RelMetadataQuery.getRowCount(getChild())
                * program.getExprCount();
        double dIo = 0;
        return planner.makeCost(dRows, dCpu, dIo);
      }

      public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
        return new MongoCalcRel(
            getCluster(),
            traitSet,
            sole(inputs),
            program.copy(),
            getFlags());
      }

      public int getFlags() {
        return flags;
      }

      public RexProgram getProgram() {
        return program;
      }

      public SqlString implement(MongoImplementor implementor) {
        final SqlBuilder buf = new SqlBuilder(implementor.dialect);
        buf.append("SELECT ");
        if (isStar(program)) {
          buf.append("*");
        } else {
          for (Ord<RexLocalRef> ref : Ord.zip(program.getProjectList())) {
            buf.append(ref.i == 0 ? "" : ", ");
            expr(buf, program, ref.e);
            alias(buf, null, getRowType().getFieldNames().get(ref.i));
          }
        }
        implementor.newline(buf)
            .append("FROM ");
        implementor.subQuery(buf, 0, getChild(), "t");
        if (program.getCondition() != null) {
          implementor.newline(buf);
          buf.append("WHERE ");
          expr(buf, program, program.getCondition());
        }
        return buf.toSqlString();
      }

      private static boolean isStar(RexProgram program) {
        int i = 0;
        for (RexLocalRef ref : program.getProjectList()) {
          if (ref.getIndex() != i++) {
            return false;
          }
        }
        return i == program.getInputRowType().getFieldCount();
      }

      private static void expr(
          SqlBuilder buf, RexProgram program, RexNode rex) {
        if (rex instanceof RexLocalRef) {
          final int index = ((RexLocalRef) rex).getIndex();
          expr(buf, program, program.getExprList().get(index));
        } else if (rex instanceof RexInputRef) {
          buf.identifier(
              program.getInputRowType().getFieldNames().get(
                  ((RexInputRef) rex).getIndex()));
        } else if (rex instanceof RexLiteral) {
          toSql(buf, (RexLiteral) rex);
        } else if (rex instanceof RexCall) {
          final RexCall call = (RexCall) rex;
          switch (call.getOperator().getSyntax()) {
          case Binary:
            expr(buf, program, call.getOperands().get(0));
            buf.append(' ')
                .append(call.getOperator().toString())
                .append(' ');
            expr(buf, program, call.getOperands().get(1));
            break;
          default:
            throw new AssertionError(call.getOperator());
          }
        } else {
          throw new AssertionError(rex);
        }
      }
    }

    private static SqlBuilder toSql(SqlBuilder buf, RexLiteral rex) {
      switch (rex.getTypeName()) {
      case CHAR:
      case VARCHAR:
        return buf.append(
            new NlsString(rex.getValue2().toString(), null, null)
                .asSql(false, false));
      default:
        return buf.append(rex.getValue2().toString());
      }
    }

    */

    /**
     * Rule to convert an {@link LogicalAggregate}
     * to an {@link DocumentDbAggregate}.
     */
    private static class DocumentDbAggregateRule extends DocumentDbConverterRule {
        static final DocumentDbAggregateRule INSTANCE = Config.INSTANCE
                .withConversion(LogicalAggregate.class, Convention.NONE,
                        DocumentDbRel.CONVENTION, "DocumentDbAggregateRule")
                .withRuleFactory(DocumentDbAggregateRule::new)
                .toRule(DocumentDbAggregateRule.class);

        DocumentDbAggregateRule(final Config config) {
            super(config);
        }

        @Override public RelNode convert(final RelNode rel) {
            final LogicalAggregate agg = (LogicalAggregate) rel;
            final RelTraitSet traitSet =
                    agg.getTraitSet().replace(out);
            try {
                return new DocumentDbAggregate(
                        rel.getCluster(),
                        traitSet,
                        convert(agg.getInput(), traitSet.simplify()),
                        agg.getGroupSet(),
                        agg.getGroupSets(),
                        agg.getAggCallList());
            } catch (InvalidRelException e) {
                LOGGER.warn(e.toString());
                return null;
            }
        }
    }

    /*
    /**
     * Rule to convert an {@link org.apache.calcite.rel.logical.Union} to a
     * {@link MongoUnionRel}.
     o/
    private static class MongoUnionRule
        extends DocumentDbConverterRule {
      private MongoUnionRule(MongoConvention out) {
        super(
            Union.class,
            Convention.NONE,
            out,
            "MongoUnionRule");
      }

      public RelNode convert(RelNode rel) {
        final Union union = (Union) rel;
        final RelTraitSet traitSet =
            union.getTraitSet().replace(out);
        return new MongoUnionRel(
            rel.getCluster(),
            traitSet,
            convertList(union.getInputs(), traitSet),
            union.all);
      }
    }

    public static class MongoUnionRel
        extends Union
        implements MongoRel {
      public MongoUnionRel(
          RelOptCluster cluster,
          RelTraitSet traitSet,
          List<RelNode> inputs,
          boolean all) {
        super(cluster, traitSet, inputs, all);
      }

      public MongoUnionRel copy(
          RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
        return new MongoUnionRel(getCluster(), traitSet, inputs, all);
      }

      @Override public RelOptCost computeSelfCost(RelOptPlanner planner) {
        return super.computeSelfCost(planner).multiplyBy(.1);
      }

      public SqlString implement(MongoImplementor implementor) {
        return setOpSql(this, implementor, "UNION");
      }
    }

    private static SqlString setOpSql(
        SetOp setOpRel, MongoImplementor implementor, String op) {
      final SqlBuilder buf = new SqlBuilder(implementor.dialect);
      for (Ord<RelNode> input : Ord.zip(setOpRel.getInputs())) {
        if (input.i > 0) {
          implementor.newline(buf)
              .append(op + (setOpRel.all ? " ALL " : ""));
          implementor.newline(buf);
        }
        buf.append(implementor.visitChild(input.i, input.e));
      }
      return buf.toSqlString();
    }

    /**
     * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalIntersect}
     * to an {@link MongoIntersectRel}.
     o/
    private static class MongoIntersectRule
        extends DocumentDbConverterRule {
      private MongoIntersectRule(MongoConvention out) {
        super(
            LogicalIntersect.class,
            Convention.NONE,
            out,
            "MongoIntersectRule");
      }

      public RelNode convert(RelNode rel) {
        final LogicalIntersect intersect = (LogicalIntersect) rel;
        if (intersect.all) {
          return null; // INTERSECT ALL not implemented
        }
        final RelTraitSet traitSet =
            intersect.getTraitSet().replace(out);
        return new MongoIntersectRel(
            rel.getCluster(),
            traitSet,
            convertList(intersect.getInputs(), traitSet),
            intersect.all);
      }
    }

    public static class MongoIntersectRel
        extends Intersect
        implements MongoRel {
      public MongoIntersectRel(
          RelOptCluster cluster,
          RelTraitSet traitSet,
          List<RelNode> inputs,
          boolean all) {
        super(cluster, traitSet, inputs, all);
        assert !all;
      }

      public MongoIntersectRel copy(
          RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
        return new MongoIntersectRel(getCluster(), traitSet, inputs, all);
      }

      public SqlString implement(MongoImplementor implementor) {
        return setOpSql(this, implementor, " intersect ");
      }
    }

    /**
     * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalMinus}
     * to an {@link MongoMinusRel}.
     o/
    private static class MongoMinusRule
        extends DocumentDbConverterRule {
      private MongoMinusRule(MongoConvention out) {
        super(
            LogicalMinus.class,
            Convention.NONE,
            out,
            "MongoMinusRule");
      }

      public RelNode convert(RelNode rel) {
        final LogicalMinus minus = (LogicalMinus) rel;
        if (minus.all) {
          return null; // EXCEPT ALL not implemented
        }
        final RelTraitSet traitSet =
            rel.getTraitSet().replace(out);
        return new MongoMinusRel(
            rel.getCluster(),
            traitSet,
            convertList(minus.getInputs(), traitSet),
            minus.all);
      }
    }

    public static class MongoMinusRel
        extends Minus
        implements MongoRel {
      public MongoMinusRel(
          RelOptCluster cluster,
          RelTraitSet traitSet,
          List<RelNode> inputs,
          boolean all) {
        super(cluster, traitSet, inputs, all);
        assert !all;
      }

      public MongoMinusRel copy(
          RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
        return new MongoMinusRel(getCluster(), traitSet, inputs, all);
      }

      public SqlString implement(MongoImplementor implementor) {
        return setOpSql(this, implementor, " minus ");
      }
    }

    public static class MongoValuesRule extends DocumentDbConverterRule {
      private MongoValuesRule(MongoConvention out) {
        super(
            LogicalValues.class,
            Convention.NONE,
            out,
            "MongoValuesRule");
      }

      @Override public RelNode convert(RelNode rel) {
        LogicalValues valuesRel = (LogicalValues) rel;
        return new MongoValuesRel(
            valuesRel.getCluster(),
            valuesRel.getRowType(),
            valuesRel.getTuples(),
            valuesRel.getTraitSet().plus(out));
      }
    }

    public static class MongoValuesRel
        extends Values
        implements MongoRel {
      MongoValuesRel(
          RelOptCluster cluster,
          RelDataType rowType,
          List<List<RexLiteral>> tuples,
          RelTraitSet traitSet) {
        super(cluster, rowType, tuples, traitSet);
      }

      @Override public RelNode copy(
          RelTraitSet traitSet, List<RelNode> inputs) {
        assert inputs.isEmpty();
        return new MongoValuesRel(
            getCluster(), rowType, tuples, traitSet);
      }

      public SqlString implement(MongoImplementor implementor) {
        throw new AssertionError(); // TODO:
      }
    }
    */
}

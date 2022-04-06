package tech.tablesaw.joining;

import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;
import tech.tablesaw.selection.Selection;

/** Implements joins between two or more Tables */
public class DataFrameJoiner {

  /** The types of joins that are supported */
  private enum JoinType {
    INNER,
    LEFT_OUTER,
    RIGHT_OUTER,
    FULL_OUTER
  }

  private static final String TABLE_ALIAS = "T";

  private final Table table;
  private final String[] joinColumnNames;
  private int[] joinColumnIndexes;
  private final AtomicInteger joinTableId = new AtomicInteger(2);

  /**
   * Constructor.
   *
   * @param table The table to join on.
   * @param joinColumnNames The join column names to join on.
   */
  public DataFrameJoiner(Table table, String... joinColumnNames) {
    this.table = table;
    this.joinColumnNames = joinColumnNames;
    this.joinColumnIndexes = getJoinIndexes(table, joinColumnNames);
  }

  /**
   * Finds the index of the columns corresponding to the columnNames. E.G. The column named "ID" is
   * located at index 5 in table.
   *
   * @param table the table that contains the columns.
   * @param columnNames the column names to find indexes of.
   * @return a list of column indexes within the table.
   */
  private int[] getJoinIndexes(Table table, String[] columnNames) {
    int[] results = new int[columnNames.length];
    for (int i = 0; i < columnNames.length; i++) {
      String nm = columnNames[i];
      results[i] = table.columnIndex(nm);
    }
    return results;
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param tables The tables to join with
   */
  public Table inner(Table... tables) {
    return inner(false, tables);
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @param tables The tables to join with
   */
  public Table inner(boolean allowDuplicateColumnNames, Table... tables) {
    Table joined = table;
    for (Table currT : tables) {
      joined =
          joinInternal(
              joined, currT, JoinType.INNER, allowDuplicateColumnNames, false, joinColumnNames);
    }
    return joined;
  }

  /**
   * Joins the joiner to the table2, using the given column for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Name The column to join on. If col2Name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table inner(Table table2, String col2Name) {
    return inner(table2, false, col2Name);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table inner(Table table2, String[] col2Names) {
    return inner(table2, false, col2Names);
  }

  /**
   * Joins the joiner to the table2, using the given column for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Name The column to join on. If col2Name refers to a double column, the join is
   *     performed after rounding to integers.
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @return The resulting table
   */
  public Table inner(Table table2, String col2Name, boolean allowDuplicateColumnNames) {
    return inner(table2, allowDuplicateColumnNames, col2Name);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table inner(Table table2, boolean allowDuplicateColumnNames, String... col2Names) {
    Table joinedTable;
    joinedTable =
        joinInternal(table, table2, JoinType.INNER, allowDuplicateColumnNames, false, col2Names);
    return joinedTable;
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @param keepAllJoinKeyColumns if {@code false} the join will only keep join key columns in
   *     table1 if {@code true} the join will return all join key columns in both table, which may
   *     have difference when there are null values
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table inner(
      Table table2,
      boolean allowDuplicateColumnNames,
      boolean keepAllJoinKeyColumns,
      String... col2Names) {
    return joinInternal(
        table, table2, JoinType.INNER, allowDuplicateColumnNames, keepAllJoinKeyColumns, col2Names);
  }

  /**
   * Joins two tables.
   *
   * @param table1 the table on the left side of the join.
   * @param table2 the table on the right side of the join.
   * @param joinType the type of join.
   * @param allowDuplicates if {@code false} the join will fail if any columns other than the join
   *     column have the same name if {@code true} the join will succeed and duplicate columns are
   *     renamed
   * @param keepAllJoinKeyColumns if {@code false} the join will only keep join key columns in
   *     table1 if {@code true} the join will return all join key columns in both table, which may
   *     have difference when there are null values
   * @param table2JoinColumnNames The names of the columns in table2 to join on.
   * @return the joined table
   */
  private Table joinInternal(
      Table table1,
      Table table2,
      JoinType joinType,
      boolean allowDuplicates,
      boolean keepAllJoinKeyColumns,
      String... table2JoinColumnNames) {

    int[] table2JoinColumnIndexes = getJoinIndexes(table2, table2JoinColumnNames);

    table1 = table1.sortOn(joinColumnIndexes);
    table2 = table2.sortOn(table2JoinColumnIndexes);

    Table result = Table.create(table1.name());
    // A set of column indexes in the result table that can be ignored. They are duplicate join
    // keys.
    Set<Integer> resultIgnoreColIndexes =
        emptyTableFromColumns(
            result,
            table1,
            table2,
            joinType,
            allowDuplicates,
            table2JoinColumnIndexes,
            keepAllJoinKeyColumns);

    validateIndexes(table1, joinColumnIndexes, table2, table2JoinColumnIndexes);
    if (table1.rowCount() == 0 && (joinType == JoinType.LEFT_OUTER || joinType == JoinType.INNER)) {
      // Handle special case of empty table here so it doesn't fall through to the behavior
      // that adds rows for full outer and right outer joins
      if (!keepAllJoinKeyColumns) {
        result.removeColumns(Ints.toArray(resultIgnoreColIndexes));
      }
      return result;
    }

    // use table 2 for row iteration, which can significantly increase performance
    if (table1.rowCount() > table2.rowCount() && joinType == JoinType.INNER) {
      innerJoin(result, table1, table2, resultIgnoreColIndexes, keepAllJoinKeyColumns);
    } else if (joinType == JoinType.LEFT_OUTER) {
      leftOuterJoin(
          result,
          table1,
          table2,
          table2JoinColumnIndexes,
          resultIgnoreColIndexes,
          keepAllJoinKeyColumns);
    }
    /* else {
      if ((joinType == JoinType.LEFT_OUTER || joinType == JoinType.FULL_OUTER)) {
        withMissingLeftJoin(
            result, table1, resultIgnoreColIndexes, keepAllJoinKeyColumns);
      } else {
        crossProduct(
            result,
            table1,
            table2,
            resultIgnoreColIndexes,
            keepAllJoinKeyColumns);
      }

    if (joinType == JoinType.FULL_OUTER || joinType == JoinType.RIGHT_OUTER) {
        // Update done rows in table2 for full Outer.
      } else if (table1DoneRows.size() == table1.rowCount()) {
        // Processed all the rows in table1 exit early.
        if (!keepAllJoinKeyColumns) {
          result.removeColumns(Ints.toArray(resultIgnoreColIndexes));
        }
        return result;
      }
    }*/
    // return result;
    // }

    if (!keepAllJoinKeyColumns) {
      result.removeColumns(Ints.toArray(resultIgnoreColIndexes));
    }
    return result;
  }

  private void validateIndexes(
      Table table1, int[] table1Indexes, Table table2, int[] table2Indexes) {
    if (table1Indexes.length != table2Indexes.length) {
      throw new IllegalArgumentException(
          "Cannot join using a different number of indices on each table: "
              + Arrays.toString(table1Indexes)
              + " and "
              + Arrays.toString(table2Indexes));
    }
    for (int i = 0; i < table1Indexes.length; i++) {
      if (!table1
          .column(table1Indexes[i])
          .getClass()
          .equals(table2.column(table2Indexes[i]).getClass())) {
        throw new IllegalArgumentException(
            "Cannot join using different index types: "
                + Arrays.toString(table1Indexes)
                + " and "
                + Arrays.toString(table2Indexes));
      }
    }
  }

  private String newName(String table2Alias, String columnName) {
    return table2Alias + "." + columnName;
  }

  /**
   * Full outer join to the given tables assuming that they have a column of the name we're joining
   * on
   *
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table fullOuter(Table... tables) {
    return fullOuter(false, tables);
  }

  /**
   * Full outer join to the given tables assuming that they have a column of the name we're joining
   * on
   *
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table fullOuter(boolean allowDuplicateColumnNames, Table... tables) {
    Table joined = table;

    for (Table currT : tables) {
      joined =
          joinInternal(
              joined,
              currT,
              JoinType.FULL_OUTER,
              allowDuplicateColumnNames,
              false,
              joinColumnNames);
    }
    return joined;
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param keepAllJoinKeyColumns if {@code false} the join will only keep join key columns in
   *     table1 if {@code true} the join will return all join key columns in both table, which may
   *     have difference when there are null values
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table fullOuter(
      Table table2,
      boolean allowDuplicateColumnNames,
      boolean keepAllJoinKeyColumns,
      String... col2Names) {
    return joinInternal(
        table,
        table2,
        JoinType.FULL_OUTER,
        allowDuplicateColumnNames,
        keepAllJoinKeyColumns,
        col2Names);
  }

  /**
   * Full outer join the joiner to the table2, using the given column for the second table and
   * returns the resulting table
   *
   * @param table2 The table to join with
   * @param col2Name The column to join on. If col2Name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table fullOuter(Table table2, String col2Name) {
    return joinInternal(table, table2, JoinType.FULL_OUTER, false, false, col2Name);
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table leftOuterJoin(Table... tables) {
    return leftOuterJoin(false, tables);
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed*
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table leftOuterJoin(boolean allowDuplicateColumnNames, Table... tables) {
    Table joined = table;
    for (Table table2 : tables) {
      joined =
          joinInternal(
              joined,
              table2,
              JoinType.LEFT_OUTER,
              allowDuplicateColumnNames,
              false,
              joinColumnNames);
    }
    return joined;
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table leftOuterJoin(Table table2, String[] col2Names) {
    return leftOuterJoin(table2, false, col2Names);
  }

  /**
   * Joins the joiner to the table2, using the given column for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Name The column to join on. If col2Name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table leftOuterJoin(Table table2, String col2Name) {
    return leftOuterJoin(table2, false, col2Name);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table leftOuterJoin(Table table2, boolean allowDuplicateColumnNames, String... col2Names) {
    return joinInternal(
        table, table2, JoinType.LEFT_OUTER, allowDuplicateColumnNames, false, col2Names);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param keepAllJoinKeyColumns if {@code false} the join will only keep join key columns in
   *     table1 if {@code true} the join will return all join key columns in both table, which may
   *     have difference when there are null values
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table leftOuterJoin(
      Table table2,
      boolean allowDuplicateColumnNames,
      boolean keepAllJoinKeyColumns,
      String... col2Names) {
    return joinInternal(
        table,
        table2,
        JoinType.LEFT_OUTER,
        allowDuplicateColumnNames,
        keepAllJoinKeyColumns,
        col2Names);
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table rightOuter(Table... tables) {
    return rightOuter(false, tables);
  }

  /**
   * Joins to the given tables assuming that they have a column of the name we're joining on
   *
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param tables The tables to join with
   * @return The resulting table
   */
  public Table rightOuter(boolean allowDuplicateColumnNames, Table... tables) {
    Table joined = table;
    for (Table table2 : tables) {
      joined =
          joinInternal(
              joined,
              table2,
              JoinType.RIGHT_OUTER,
              allowDuplicateColumnNames,
              false,
              joinColumnNames);
      joinColumnIndexes = getJoinIndexes(joined, joinColumnNames);
    }
    return joined;
  }

  /**
   * Joins the joiner to the table2, using the given column for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Name The column to join on. If col2Name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table rightOuter(Table table2, String col2Name) {
    return rightOuter(table2, false, col2Name);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table rightOuter(Table table2, String[] col2Names) {
    return rightOuter(table2, false, col2Names);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table rightOuter(Table table2, boolean allowDuplicateColumnNames, String... col2Names) {
    return joinInternal(
        table, table2, JoinType.RIGHT_OUTER, allowDuplicateColumnNames, false, col2Names);
  }

  /**
   * Joins the joiner to the table2, using the given columns for the second table and returns the
   * resulting table
   *
   * @param table2 The table to join with
   * @param allowDuplicateColumnNames if {@code false} the join will fail if any columns other than
   *     the join column have the same name if {@code true} the join will succeed and duplicate
   *     columns are renamed
   * @param keepAllJoinKeyColumns if {@code false} the join will only keep join key columns in
   *     table1 if {@code true} the join will return all join key columns in both table, which may
   *     have difference when there are null values
   * @param col2Names The columns to join on. If a name refers to a double column, the join is
   *     performed after rounding to integers.
   * @return The resulting table
   */
  public Table rightOuter(
      Table table2,
      boolean allowDuplicateColumnNames,
      boolean keepAllJoinKeyColumns,
      String... col2Names) {
    return joinInternal(
        table,
        table2,
        JoinType.RIGHT_OUTER,
        allowDuplicateColumnNames,
        keepAllJoinKeyColumns,
        col2Names);
  }

  /**
   * Adds empty columns to the destination table with the same type as columns in table1 and table2.
   *
   * <p>For inner, left and full outer join types the join columns in table2 are not needed and will
   * be marked as placeholders. The indexes of those columns will be returned. The downstream logic
   * is easier if we wait to remove the redundant columns until the last step.
   *
   * @param destination the table to fill up with columns. Will be mutated in place.
   * @param table1 the table on left side of the join.
   * @param table2 the table on the right side of the join.
   * @param joinType the type of join.
   * @param allowDuplicates whether to allow duplicates. If yes rename columns in table2 that have
   *     the same name as columns in table1 with the exception of join columns in table2 when
   *     performing a right join.
   * @param table2JoinColumnIndexes the index locations of the table2 join columns.
   * @return A
   */
  private Set<Integer> emptyTableFromColumns(
      Table destination,
      Table table1,
      Table table2,
      JoinType joinType,
      boolean allowDuplicates,
      int[] table2JoinColumnIndexes,
      boolean keepTable2JoinKeyColumns) {

    Column<?>[] cols =
        Streams.concat(table1.columns().stream(), table2.columns().stream())
            .map(Column::emptyCopy)
            .toArray(Column[]::new);

    // For inner join, left join and full outer join mark the join columns in table2 as
    // placeholders.
    // For right join mark the join columns in table1 as placeholders.
    // Keep track of which join columns are placeholders so they can be ignored.
    Set<Integer> ignoreColumns = new HashSet<>();
    for (int c = 0; c < cols.length; c++) {
      if (joinType == JoinType.RIGHT_OUTER) {
        if (c < table1.columnCount() && indexesContainsValue(joinColumnIndexes, c)) {
          if (!keepTable2JoinKeyColumns) {
            cols[c].setName("Placeholder_" + ignoreColumns.size());
          }
          ignoreColumns.add(c);
        }
      } else { // JoinType is LEFT, INNER, or FULL
        int table2Index = c - table1.columnCount();
        if (c >= table1.columnCount()
            && indexesContainsValue(table2JoinColumnIndexes, table2Index)) {
          if (!keepTable2JoinKeyColumns) {
            cols[c].setName("Placeholder_" + ignoreColumns.size());
          }
          ignoreColumns.add(c);
        }
      }
    }

    // Rename duplicate columns in second table
    if (allowDuplicates) {
      Set<String> table1ColNames =
          Arrays.stream(cols)
              .map(Column::name)
              .map(String::toLowerCase)
              .limit(table1.columnCount())
              .collect(Collectors.toSet());

      String table2Alias = TABLE_ALIAS + joinTableId.getAndIncrement();
      for (int c = table1.columnCount(); c < cols.length; c++) {
        String columnName = cols[c].name();
        if (table1ColNames.contains(columnName.toLowerCase())) {
          cols[c].setName(newName(table2Alias, columnName));
        }
      }
    }
    destination.addColumns(cols);
    return ignoreColumns;
  }

  /**
   * Creates cross product for the selection of two tables.
   *
   * @param destination the destination table.
   * @param table1 the table on left of join.
   * @param table2 the table on right of join.
   * @param ignoreColumns a set of column indexes in the result to ignore. They are redundant join
   *     columns.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void crossProduct(
      Table destination,
      Table table1,
      Table table2,
      Set<Integer> ignoreColumns,
      boolean keepTable2JoinKeyColumns) {
    for (int c = 0; c < table1.columnCount() + table2.columnCount(); c++) {
      if (!keepTable2JoinKeyColumns && ignoreColumns.contains(c)) {
        continue;
      }
      int table2Index = c - table1.columnCount();
      for (Row r1 : table1) {
        for (Row r2 : table2) {
          if (c < table1.columnCount()) {
            Column t1Col = table1.column(c);
            destination.column(c).append(t1Col, r1.getRowNumber());
          } else {
            Column t2Col = table2.column(table2Index);
            destination.column(c).append(t2Col, r2.getRowNumber());
          }
        }
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void innerJoin(
      Table destination,
      Table table1,
      Table table2,
      Set<Integer> ignoreColumns,
      boolean keepTable2JoinKeyColumns) {
    Table joinTable = Table.create();
    for (int i : joinColumnIndexes) {
      joinTable.addColumns(table1.column(i).emptyCopy());
    }

    for (int c = 0; c < table1.columnCount() + table2.columnCount(); c++) {
      if (!keepTable2JoinKeyColumns && ignoreColumns.contains(c)) {
        continue;
      }
      int table2Index = c - table1.columnCount();
      for (Row r1 : table1) {
        for (Row r2 : table2) {
          if (c < table1.columnCount()) {
            Column t1Col = table1.column(c);
            destination.column(c).append(t1Col, r1.getRowNumber());
          } else {
            Column t2Col = table2.column(table2Index);
            destination.column(c).append(t2Col, r2.getRowNumber());
          }
        }
      }
    }
  }

  private void leftOuterJoin(
      Table destination,
      Table left,
      Table right,
      int[] rightJoinColumnIndexes,
      Set<Integer> ignoreColumns,
      boolean keepTable2JoinKeyColumns) {

    List<ColumnIndexPair> pairs = new ArrayList<>();
    for (int i = 0; i < joinColumnIndexes.length; i++) {
      ColumnIndexPair columnIndexPair =
          new ColumnIndexPair(
              left.column(i).type(), joinColumnIndexes[i], rightJoinColumnIndexes[i]);
      pairs.add(columnIndexPair);
    }

    // fill the destination with all values from the left table
    withMissingLeftJoin(destination, left, ignoreColumns, keepTable2JoinKeyColumns);

    Comparator<Row> comparator = SortKey.getChain(SortKey.create(pairs));
    Row rightRow = right.row(0);
    Row destRow = destination.row(0);
    int mark = -1;
    while (rightRow.hasNext() || destRow.hasNext()) {
      if (mark == -1) {
        int result = comparator.compare(destRow, rightRow);
        while (result < 0 && destRow.hasNext()) {
          destRow.next();
          result = comparator.compare(destRow, rightRow);
        }
        while (result > 0 && rightRow.hasNext()) {
          rightRow.next();
          result = comparator.compare(destRow, rightRow);
        }
        mark = rightRow.getRowNumber();
      }
      int result = comparator.compare(rightRow, destRow);
      if (result == 0) {
        // add values from the right table
        addRightValues(
            left.columnCount(), rightRow, destRow, keepTable2JoinKeyColumns, ignoreColumns);
        if (rightRow.hasNext()) rightRow.next();
      } else {
        rightRow.at(mark);
        if (destRow.hasNext()) {
          destRow.next();
          mark = -1;
        }
      }
    }
  }

  private void addRightValues(
      int leftColumnCount,
      Row rightRow,
      Row destRow,
      boolean keepTable2JoinKeyColumns,
      Set<Integer> ignoreColumns) {
    for (int c = leftColumnCount; c < leftColumnCount + rightRow.columnCount(); c++) {
      if (!keepTable2JoinKeyColumns && ignoreColumns.contains(c)) {
        continue;
      }
      int rightIndex = c - leftColumnCount;
      ColumnType type = destRow.getColumnType(c);
      if (type.equals(ColumnType.INTEGER)) {
        destRow.setInt(c, rightRow.getInt(rightIndex));
      }
    }
  }

  /*for (int c = 0; c < left.columnCount() + right.columnCount(); c++) {
  if (!keepTable2JoinKeyColumns && ignoreColumns.contains(c)) {
    continue;
  }
  int table2Index = c - left.columnCount();
  for (Row r1 : left) {
    for (Row r2 : right) {
      if (c < left.columnCount()) {
        Column t1Col = left.column(c);
        destination.column(c).append(t1Col, r1.getRowNumber());
      } else {
        Column t2Col = right.column(table2Index);
        destination.column(c).append(t2Col, r2.getRowNumber());
      }
    }
  }
  */

  /**
   * Adds rows to destination for each row in table1 with the columns from table2 added as missing
   * values.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void withMissingLeftJoin(
      Table destination,
      Table table1,
      Set<Integer> ignoreColumns,
      boolean keepTable2JoinKeyColumns) {
    Selection table1Rows = Selection.withRange(0, table1.rowCount());

    for (int c = 0; c < destination.columnCount(); c++) {
      if (!keepTable2JoinKeyColumns && ignoreColumns.contains(c)) {
        continue;
      }
      if (c < table1.columnCount()) {
        Column t1Col = table1.column(c);
        destination.column(c).append(t1Col.copy());
      } else {
        for (int r1 = 0; r1 < table1Rows.size(); r1++) {
          destination.column(c).appendMissing();
        }
      }
    }
  }

  /**
   * Adds rows to destination for each row in table2 with the columns from table1 added as missing
   * values.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void withMissingRight(
      Table destination,
      int table1ColCount,
      Table table2,
      Selection table2Rows,
      JoinType joinType,
      List<Integer> col2Indexes,
      Set<Integer> ignoreColumns,
      boolean keepTable2JoinKeyColumns) {

    // Add index data from table2 into join column positions in table one.
    if (joinType == JoinType.FULL_OUTER) {
      for (int i = 0; i < col2Indexes.size(); i++) {
        Column t2Col = table2.column(col2Indexes.get(i));
        for (int index : table2Rows) {
          destination.column(joinColumnIndexes[i]).append(t2Col, index);
        }
      }
    }

    for (int c = 0; c < destination.columnCount(); c++) {
      if (!keepTable2JoinKeyColumns) {
        if (ignoreColumns.contains(c) || indexesContainsValue(joinColumnIndexes, c)) {
          continue;
        }
      }
      if (c < table1ColCount) {
        for (int r1 = 0; r1 < table2Rows.size(); r1++) {
          destination.column(c).appendMissing();
        }
      } else {
        Column t2Col = table2.column(c - table1ColCount);
        for (int index : table2Rows) {
          destination.column(c).append(t2Col, index);
        }
      }
    }
  }

  private boolean indexesContainsValue(int[] joinColumnIndexes, int columnIndex) {
    for (int i : joinColumnIndexes) {
      if (columnIndex == i) {
        return true;
      }
    }
    return false;
  }

  /**
   * Describes two columns that are to be compared in a sort The columns are expected to be
   * referenced in two separate rows. The values of left and right provide the column index
   * (position) in each of the two rows.
   */
  public static class ColumnIndexPair {
    final ColumnType type;
    final int left;
    final int right;

    public ColumnIndexPair(ColumnType type, int left, int right) {
      this.type = type;
      this.left = left;
      this.right = right;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("IndexPair{");
      sb.append("type=").append(type);
      sb.append(", left=").append(left);
      sb.append(", right=").append(right);
      sb.append('}');
      return sb.toString();
    }
  }
}

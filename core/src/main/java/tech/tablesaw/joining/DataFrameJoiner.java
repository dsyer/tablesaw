package tech.tablesaw.joining;

import com.google.common.collect.Streams;
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

  private static final String LEFT_RECORD_ID_NAME = "_left_record_id_";
  private static final String RIGHT_RECORD_ID_NAME = "_right_record_id_";

  private static final String TABLE_ALIAS = "T";

  private final Table table;
  private final String[] joinColumnNames;
  // private final String[] joinColumnNames;

  private int[] leftJoinColumnIndexes;
  private int[] rightJoinColumnIndexes;

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
    this.leftJoinColumnIndexes = getJoinIndexes(table, joinColumnNames);
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

    rightJoinColumnIndexes = getJoinIndexes(table2, table2JoinColumnNames);

    table1 = table1.sortOn(leftJoinColumnIndexes);
    table2 = table2.sortOn(rightJoinColumnIndexes);

    Table result = Table.create(table1.name());

    // A set of column indexes in the result table that can be ignored. They are duplicate join
    // keys.
    int[] resultIgnoreColIndexes =
        emptyTableFromColumns(result, table1, table2, joinType, allowDuplicates);

    // add indexes for outer join processing
    IntColumn indexLeft = IntColumn.indexColumn(LEFT_RECORD_ID_NAME, table1.rowCount(), 0);
    table1.addColumns(indexLeft);
    result.addColumns(IntColumn.create(LEFT_RECORD_ID_NAME));

    IntColumn indexRight = IntColumn.indexColumn(RIGHT_RECORD_ID_NAME, table2.rowCount(), 0);
    table2.addColumns(indexRight);
    result.addColumns(IntColumn.create(RIGHT_RECORD_ID_NAME));

    validateIndexes(table1, table2);

    if (table1.rowCount() == 0 && (joinType == JoinType.LEFT_OUTER || joinType == JoinType.INNER)) {
      // Handle special case of empty table here so it doesn't fall through to the behavior
      // that adds rows for full outer and right outer joins
      if (!keepAllJoinKeyColumns) {
        result.removeColumns(resultIgnoreColIndexes);
      }
      return result;
    }
    if (joinType == JoinType.INNER) {
      joinInner(result, table1, table2, resultIgnoreColIndexes);
    } else if (joinType == JoinType.LEFT_OUTER) {
      joinLeft(result, table1, table2, resultIgnoreColIndexes);
    } else if (joinType == JoinType.RIGHT_OUTER) {
      joinRight(result, table1, table2, resultIgnoreColIndexes);
    } else if (joinType == JoinType.FULL_OUTER) {
      joinFull(result, table1, table2, resultIgnoreColIndexes);
    }
    // System.out.println(result);
    result.removeColumns(LEFT_RECORD_ID_NAME, RIGHT_RECORD_ID_NAME);

    if (!keepAllJoinKeyColumns) {
      result = result.removeColumns(resultIgnoreColIndexes);
      System.out.println(result);
    } else {
      renameColumnIndexes(result, resultIgnoreColIndexes);
    }
    return result;
  }

  /**
   * Renames the column indexes for the second table from Placeholder_X to their original names
   *
   * @param resultIgnoreColIndexes The positions of the secondary join columns
   */
  private void renameColumnIndexes(Table result, int[] resultIgnoreColIndexes) {

    for (int i = 0; i < resultIgnoreColIndexes.length; i++) {
      // TODO: Implement me, consider Left vs Right and possible renaming to avoid dupe names
      // Column c = result.column(resultIgnoreColIndexes.)
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
  public Table leftOuter(Table... tables) {
    return leftOuter(false, tables);
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
  public Table leftOuter(boolean allowDuplicateColumnNames, Table... tables) {
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
  public Table leftOuter(Table table2, String[] col2Names) {
    return leftOuter(table2, false, col2Names);
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
  public Table leftOuter(Table table2, String col2Name) {
    return leftOuter(table2, false, col2Name);
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
  public Table leftOuter(Table table2, boolean allowDuplicateColumnNames, String... col2Names) {
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
  public Table leftOuter(
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
      leftJoinColumnIndexes = getJoinIndexes(joined, joinColumnNames);
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
   *     the same name as columns in table1, with the exception of join columns in table2 when
   *     performing a right join.
   * @return A
   */
  private int[] emptyTableFromColumns(
      Table destination, Table table1, Table table2, JoinType joinType, boolean allowDuplicates) {

    Column<?>[] cols =
        Streams.concat(table1.columns().stream(), table2.columns().stream())
            .map(Column::emptyCopy)
            .toArray(Column[]::new);

    // For inner join, left join and full outer join mark the join columns in table2 as
    // placeholders.
    // For right join, mark the join columns in table1 as placeholders.
    // Keep track of which join columns are placeholders so they can be ignored.
    int[] ignoreColumns = new int[leftJoinColumnIndexes.length];
    int i = 0;
    int ignoreIndex = 0;
    for (int c = 0; c < cols.length; c++) {
      if (joinType == JoinType.RIGHT_OUTER) {
        if (c < table1.columnCount() && indexesContainsValue(leftJoinColumnIndexes, c)) {
          ignoreColumns[ignoreIndex] = c;
          cols[c].setName("Placeholder_" + ignoreIndex);
          ignoreIndex++;
        }
      } else { // JoinType is LEFT, INNER, or FULL
        int table2Index = c + table1.columnCount();
        if (indexesContainsValue(rightJoinColumnIndexes, c)) {
          ignoreColumns[ignoreIndex] = table2Index;
          cols[table2Index].setName("Placeholder_" + ignoreIndex);
          ignoreIndex++;
        }
      }
      i++;
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

  private void joinLeft(Table destination, Table left, Table right, int[] ignoreColumns) {

    joinInner(destination, left, right, ignoreColumns);
    Selection unmatched =
        left.intColumn(LEFT_RECORD_ID_NAME)
            .isNotIn(destination.intColumn(LEFT_RECORD_ID_NAME).unique());
    addLeftOnlyValues(destination, left, unmatched, true);
  }

  private void addLeftOnlyValues(
      Table destination, Table left, Selection unmatched, boolean fullJoin) {
    for (Row leftRow : left.where(unmatched)) {
      Row destRow = destination.appendRow();
      for (int c = 0; c < leftRow.columnCount() - 1; c++) {
        updateDestinationRow(destRow, leftRow, c, c);
      }
      // update the index column putting it at the end of the destination table
      updateDestinationRow(destRow, leftRow, destRow.columnCount() - 2, leftRow.columnCount() - 1);
    }
  }

  private void addRightOnlyValues(Table destination, Table left, Table right, Selection unmatched) {
    int leftColumnCount = left.columnCount();
    for (Row rightRow : right.where(unmatched)) {
      Row destRow = destination.appendRow();
      for (int c = 0; c < rightRow.columnCount() - 1; c++) {
        updateDestinationRow(destRow, rightRow, c + leftColumnCount - 1, c);
      }
      // update the index column putting it at the end of the destination table
      updateDestinationRow(
          destRow, rightRow, destRow.columnCount() - 1, rightRow.columnCount() - 1);
    }
  }

  private void joinRight(Table destination, Table left, Table right, int[] ignoreColumns) {
    joinInner(destination, left, right, ignoreColumns);
    Selection unmatched =
        right
            .intColumn(RIGHT_RECORD_ID_NAME)
            .isNotIn(destination.intColumn(RIGHT_RECORD_ID_NAME).unique());
    addRightOnlyValues(destination, left, right, unmatched);
  }

  private void joinFull(Table destination, Table left, Table right, int[] ignoreColumns) {

    Table tempDestination = destination.emptyCopy();

    joinInner(destination, left, right, ignoreColumns);

    Selection unmatchedLeft =
        left.intColumn(LEFT_RECORD_ID_NAME)
            .isNotIn(destination.intColumn(LEFT_RECORD_ID_NAME).unique());
    addLeftOnlyValues(destination, left, unmatchedLeft, true);

    Selection unmatchedRight =
        right
            .intColumn(RIGHT_RECORD_ID_NAME)
            .isNotIn(destination.intColumn(RIGHT_RECORD_ID_NAME).unique());
    addRightOnlyValues(tempDestination, left, right, unmatchedRight);
    for (int i = 0; i < ignoreColumns.length; i++) {
      String name = tempDestination.columnNames().get(leftJoinColumnIndexes[i]);
      tempDestination.replaceColumn(
          leftJoinColumnIndexes[i], tempDestination.column(ignoreColumns[i]).copy().setName(name));
    }
    destination.append(tempDestination);
  }

  private void joinInner(Table destination, Table left, Table right, int[] ignoreColumns) {

    Comparator<Row> comparator = getRowComparator(left, rightJoinColumnIndexes);

    Row leftRow = left.row(0);
    Row rightRow = right.row(0);

    // Marks the position of the first record in right table that matches a specific join value
    int mark = -1;

    while (leftRow.hasNext() || rightRow.hasNext()) {
      if (mark == -1) {
        while (comparator.compare(leftRow, rightRow) < 0 && leftRow.hasNext()) leftRow.next();
        while (comparator.compare(leftRow, rightRow) > 0 && rightRow.hasNext()) rightRow.next();
        mark =
            rightRow
                .getRowNumber(); // set the position of the first matching record on the right side
      }
      if (comparator.compare(leftRow, rightRow) == 0 && (leftRow.hasNext() || rightRow.hasNext())) {
        addValues(destination, leftRow, rightRow, ignoreColumns);
        if (rightRow.hasNext()) {
          rightRow.next();
        } else {
          rightRow.at(mark);
          if (leftRow.hasNext()) {
            leftRow.next();
          }
          mark = -1;
        }
      } else {
        if (rightRow.hasNext() && leftRow.hasNext()) {
          rightRow.at(mark);
          leftRow.next();
          mark = -1;
        } else {
          if (leftRow.hasNext()) leftRow.next();
          break;
        }
      }
    }
    // add the last value if you end on a match
    if (comparator.compare(leftRow, rightRow) == 0) {
      addValues(destination, leftRow, rightRow, ignoreColumns);
    }
  }

  private Comparator<Row> getRowComparator(Table left, int[] rightJoinColumnIndexes) {
    List<ColumnIndexPair> pairs = createJoinColumnPairs(left, rightJoinColumnIndexes);
    return SortKey.getChain(SortKey.create(pairs));
  }

  private List<ColumnIndexPair> createJoinColumnPairs(Table left, int[] rightJoinColumnIndexes) {
    List<ColumnIndexPair> pairs = new ArrayList<>();
    for (int i = 0; i < leftJoinColumnIndexes.length; i++) {
      ColumnIndexPair columnIndexPair =
          new ColumnIndexPair(
              left.column(leftJoinColumnIndexes[i]).type(),
              leftJoinColumnIndexes[i],
              rightJoinColumnIndexes[i]);
      pairs.add(columnIndexPair);
    }
    return pairs;
  }

  private void updateDestinationRow(
      Row destRow, Row sourceRow, int destColumnPosition, int sourceColumnPosition) {
    ColumnType type = destRow.getColumnType(destColumnPosition);
    if (type.equals(ColumnType.INTEGER)) {
      destRow.setInt(destColumnPosition, sourceRow.getInt(sourceColumnPosition));
    } else if (type.equals(ColumnType.LONG)) {
      destRow.setLong(destColumnPosition, sourceRow.getLong(sourceColumnPosition));
    } else if (type.equals(ColumnType.SHORT)) {
      destRow.setShort(destColumnPosition, sourceRow.getShort(sourceColumnPosition));
    } else if (type.equals(ColumnType.STRING)) {
      destRow.setString(destColumnPosition, sourceRow.getString(sourceColumnPosition));
    } else if (type.equals(ColumnType.LOCAL_DATE)) {
      destRow.setPackedDate(destColumnPosition, sourceRow.getPackedDate(sourceColumnPosition));
    } else if (type.equals(ColumnType.LOCAL_TIME)) {
      destRow.setPackedTime(destColumnPosition, sourceRow.getPackedTime(sourceColumnPosition));
    } else if (type.equals(ColumnType.LOCAL_DATE_TIME)) {
      destRow.setPackedDateTime(
          destColumnPosition, sourceRow.getPackedDateTime(sourceColumnPosition));
    } else if (type.equals(ColumnType.INSTANT)) {
      destRow.setPackedInstant(
          destColumnPosition, sourceRow.getPackedInstant(sourceColumnPosition));
    } else if (type.equals(ColumnType.DOUBLE)) {
      destRow.setDouble(destColumnPosition, sourceRow.getDouble(sourceColumnPosition));
    } else if (type.equals(ColumnType.FLOAT)) {
      destRow.setFloat(destColumnPosition, sourceRow.getFloat(sourceColumnPosition));
    } else if (type.equals(ColumnType.BOOLEAN)) {
      destRow.setBooleanAsByte(
          destColumnPosition, sourceRow.getBooleanAsByte(sourceColumnPosition));
    } else if (type.equals(ColumnType.TEXT)) {
      destRow.setText(destColumnPosition, sourceRow.getText(sourceColumnPosition));
    }
  }

  private void addValues(Table destination, Row leftRow, Row rightRow, int[] ignoreColumns) {

    Row destRow = destination.appendRow();
    // update positionally, but take into account the RECORD_ID COLUMNS at the end of the dest table

    int leftColumnCount = leftRow.columnCount();
    int rightColumnCount = rightRow.columnCount();

    // update from the left table first (everythint but the RECORD_ID column)
    for (int destIdx1 = 0; destIdx1 < leftColumnCount - 1; destIdx1++) {
      updateDestinationRow(destRow, leftRow, destIdx1, destIdx1);
    }

    // update from the right table (everythint but the RECORD_ID column)
    for (int destIdx2 = (leftColumnCount - 1);
        destIdx2 < (leftColumnCount + rightColumnCount) - 2;
        destIdx2++) {
      int rightIndex = destIdx2 - (leftColumnCount - 1);
      updateDestinationRow(destRow, rightRow, destIdx2, rightIndex);
    }

    // update the RECORD_ID columns
    updateDestinationRow(destRow, leftRow, destRow.columnCount() - 2, leftColumnCount - 1);
    updateDestinationRow(destRow, rightRow, destRow.columnCount() - 1, rightColumnCount - 1);
  }

  private boolean indexesContainsValue(int[] joinColumnIndexes, int columnIndex) {
    for (int i : joinColumnIndexes) {
      if (columnIndex == i) {
        return true;
      }
    }
    return false;
  }

  private void validateIndexes(Table table1, Table table2) {
    if (leftJoinColumnIndexes.length != rightJoinColumnIndexes.length) {
      throw new IllegalArgumentException(
          "Cannot join using a different number of indices on each table: "
              + Arrays.toString(leftJoinColumnIndexes)
              + " and "
              + Arrays.toString(rightJoinColumnIndexes));
    }
    for (int i = 0; i < leftJoinColumnIndexes.length; i++) {
      if (!table1
          .column(leftJoinColumnIndexes[i])
          .getClass()
          .equals(table2.column(rightJoinColumnIndexes[i]).getClass())) {
        throw new IllegalArgumentException(
            "Cannot join using different index types: "
                + Arrays.toString(leftJoinColumnIndexes)
                + " and "
                + Arrays.toString(rightJoinColumnIndexes));
      }
    }
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
      final StringBuilder sb = new StringBuilder("ColumnIndexPair{");
      sb.append("type=").append(type);
      sb.append(", left=").append(left);
      sb.append(", right=").append(right);
      sb.append('}');
      return sb.toString();
    }
  }
}

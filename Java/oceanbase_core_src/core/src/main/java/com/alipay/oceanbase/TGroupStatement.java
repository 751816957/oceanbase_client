package com.alipay.oceanbase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.alipay.oceanbase.factory.DataSourceHolder;
import com.alipay.oceanbase.group.MergeServerSelector.DataSourceTryer;
import com.alipay.oceanbase.util.parse.SQLHintParser;
import com.alipay.oceanbase.util.parse.SQLParser;
import com.alipay.oceanbase.util.parse.SqlHintType;
import com.alipay.oceanbase.util.parse.SqlType;

/**
 * 
 * 
 * @author liangjie.li
 * @version $Id: TGroupStatement.java, v 0.1 2013-5-24 下午4:13:42 liangjie.li Exp $
 */
public class TGroupStatement implements Statement {

    private static final Logger logger           = Logger.getLogger(TGroupStatement.class);

    protected TGroupConnection  tGroupConnection = null;
    protected OBGroupDataSource tGroupDataSource = null;

    public TGroupStatement(OBGroupDataSource tGroupDataSource, TGroupConnection tGroupConnection) {
        this.tGroupDataSource = tGroupDataSource;
        this.tGroupConnection = tGroupConnection;
    }

    protected Statement baseStatement = null;

    void setBaseStatement(Statement baseStatement) {
        if (this.baseStatement != null) {
            try {
                this.baseStatement.close();
            } catch (SQLException e) {
                logger.error("close baseStatement failed.", e);
            }
        }
        this.baseStatement = baseStatement;
    }

    protected int       fetchSize            = 0;
    protected int       maxRows              = 0;
    protected int       updateCount          = 0;

    protected int       queryTimeout         = -1;
    protected int       resultSetHoldability = -1;
    protected int       resultSetType        = ResultSet.TYPE_FORWARD_ONLY;
    protected int       resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;

    protected ResultSet currentResultSet     = null;

    public boolean execute(String sql) throws SQLException {
        return executeInternal(sql, -1, null, null);
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return executeInternal(sql, autoGeneratedKeys, null, null);
    }

    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return executeInternal(sql, -1, columnIndexes, null);
    }

    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return executeInternal(sql, -1, null, columnNames);
    }

    private boolean executeInternal(String sql, int autoGeneratedKeys, int[] columnIndexes,
                                    String[] columnNames) throws SQLException {
        checkClosed();
        ensureResultSetIsEmpty();

        Connection conn = tGroupConnection.getBaseConnection(sql, false);
        boolean gotoRead = SqlType.SELECT.equals(SQLParser.getSqlType(sql))
                           && tGroupConnection.getAutoCommit();

        if (conn != null) {
            return this.executeOnConnection(conn, sql, autoGeneratedKeys, columnIndexes,
                columnNames);
        } else {
            return this.tGroupDataSource.getDBSelector().tryExecute(executeTryer,
                this.getConsistency(sql, gotoRead), SQLHintParser.getCluster(sql), sql,
                autoGeneratedKeys, columnIndexes, columnNames);
        }
    }

    protected boolean executeOnConnection(Connection conn, String sql, int autoGeneratedKeys,
                                          int[] columnIndexes, String[] columnNames)
                                                                                    throws SQLException {
        Statement stmt = createStatementInternal(conn, false);
        boolean result = false;

        if (autoGeneratedKeys == -1 && columnIndexes == null && columnNames == null) {
            result = stmt.execute(sql);
        } else if (autoGeneratedKeys != -1) {
            result = stmt.execute(sql, autoGeneratedKeys);
        } else if (columnIndexes != null) {
            result = stmt.execute(sql, columnIndexes);
        } else if (columnNames != null) {
            result = stmt.execute(sql, columnNames);
        } else {
            result = stmt.execute(sql);
        }

        this.currentResultSet = stmt.getResultSet();
        this.updateCount = stmt.getUpdateCount();
        return result;
    }

    protected DataSourceTryer<Boolean> executeTryer = new DataSourceTryer<Boolean>() {
                                                        public Boolean tryOnDataSource(DataSourceHolder dsw,
                                                                                       Object... args)
                                                                                                      throws SQLException {
                                                            Connection conn = TGroupStatement.this.tGroupConnection
                                                                .createNewConnection(dsw, false);
                                                            return executeOnConnection(conn,
                                                                (String) args[0],
                                                                (Integer) args[1], (int[]) args[2],
                                                                (String[]) args[3]);
                                                        }
                                                    };

    public int executeUpdate(String sql) throws SQLException {
        return executeUpdateInternal(sql, -1, null, null);
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdateInternal(sql, autoGeneratedKeys, null, null);
    }

    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdateInternal(sql, -1, columnIndexes, null);
    }

    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdateInternal(sql, -1, null, columnNames);
    }

    private int executeUpdateInternal(final String sql, int autoGeneratedKeys, int[] columnIndexes,
                                      String[] columnNames) throws SQLException {
        checkClosed();
        ensureResultSetIsEmpty();

        Connection conn = tGroupConnection.getBaseConnection(sql, false);
        if (conn != null) {
            this.updateCount = executeUpdateOnConnection(conn, sql, autoGeneratedKeys,
                columnIndexes, columnNames);
            return this.updateCount;
        } else {
            this.updateCount = this.tGroupDataSource.getDBSelector().tryExecute(executeUpdateTryer,
                true, SqlHintType.CLUSTER_NONE, sql, autoGeneratedKeys, columnIndexes, columnNames);
            return this.updateCount;
        }
    }

    private int executeUpdateOnConnection(Connection conn, String sql, int autoGeneratedKeys,
                                          int[] columnIndexes, String[] columnNames)
                                                                                    throws SQLException {
        Statement stmt = createStatementInternal(conn, false);

        if (autoGeneratedKeys == -1 && columnIndexes == null && columnNames == null) {
            return stmt.executeUpdate(sql);
        } else if (autoGeneratedKeys != -1) {
            return stmt.executeUpdate(sql, autoGeneratedKeys);
        } else if (columnIndexes != null) {
            return stmt.executeUpdate(sql, columnIndexes);
        } else if (columnNames != null) {
            return stmt.executeUpdate(sql, columnNames);
        } else {
            return stmt.executeUpdate(sql);
        }
    }

    private DataSourceTryer<Integer> executeUpdateTryer = new DataSourceTryer<Integer>() {
                                                            public Integer tryOnDataSource(DataSourceHolder dsw,
                                                                                           Object... args)
                                                                                                          throws SQLException {
                                                                Connection conn = TGroupStatement.this.tGroupConnection
                                                                    .createNewConnection(dsw, false);
                                                                return executeUpdateOnConnection(
                                                                    conn, (String) args[0],
                                                                    (Integer) args[1],
                                                                    (int[]) args[2],
                                                                    (String[]) args[3]);
                                                            }
                                                        };

    private Statement createStatementInternal(Connection conn, boolean isBatch) throws SQLException {
        Statement stmt = null;
        if (isBatch) {
            stmt = conn.createStatement();
        } else {
            int resultSetHoldability = this.resultSetHoldability;
            if (resultSetHoldability == -1) {
                resultSetHoldability = conn.getHoldability();
            }
            stmt = conn.createStatement(this.resultSetType, this.resultSetConcurrency,
                resultSetHoldability);
        }

        setBaseStatement(stmt);
        if (queryTimeout >= 0) {
            stmt.setQueryTimeout(queryTimeout);
        }
        stmt.setFetchSize(fetchSize);
        stmt.setMaxRows(maxRows);

        return stmt;
    }

    /* ========================================================================
     * executeBatch
     * ======================================================================*/
    protected List<String> batchedArgs = null;

    public void addBatch(String sql) throws SQLException {
        checkClosed();
        if (batchedArgs == null) {
            batchedArgs = new LinkedList<String>();
        }
        if (sql != null) {
            batchedArgs.add(sql);
        }
    }

    public void clearBatch() throws SQLException {
        checkClosed();
        if (batchedArgs != null) {
            batchedArgs.clear();
        }
    }

    public int[] executeBatch() throws SQLException {
        try {
            checkClosed();
            ensureResultSetIsEmpty();
            if (batchedArgs == null || batchedArgs.isEmpty()) {
                return new int[0];
            }
            Connection conn = tGroupConnection.getBaseConnection(null, false);
            if (conn != null) {
                return executeBatchOnConnection(conn, this.batchedArgs);
            } else {
                return tGroupDataSource.getDBSelector().tryExecute(executeBatchTryer, true,
                    SqlHintType.CLUSTER_NONE, batchedArgs.get(0));
            }
        } finally {
            if (batchedArgs != null) {
                batchedArgs.clear();
            }
        }
    }

    private DataSourceTryer<int[]> executeBatchTryer = new DataSourceTryer<int[]>() {
                                                         public int[] tryOnDataSource(DataSourceHolder dsw,
                                                                                      Object... args)
                                                                                                     throws SQLException {
                                                             Connection conn = TGroupStatement.this.tGroupConnection
                                                                 .createNewConnection(dsw, false);
                                                             return executeBatchOnConnection(conn,
                                                                 TGroupStatement.this.batchedArgs);
                                                         }
                                                     };

    private int[] executeBatchOnConnection(Connection conn, List<String> batchedSqls)
                                                                                     throws SQLException {
        Statement stmt = createStatementInternal(conn, true);
        for (String sql : batchedSqls) {
            stmt.addBatch(sql);
        }
        return stmt.executeBatch();
    }

    protected boolean closed = false;

    public void close() throws SQLException {
        close(true);
    }

    void close(boolean removeThis) throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (currentResultSet != null) {
                currentResultSet.close();
            }
        } catch (SQLException e) {
            logger.warn("Close currentResultSet failed.", e);
        } finally {
            currentResultSet = null;
        }
        try {
            if (this.baseStatement != null) {
                this.baseStatement.close();
            }
        } finally {
            this.baseStatement = null;
            if (removeThis) {
                tGroupConnection.removeOpenedStatements(this);
            }
        }
    }

    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("No operations allowed after statement closed.");
        }
    }

    protected void ensureResultSetIsEmpty() throws SQLException {
        if (currentResultSet != null) {
            try {
                currentResultSet.close();
            } catch (SQLException e) {
                logger.error("exception on close last result set . can do nothing..", e);
            } finally {
                currentResultSet = null;
            }
        }
    }

    public ResultSet executeQuery(final String sql) throws SQLException {
        checkClosed();
        ensureResultSetIsEmpty();
        // read operation
        boolean gotoRead = SqlType.SELECT.equals(SQLParser.getSqlType(sql))
                           && tGroupConnection.getAutoCommit();
        Connection conn = tGroupConnection.getBaseConnection(sql, gotoRead);
        if (conn != null) {
            return executeQueryOnConnection(conn, sql);
        } else {
            return this.tGroupDataSource.getDBSelector().tryExecute(executeQueryTryer,
                getConsistency(sql, gotoRead), SQLHintParser.getCluster(sql), sql);
        }
    }

    /**
     * 
     * 
     * @param sql
     * @param gotoRead
     * @return
     */
    protected boolean getConsistency(final String sql, boolean gotoRead) {
        boolean isConsistency = true;// default: consistency=true, read_consistency_level>=4
        if (gotoRead) {
            SqlHintType hint = SQLHintParser.getConsistencyHint(sql);
            if (hint == SqlHintType.CONSISTENCY_NONE && tGroupDataSource.isStrongConsistency < 4) {
                isConsistency = false;
            } else if (hint == SqlHintType.CONSISTENCY_WEAK) {
                isConsistency = false;
            }
        }
        return isConsistency;
    }

    protected ResultSet executeQueryOnConnection(Connection conn, String sql) throws SQLException {
        Statement stmt = createStatementInternal(conn, false);
        this.currentResultSet = stmt.executeQuery(sql);
        return this.currentResultSet;
    }

    protected DataSourceTryer<ResultSet> executeQueryTryer = new DataSourceTryer<ResultSet>() {
                                                               public ResultSet tryOnDataSource(DataSourceHolder dsw,
                                                                                                Object... args)
                                                                                                               throws SQLException {
                                                                   String sql = (String) args[0];
                                                                   Connection conn = TGroupStatement.this.tGroupConnection
                                                                       .createNewConnection(dsw,
                                                                           true);
                                                                   return executeQueryOnConnection(
                                                                       conn, sql);
                                                               }
                                                           };

    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        if (baseStatement != null) {
            return baseStatement.getWarnings();
        }
        return null;
    }

    public void clearWarnings() throws SQLException {
        checkClosed();
        if (baseStatement != null) {
            baseStatement.clearWarnings();
        }
    }

    protected boolean moreResults;

    public boolean getMoreResults() throws SQLException {
        return moreResults;
    }

    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    public void setQueryTimeout(int queryTimeout) throws SQLException {
        this.queryTimeout = queryTimeout;
    }

    public ResultSet getResultSet() throws SQLException {
        return currentResultSet;
    }

    public int getUpdateCount() throws SQLException {
        return updateCount;
    }

    public int getResultSetConcurrency() throws SQLException {
        return resultSetConcurrency;
    }

    public int getResultSetHoldability() throws SQLException {
        return resultSetHoldability;
    }

    public int getResultSetType() throws SQLException {
        return resultSetType;
    }

    public void setResultSetType(int resultSetType) {
        this.resultSetType = resultSetType;
    }

    public void setResultSetConcurrency(int resultSetConcurrency) {
        this.resultSetConcurrency = resultSetConcurrency;
    }

    public void setResultSetHoldability(int resultSetHoldability) {
        this.resultSetHoldability = resultSetHoldability;
    }

    public Connection getConnection() throws SQLException {
        return tGroupConnection;
    }

    public int getFetchDirection() throws SQLException {
        throw new UnsupportedOperationException("getFetchDirection");
    }

    public int getFetchSize() throws SQLException {
        return this.fetchSize;
    }

    public int getMaxFieldSize() throws SQLException {
        throw new UnsupportedOperationException("getMaxFieldSize");
    }

    public int getMaxRows() throws SQLException {
        return this.maxRows;
    }

    public void setCursorName(String cursorName) throws SQLException {
        throw new UnsupportedOperationException("setCursorName");
    }

    public void setEscapeProcessing(boolean escapeProcessing) throws SQLException {
        throw new UnsupportedOperationException("setEscapeProcessing");
    }

    public boolean getMoreResults(int current) throws SQLException {
        throw new UnsupportedOperationException("getMoreResults");
    }

    public void setFetchDirection(int fetchDirection) throws SQLException {
        throw new UnsupportedOperationException("setFetchDirection");
    }

    public void setFetchSize(int fetchSize) throws SQLException {
        this.fetchSize = fetchSize;
    }

    public void setMaxFieldSize(int maxFieldSize) throws SQLException {
        throw new UnsupportedOperationException("setMaxFieldSize");
    }

    public void setMaxRows(int maxRows) throws SQLException {
        this.maxRows = maxRows;
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        if (this.baseStatement != null) {
            return this.baseStatement.getGeneratedKeys();
        } else {
            throw new SQLException("no update operation before getGeneratedKeys");
        }
    }

    public void cancel() throws SQLException {
        throw new UnsupportedOperationException("cancel");
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return this.getClass().isAssignableFrom(iface);
    }

    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        try {
            return (T) this;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public boolean isClosed() throws SQLException {
        throw new SQLException("not support exception");
    }

    public void setPoolable(boolean poolable) throws SQLException {
        throw new SQLException("not support exception");
    }

    public boolean isPoolable() throws SQLException {
        throw new SQLException("not support exception");
    }
}

package org.sqlite.encrypted.examples.roomnotes;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteTransactionListener;
import android.os.CancellationSignal;
import android.util.Pair;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.SupportSQLiteProgram;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.sqlite.database.sqlite.SQLiteOpenHelper;

import java.util.List;
import java.util.Locale;

/** Adapts SQLite.Encrypted's Android API to the SupportSQLite API used by Room. */
public final class SeeOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {
    private final String databaseKey;

    public SeeOpenHelperFactory(String databaseKey) {
        this.databaseKey = databaseKey;
    }

    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        return new SeeOpenHelper(configuration, databaseKey);
    }

    private static final class SeeOpenHelper implements SupportSQLiteOpenHelper {
        private final String name;
        private final SQLiteOpenHelper delegate;

        SeeOpenHelper(
                SupportSQLiteOpenHelper.Configuration configuration,
                String databaseKey) {
            name = configuration.name;
            delegate = new SQLiteOpenHelper(
                    configuration.context,
                    configuration.name,
                    null,
                    configuration.callback.version,
                    databaseKey,
                    null) {
                @Override
                public void onConfigure(org.sqlite.database.sqlite.SQLiteDatabase database) {
                    configuration.callback.onConfigure(new SeeDatabase(database));
                }

                @Override
                public void onCreate(org.sqlite.database.sqlite.SQLiteDatabase database) {
                    configuration.callback.onCreate(new SeeDatabase(database));
                }

                @Override
                public void onUpgrade(
                        org.sqlite.database.sqlite.SQLiteDatabase database,
                        int oldVersion,
                        int newVersion) {
                    configuration.callback.onUpgrade(
                            new SeeDatabase(database), oldVersion, newVersion);
                }

                @Override
                public void onDowngrade(
                        org.sqlite.database.sqlite.SQLiteDatabase database,
                        int oldVersion,
                        int newVersion) {
                    configuration.callback.onDowngrade(
                            new SeeDatabase(database), oldVersion, newVersion);
                }

                @Override
                public void onOpen(org.sqlite.database.sqlite.SQLiteDatabase database) {
                    configuration.callback.onOpen(new SeeDatabase(database));
                }
            };
        }

        @Override
        public String getDatabaseName() {
            return name;
        }

        @Override
        public void setWriteAheadLoggingEnabled(boolean enabled) {
            delegate.setWriteAheadLoggingEnabled(enabled);
        }

        @Override
        public SupportSQLiteDatabase getWritableDatabase() {
            return new SeeDatabase(delegate.getWritableDatabase());
        }

        @Override
        public SupportSQLiteDatabase getReadableDatabase() {
            return new SeeDatabase(delegate.getReadableDatabase());
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class SeeDatabase implements SupportSQLiteDatabase {
        private static final String[] CONFLICT_VALUES = {
            "", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE"
        };

        private final org.sqlite.database.sqlite.SQLiteDatabase delegate;

        SeeDatabase(org.sqlite.database.sqlite.SQLiteDatabase delegate) {
            this.delegate = delegate;
        }

        @Override
        public SupportSQLiteStatement compileStatement(String sql) {
            return new SeeStatement(delegate.compileStatement(sql));
        }

        @Override public void beginTransaction() { delegate.beginTransaction(); }
        @Override public void beginTransactionNonExclusive() { delegate.beginTransactionNonExclusive(); }

        @Override
        public void beginTransactionWithListener(SQLiteTransactionListener listener) {
            delegate.beginTransactionWithListener(adapt(listener));
        }

        @Override
        public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener listener) {
            delegate.beginTransactionWithListenerNonExclusive(adapt(listener));
        }

        private static org.sqlite.database.sqlite.SQLiteTransactionListener adapt(
                SQLiteTransactionListener listener) {
            return new org.sqlite.database.sqlite.SQLiteTransactionListener() {
                @Override public void onBegin() { listener.onBegin(); }
                @Override public void onCommit() { listener.onCommit(); }
                @Override public void onRollback() { listener.onRollback(); }
            };
        }

        @Override public void endTransaction() { delegate.endTransaction(); }
        @Override public void setTransactionSuccessful() { delegate.setTransactionSuccessful(); }
        @Override public boolean inTransaction() { return delegate.inTransaction(); }
        @Override public boolean isDbLockedByCurrentThread() { return delegate.isDbLockedByCurrentThread(); }
        @Override public boolean yieldIfContendedSafely() { return delegate.yieldIfContendedSafely(); }
        @Override public boolean yieldIfContendedSafely(long delay) { return delegate.yieldIfContendedSafely(delay); }
        @Override public int getVersion() { return delegate.getVersion(); }
        @Override public void setVersion(int version) { delegate.setVersion(version); }
        @Override public long getMaximumSize() { return delegate.getMaximumSize(); }
        @Override public long setMaximumSize(long bytes) { return delegate.setMaximumSize(bytes); }
        @Override public long getPageSize() { return delegate.getPageSize(); }
        @Override public void setPageSize(long bytes) { delegate.setPageSize(bytes); }

        @Override
        public Cursor query(String sql) {
            return delegate.rawQuery(sql, null);
        }

        @Override
        public Cursor query(String sql, Object[] bindArgs) {
            return query(new SimpleSQLiteQuery(sql, bindArgs));
        }

        @Override
        public Cursor query(SupportSQLiteQuery query) {
            return query(query, null);
        }

        @Override
        public Cursor query(
                SupportSQLiteQuery supportQuery,
                CancellationSignal cancellationSignal) {
            return delegate.rawQueryWithFactory(
                    (database, driver, editTable, query) -> {
                        supportQuery.bindTo(new SeeProgram(query));
                        return new org.sqlite.database.sqlite.SQLiteCursor(
                                driver, editTable, query);
                    },
                    supportQuery.getSql(),
                    null,
                    null,
                    cancellationSignal);
        }

        @Override
        public long insert(String table, int conflictAlgorithm, ContentValues values) {
            return delegate.insertWithOnConflict(table, null, values, conflictAlgorithm);
        }

        @Override
        public int delete(String table, String whereClause, Object[] whereArgs) {
            String sql = "DELETE FROM " + table
                    + (whereClause == null || whereClause.isEmpty()
                            ? ""
                            : " WHERE " + whereClause);
            SeeStatement statement = new SeeStatement(delegate.compileStatement(sql));
            try {
                SimpleSQLiteQuery.bind(statement, whereArgs);
                return statement.executeUpdateDelete();
            } finally {
                statement.close();
            }
        }

        @Override
        public int update(
                String table,
                int conflictAlgorithm,
                ContentValues values,
                String whereClause,
                Object[] whereArgs) {
            if (values == null || values.size() == 0) {
                throw new IllegalArgumentException("Empty values");
            }
            StringBuilder sql = new StringBuilder("UPDATE");
            sql.append(CONFLICT_VALUES[conflictAlgorithm])
                    .append(' ')
                    .append(table)
                    .append(" SET ");
            Object[] bindArgs = new Object[
                    values.size() + (whereArgs == null ? 0 : whereArgs.length)];
            int index = 0;
            for (String column : values.keySet()) {
                if (index > 0) {
                    sql.append(',');
                }
                sql.append(column).append("=?");
                bindArgs[index++] = values.get(column);
            }
            if (whereArgs != null) {
                for (Object argument : whereArgs) {
                    bindArgs[index++] = argument;
                }
            }
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            SeeStatement statement = new SeeStatement(
                    delegate.compileStatement(sql.toString()));
            try {
                SimpleSQLiteQuery.bind(statement, bindArgs);
                return statement.executeUpdateDelete();
            } finally {
                statement.close();
            }
        }

        @Override public void execSQL(String sql) { delegate.execSQL(sql); }
        @Override public void execSQL(String sql, Object[] bindArgs) { delegate.execSQL(sql, bindArgs); }
        @Override public boolean isReadOnly() { return delegate.isReadOnly(); }
        @Override public boolean isOpen() { return delegate.isOpen(); }
        @Override public boolean needUpgrade(int newVersion) { return delegate.needUpgrade(newVersion); }
        @Override public String getPath() { return delegate.getPath(); }
        @Override public void setLocale(Locale locale) { delegate.setLocale(locale); }
        @Override public void setMaxSqlCacheSize(int cacheSize) { delegate.setMaxSqlCacheSize(cacheSize); }
        @Override public void setForeignKeyConstraintsEnabled(boolean enabled) { delegate.setForeignKeyConstraintsEnabled(enabled); }
        @Override public boolean enableWriteAheadLogging() { return delegate.enableWriteAheadLogging(); }
        @Override public void disableWriteAheadLogging() { delegate.disableWriteAheadLogging(); }
        @Override public boolean isWriteAheadLoggingEnabled() { return delegate.isWriteAheadLoggingEnabled(); }
        @Override public List<Pair<String, String>> getAttachedDbs() { return delegate.getAttachedDbs(); }
        @Override public boolean isDatabaseIntegrityOk() { return delegate.isDatabaseIntegrityOk(); }
        @Override public void close() { delegate.close(); }
    }

    private static class SeeProgram implements SupportSQLiteProgram {
        final org.sqlite.database.sqlite.SQLiteProgram delegate;

        SeeProgram(org.sqlite.database.sqlite.SQLiteProgram delegate) {
            this.delegate = delegate;
        }

        @Override public void bindNull(int index) { delegate.bindNull(index); }
        @Override public void bindLong(int index, long value) { delegate.bindLong(index, value); }
        @Override public void bindDouble(int index, double value) { delegate.bindDouble(index, value); }
        @Override public void bindString(int index, String value) { delegate.bindString(index, value); }
        @Override public void bindBlob(int index, byte[] value) { delegate.bindBlob(index, value); }
        @Override public void clearBindings() { delegate.clearBindings(); }
        @Override public void close() { delegate.close(); }
    }

    private static final class SeeStatement extends SeeProgram
            implements SupportSQLiteStatement {
        private final org.sqlite.database.sqlite.SQLiteStatement statement;

        SeeStatement(org.sqlite.database.sqlite.SQLiteStatement statement) {
            super(statement);
            this.statement = statement;
        }

        @Override public void execute() { statement.execute(); }
        @Override public int executeUpdateDelete() { return statement.executeUpdateDelete(); }
        @Override public long executeInsert() { return statement.executeInsert(); }
        @Override public long simpleQueryForLong() { return statement.simpleQueryForLong(); }
        @Override public String simpleQueryForString() { return statement.simpleQueryForString(); }
    }
}

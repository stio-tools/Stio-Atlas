/*
 * Copyright (c) 2015 Oleg Orlov. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.stio.atlas;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Dump tool
 * <p>set of convinient debug methods:
 * <li>toString * ()...
 * <li>dump*()
 * <li>print*()
 *
 * <p>...
 *
 * @author Oleg Orlov
 * @since  26 Oct 2015
 */
public class Dt {
    private static final String TAG = Dt.class.getSimpleName();

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /** for {@link #printInWidth(String, int, StringBuilder)} */
    public static final int DIR_LEFT = -1;
    public static final int DIR_CENTER = 0;
    public static final int DIR_RIGHT = 1;

    /** The Constant object of empty <code>String</code>. */
    public final static String EMPTY_STRING_OPT = "";

    /** The Constant object of empty <code>char</code> array. */
    public final static char[] EMPTY_CHAR_OPT = new char[0];

    /** The Constant object of empty <code>int</code> array. */
    public final static int[] EMPTY_INT_OPT = new int[0];

    /** The Constant object of empty <code>float</code> array. */
    public final static float[] EMPTY_FLOAT_OPT = new float[0];

    public final static String[] EMPTY_STRINGS = new String[0];

    public static String bytesToHexChars(byte[] bytes, int maxChar) {
        final int bytesToProcess = bytes.length > maxChar / 2 ? maxChar / 2 : bytes.length;
        return toHexString(bytes, bytesToProcess);
    }

    /** @param count - use bytes.length by default */
    public static String toHexString(byte[] bytes, int count) {
        if (bytes == null) return null;
        if (count > bytes.length) count = bytes.length;

        char[] hexChars = new char[count * 2];
        for (int j = 0; j < count; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static StringBuilder dump(SQLiteDatabase db, String tableName) {
        StringBuilder sb = new StringBuilder();
        toString(db, tableName, sb);
        return sb;
    }

    public static StringBuilder toString(SQLiteDatabase db, String table) {
        return toString(db, table, new StringBuilder());
    }

    public static StringBuilder toString(SQLiteDatabase db, String table, StringBuilder sb) {
        Column[] cols = getColumns(db, table);
        Cursor cur = db.query(table, null, null, null, null, null, null);
        toString(cur, cols, sb);
        cur.close();
        return sb;
    }

    public static StringBuilder toString(Cursor cur) {
        if (cur == null) return new StringBuilder("null cursor");
        StringBuilder sb = new StringBuilder();
        Column[] cols = getColumns(cur);
        toString(cur, cols, sb);
        return sb;
    }

    public static StringBuilder toString(Cursor cur, StringBuilder sb) {
        Column[] cols = getColumns(cur);
        toString(cur, cols, sb);
        return sb;
    }

    private static void toString(Cursor cur, Column[] cols, StringBuilder sb) {
        int savedPos = cur.getPosition();
        cur.moveToPosition(-1);
        for (int rowNum = 0; rowNum == 0 ? cur.moveToFirst() : cur.moveToNext(); rowNum++) {
            for (int iCol = 0; iCol < cur.getColumnCount(); iCol++) {
                Column col = cols[iCol];
                String value = col.getValue(cur, iCol);
                int width = value.length() == 0 ? COL_WIDTH_EMPTY : value.length();
                if (col.width < width)
                    col.width = width < COL_WIDTH_MAX ? width : COL_WIDTH_MAX;
            }
        }

        Dt.printInWidth("" + (cur.getCount() > 0 ? cur.getCount() : "E"), 4, sb, '_', Dt.DIR_CENTER);
        for (Column col : cols) {
            sb.append(" ");
            Dt.printInWidthL(col.name, col.width, sb);
        }

        for (int rowNum = 0; rowNum == 0 ? cur.moveToFirst() : cur.moveToNext(); rowNum++) {
            sb.append("\n");
            Dt.printInWidthL(rowNum + ":", 4, sb);
            for (int iCol = 0; iCol < cur.getColumnCount(); iCol++) {
                Column col = cols[iCol];
                String value = col.getValue(cur, iCol);
                sb.append("|");
                Dt.printInWidth(value, col.width, sb);
            }
        }
        cur.moveToPosition(savedPos);
    }

    private static Column[] getColumns(Cursor cur) {
        Column[] cols = new Column[cur.getColumnCount()];
        int savedPos = cur.getPosition();
        boolean noRows = cur.getCount() == 0;
        if (!noRows) cur.moveToFirst();
        for (int i = 0; i < cur.getColumnCount(); i++) {
            Column col = cols[i] = new Column();
            col.name = noRows ? "NoRows" : cur.getColumnName(i);
            col.type = noRows ? "NoRows" : Column.types[cur.getType(i)];
            col.width = col.name.length();
        }
        cur.moveToPosition(savedPos);
        return cols;
    }

    private static Column[] getColumns(SQLiteDatabase db, String table) {
        Cursor ti = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        Column[] cols = new Column[ti.getCount()];
        for (int i = 0; ti.moveToNext(); i++) {
            Column col = new Column();
            col.name = ti.getString(1);
            col.type = ti.getString(2);
            col.width = col.name.length();
            cols[i] = col;
        }
        ti.close();
        return cols;
    }

    static final int COL_WIDTH_MAX = 36;
    static final int COL_WIDTH_EMPTY = 2;

    /** @see #printStackTrace(StringBuilder, int) */
    public static StringBuilder printStackTrace() {
        return Dt.printStackTrace(null, -1, 5);
    }

    /** @see #printStackTrace(StringBuilder, int) */
    public static StringBuilder printStackTrace(int frames) {
        return Dt.printStackTrace(null, frames, 5);
    }

    /**
     * Prints the stack trace of the current thread.
     *
     * @param frames - number of frames to print. -1 equal to "all frames"
     * @param to     - optional StringBuilder to print output
     * @return the stack trace of the current thread
     */
    public static StringBuilder printStackTrace(StringBuilder to, int frames) {
        StringBuilder result = Dt.printStackTrace(to, frames, 5);
        return result;
    }

    private static StringBuilder printStackTrace(StringBuilder to, int frames, final int startFrame) {
        StringBuilder result = to == null ? new StringBuilder() : to;
        StackTraceElement[] traces = Thread.currentThread().getStackTrace();
        int lastFrame = traces.length - 1;
        if (frames != -1 && startFrame + frames < lastFrame) lastFrame = startFrame + frames;
        for (int i = startFrame; i <= lastFrame; i++) {
            StackTraceElement trace = traces[i];
            result.append(i == startFrame ? "" : " <- ").append(Dt.getSimpleClassName(trace.getClassName())).append(".").append(trace.getMethodName()).append("() ");
        }
        return result;
    }

    public static StringBuilder printInWidth(String what, int widthToFit, StringBuilder to) {
        return printInWidth(what, widthToFit, to, ' ', Dt.DIR_CENTER);
    }

    public static StringBuilder printInWidthL(String what, int widthToFit, StringBuilder to) {
        return printInWidth(what, widthToFit, to, ' ', Dt.DIR_LEFT);
    }

    public static StringBuilder printInWidthR(String what, int widthToFit, StringBuilder to) {
        return printInWidth(what, widthToFit, to, ' ', Dt.DIR_RIGHT);
    }

    public static StringBuilder printInWidth(String what, int widthToFit, StringBuilder to, char placeHolder, int dir) {
        if (to == null) to = new StringBuilder();
        if (what.length() > widthToFit) {
            to.append(what.substring(0, widthToFit));
        } else {
            int offset = (widthToFit - what.length()) / 2; /*if (dir ==DIR_CENTER)*/
            if (dir == Dt.DIR_LEFT) offset = 0;
            if (dir == Dt.DIR_RIGHT) offset = widthToFit - what.length();

            for (int i = 0; i < offset; i++) to.append(placeHolder);
            to.append(what);
            for (int i = offset + what.length(); i < widthToFit; i++) to.append(placeHolder);
        }
        return to;
    }

    /**
     * Converts the array of <code>int</code> to the human readable string.
     *
     * @param items an array of integers
     * @return the converted string
     */
    public static String toString(int[] items) {
        return Arrays.toString(items);
    }

    /**
     * Converts the array of <code>float</code> to the human readable string.
     *
     * @param items an array of integers
     * @return the converted string
     */
    public static String toString(float[] items) {
        return Arrays.toString(items);
    }

    /**
     * Converts the array of <code>int</code> to the human readable string.
     *
     * @param bytes an array of integers
     * @return the converted string
     */
    public static String toString(byte[] bytes) {
        return toHexString(bytes, bytes.length);
    }

    /**
     * Converts the array of <code>Object</code> to the human readable string.
     *
     * @param items an array of <code>Object</code>
     * @return the converted string
     */
    public static String toString(Object[] items) {
        return toString(items, ", ", "");
    }

    public static String toString(Object[] items, String separator) {
        return toString(items, separator, "", false);
    }

    public static String toString(Object[] items, String separator, boolean printIds) {
        return toString(items, separator, "", printIds);
    }

    public static String toString(Object[] items, String separator, String firstSeparator) {
        return toString(items, separator, firstSeparator, false);
    }

    /**
     * Converts the array of <code>Object</code> to the human readable string.
     *
     * @param items an array of <code>Object</code>
     * @param separator - between items (put '\n' to produce line per item)
     * @param firstSeparator - separator in front of first item
     * @param printIds - add index of item in front of items
     *
     * @return the converted string
     */
    public static String toString(Object[] items, String separator, String firstSeparator, boolean printIds) {
        if (items == null) return null;
        return toString(Arrays.asList(items), separator, firstSeparator, printIds);
    }

    public static <T> String toString(T[] items, String separator, String firstSeparator, boolean printIds, Adapter<T> adapter) {
        if (items == null) return null;
        return toString(Arrays.asList(items), separator, firstSeparator, printIds, adapter);
    }

    /**
     * Converts a particular collection (for example, {@link java.util.ArrayList etc.}, {@link java.util.HashSet})
     * to the human readable string.
     *
     * @param items the collection for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.
     * @return the converted string
     */
    public static String toString(Collection<?> items) {
        return toString(items, ", ", "");
    }
    /** @see #toString(Collection, String, String, boolean) */
    public static String toString(Collection<?> items, String separator) {
        return toString(items, separator, "", false);
    }
    /** @see #toString(Collection, String, String, boolean) */
    public static String toString(Collection<?> items, String separator, boolean printIds) {
        return toString(items, separator, "", printIds);
    }
    /** @see #toString(Collection, String, String, boolean) */
    public static String toString(Collection<?> items, String separator, String firstSeparator) {
        return toString(items, separator, firstSeparator, false);
    }

    /**
     * Converts a particular collection (for example, {@link java.util.ArrayList etc.}, {@link java.util.HashSet})
     * to the human readable string.
     *
     * @param items the collection for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.
     * @param separator - between items (put '\n' to produce line per item)
     * @param firstSeparator - separator in front of first item
     * @param printIds - add index of item in front of items
     *
     * @return the converted string
     */
    public static String toString(Collection<?> items, String separator, String firstSeparator, boolean printIds) {
        return toString(items, separator, firstSeparator, printIds, (Adapter)null);
    }

    public static String toString(Collection<?> items, String separator, String firstSeparator, boolean printIds, Adapter adapter) {
        if (items == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        int indexWidth = ("" + items.size()).length();
        for (Iterator<?> itIdx = items.iterator(); itIdx.hasNext(); i++) {
            sb.append(i == 0 ? firstSeparator : separator);
            if (printIds) {
                if (separator.contains("\n")) Dt.printInWidth(i + ":", indexWidth + 2, sb);
                else sb.append(i).append(": ");
            }
            Object item = itIdx.next();
            sb.append(adapter != null ? adapter.toString(item) : item);
        }
        sb.append(firstSeparator).append("]");
        return sb.toString();
    }

    /**
     * @see #toString(Collection, String, String, boolean, Adapter)
     * @param adapter - also is used to retreive items for printing
     */
    public static String toString(Object what, String separator, String firstSeparator, boolean printIds, AdapterOnItem adapter) {
        if (adapter == null || what == null) return "null";
        int items = adapter.items(what);
        StringBuilder sb = new StringBuilder("[");
        int indexWidth = ("" + items).length();
        for (int i = 0; i < items; i++) {
            sb.append(i == 0 ? firstSeparator : separator);
            if (printIds) {
                if (separator.contains("\n")) Dt.printInWidth(i + ":", indexWidth + 2, sb);
                else sb.append(i).append(": ");
            }
            Object item = adapter.item(i, what);
            sb.append(adapter != null ? adapter.toString(item) : item);
        }
        sb.append(firstSeparator).append("]");
        return sb.toString();
    }

    public interface AdapterOnItem extends Adapter {
        public Object item(int at, Object from);
        public int items(Object from);
    }

    public interface Adapter <T> {
        public String toString(T what);
    }

    public static String toString(Map<?, ?> map) {
        return toString(map, ", ", "");
    }

    public static String toString(Map<?, ?> map, String separator) {
        return toString(map, separator, "");
    }

    public static String toString(Map<?, ?> map, String separator, String firstSeparator) {
        if (map == null) return "null";
        return toString(map.entrySet(), separator, firstSeparator);
    }

    public static String toString(Set<? extends Entry<?, ?>> entrySet, String separator, String firstSeparator) {
        if (entrySet == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Iterator itEntry = entrySet.iterator(); itEntry.hasNext(); i++) {
            Entry entry = (Entry) itEntry.next();
            sb.append(i == 0 ? firstSeparator : separator).append(i).append(": ");
            sb.append(entry.getKey()).append(" : ").append(entry.getValue());
        }
        sb.append(firstSeparator).append("]");
        return sb.toString();
    }

    /**
     * Converts a Bundle to the human readable string.
     *
     * @param bundle the collection for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.
     * @return the converted string
     */
    public static String toString(Bundle bundle) {
        return toString(bundle, ", ", "");
    }

    public static String toString(Bundle bundle, String separator, String firstSeparator) {
        if (bundle == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Iterator<String> itKey = bundle.keySet().iterator(); itKey.hasNext(); i++) {
            String key = itKey.next();
            sb.append(i == 0 ? firstSeparator : separator).append(i).append(": ");
            sb.append(key).append(" : ").append(bundle.get(key));
        }
        sb.append(firstSeparator);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Converts a particular collection (for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.)
     * of {@link Float} to the array of <code>float</code>.
     *
     * @param floats the collection (for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.)
     *        of {@link Float}
     * @param defaultValue the default value of an item
     * @return the array of <code>float</code>
     */
    public static float[] toFloatArray(Collection<Float> floats, float defaultValue) {
        if(floats.size() == 0) {
            return EMPTY_FLOAT_OPT;
        }
        float[] result = new float[floats.size()];
        int i = 0;
        for (Iterator<Float> iterator = floats.iterator(); iterator.hasNext(); i++) {
            Float floating = iterator.next();
            result[i] = floating == null ? defaultValue : floating.floatValue();
        }
        return result;
    }

    /**
     * Converts a particular collection (for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.)
     * of {@link Integer} to the array of <code>int</code>.
     *
     * @param integers the collection (for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.)
     *        of {@link Integer}
     * @param defaultValue the default value of an item
     * @return the array of <code>int</code>
     */
    public static int[] toIntArray(Collection<Integer> integers, int defaultValue) {
        if(integers.size() == 0) {
            return EMPTY_INT_OPT;
        }
        int[] result = new int[integers.size()];
        int i = 0;
        for (Iterator<Integer> iterator = integers.iterator(); iterator.hasNext(); i++) {
            Integer integer = iterator.next();
            result[i] = integer == null ? defaultValue : integer.intValue();
        }
        return result;
    }

    public static String toString(InputStream is) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the simple name of the class. It does not include any other information such as extension of
     * class file, the inner class name (that is separated by the '$' sign) etc.
     *
     * @param className the class name
     * @return the simple name of the class
     */
    public static String getSimpleClassName(String className) {
        String result = className;
        int lastIndex = -1;
        if ((lastIndex = Math.max(className.lastIndexOf("."), className.lastIndexOf("$"))) != -1)
            result = className.substring(lastIndex + 1);
        return result;
    }

    public static URL url(String from) {
        try {
            return new URL(from);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Wrong URL format ", e);
        }
    }

    /** info about colum in tables */
    static class Column {
        /** FIELD_TYPE_NULL = 0;<li>FIELD_TYPE_INTEGER = 1;<li>FIELD_TYPE_FLOAT = 2;<li>FIELD_TYPE_STRING = 3;<li>FIELD_TYPE_BLOB = 4;*/
        static String[] types = new String[]{"NULL", "INTEGER", "FLOAT", "TEXT", "BLOB"};

        String name;
        String type;
        int width;

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Column [name: ").append(name).append(", type: ").append(type).append(", width: ").append(width).append("]");
            return builder.toString();
        }

        String getValue(Cursor cur, int iCol) {
            String value = "";
            if      ("INTEGER".equals(type)) value = String.valueOf(cur.getInt(iCol));
            else if ("FLOAT".equals(type)) value = String.valueOf(cur.getFloat(iCol));
            else if ("BLOB".equals(type))
                value = cur.getBlob(iCol) == null ? "" : bytesToHexChars(cur.getBlob(iCol), COL_WIDTH_MAX);
            else /*if ("TEXT".equals(type))*/ value = cur.getString(iCol);
            return value == null ? "" : value;
        }
    }

    public static String[] getTableNames(SQLiteDatabase db) {
        ArrayList<String> tableNames = new ArrayList<String>();
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        for (int i = 0; c.moveToNext(); i++) {
            tableNames.add(c.getString(c.getColumnIndex("name")));
        }
        c.close();
        return (String[]) tableNames.toArray(Dt.EMPTY_STRINGS);
    }

    static class Table {
        String name;
        int rows;

        public final static Comparator<Table> TOP_ROWS = new Comparator<Table>() {
            public int compare(Table lhs, Table rhs) {
                final int result = rhs.rows - lhs.rows;
                if (result == 0) return TABLE_NAME.compare(lhs, rhs);
                return result;
            }
        };
        public final static Comparator<Table> TABLE_NAME = new Comparator<Table>() {
            public int compare(Table lhs, Table rhs) {
                return String.CASE_INSENSITIVE_ORDER.compare(lhs.name, rhs.name);
            }
        };
        public final static Table[] EMPTY = new Table[0];

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[tableName: ").append(name).append(", rows: ").append(rows).append("]");
            return builder.toString();
        }
    }

    public static String dumpNonEmptyNames(SQLiteDatabase fromDb) {
        final Table[] tables = tables(fromDb);
        Arrays.sort(tables, Table.TOP_ROWS);
        return toString(filterNotEmpty(tables), "\n", "\n", true);
    }

    public static void dumpNonEmpty(SQLiteDatabase fromDb) {
        Table[] tables = tables(fromDb);
        ArrayList<Table> filtered = filterNotEmpty(tables);
        if (filtered.size() == 0) {
            Log.w(TAG, "" + Dt.printInWidth("< tables are empty >", 90, null, '.', Dt.DIR_CENTER));
            return;
        }
        Collections.sort(filtered, Table.TABLE_NAME);
        StringBuilder sb = new StringBuilder();
        for (Iterator<Table> iterator = filtered.iterator(); iterator.hasNext(); ) {
            Table table = iterator.next();
            Dt.printInWidth(": " + table.name + " :", 90, sb, '.', Dt.DIR_CENTER);
            sb.append("\n");
            toString(fromDb, table.name, sb);
            Log.w(TAG, sb.toString());
            sb.setLength(0);
        }
    }

    public static ArrayList<Table> filterNotEmpty(SQLiteDatabase fromDb) {
        return filterNotEmpty(tables(fromDb));
    }

    public static ArrayList<Table> filterNotEmpty(Table[] tables) {
        ArrayList<Table> filtered = new ArrayList<Table>();
        for (int i = 0; i < tables.length; i++) {
            if (tables[i].rows == 0) continue;
            if (tables[i].name.contains("sqlite_")) continue;
            if (tables[i].name.contains("android_metadata")) continue;
            if (tables[i].name.contains("schema_migration")) continue;
            filtered.add(tables[i]);
        }
        return filtered;
    }

    public static Table[] tables(SQLiteDatabase fromDb) {
        String[] tableNames = getTableNames(fromDb);
        Table[] tables = new Table[tableNames.length];
        for (int iTab = 0; iTab < tableNames.length; iTab++) {
            Cursor c = fromDb.rawQuery("select COUNT(*) from " + tableNames[iTab], null);
            c.moveToFirst();
            int rows = c.getInt(0);
            Table table = new Table();
            table.name = tableNames[iTab];
            table.rows = rows;
            tables[iTab] = table;
            c.close();
        }
        Arrays.sort(tables, Table.TOP_ROWS);
        return tables;
    }

    /**
     * Encodes data to Base64 format using {@link Base64#encode64} table
     *
     * @see {@link Base64#encode64(byte[], char[])} for more options
     */
    public static String encode64(byte[] data) {
        return Base64.encode64(data, Base64.encode64);
    }

    /**
     * Decodes data from Base64 encoded with {@link Base64#encode64} table
     *
     * @see {@link Base64#decode64(String, byte[])} for more options
     */
    public static byte[] decode64(String from) {
        return Base64.decode64(from, Base64.decode64);
    }

    public static String encode64Url(byte[] data) {
        return Base64.encode64(data, Base64.encode64Url);
    }

    public static byte[] decode64url(String from) {
        return Base64.decode64(from, Base64.decode64Url);
    }

    public static byte[] trimLeadingZeros(byte[] from) {
        for (int i = 0; i < from.length; i++) {
            if (from[i] == 0) continue;
            return Arrays.copyOfRange(from, i, from.length);
        }
        return new byte[0];
    }

    public static class Log {
        private static final String TAG = "Dt";

        public static final int VERBOSE = android.util.Log.VERBOSE;
        public static final int DEBUG = android.util.Log.DEBUG;
        public static final int WARN = android.util.Log.WARN;
        public static final int INFO = android.util.Log.INFO;
        public static final int ERROR = android.util.Log.ERROR;

        private static LogBack log;

        static {
            try {
                log = new LogBackAndroid();
                log.d("Dt.Log", "Found Android/LogCat. Using LogBackAndroid");
            } catch (Throwable e){
                log = new LogBackConsole();
            }
        }

        public static class Tag {
            private final String tagName;
            private String componentName;
            public final boolean d;

            private Tag(String tagName, String component, boolean debug) {
                this.tagName = tagName;
                this.componentName = component;
                this.d = debug;
            }

            public boolean d() {
                return d;
            }
        }

        public static Tag tag(String tagName) {
            return new Tag(tagName, null, false);
        }

        public static Tag tag(Class clazz) {
            return new Tag(clazz.getSimpleName(), null, false);
        }

        public static Tag tag(Class clazz, boolean debug) {
            return new Tag(clazz.getSimpleName(), null, debug);
        }

        public static Tag tag(Class clazz, String componentName, boolean debug) {
            return new Tag(clazz.getSimpleName(), componentName, debug);
        }

        public static void v(Object tag, String what) {
            if (tag.getClass() == Tag.class) {
                log.v(((Tag)tag).tagName, what(what));
            } else if (tag.getClass() == String.class) {
                log.v((String) tag, what(what));
            }
        }

        public static void v(Object tag, String what, Throwable e) {
            if (tag.getClass() == Tag.class) {
                log.v(((Tag)tag).tagName, what(what), e);
            } else if (tag.getClass() == String.class) {
                log.v((String) tag, what(what), e);
            }
        }

        public static void d(Object tag, String what, Throwable e) {
            if (tag.getClass() == Tag.class) {
                log.d(((Tag)tag).tagName, what(what), e);
            } else if (tag.getClass() == String.class) {
                log.d((String) tag, what(what), e);
            }
        }

        public static void d(Object tag, String what) {
            if (tag.getClass() == Tag.class) {
                log.d(((Tag)tag).tagName, what(what));
            } else if (tag.getClass() == String.class) {
                log.d((String) tag, what(what));
            }
        }

        public static void i(Object tag, String what) {
            if (tag.getClass() == Tag.class) {
                log.i(((Tag)tag).tagName, what(what));
            } else if (tag.getClass() == String.class) {
                log.i((String) tag, what(what));
            }
        }

        public static void w(Object tag, String what) {
            if (tag.getClass() == Tag.class) {
                log.w(((Tag)tag).tagName, what(what));
            } else if (tag.getClass() == String.class) {
                log.w((String) tag, what(what));
            }
        }

        public static void w(Object tag, String what, Throwable e) {
            if (tag.getClass() == Tag.class) {
                log.w(((Tag)tag).tagName, what(what), e);
            } else if (tag.getClass() == String.class) {
                log.w((String) tag, what(what), e);
            }
        }

        public static void e(Object tag, String what) {
            if (tag.getClass() == Tag.class) {
                log.e(((Tag)tag).tagName, what(what));
            } else if (tag.getClass() == String.class) {
                log.e((String) tag, what(what));
            }
        }

        public static void e(Object tag, String what, Throwable e) {
            if (tag.getClass() == Tag.class) {
                log.e(((Tag)tag).tagName, what(what), e);
            } else if (tag.getClass() == String.class) {
                log.e((String) tag, what(what), e);
            }
        }


        private static ThreadLocal<StringBuilder> localSb = new ThreadLocal<StringBuilder>();
        public static String what(String what) {

            if (false) return what;

            StringBuilder sb = localSb.get();
            if (sb == null) localSb.set(sb = new StringBuilder(1000));// = new StringBuilder(what.length() + 42 + Thread.currentThread().getName().length() + 2);
            sb.setLength(0);

            sb.append(what);

            long totalMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();

            sb.append(" [");
            sb.append("T:").append((totalMemory / 1024));
            sb.append("|F:").append((freeMemory / 1024));
            sb.append("|X:").append((maxMemory / 1024));
            sb.append("|U:").append(((totalMemory - freeMemory) / 1024));

            sb.append("]");
            sb.append(" [").append(Thread.currentThread().getName()).append("]");
            return sb.toString();
        }

        public static abstract class LogBack {
            public abstract void v(String tag, String what);
            public abstract void v(String tag, String what, Throwable e);
            public abstract void d(String tag, String what);
            public abstract void d(String tag, String what, Throwable e);
            public abstract void i(String tag, String what);
            public abstract void i(String tag, String what, Throwable e);
            public abstract void w(String tag, String what);
            public abstract void w(String tag, String what, Throwable e);
            public abstract void e(String tag, String what);
            public abstract void e(String tag, String what, Throwable e);
        }

        public static class LogBackAndroid extends LogBack {
            private static final boolean ENABLED = true;/*BuildConfig.DEBUG*/;

            /**
             #define LOGGER_ENTRY_MAX_LEN        (4*1024)
             #define LOGGER_ENTRY_MAX_PAYLOAD (LOGGER_ENTRY_MAX_LEN - sizeof(struct logger_entry))                            int to = from + 2048;
             */
            private static void log(int priority, String TAG, String what, Throwable e) {
                if (e != null) {
                    what = what + "\n" + android.util.Log.getStackTraceString(e);
                }

                for (int from = 0; from < what.length(); ) {
                    int to = from + 3072;
                    if (to > what.length()) to = what.length();
                    String sub = what.substring(from, to);
                    android.util.Log.println(priority, TAG, sub);
                    from = to;
                }
            }

            public void v(String tag, String what) {
                if (ENABLED) log(android.util.Log.VERBOSE, tag, what, null);
            }

            public void v(String tag, String what, Throwable e) {
                if (ENABLED) log(android.util.Log.VERBOSE, tag, what, e);
            }

            public void d(String tag, String what) {
                if (ENABLED) log(android.util.Log.DEBUG, tag, what, null);
            }

            public void d(String tag, String what, Throwable e) {
                if (ENABLED) log(android.util.Log.DEBUG, tag, what, e);
            }

            public void i(String tag, String what) {
                if (ENABLED) log(android.util.Log.INFO, tag, what, null);
            }
            
            public void i(String tag, String what, Throwable e) {
                if (ENABLED) log(android.util.Log.INFO, tag, what, e);
            }

            public void w(String tag, String what) {
                if (ENABLED) log(android.util.Log.WARN, tag, what, null);
            }
            
            public void w(String tag, String what, Throwable e) {
                if (ENABLED) log(android.util.Log.WARN, tag, what, e);
            }
            
            public void e(String tag, String what) {
                if (ENABLED) log(android.util.Log.ERROR, tag, what, null);
            }
            
            public void e(String tag, String what, Throwable e) {
                if (ENABLED) log(android.util.Log.ERROR, tag, what, e);
            }

        }

        public static class LogBackConsole extends LogBack {
            private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

            public void v(String tag, String what, Throwable e) {
                System.out.println(what("V", tag, what));
                e.printStackTrace(System.err);
            }

            public void v(String tag, String what) {
                System.out.println(what("V", tag, what));
            }
            
            public void d(String tag, String what) {
                System.out.println(what("D", tag, what));
            }
            
            public void d(String tag, String what, Throwable e) {
                System.out.println(what("D", tag, what));
                e.printStackTrace(System.out);
            }
            
            public void i(String tag, String what) {
                System.out.println(what("I", tag, what));
            }
            
            public void i(String tag, String what, Throwable e) {
                System.out.println(what("I", tag, what));
                e.printStackTrace(System.out);
            }
            
            public void w(String tag, String what) {
                System.out.println(what("W", tag, what));
            }
            
            public void w(String tag, String what, Throwable e) {
                System.out.println(what("W", tag, what));
                e.printStackTrace(System.out);
            }
            
            public void e(String tag, String what) {
                System.err.println(what("E", tag, what));
            }
            
            public void e(String tag, String what, Throwable e) {
                System.err.println(what("E", tag, what));
                e.printStackTrace(System.err);
            }

            private static ThreadLocal<StringBuilder>  localSb  = new ThreadLocal<StringBuilder>();
            private static ThreadLocal<Date>           localNow = new ThreadLocal<Date>();

            public static String what(String level, String tag, String what) {
                StringBuilder sb = localSb.get();
                if (sb == null) localSb.set(sb = new StringBuilder(1000));// = new StringBuilder(what.length() + 42 + Thread.currentThread().getName().length() + 2);
                sb.setLength(0);

                Date now = localNow.get();
                if (now == null) localNow.set(now = new Date());
                now.setTime(System.currentTimeMillis());

                sb.append(sdf.format(now));
                sb.append(" (").append(level);
                sb.append("/").append(tag);
                sb.append("): ").append(what);
                return sb.toString();
            }
        }

    }

    public static class Base64 {

        /**
         * @param data
         * @param encodeTable
         */
        public static String encode64(byte[] data, char[] encodeTable) {
            int aLen = data.length;
            int numFullGroups = aLen / 3;
            int numBytesInPartialGroup = aLen - 3 * numFullGroups;
            int resultLen = 4 * ((aLen + 2) / 3);
            StringBuffer result = new StringBuffer(resultLen);

            // Translate all full groups from byte array elements to Base64
            int inCursor = 0;
            for (int i = 0; i < numFullGroups; i++) {
                int byte0 = data[inCursor++] & 0xff;
                int byte1 = data[inCursor++] & 0xff;
                int byte2 = data[inCursor++] & 0xff;
                result.append(encodeTable[byte0 >> 2]);
                result.append(encodeTable[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
                result.append(encodeTable[(byte1 << 2) & 0x3f | (byte2 >> 6)]);
                result.append(encodeTable[byte2 & 0x3f]);
            }

            // Translate partial group if present
            if (numBytesInPartialGroup != 0) {
                int byte0 = data[inCursor++] & 0xff;
                result.append(encodeTable[byte0 >> 2]);
                if (numBytesInPartialGroup == 1) {
                    result.append(encodeTable[(byte0 << 4) & 0x3f]);
                    result.append("==");
                } else {
                    // assert numBytesInPartialGroup == 2;
                    int byte1 = data[inCursor++] & 0xff;
                    result.append(encodeTable[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
                    result.append(encodeTable[(byte1 << 2) & 0x3f]);
                    result.append('=');
                }
            }
            return result.toString();
        }

        public static byte[] decode64(String s, byte[] decodeTable) {
            int sLen = s.length();
            int numGroups = sLen / 4;
            if (4 * numGroups != sLen) throw new IllegalArgumentException("String length must be a multiple of four.");
            int missingBytesInLastGroup = 0;
            int numFullGroups = numGroups;
            if (sLen != 0) {
                if (s.charAt(sLen - 1) == '=') {
                    missingBytesInLastGroup++;
                    numFullGroups--;
                }
                if (s.charAt(sLen - 2) == '=') missingBytesInLastGroup++;
            }
            byte[] result = new byte[3 * numGroups - missingBytesInLastGroup];

            // Translate all full groups from base64 to byte array elements
            int inCursor = 0,outCursor = 0;
            for (int i = 0; i < numFullGroups; i++) {
                int ch0 = Base64.decode(s.charAt(inCursor++), decodeTable);
                int ch1 = Base64.decode(s.charAt(inCursor++), decodeTable);
                int ch2 = Base64.decode(s.charAt(inCursor++), decodeTable);
                int ch3 = Base64.decode(s.charAt(inCursor++), decodeTable);
                result[outCursor++] = (byte) ((ch0 << 2) | (ch1 >> 4));
                result[outCursor++] = (byte) ((ch1 << 4) | (ch2 >> 2));
                result[outCursor++] = (byte) ((ch2 << 6) | ch3);
            }

            // Translate partial group, if present
            if (missingBytesInLastGroup != 0) {
                int ch0 = Base64.decode(s.charAt(inCursor++), decodeTable);
                int ch1 = Base64.decode(s.charAt(inCursor++), decodeTable);
                result[outCursor++] = (byte) ((ch0 << 2) | (ch1 >> 4));

                if (missingBytesInLastGroup == 1) {
                    int ch2 = Base64.decode(s.charAt(inCursor++), decodeTable);
                    result[outCursor++] = (byte) ((ch1 << 4) | (ch2 >> 2));
                }
            }
            return result;
        }

        private static int decode(char c, byte[] decodeTable) {
            if (decodeTable[c] == -1)   throw new IllegalArgumentException("Illegal character " + c);
            if (c > decodeTable.length) throw new IllegalArgumentException("Illegal character: " + c + " [" + ((int)c)+ "]");
            return decodeTable[c];
        }

        /**
         * This array is a lookup table that translates 6-bit positive integer
         * index values into their "Base64 Alphabet" equivalents as specified
         * in Table 1 of RFC 2045.
         */
        public static final char encode64[] = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
        };

        /**
         * This is a copy of the STANDARD_ENCODE_TABLE above, but with + and /
         * changed to - and _ to make the encoded Base64 results more URL-SAFE.
         * This table is only used when the Base64's mode is set to URL-SAFE.
         */
        public static final char[] encode64Url = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
        };

        /**
         * This array is a lookup table that translates 6-bit positive integer
         * index values into their "Alternate Base64 Alphabet" equivalents.
         * This is NOT the real Base64 Alphabet as per in Table 1 of RFC 2045.
         * This alternate alphabet does not use the capital letters.  It is
         * designed for use in environments where "case folding" occurs.
         */
        public static final char encode64Alt[] = {
            '!', '"', '#', '$', '%', '&', '\'', '(', ')', ',', '-', '.', ':',
            ';', '<', '>', '@', '[', ']', '^',  '`', '_', '{', '|', '}', '~',
            'a', 'b', 'c', 'd', 'e', 'f', 'g',  'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't',  'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6',  '7', '8', '9', '+', '?'
        };

        /**
         * Decodes both {@link #encode64} and {@link #encode64Url} alphabets<p>
         *
         * This array is a lookup table that translates unicode characters
         * drawn from the "Base64 Alphabet" (as specified in Table 1 of RFC 2045)
         * into their 6-bit positive integer equivalents.  Characters that
         * are not in the Base64 alphabet but fall within the bounds of the
         * array are translated to -1.
         */
        private static final byte[] decode64Universal = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63, 52, 53, 54,
            55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1,  0,  1,  2,
            3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
            48, 49, 50, 51
        };
        public static final byte[] decode64    = decode64Universal;
        public static final byte[] decode64Url = decode64Universal;

        /**
         * Decodes {@link #encode64Alt} alphabet
         */
        public static final byte[] decode64Alt = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,
            1,  2,  3,  4,  5,  6,  7,  8, -1, 62,  9, 10, 11, -1, 52, 53, 54,
            55, 56, 57, 58, 59, 60, 61, 12, 13, 14, -1, 15, 63, 16, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, 17, -1, 18, 19, 21, 20, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
            48, 49, 50, 51, 22, 23, 24, 25
        };

    }

}
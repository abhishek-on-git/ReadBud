package com.wcp.readassist.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.wcp.readassist.utils.ReadAssistUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class DictionaryDBHelper extends SQLiteOpenHelper {

    Context mContext;
    private String mDBPath = null;
    private static SQLiteDatabase mDatabase;

    private static final String TAG = "ReadAssistDBHelper";


    public DictionaryDBHelper (Context context) {
        super(context, ReadAssistUtils.DB_NAME, null, 1);
        mContext = context;
        mDBPath = ReadAssistUtils.ROOT_DIR + "/databases/";
    }

    public void createDB() {
        //TODO Check this api's documentation. Should be called from background thread.
        this.getReadableDatabase();
        ReadAssistUtils.copyDictionaryDB(mContext);
    }

    public boolean dbExists() {
        SQLiteDatabase db = null;
        try {
            File dbLocation = mContext.getExternalFilesDir(ReadAssistUtils.DB_ROOT);
            if(!dbLocation.exists()) {
                dbLocation.mkdirs();
            }
            String path = dbLocation.toString() + "/" + ReadAssistUtils.DB_NAME;
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);

            if (db != null) {
                db.close();
            }
        } catch (Exception e) {
            Log.e(ReadAssistUtils.TAG, "DB exists exception : "+e);
            return false;
        }
        return db != null ? true : false;
    }

    public void openDB() {
        File dbLocation = mContext.getExternalFilesDir(ReadAssistUtils.DB_ROOT);
        if(!dbLocation.exists()) {
            dbLocation.mkdirs();
        }
        String path = dbLocation.toString() + "/" + ReadAssistUtils.DB_NAME;
        mDatabase = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE);
    }

    public static Cursor getWordMeaning(String word) {
        word = word.toUpperCase();
        Cursor c = mDatabase.rawQuery("SELECT wordtype,definition FROM entries WHERE word==UPPER('"+word+"')", null);
        return c;
    }

    public static Cursor getSuggestions(String query) {
        Cursor c = mDatabase.rawQuery("SELECT word, rowid _id FROM entries WHERE word LIKE '"+query+"%' GROUP BY word LIMIT 5", null);
        return c;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        this.getReadableDatabase();
        mContext.deleteDatabase(ReadAssistUtils.DB_NAME);
        ReadAssistUtils.copyDictionaryDB(mContext);
    }

    @Override
    public synchronized void close() {
        super.close();
        if(mDatabase != null) {
            mDatabase.close();
        }
    }
}

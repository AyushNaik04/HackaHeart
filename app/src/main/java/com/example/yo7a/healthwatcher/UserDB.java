package com.example.yo7a.healthwatcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

// User model class
class user {
    private String username;
    private String name;
    private String password;
    private String email;
    private int age;
    private int height;
    private int weight;
    private int gender;

    // Vital signs
    private int HR = 0;
    private int RR = 0;
    private int SP = 0;
    private int DP = 0;
    private int SpO2 = 0;

    // Getters
    public String getUsername() { return username; }
    public String getemail() { return email; }
    public String getname() { return name; }
    public String getPass() { return password; }
    public int getage() { return age; }
    public int getheight() { return height; }
    public int getweight() { return weight; }
    public int getgender() { return gender; }

    public int getHR() { return HR; }
    public int getRR() { return RR; }
    public int getSP() { return SP; }
    public int getDP() { return DP; }
    public int getSpO2() { return SpO2; }

    // Setters
    public void setUsername(String usrname) { username = usrname; }
    public void setemail(String E) { email = E; }
    public void setname(String nam) { name = nam; }
    public void setPass(String pass) { password = pass; }
    public void setage(int g) { age = g; }
    public void setheight(int h) { height = h; }
    public void setweight(int w) { weight = w; }
    public void setgender(int gen) { gender = gen; }

    public void setHR(int hr) { HR = hr; }
    public void setRR(int rr) { RR = rr; }
    public void setSP(int sp) { SP = sp; }
    public void setDP(int dp) { DP = dp; }
    public void setSpO2(int spo2) { SpO2 = spo2; }
}

public class UserDB extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "userDB.db";

    // Table and column names
    private static final String TABLE = "users";
    private static final String USERNAME = "username";
    private static final String NAME = "name";
    private static final String PASSWORD = "password";
    private static final String AGE = "age";
    private static final String HEIGHT = "height";
    private static final String WEIGHT = "weight";
    private static final String GENDER = "gender";
    private static final String EMAIL = "email";

    // Vital signs columns
    private static final String HR = "HR";
    private static final String RR = "RR";
    private static final String SP = "SP";
    private static final String DP = "DP";
    private static final String SpO2 = "SpO2";

    SQLiteDatabase db;

    public UserDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        getWritableDatabase(); // Ensures table is created early
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE + " ("
                + USERNAME + " TEXT PRIMARY KEY NOT NULL, "
                + NAME + " TEXT NOT NULL, "
                + PASSWORD + " TEXT NOT NULL, "
                + EMAIL + " TEXT NOT NULL, "
                + AGE + " INTEGER NOT NULL, "
                + HEIGHT + " INTEGER NOT NULL, "
                + WEIGHT + " INTEGER NOT NULL, "
                + GENDER + " INTEGER NOT NULL, "
                + HR + " INTEGER DEFAULT 0, "
                + RR + " INTEGER DEFAULT 0, "
                + SP + " INTEGER DEFAULT 0, "
                + DP + " INTEGER DEFAULT 0, "
                + SpO2 + " INTEGER DEFAULT 0"
                + ");";
        db.execSQL(createTable);
        this.db = db;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // Add a new user (initial vitals default to 0)
    public void addUser(user u) {
        db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(USERNAME, u.getUsername());
        values.put(NAME, u.getname());
        values.put(PASSWORD, u.getPass());
        values.put(EMAIL, u.getemail());
        values.put(AGE, u.getage());
        values.put(HEIGHT, u.getheight());
        values.put(WEIGHT, u.getweight());
        values.put(GENDER, u.getgender());
        values.put(HR, u.getHR());
        values.put(RR, u.getRR());
        values.put(SP, u.getSP());
        values.put(DP, u.getDP());
        values.put(SpO2, u.getSpO2());

        db.insert(TABLE, null, values);
        db.close();
    }

    // Update a specific column for a user
    public void updateField(String username, String column, int value) {
        db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(column, value);
        db.update(TABLE, cv, "username = ?", new String[]{username});
        db.close();
    }

    // Check if username exists (1 = available, 0 = exists)
    public int checkUser(String user) {
        db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + USERNAME + " FROM " + TABLE + " WHERE " + USERNAME + " = ?", new String[]{user});
        int result = cursor.moveToFirst() ? 0 : 1;
        cursor.close();
        return result;
    }

    // Get password for a user
    public String checkPass(String user) {
        db = this.getReadableDatabase();
        String password = "Not found";
        Cursor cursor = db.rawQuery("SELECT " + PASSWORD + " FROM " + TABLE + " WHERE " + USERNAME + " = ?", new String[]{user});
        if (cursor.moveToFirst()) {
            password = cursor.getString(0);
        }
        cursor.close();
        return password;
    }

    // Get any integer field for a user
    public int getIntField(String user, String column, int defaultValue) {
        db = this.getReadableDatabase();
        int value = defaultValue;
        Cursor cursor = db.rawQuery("SELECT " + column + " FROM " + TABLE + " WHERE " + USERNAME + " = ?", new String[]{user});
        if (cursor.moveToFirst()) {
            value = cursor.getInt(0);
        }
        cursor.close();
        return value;
    }

    // Getters for vitals
    public int getHR(String user) { return getIntField(user, HR, 0); }
    public int getRR(String user) { return getIntField(user, RR, 0); }
    public int getSP(String user) { return getIntField(user, SP, 0); }
    public int getDP(String user) { return getIntField(user, DP, 0); }
    public int getSpO2(String user) { return getIntField(user, SpO2, 0); }

    // Existing getters
    public int getweight(String user) { return getIntField(user, WEIGHT, 80); }
    public int getheight(String user) { return getIntField(user, HEIGHT, 180); }
    public int getage(String user) { return getIntField(user, AGE, 26); }
    public int getgender(String user) { return getIntField(user, GENDER, 2); }
}


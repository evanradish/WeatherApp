/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.radish.evan.weatherapp.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class WeatherProvider extends ContentProvider {

    static final int WEATHER = 100;
    static final int WEATHER_WITH_LOCATION = 101;
    static final int WEATHER_WITH_LOCATION_AND_DATE = 102;
    static final int LOCATION = 300;
    static final int LOCATION_ID = 301;

    private static final UriMatcher uriMatcher = buildUriMatcher();
    private WeatherDbHelper weatherDbHelper;

    private static final SQLiteQueryBuilder weatherByLocationSettingQueryBuilder;

    static {
        weatherByLocationSettingQueryBuilder = new SQLiteQueryBuilder();
        weatherByLocationSettingQueryBuilder.setTables(
                WeatherContract.WeatherEntry.TABLE_NAME + " INNER JOIN " +
                        WeatherContract.LocationEntry.TABLE_NAME +
                        " ON " + WeatherContract.WeatherEntry.TABLE_NAME +
                        "." + WeatherContract.WeatherEntry.COLUMN_LOC_KEY +
                        " = " + WeatherContract.LocationEntry.TABLE_NAME +
                        "." + WeatherContract.LocationEntry._ID);
    }

    private static final String locationSettingSelection =
            WeatherContract.LocationEntry.TABLE_NAME +
                    "." + WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? ";

    private static final String locationSettingWithStartDateSelection =
            locationSettingSelection + " AND " + WeatherContract.WeatherEntry.COLUMN_DATE + " >= ?";

    private static final String locationSettingWithDaySelection =
            locationSettingSelection + " AND " + WeatherContract.WeatherEntry.COLUMN_DATE + " = ?";

    private Cursor getWeatherByLocationSetting(Uri uri, String[] projection, String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        String startDate = WeatherContract.WeatherEntry.getStartDateFromUri(uri);

        String[] selectionArgs;
        String selection;

        if (startDate == null) {
            selection = locationSettingSelection;
            selectionArgs = new String[]{locationSetting};
        } else {
            selection = locationSettingWithStartDateSelection;
            selectionArgs = new String[]{locationSetting, startDate};
        }

        return weatherByLocationSettingQueryBuilder.query(weatherDbHelper.getReadableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
    }

    private Cursor getWeatherByLocationAndDay(Uri uri, String[] projection, String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        String day = WeatherContract.WeatherEntry.getDateFromUri(uri);

        return weatherByLocationSettingQueryBuilder.query(weatherDbHelper.getReadableDatabase(),
                projection,
                locationSettingWithDaySelection,
                new String[]{locationSetting, day},
                null,
                null,
                sortOrder
        );
    }

    public static UriMatcher buildUriMatcher() {

        final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = WeatherContract.CONTENT_AUTHORITY;

        uriMatcher.addURI(authority, WeatherContract.PATH_WEATHER, WEATHER);
        uriMatcher.addURI(authority, WeatherContract.PATH_WEATHER  + "/*", WEATHER_WITH_LOCATION);
        uriMatcher.addURI(authority, WeatherContract.PATH_WEATHER + "/*/#", WEATHER_WITH_LOCATION_AND_DATE);

        uriMatcher.addURI(authority, WeatherContract.PATH_LOCATION, LOCATION);
        uriMatcher.addURI(authority, WeatherContract.PATH_LOCATION + "/#", LOCATION_ID);

        return uriMatcher;
    }

    @Override
    public boolean onCreate() {
        weatherDbHelper = new WeatherDbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the database accordingly.
        Cursor retCursor;
        switch (uriMatcher.match(uri)) {
            // "weather/*/*"
            case WEATHER_WITH_LOCATION_AND_DATE:
            {
                retCursor = getWeatherByLocationAndDay(uri, projection, sortOrder);
                break;
            }
            // "weather/*"
            case WEATHER_WITH_LOCATION:
            {
                retCursor = getWeatherByLocationSetting(uri, projection, sortOrder);
                break;
            }
            // "weather"
            case WEATHER: {
                retCursor = weatherDbHelper.getReadableDatabase().query(
                        WeatherContract.WeatherEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            // "location/*"
            case LOCATION_ID: {
                long locationId = ContentUris.parseId(uri);

                String modifiedSelection = WeatherContract.LocationEntry._ID + " = ?";

                retCursor = weatherDbHelper.getReadableDatabase().query(
                        WeatherContract.LocationEntry.TABLE_NAME,
                        projection,
                        modifiedSelection,
                        new String[] { Long.toString(locationId) },
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            // "location"
            case LOCATION: {
                retCursor = weatherDbHelper.getReadableDatabase().query(
                        WeatherContract.LocationEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        retCursor.setNotificationUri(getContext().getContentResolver(), uri);

        return retCursor;
    }

    @Override
    public String getType(Uri uri) {
        final int match = uriMatcher.match(uri);

        switch (match) {
            case WEATHER_WITH_LOCATION_AND_DATE:
                return WeatherContract.WeatherEntry.CONTENT_ITEM_TYPE;

            case WEATHER_WITH_LOCATION:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;
            case WEATHER:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;

            case LOCATION:
                return WeatherContract.LocationEntry.CONTENT_TYPE;

            case LOCATION_ID:
                return WeatherContract.LocationEntry.CONTENT_ITEM_TYPE;

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        final int match = uriMatcher.match(uri);
        Uri returnUri = null;
        SQLiteDatabase db = weatherDbHelper.getWritableDatabase();

        switch (match) {
            case WEATHER: {
                long id = db.insert(WeatherContract.WeatherEntry.TABLE_NAME, null, contentValues);
                if (id > 0) {
                    returnUri = WeatherContract.WeatherEntry.buildWeatherUri(id);
                } else {
                    throw new SQLException("Failed to insert row into " + uri);
                }
                break;
            }

            case LOCATION: {
                long id = db.insert(WeatherContract.LocationEntry.TABLE_NAME, null, contentValues);
                if (id > 0) {
                    returnUri = WeatherContract.LocationEntry.buildLocationUri(id);
                } else {
                    throw new SQLException("Failed to insert row into " + uri);
                }
                break;
            }

            default:
                throw new UnsupportedOperationException("Unknown uri " + uri);
        }

        // notify any registered observers of this change
        getContext().getContentResolver().notifyChange(uri, null);

        return returnUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        final String tableName = getTableName(uri);

        // do the actual deletion
        int affectedRows = weatherDbHelper.getWritableDatabase()
                .delete(tableName, selection, selectionArgs);

        // notify any registered observers of this change
        if (selection == null || affectedRows > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }


        return affectedRows;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String selection, String[] selectionArgs) {

        final String tableName = getTableName(uri);

        // do the actual update
        int affectedRows = weatherDbHelper.getWritableDatabase()
                .update(tableName, contentValues, selection, selectionArgs);

        // notify any registered observers of this change
        if (affectedRows > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return affectedRows;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SQLiteDatabase db = weatherDbHelper.getWritableDatabase();

        final int match = uriMatcher.match(uri);

        switch (match) {
            case WEATHER:

                db.beginTransaction();
                int returnCount = 0;
                try {
                    for (ContentValues value : values) {
                        long id = db.insert(WeatherContract.WeatherEntry.TABLE_NAME, null, value);
                        if (-1 != id) {
                            ++returnCount;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                getContext().getContentResolver().notifyChange(uri, null);

                return returnCount;

            default:
                return super.bulkInsert(uri, values);
        }
    }

    private String getTableName(Uri uri) {
        final int match = uriMatcher.match(uri);
        final String tableName;

        switch (match) {
            case WEATHER: {
                tableName = WeatherContract.WeatherEntry.TABLE_NAME;
                break;
            }

            case LOCATION: {
                tableName = WeatherContract.LocationEntry.TABLE_NAME;
                break;
            }

            default:
                throw new UnsupportedOperationException("Unknown uri " + uri);
        }
        return tableName;
    }
}
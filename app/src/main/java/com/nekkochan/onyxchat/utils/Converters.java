package com.nekkochan.onyxchat.utils;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Type converters for Room database.
 */
public class Converters {
    
    /**
     * Convert a timestamp to a Date object.
     *
     * @param value The timestamp in milliseconds
     * @return The Date object
     */
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    /**
     * Convert a Date object to a timestamp.
     *
     * @param date The Date object
     * @return The timestamp in milliseconds
     */
    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    /**
     * Convert a boolean to an integer.
     *
     * @param value The boolean value
     * @return 1 for true, 0 for false
     */
    @TypeConverter
    public static Integer booleanToInt(Boolean value) {
        return value == null ? null : (value ? 1 : 0);
    }

    /**
     * Convert an integer to a boolean.
     *
     * @param value The integer value
     * @return true for 1, false for 0 or null
     */
    @TypeConverter
    public static Boolean intToBoolean(Integer value) {
        return value == null ? null : (value == 1);
    }
} 
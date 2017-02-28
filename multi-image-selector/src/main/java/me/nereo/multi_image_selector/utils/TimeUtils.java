package me.nereo.multi_image_selector.utils;

import android.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 时间处理工具
 * Created by Nereo on 2015/4/8.
 */
public class TimeUtils {

    public static String timeFormat(long timeMillis, String pattern){
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CHINA);
        return format.format(new Date(timeMillis));
    }

    public static String formatPhotoDate(long time){
        return timeFormat(time, "yyyy-MM-dd");
    }

    public static String formatPhotoDate(String path){
        File file = new File(path);
        if(file.exists()){
            long time = file.lastModified();
            return formatPhotoDate(time);
        }
        return "1970-01-01";
    }

    public static String getDurationStr(int duration) {
        int hours = duration / 3600;
        int minutes = (duration % 3600) / 60;
        int seconds = duration % 60;

        String result = "";
        if (hours > 0) {
            result = hours + ":";
        }

        if (minutes >= 0) {
            result = result + (minutes >= 10 ? minutes : ("0"+minutes)) + ":";
        }

        if (seconds >= 0) {
            result = result + (seconds >= 10 ? seconds : ("0"+seconds)) + "";
        }

        return result;
    }
}

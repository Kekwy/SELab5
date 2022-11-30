package com.kekwy.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DatePrefix {
	public static String getDateString() {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		return sdf.format(calendar.getTime());
	}
}

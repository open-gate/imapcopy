package biz.opengate.imapCopy;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class DateUtilities {
	public static Date newDate(int year, int month, int day) {
		GregorianCalendar gc=new GregorianCalendar();
		gc.set(year, month-1, day);
		return stripTimeData(gc.getTime());
	}
	
	public static Date addDays(Date date, int days) {
		return add(date, Calendar.DAY_OF_MONTH, days);
	}
	
	public static Date add(Date date, int field, int quantity) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(field, quantity);
		return cal.getTime();
	}
	
	/**YYYYMMDD*/
	public static Integer getYearMonthDay(Date date) {
		if (date==null) return null;
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return (cal.get(Calendar.YEAR))*10000+(cal.get(Calendar.MONTH)+1)*100+cal.get(Calendar.DAY_OF_MONTH);
	}
	
	public static Date stripTimeData(Date date) {
		if (date==null) {
			return null;
		}
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE     , 0);
		cal.set(Calendar.SECOND     , 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}
	
	public static String formatDate(Date date) {
		return formatDate(date,"yyyy/MM/dd");
	}

	public static String formatDateTime(Date date) {
		return formatDate(date,"yyyy/MM/dd HH:mm:ss");
	}

	private static String formatDate(Date date, String format) {
		if (date==null) return "";
		Locale currentLocale = new Locale("it");		
		SimpleDateFormat dateFormat = new SimpleDateFormat(format, currentLocale);
		return dateFormat.format(date);
	}

}

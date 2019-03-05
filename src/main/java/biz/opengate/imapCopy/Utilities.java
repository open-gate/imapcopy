package biz.opengate.imapCopy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

import javax.mail.Address;
import javax.mail.MessagingException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

public class Utilities {
	public static final String MESSAGE_ID_HEADER_NAME="Message-ID";
	
	public static <T> T getFirst(HashSet<T> messageSet) {
		Iterator<T> iterator = messageSet.iterator();
		if (!iterator.hasNext()) {
			return null;
		}
		return iterator.next();
	}

	public static String formatAddresses(Address[] addressArray) throws MessagingException {
		String result="";
				
		for (Address address: addressArray) {
			if (address instanceof javax.mail.internet.InternetAddress) {
				javax.mail.internet.InternetAddress casted=(javax.mail.internet.InternetAddress) address;
				result+=casted.getAddress()+",";
			}
			else {
				result+=address.toString()+",";	
			}
		}

		result=cutTail(result, ",");
		return result;
	}

	public static String cutTail(String s, String tail) {
		if (s==null) return s;
		if (s.endsWith(tail)) {
			s=s.substring(0,s.length()-tail.length());
		}
		return s;
	}


	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DATE UTILITIES
	
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
		return formatDate(date,"yyyy/MM/dd HH:mm:ss");
	}
	
	private static String formatDate(Date date, String format) {
		if (date==null) return "";
		Locale currentLocale = new Locale("it");		
		SimpleDateFormat dateFormat = new SimpleDateFormat(format, currentLocale);
		return dateFormat.format(date);
	}


	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	MARSHALLING / UNMARSHALLING
	
	public static void marshall(File file, Object object) throws FileNotFoundException {
		String json=toJsonString(object);
		stringToFile(json, file);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T unmarshall(File file, Class<T> clazz) throws IOException {
		String json=fileToString(file);

		if (clazz.equals(com.google.gson.JsonObject.class)) {
			JsonParser parser = new JsonParser();
			return (T) parser.parse(json).getAsJsonObject();
		}

		Gson gson=new Gson();
		return gson.fromJson(json, clazz);
	}
	
	public static String toJsonString(Object object) {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		Gson gson=builder.create();
		return gson.toJson(object);
	}

	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	FILE UTILITIES

	public static String fileToString(File file) throws IOException {
		byte[] encoded = Files.readAllBytes(file.toPath());
		return new String(encoded, StandardCharsets.UTF_8);
	}
	
	public static void stringToFile(String string, File file) throws FileNotFoundException {
		PrintWriter out = new PrintWriter(file);
		out.print(string);
		out.close();
	}
}

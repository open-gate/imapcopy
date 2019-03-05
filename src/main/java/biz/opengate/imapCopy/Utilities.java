package biz.opengate.imapCopy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;

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

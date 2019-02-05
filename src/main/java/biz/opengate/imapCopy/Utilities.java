package biz.opengate.imapCopy;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.TreeSet;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

import javax.mail.Folder;

public class Utilities {
	public static final String MESSAGE_ID_HEADER_NAME="Message-ID";

	public static boolean canHoldMessages(Folder folder) throws MessagingException {
		return (folder.getType() & Folder.HOLDS_MESSAGES)!=0;
	}

	public static void pushAllChildren(Folder folder, Stack<Folder> stack) throws MessagingException {
		Folder[] childFolders=folder.list();
		for (Folder childFolder: childFolders) {
			stack.push(childFolder);
		}
	}

	/**https://www.iana.org/assignments/message-headers/message-headers.xhtml*/
	public static String getMessageId(Message m) {
		try {
			String[] header = m.getHeader(MESSAGE_ID_HEADER_NAME);
			return header[0];
		}
		catch (Exception e) {
			return null;
		}
	}
	
	public static <T extends Comparable<T>> void remove(TreeSet<T> set, T key) {
		T firstMatch = Utilities.getFirstMatch(set, key);
		if (firstMatch!=null) {
			set.remove(firstMatch);
		}
	}

	public static <T extends Comparable<T>> T getFirstMatch(TreeSet<T> set, T key) {
		if (!set.isEmpty()) {
			T floor = set.floor(key);
			if (floor!=null) {
				if (floor.compareTo(key)==0) {
					return floor;	
				}
			}
		}

		return null;
	}

	public static String getAllFromAddresses(Message message) throws MessagingException {
		Address[] from = message.getFrom();
		String result="";
				
		for (Address address: from) {
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
	
	public static String formatDate(Date date) {
		return formatDate(date,"yyyy/MM/dd HH:mm:ss");
	}
	
	public static String formatDate(Date date, String format) {
		if (date==null) return "";
		Locale currentLocale = new Locale("it");		
		SimpleDateFormat dateFormat = new SimpleDateFormat(format, currentLocale);
		return dateFormat.format(date);
	}

	public static String cutTail(String s, String tail) {
		if (s==null) return s;
		if (s.endsWith(tail)) {
			s=s.substring(0,s.length()-tail.length());
		}
		return s;
	}
}

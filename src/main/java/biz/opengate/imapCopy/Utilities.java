package biz.opengate.imapCopy;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Stack;
import java.util.TreeSet;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Flags.Flag;
import javax.mail.Folder;

public class Utilities {
	
	
	public static boolean canHoldMessages(Folder folder) throws MessagingException {
//		if (!"INBOX".equals(folder.getFullName())) return false;
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
			String[] header = m.getHeader("Message-ID");
			return header[0];
		}
		catch (Exception e) {
			return null;
		}
	}


	
	
	
	public static void debugDump(Message m) throws MessagingException {
		System.out.println("===============================================");
		System.out.println("==	HEADERS");
		Enumeration<Header> allHeaders = m.getAllHeaders();
		while (allHeaders.hasMoreElements()) {
			Header nextElement = allHeaders.nextElement();
			System.out.println(noNewLine(nextElement.getName())+"        "+noNewLine(nextElement.getValue()));
		}
		System.out.println("===============================================");
		Flags flags = m.getFlags();
		System.out.println("===============================================");
		System.out.println("==	SYSTEM FLAGS");
		for (Flag f: flags.getSystemFlags()) {
			System.out.println(f);
		}
		System.out.println("===============================================");
		
		System.out.println("===============================================");
		System.out.println("==	USER FLAGS");
		
		for (String f: flags.getUserFlags()) {					
			System.out.println(f);
		}
		System.out.println("===============================================");
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

	
	public static String noNewLine(String s) {
		s=replace(s, "\n", "\\n");
		s=replace(s, "\r", "\\r");
		return s;
	}
	
	
	

	public static String replace(String text, String pattern, String substitution) {
		
		int POSITION=0;

		while (true) {
			POSITION=text.indexOf(pattern,POSITION);
			if (POSITION==-1) return text;

			text=text.substring(0,POSITION)+substitution+text.substring(POSITION+pattern.length(),text.length());

			POSITION=POSITION+substitution.length();
		}
	}


	public static String cutTail(String s, String tail) {
		if (s==null) return s;
		if (s.endsWith(tail)) {
			s=s.substring(0,s.length()-tail.length());
		}
		return s;
	}

}

package biz.opengate.imapCopy;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

import javax.mail.Address;
import javax.mail.MessagingException;

import biz.opengate.imapCopy.connector.MessageBag;
import biz.opengate.imapCopy.connector.MessageMeta;

public class Utilities {
	public static final String MESSAGE_ID_HEADER_NAME="Message-ID";

	public static MessageBag toMessageBag(TreeSet<MessageMeta> messageSet) throws Exception {
		MessageBag bag=new MessageBag();		
		for (MessageMeta m: messageSet) {
			bag.push(m);
		}
		return bag;
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

	public static String formatDate(Date date) {
		return formatDate(date,"yyyy/MM/dd HH:mm:ss");
	}
	
	private static String formatDate(Date date, String format) {
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

package biz.opengate.imapCopy.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.mail.util.MimeMessageUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import biz.opengate.imapCopy.ImapConnection;
import biz.opengate.imapCopy.ImapCopy;
import biz.opengate.imapCopy.Utilities;

public class MessageMeta implements Comparable<MessageMeta> {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION
	
	private static final Logger logger = LogManager.getLogger();
	
	private Message message;
	private String messageId;

	public MessageMeta(Message message) {
		this.message=message;
		this.messageId=Utilities.getMessageId(message);
	}

	private MessageMeta(Message message, String messageId) {
		this.message=message;
		this.messageId=messageId;
	}

	
	///////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES	
	
	public MessageMeta clone(ImapConnection destinationConnection) throws MessagingException, IOException {
		ByteArrayOutputStream baos=new ByteArrayOutputStream(ImapCopy.COPY_BUFFER_INITIAL_SIZE_BYTES);
		getMessage().writeTo(baos);
		
		if (ImapCopy.DEBUG_LOG) {
			logger.info("[clone]["+baos.toByteArray().length+" bytes]");
		}
		
		ByteArrayInputStream bais=new ByteArrayInputStream(baos.toByteArray());
		MimeMessage msg = MimeMessageUtils.createMimeMessage(destinationConnection.getSession(),bais);
		msg.setHeader(Utilities.MESSAGE_ID_HEADER_NAME, getMessageId());
		//msg.saveChanges();		//no
		return new MessageMeta(msg,getMessageId());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((messageId == null) ? 0 : messageId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		MessageMeta other = (MessageMeta) obj;
		if (messageId == null) {
			if (other.messageId != null) {
				return false;
			}
		} 
		else if (!messageId.equals(other.messageId)) {
			return false;
		}
		return true;
	}

	@Override
	public int compareTo(MessageMeta o) {
		return messageId.compareTo(o.messageId);
	}

	@Override
	public String toString() {
		String result="";
		try {result+="[id: "+getMessageId()+"]";} catch (Exception e) {}
		try {result+="[folder: "+message.getFolder().getFullName()+"]";} catch (Exception e) {}
		try {result+="[from: "+Utilities.getAllFromAddresses(message)+"]";} catch (Exception e) {}
		try {result+="[subject: "+message.getSubject()+"]";} catch (Exception e) {}
		try {result+="[received: "+Utilities.formatDate(message.getReceivedDate())+"]";} catch (Exception e) {}
		try {result+="[sent: "+Utilities.formatDate(message.getSentDate())+"]";} catch (Exception e) {}
		return result;
	}
	
	
	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS
	
	/**result.get(0) is always root*/
	public List<Folder> getFullPath() throws MessagingException {
		List<Folder> result=new ArrayList<Folder>();
		
		Folder folder=message.getFolder();
		
		while (folder!=null) {		
			result.add(0,folder);
			folder=folder.getParent();
		}

		return result;
	}

	public Message getMessage() {
		return message;
	}
	
	public String getMessageId() {
		return messageId;
	}
}

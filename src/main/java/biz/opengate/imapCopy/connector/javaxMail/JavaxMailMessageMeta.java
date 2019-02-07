package biz.opengate.imapCopy.connector.javaxMail;

import javax.mail.Message;
import javax.mail.MessagingException;

import biz.opengate.imapCopy.Utilities;
import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MessageMeta;

public class JavaxMailMessageMeta extends MessageMeta {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private Message message;
	
	public JavaxMailMessageMeta(Message message) {
		setMessage(message);

		try {
			String[] header = message.getHeader(Utilities.MESSAGE_ID_HEADER_NAME);
			setMessageId(header[0]);
		}
		catch (Exception e) {
			//do nothing
		}
	}

	
	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS
	
	public FolderMeta getFolderMeta() throws MessagingException {
		return new JavaxMailFolderMeta(getMessage().getFolder());
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}
}

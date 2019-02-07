package biz.opengate.imapCopy.connector.gmailApi;

import java.util.List;

import javax.mail.MessagingException;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import biz.opengate.imapCopy.Utilities;
import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MessageMeta;

public class GmailApiMessageMeta extends MessageMeta {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private Message message;
	private String googleMessageId;
	
	public GmailApiMessageMeta(Message message) {
		setMessage(message);

    	MessagePart payload = getMessage().getPayload();
    	List<MessagePartHeader> headers = payload.getHeaders();
    	for (MessagePartHeader h: headers) {
    		if (Utilities.MESSAGE_ID_HEADER_NAME.toLowerCase().equals(h.getName().toLowerCase())) {
    			setMessageId(h.getValue());
    		}
    	}
	}

	
	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS

	public FolderMeta getFolderMeta() throws MessagingException {
		throw new UnsupportedOperationException();
	}
	
	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}
	
	public String getGoogleMessageId() {
		return googleMessageId;
	}
}

package biz.opengate.imapCopy.connector.gmailApi;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;

import java.util.List;
import java.util.TreeSet;

import biz.opengate.imapCopy.Utilities;
import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MailServerConnector;
import biz.opengate.imapCopy.connector.MessageMeta;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.gson.JsonObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
https://developers.google.com/gmail/api/quickstart/java
https://github.com/googleapis/google-api-java-client
https://developers.google.com/resources/api-libraries/documentation/gmail/v1/java/latest/allclasses-noframe.html
https://developers.google.com/resources/api-libraries/documentation/gmail/v1/java/latest/com/google/api/services/gmail/Gmail.Users.Messages.List.html
https://developers.google.com/gmail/api/v1/reference/users/messages/get
https://developers.google.com/gmail/api/v1/reference/users/messages/insert
*/
public class GmailApiConnector extends MailServerConnector {
	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION
	
	private static final Logger logger = LogManager.getLogger();
	
	private static final String APPLICATION_NAME = "GmailApiConnector";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Collections.singletonList(GmailScopes.MAIL_GOOGLE_COM);
	
	private Gmail service;

	public GmailApiConnector(String connectionName, JsonObject configuration) {
		super(connectionName, configuration);
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	OVERRIDES
	
	@Override
	public void connect() throws Exception {
		final String credentialsFilePath=getConfiguration().get("credentialsFilePath").getAsString();
		final String userName=getConfiguration().get("mail.imap.user").getAsString();
				
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredential cr = GoogleCredential.fromStream(new FileInputStream(credentialsFilePath)).createScoped(SCOPES);
        
        GoogleCredential.Builder builder = new GoogleCredential.Builder()
			.setTransport(HTTP_TRANSPORT)
			.setJsonFactory(JSON_FACTORY)
			.setServiceAccountScopes(SCOPES)
			.setServiceAccountId(cr.getServiceAccountId())
			.setServiceAccountPrivateKey(cr.getServiceAccountPrivateKey())
			.setServiceAccountPrivateKeyId(cr.getServiceAccountPrivateKeyId())
			.setTokenServerEncodedUrl(cr.getTokenServerEncodedUrl())
			.setServiceAccountUser(userName);
        
        GoogleCredential googleCredentials = builder.build();		
		
	    service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, googleCredentials).setApplicationName(APPLICATION_NAME).build();
	}

	@Override
	public void disconnect() {
		service=null;
	}

	@Override
	public TreeSet<MessageMeta> getMessages(Integer maxMessageAgeDays) throws Exception {
		TreeSet<MessageMeta> result=new TreeSet<MessageMeta>();
		List<GmailApiMessageMeta> childMessages = getChildMessages(maxMessageAgeDays);
		logger.info("[getMessages]["+childMessages.size()+" messages]");
			
		for (GmailApiMessageMeta childMessage: childMessages) {
			if (childMessage.getMessageId()!=null) {
				result.add(childMessage);
			}
		}
		
		return result;
	}

	@Override
	public void removePresentMessages(Integer maxMessageAgeDays, TreeSet<MessageMeta> messageSet) throws Exception {
		Iterator<MessageMeta> iterator = messageSet.iterator();
		
		while (iterator.hasNext()) {
			MessageMeta meta = iterator.next();
			final String messageId=meta.getMessageId();
			
			if (checkMessageByMessageId(messageId)) {
				iterator.remove();
			}
		}
	}

	@Override
	public void generatePathIfInexistent(List<String> path) throws Exception {
		FolderMeta folder = getFolder(path);
		if (folder!=null) return;
		
		final String completePath=formatPath(path);

		logger.info("[generatePathIfInexistent][generating: "+completePath+"]");
		
		Label label=new Label();
		label.setName(completePath);
		label.setLabelListVisibility("labelShow");
		label.setMessageListVisibility("show");
		service.users().labels().create("me",label).execute();
	}

	@Override
	public FolderMeta getFolder(List<String> path) throws Exception {
		final String completePath=formatPath(path);
	
		ListLabelsResponse listResponse = service.users().labels().list("me").execute();
        List<Label> labels = listResponse.getLabels();
        
        for (Label label: labels) {
        	if (completePath.equals(label.getName())) {
        		GmailApiFolderMeta meta=new GmailApiFolderMeta(label);
        		return meta;
        	}
        }
        
		return null;
	}
	
	@Override
	public byte[] getRawMessage(MessageMeta messageMeta) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void appendRawMessage(byte[] raw, FolderMeta destinationFolderMeta, String messageId) throws Exception {
		GmailApiFolderMeta casted=(GmailApiFolderMeta) destinationFolderMeta;
		String encodedEmail=Base64.encodeBase64URLSafeString(raw);
		Message message = new Message();
		message.setRaw(encodedEmail);
		message.setLabelIds(Arrays.asList(casted.getLabel().getId()));
	    message = service.users().messages().insert("me", message).execute();
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	PRIVATE UTILITIES
	
	private String formatPath(List<String> path) {
		String completePath="";
		for (String s: path) {
			completePath+=s+"/";
		}
		completePath=Utilities.cutTail(completePath, "/");
		return completePath; 
	}
		
	private boolean checkMessageByMessageId(String messageId) throws IOException {
		final String query="rfc822msgid:"+messageId;
		ListMessagesResponse response = service.users().messages().list("me").setQ(query).execute();
		List<Message> messages = response.getMessages();
		return (messages!=null) && (!messages.isEmpty());
	}

    private List<GmailApiMessageMeta> getChildMessages(Integer maxMessageAgeDays) throws IOException {
    	if (maxMessageAgeDays==null) {
    		return listAllMessages();
    	}
    	
    	return listMessagesMatchingQuery("newer_than:"+maxMessageAgeDays+"d");
    }

	private List<GmailApiMessageMeta> listMessagesMatchingQuery(String query) throws IOException {
		ListMessagesResponse response = service.users().messages().list("me").setQ(query).execute();
		List<GmailApiMessageMeta> result=new LinkedList<GmailApiMessageMeta>();
		  
		while (response.getMessages() != null) {
			for (Message message: response.getMessages()) {
				Message reloadedMessage=service.users().messages().get("me", message.getId()).execute();
				result.add(new GmailApiMessageMeta(reloadedMessage));
			}

			if (response.getNextPageToken() != null) {
				String pageToken = response.getNextPageToken();
				response = service.users().messages().list("me").setQ(query).setPageToken(pageToken).execute();
			} 
			else {
				break;
			}
		}

		return result;  
	}

    private List<GmailApiMessageMeta> listAllMessages() throws IOException {
    	List<String> allLabelsId = getAllLabelsId();
		ListMessagesResponse response = service.users().messages().list("me").setLabelIds(allLabelsId).execute();
		List<GmailApiMessageMeta> result=new LinkedList<GmailApiMessageMeta>();
		  
		while (response.getMessages() != null) {
			for (Message message: response.getMessages()) {
				Message reloadedMessage=service.users().messages().get("me", message.getId()).execute();
				result.add(new GmailApiMessageMeta(reloadedMessage));
			}

			if (response.getNextPageToken() != null) {
				String pageToken = response.getNextPageToken();
				response = service.users().messages().list("me").setLabelIds(allLabelsId).setPageToken(pageToken).execute();
			} 
			else {
				break;
			}
		}

		return result;  
	}
    
	private List<String> getAllLabelsId() throws IOException {
		ListLabelsResponse listResponse = service.users().labels().list("me").execute();
        List<Label> labels = listResponse.getLabels();
        List<String> result=new ArrayList<String>(labels.size());

        for (Label label : labels) {
        	result.add(label.getId());
        }
        
        return result;
	}
}

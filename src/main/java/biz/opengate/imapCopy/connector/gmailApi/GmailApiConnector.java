package biz.opengate.imapCopy.connector.gmailApi;

import java.util.List;
import java.util.Map.Entry;

import java.util.Set;

import biz.opengate.imapCopy.ImapCopy;
import biz.opengate.imapCopy.Utilities;
import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MailServerConnector;
import biz.opengate.imapCopy.connector.MessageMeta;
import biz.opengate.imapCopy.connector.RawMessage;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.gson.JsonObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
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

import com.google.api.services.gmail.Gmail.Users.Messages;
https://developers.google.com/resources/api-libraries/documentation/gmail/v1/java/latest/com/google/api/services/gmail/Gmail.Users.Messages.html
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

	    if (ImapCopy.isVerbose()) {
	    	logger.info(getConnectionName()+"|connect|connecting|gmailApi|"+userName);
	    }
	}

	@Override
	public void disconnect() {
		service=null;
		if (ImapCopy.isVerbose()) {
			logger.info(getConnectionName()+"|disconnect|disconnected");
		}		
	}

	@Override
	public HashSet<MessageMeta> getMessages(String folderPath, Integer maxMessageAgeDays, HashSet<String> idToIgnore) throws Exception {
		HashSet<MessageMeta> result=new HashSet<MessageMeta>();
		List<GmailApiMessageMeta> childMessages = getChildMessages(maxMessageAgeDays);
		logger.info("getMessages|"+childMessages.size()+" messages");
			
		for (GmailApiMessageMeta childMessage: childMessages) {
			if (childMessage.getMessageId()==null) continue;
			boolean wasNotPresent = idToIgnore.add(childMessage.getMessageId());
			if (wasNotPresent) {
				result.add(childMessage);
			}
		}
		
		return result;
	}

	@Override
	public HashSet<MessageMeta> getMessages(String folderPath, Date day, HashSet<String> idToIgnore) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void debugLogMessageByMessageId(String messageId) throws Exception {
		Message message=fetchSingleMessage(messageId); 
		if (message==null) {
			logger.info("message not found");
		}
		else {
			Message reloadedMessage=service.users().messages().get("me", message.getId()).execute();
			GmailApiMessageMeta meta=new GmailApiMessageMeta(reloadedMessage);
			debugLogMessage(meta);
		}		
	}
	
	@Override
	public boolean checkMessageByMessageId(String messageId) throws IOException {
		return fetchSingleMessage(messageId)!=null;
	}

	@Override
	public void generatePathIfInexistent(List<String> path) throws Exception {
		for (int depth=0; depth<path.size(); depth++) {
			try {			
				generateLabel(path,depth);
			}
			catch (ReservedFolderNameException e) {
				logger.debug("generatePathIfInexistent|reserved folder name|"+path.get(depth));
				path.set(depth, "IMAP_"+path.get(depth));
				generateLabel(path,depth);
			}
		}
	}

	@Override
	public FolderMeta getFolder(List<String> path) throws Exception {
		final String completePath=formatPath(path);
	
		ListLabelsResponse listResponse = service.users().labels().list("me").execute();
        List<Label> labels = listResponse.getLabels();
        
        for (Label label: labels) {
        	if (completePath.toLowerCase().equals(label.getName().toLowerCase())) {
        		GmailApiFolderMeta meta=new GmailApiFolderMeta(label);
        		return meta;
        	}
        }
        
		return null;
	}

	@Override
	public HashSet<String> getAllFoldersPaths() throws Exception {
		ListLabelsResponse listResponse = service.users().labels().list("me").execute();
        List<Label> labels = listResponse.getLabels();
        HashSet<String> result=new HashSet<String>();

        for (Label label : labels) {
        	result.add(label.getName());
        }
        
        return result;
	}

	@Override
	public RawMessage getRawMessage(MessageMeta messageMeta) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void appendRawMessage(RawMessage raw, FolderMeta destinationFolderMeta, String messageId) throws Exception {
		GmailApiFolderMeta casted=(GmailApiFolderMeta) destinationFolderMeta;
		Message message = new Message();
		message.encodeRaw(raw.getRaw());
		message.setLabelIds(Arrays.asList(casted.getLabel().getId()));
		
		service.users().messages().gmailImport("me",message)
			.setInternalDateSource("dateHeader")								//do not add a gmail Received header
			.execute();
		
//		message = service.users().messages().insert("me", message).execute();	//sets incorrect Received header
	}
	

	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	PRIVATE UTILITIES
	
	private Message fetchSingleMessage(String messageId) throws IOException {
		final String query="rfc822msgid:"+messageId;
		ListMessagesResponse response = service.users().messages().list("me").setQ(query).execute();
		List<Message> messages = response.getMessages();
		if (messages==null) return null;
		if (messages.isEmpty()) return null;
		return messages.get(0);
	}

	private void generateLabel(List<String> path, int depth) throws Exception {
		try {
			List<String> tmp=new ArrayList<String>(depth+1);
			for (int i=0; i<=depth; i++) tmp.add(path.get(i));
			
			FolderMeta folder = getFolder(tmp);
			if (folder!=null) return;
			
			final String completePath=formatPath(tmp);
			logger.info("generateLabel|generating: "+completePath);
			
			Label label=new Label();
			label.setName(completePath);
			label.setLabelListVisibility("labelShow");
			label.setMessageListVisibility("show");
			service.users().labels().create("me",label).execute();
		}
		catch (GoogleJsonResponseException e) {
			String content="";
			if (e.getContent()!=null) content=e.getContent();
			logger.warn("generateLabel|content:"+content);
			content=content.toLowerCase();
						
			if (content.contains("invalid label name")) {
				throw new ReservedFolderNameException(e);	
			}
			
			if (content.contains("label name exists or conflicts")) {
				throw new ReservedFolderNameException(e);	
			}
						
			throw e;
		}
	}
	
	private void debugLogMessage(GmailApiMessageMeta messageMeta) {
		Message message = messageMeta.getMessage();

		logger.info("-----------------------------------");
		logger.info("----------------------");
		logger.info("Labels:");		
		List<String> labelIds = messageMeta.getMessage().getLabelIds();
		for (String label: labelIds) {
			logger.info(label); 
			
		}		
		logger.info("----------------------");
		logger.info("----------------------");
		logger.info("EntrySet:");
		try {
			Set<Entry<String, Object>> entrySet = message.entrySet();
			Iterator<Entry<String, Object>> iterator = entrySet.iterator();
			while (iterator.hasNext()) {
				Entry<String, Object> next = iterator.next();
				logger.info(next.getKey()+"\t\t\t"+next.getValue());
			}
		}
		catch (Exception e) {}
		logger.info("----------------------");
		logger.info("----------------------");
		logger.info("Payload:");
		try {
	    	MessagePart payload = message.getPayload();
	    	List<MessagePartHeader> headers = payload.getHeaders();
	    	for (MessagePartHeader h: headers) {
	    		logger.info(h.getName()+"\t\t\t"+h.getValue());
	    	}
		}
		catch (Exception e) {}
		logger.info("----------------------");
		logger.info("-----------------------------------");
	}
	
	private String formatPath(List<String> path) {
		String completePath="";
		for (String s: path) {
			completePath+=s+"/";
		}
		completePath=Utilities.cutTail(completePath, "/");
		return completePath; 
	}
		
    private List<GmailApiMessageMeta> getChildMessages(Integer maxMessageAgeDays) throws IOException {
    	if (maxMessageAgeDays==null) {
    		return listAllMessages();
    	}
    	
    	return listMessagesMatchingQuery("newer_than:"+maxMessageAgeDays+"d");
    }

	/**https://support.google.com/mail/answer/7190?hl=en*/
	public List<GmailApiMessageMeta> listMessagesMatchingQuery(String query) throws IOException {
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
	
	@Override
	public String getDescription() {
		final String username=getConfiguration().get("mail.imap.user").getAsString();
		return "gmail."+username;
	}
}

package biz.opengate.imapCopy.connector;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

import com.google.gson.JsonObject;

public abstract class MailServerConnector {
	////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION
	
	private String connectionName;
	private JsonObject configuration;
	
	public MailServerConnector(String connectionName, JsonObject configuration) {
		setConnectionName(connectionName);
		setConfiguration(configuration);
	}

	
	////////////////////////////////////////////////////////////////////////////////////////
	//	ABSTRACTS
	
	public abstract void connect() throws Exception;
	public abstract void disconnect();	
	public abstract HashSet<String> getAllFoldersPaths() throws Exception;
	/**
	 * @param maxMessageAgeDays if null, returns all the messages
	 * @return
	 */
	public abstract HashSet<MessageMeta> getMessages(String folderPath, Integer maxMessageAgeDays, HashSet<String> idToIgnore) throws Exception;
	public abstract HashSet<MessageMeta> getMessages(String folderPath, Date day, HashSet<String> idToIgnore) throws Exception;
	public abstract boolean checkMessageByMessageId(String messageId) throws Exception;
	public abstract void generatePathIfInexistent(List<String> path) throws Exception;
	public abstract RawMessage getRawMessage(MessageMeta messageMeta) throws Exception;
	public abstract void appendRawMessage(RawMessage raw, FolderMeta destinationFolderMeta, String messageId) throws Exception;
	public abstract FolderMeta getFolder(List<String> path) throws Exception;
	public abstract String getDescription();
	public abstract void debugLogMessageByMessageId(String messageId) throws Exception;


	////////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS

	public String getConnectionName() {
		return connectionName;
	}

	public void setConnectionName(String connectionName) {
		this.connectionName = connectionName;
	}

	public JsonObject getConfiguration() {
		return configuration;
	}

	public void setConfiguration(JsonObject configuration) {
		this.configuration = configuration;
	}
}

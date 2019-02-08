package biz.opengate.imapCopy.connector;

import java.util.List;
import java.util.TreeSet;

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
	/**
	 * @param maxMessageAgeDays if null, returns all the messages
	 * @return
	 */
	public abstract TreeSet<MessageMeta> getMessages(Integer maxMessageAgeDays) throws Exception;
	/**
	 * removes from messageSet the messages already present, using the header Message-ID to match messages 
	 * @param maxMessageAgeDays if null, parse all messages
	 * @param messageSet
	 * @throws Exception
	 */
	public abstract void removePresentMessages(Integer maxMessageAgeDays, TreeSet<MessageMeta> messageSet) throws Exception;
	public abstract void generatePathIfInexistent(List<String> path) throws Exception;
	public abstract RawMessage getRawMessage(MessageMeta messageMeta) throws Exception;
	public abstract FolderMeta getFolder(List<String> path) throws Exception;
	public abstract void appendRawMessage(RawMessage raw, FolderMeta destinationFolderMeta, String messageId) throws Exception;
	public abstract void deleteMessage(String messageId) throws Exception;
	

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

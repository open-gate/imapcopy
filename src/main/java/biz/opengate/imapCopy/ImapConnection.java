package biz.opengate.imapCopy;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ImapConnection {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	private JsonObject configuration;
	private String connectionName;
	private Session session;
	private Store store;
	
	public ImapConnection(JsonObject configuration, String connectionName) {
		this.configuration=configuration;
		this.connectionName=connectionName;
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES

	public void connect() throws MessagingException {
		Properties props = System.getProperties();
		JsonObject connectionConfiguration=configuration.get(connectionName).getAsJsonObject();

		///////////////////////////////////////////////////////////////////
		//	READ THE CONFIGURATION
		final String host=connectionConfiguration.get("mail.imap.host").getAsString();
		final String username=connectionConfiguration.get("mail.imap.user").getAsString();
		final String password=connectionConfiguration.get("mail.imap.password").getAsString();
		final String sessionStore=connectionConfiguration.get("mail.store.protocol").getAsString();
		///////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////
		//	READ THE PROPERTIES		
		Set<Entry<String, JsonElement>> entrySet = connectionConfiguration.entrySet();
		Iterator<Entry<String, JsonElement>> iterator = entrySet.iterator();
		while (iterator.hasNext()) {
			Entry<String, JsonElement> next = iterator.next();
			props.put(next.getKey(),next.getValue().getAsString());
			
			if (ImapCopy.verbose) {
				logger.info("["+connectionName+"][connect][propertyFound]["+next.getKey()+"]["+next.getValue().getAsString()+"]");
			}
		}
		///////////////////////////////////////////////////////////////////

		if (ImapCopy.verbose) {
			logger.info("["+connectionName+"][connect][preparingSession]");
		}
		session = Session.getDefaultInstance(props, null);
		store = session.getStore(sessionStore);
		logger.info("["+connectionName+"][connect][connecting]["+host+"]["+username+"]");
		store.connect(host,username,password);
		if (ImapCopy.verbose) {
			logger.info("["+connectionName+"][connect][connected]");
		}
	}
	
	public void disconnect() {
		try {
			if (store==null) return;
			store.close();
			store=null;
			if (ImapCopy.verbose) {
				logger.info("["+connectionName+"][disconnect][disconnected]");
			}
		}
		catch (Exception e) {
			logger.log(Level.WARN,"["+connectionName+"][disconnect]",e);
		}
	}

	public Folder getOrGeneratePath(List<Folder> fullPath) throws MessagingException {
		Folder destinationFolder=getRoot();
		
		for (int i=1; i<fullPath.size(); i++) {
			Folder sourceFolder=fullPath.get(i);
			destinationFolder=destinationFolder.getFolder(sourceFolder.getName());
		}
		
		if (!destinationFolder.exists()) {
			logger.info("[getOrGeneratePath][generating: "+destinationFolder.getFullName()+"]");
			boolean result=destinationFolder.create(Folder.READ_WRITE | Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
			
			if (!result) {
				throw new MessagingException("[getOrGeneratePath][unable to generate path: "+destinationFolder.getFullName()+"]");
			}
		}
		
		return destinationFolder;
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS

	public Folder getRoot() throws MessagingException {
		return store.getDefaultFolder();
	}	
	
	public Session getSession() {
		return session;
	}
}

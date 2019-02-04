package biz.opengate.imapCopy;

import java.util.Iterator;
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

		///////////////////////////////////////////////////////////////////
		//	READ THE CONFIGURATION
		JsonObject storeConfiguration=configuration.get(connectionName).getAsJsonObject().get("configuration").getAsJsonObject();
		final String host=storeConfiguration.get("mail.imap.host").getAsString();
		final String username=storeConfiguration.get("mail.imap.user").getAsString();
		final String password=storeConfiguration.get("mail.imap.password").getAsString();
		final String sessionStore=storeConfiguration.get("sessionStore").getAsString();
		///////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////
		//	READ THE PROPERTIES
		JsonObject storeProperties=configuration.get(connectionName).getAsJsonObject().get("properties").getAsJsonObject();
		Set<Entry<String, JsonElement>> entrySet = storeProperties.entrySet();
		Iterator<Entry<String, JsonElement>> iterator = entrySet.iterator();
		while (iterator.hasNext()) {
			Entry<String, JsonElement> next = iterator.next();
			props.put(next.getKey(),next.getValue().getAsString());
			logger.info("["+connectionName+"][connect][propertyFound]["+next.getKey()+"]["+next.getValue().getAsString()+"]");
		}
		///////////////////////////////////////////////////////////////////

		logger.info("["+connectionName+"][connect][preparingSession]");
		session = Session.getDefaultInstance(props, null);
		store = session.getStore(sessionStore);
		logger.info("["+connectionName+"][connect][connecting]["+host+"]["+username+"]");
		store.connect(host,username,password);
		logger.info("["+connectionName+"][connect][connected]");
	}
	
	public void disconnect() {
		try {
			if (store==null) return;
			store.close();
			store=null;
			logger.info("["+connectionName+"][disconnect][disconnected]");
		}
		catch (Exception e) {
			logger.log(Level.WARN,"["+connectionName+"][disconnect]",e);
		}
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

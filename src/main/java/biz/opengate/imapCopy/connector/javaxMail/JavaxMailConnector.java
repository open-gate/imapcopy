package biz.opengate.imapCopy.connector.javaxMail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.ReceivedDateTerm;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import biz.opengate.imapCopy.ImapCopy;
import biz.opengate.imapCopy.Utilities;
import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MailServerConnector;
import biz.opengate.imapCopy.connector.MessageMeta;
import biz.opengate.imapCopy.connector.RawMessage;

public class JavaxMailConnector extends MailServerConnector {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();

	private Session session;
	private Store store;

	public JavaxMailConnector(String connectionName, JsonObject configuration) {
		super(connectionName, configuration);
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	OVERRIDES

	@Override
	public void connect() throws MessagingException {
		Properties props = System.getProperties();
		
		///////////////////////////////////////////////////////////////////
		//	READ THE CONFIGURATION
		final String host=getConfiguration().get("mail.imap.host").getAsString();
		final String username=getConfiguration().get("mail.imap.user").getAsString();
		final String password=getConfiguration().get("mail.imap.password").getAsString();
		final String sessionStore=getConfiguration().get("mail.store.protocol").getAsString();
		///////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////
		//	READ THE PROPERTIES		
		Set<Entry<String, JsonElement>> entrySet = getConfiguration().entrySet();
		Iterator<Entry<String, JsonElement>> iterator = entrySet.iterator();
		while (iterator.hasNext()) {
			Entry<String, JsonElement> next = iterator.next();
			props.put(next.getKey(),next.getValue().getAsString());
			
			if (ImapCopy.isVerbose()) {
				logger.info("["+getConnectionName()+"][connect][propertyFound]["+next.getKey()+"]["+next.getValue().getAsString()+"]");
			}
		}
		///////////////////////////////////////////////////////////////////

		if (ImapCopy.isVerbose()) {
			logger.info("["+getConnectionName()+"][connect][preparingSession]");
		}
		session = Session.getDefaultInstance(props, null);
		store = session.getStore(sessionStore);
		if (ImapCopy.isVerbose()) {
			logger.info("["+getConnectionName()+"][connect][connecting]["+host+"]["+username+"]");
		}
		store.connect(host,username,password);
		if (ImapCopy.isVerbose()) {
			logger.info("["+getConnectionName()+"][connect][connected]");
		}
	}

	@Override
	public void disconnect() {
		try {
			if (store==null) return;
			store.close();
			store=null;
			if (ImapCopy.isVerbose()) {
				logger.info("["+getConnectionName()+"][disconnect][disconnected]");
			}
		}
		catch (Exception e) {
			logger.log(Level.WARN,"["+getConnectionName()+"][disconnect]",e);
		}
	}
		
	@Override
	public TreeSet<MessageMeta> getMessages(Integer maxMessageAgeDays) throws MessagingException {
		TreeSet<MessageMeta> result=new TreeSet<MessageMeta>();
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(getRoot());
				
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (ImapCopy.isVerbose()) {
				logger.info("[getMessages]["+folder.getFullName()+"]");
			}
			
			pushAllChildren(folder,stack);
			
			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (canHoldMessages(folder)) {
				try {
					folder.open(Folder.READ_ONLY);
					Message[] childMessages = getChildMessages(folder, maxMessageAgeDays);
					logger.info("[getMessages]["+folder.getFullName()+"]["+childMessages.length+" messages]");
	
					for (Message childMessage: childMessages) {
						MessageMeta mm=new JavaxMailMessageMeta(childMessage);
						if (mm.getMessageId()!=null) {
							result.add(mm);
						}
					}
				}
				finally {				
					folder.close();
				}
			}
			///////////////////////////////////////////////////////////////////////
		}
		
		return result;
	}

	@Override
	public void removePresentMessages(Integer maxMessageAgeDays, TreeSet<MessageMeta> messageSet) throws MessagingException {
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(getRoot());
		
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (ImapCopy.isVerbose()) {
				logger.info("[removePresentMessages]["+folder.getFullName()+"]");
			}

			pushAllChildren(folder,stack);

			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (canHoldMessages(folder)) {
				try {
					folder.open(Folder.READ_ONLY);
					Message[] childMessages = getChildMessages(folder, maxMessageAgeDays);
					logger.info("[removePresentMessages]["+folder.getFullName()+"]["+childMessages.length+" messages]");
	
					for (Message childMessage: childMessages) {
						try {
							MessageMeta key=new JavaxMailMessageMeta(childMessage);
							if (key.getMessageId()==null) continue;
							Utilities.remove(messageSet, key);
						}
						catch (Exception e) {
							logger.log(Level.WARN,"[removePresentMessages]",e);
						}
					}
				}
				finally {				
					folder.close();
				}
			}
			///////////////////////////////////////////////////////////////////////
		}
	}
		
	@Override
	public void generatePathIfInexistent(List<String> path) throws MessagingException {
		JavaxMailFolderMeta destinationFolderMeta=getFolder(path);
		Folder destinationFolder=destinationFolderMeta.getFolder();
		
		if (!destinationFolder.exists()) {
			logger.info("[generatePathIfInexistent][generating: "+destinationFolder.getFullName()+"]");
			boolean result=destinationFolder.create(Folder.READ_WRITE | Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
			
			if (!result) {
				throw new MessagingException("[generatePathIfInexistent][unable to generate path: "+destinationFolder.getFullName()+"]");
			}
		}
	}

	@Override
	public JavaxMailFolderMeta getFolder(List<String> path) throws MessagingException {
		Folder folder=getRoot();
		for (String folderName: path) {
			folder=folder.getFolder(folderName);
		}
		
		return new JavaxMailFolderMeta(folder);
	}

	@Override
	public RawMessage getRawMessage(MessageMeta messageMeta) throws IOException, MessagingException {
		logger.debug("[getRawMessage] "+messageMeta.getMessageId());
		
		JavaxMailMessageMeta casted=(JavaxMailMessageMeta) messageMeta;
		Folder folder=casted.getMessage().getFolder();
		
		try {
			folder.open(Folder.READ_ONLY);
			ByteArrayOutputStream baos=new ByteArrayOutputStream(ImapCopy.COPY_BUFFER_INITIAL_SIZE_BYTES);
			casted.getMessage().writeTo(baos);

			RawMessage result=new RawMessage();
			result.setRaw(baos.toByteArray());
			return result;
		}
		finally {
			folder.close();
		}
	}
	
	@Override
	public void appendRawMessage(RawMessage raw, FolderMeta destinationFolderMeta, String messageId) throws MessagingException {
//		Folder folder=((JavaxMailFolderMeta)destinationFolderMeta).getFolder();
//		
//		if (!folder.exists()) throw new MessagingException("inexistent folder: "+folder.getFullName());
//
//		try {
//			folder.open(Folder.READ_WRITE);
//			
//			ByteArrayInputStream bais=new ByteArrayInputStream(raw);
//			MimeMessage msg = MimeMessageUtils.createMimeMessage(getSession(),bais);
//			msg.setHeader(Utilities.MESSAGE_ID_HEADER_NAME, messageId);
//			//msg.saveChanges();		//no
//			
//			Message[] messageArray=new Message[1];
//			messageArray[0]=msg;
//			folder.appendMessages(messageArray);
//		}
//		finally {
//			folder.close();
//		}
		
		throw new UnsupportedOperationException();
	}
	
	public void deleteMessage(String messageId) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	PRIVATE UTILITIES
	
	@SuppressWarnings("unused")
	private void debugLogMessage(JavaxMailMessageMeta messageMeta) throws MessagingException {
		Message msg=messageMeta.getMessage();
		msg.getFolder().open(Folder.READ_ONLY);
		logger.info("-----------------------------------");
		logger.info("from        : "+Utilities.formatAddresses(msg.getFrom()));
		logger.info("recipients  : "+Utilities.formatAddresses(msg.getAllRecipients()));
		logger.info("replyTo     : "+Utilities.formatAddresses(msg.getReplyTo()));
		logger.info("receivedDate: "+Utilities.formatDate(msg.getReceivedDate()));
		logger.info("sentDate    : "+Utilities.formatDate(msg.getSentDate()));
		logger.info("rfc822msgid:"+messageMeta.getMessageId());			
		logger.info("-----------------------------------");
		msg.getFolder().close();
	}

	private Message[] getChildMessages(Folder folder, Integer maxMessageAgeDays) throws MessagingException {
		if (maxMessageAgeDays==null) {
			return folder.getMessages();
		}
		
		Calendar gc=GregorianCalendar.getInstance();
		gc.add(Calendar.DAY_OF_MONTH,-maxMessageAgeDays);
		Date thresholdDate=gc.getTime();
		
		ReceivedDateTerm term=new ReceivedDateTerm(javax.mail.search.ComparisonTerm.GE,thresholdDate);
		return folder.search(term);
	}
	
	private boolean canHoldMessages(Folder folder) throws MessagingException {
		return (folder.getType() & Folder.HOLDS_MESSAGES)!=0;
	}

	private void pushAllChildren(Folder folder, Stack<Folder> stack) throws MessagingException {
		Folder[] childFolders=folder.list();
		for (Folder childFolder: childFolders) {
			stack.push(childFolder);
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

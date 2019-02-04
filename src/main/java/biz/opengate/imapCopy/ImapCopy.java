package biz.opengate.imapCopy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Stack;
import java.util.TreeSet;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.search.AndTerm;
import javax.mail.search.SearchTerm;

import org.apache.commons.mail.util.MimeMessageUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();

	private JsonObject configuration;
	private boolean verbose;
	private ImapConnection sourceConnection;
	private ImapConnection destinationConnection;
	private TreeSet<MessageMeta> sourceMessageSet=new TreeSet<MessageMeta>();
	
	public ImapCopy() throws Exception  {
		JsonParser parser = new JsonParser();
		JsonElement jsonElement = parser.parse(new FileReader("configuration.json"));
		configuration = jsonElement.getAsJsonObject();
		verbose=configuration.get("verbose").getAsBoolean();
		
		if (verbose) {
			logger.info("[thresholdDate:"+Utilities.formatDate(getThresholdDate())+"]");
		}
		
		copyMessages();
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES

	private void copyMessages() throws Exception {
		sourceConnection=new ImapConnection(configuration,"source");
		destinationConnection=new ImapConnection(configuration,"destination");
				
		try {
			sourceConnection.connect();
			destinationConnection.connect();
			
			parseSource(sourceConnection.getRoot());
			parseDestination(destinationConnection.getRoot());
		
			copyNonPresentMessages();
		}
		finally {
			sourceConnection.disconnect();
			destinationConnection.disconnect();
		}
	}
	
	private void parseSource(Folder root) throws Exception {
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(root);
				
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (verbose) {
				logger.info("[parseSource]["+folder.getFullName()+"]");
			}
			
			Utilities.pushAllChildren(folder,stack);
			
			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (Utilities.canHoldMessages(folder)) {
				folder.open(Folder.READ_ONLY);
				Message[] childMessages = folder.search(getSourceSearchTerm());
				logger.info("[parseSource]["+folder.getFullName()+"]["+childMessages.length+" messages]");

				for (Message childMessage: childMessages) {
					MessageMeta mm=new MessageMeta(childMessage);
					if (mm.getMessageId()!=null) {
						sourceMessageSet.add(mm);	
					}
				}
				
				folder.close();
			}
			///////////////////////////////////////////////////////////////////////
		}
		
		logger.info("[parseSource]["+sourceMessageSet.size()+" messages found]");
		
		if (verbose) {
			for (MessageMeta meta: sourceMessageSet) {
				meta.getMessage().getFolder().open(Folder.READ_ONLY);
				logger.info("[parseSource]"+meta);
				meta.getMessage().getFolder().close();
			}
		}
	}
	
	private void parseDestination(Folder root) throws Exception {
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(root);
		int matchingMessagesCount=0;
		
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (verbose) {
				logger.info("[parseDestination]["+folder.getFullName()+"]");
			}
			

			
			Utilities.pushAllChildren(folder,stack);

			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (Utilities.canHoldMessages(folder)) {
				folder.open(Folder.READ_ONLY);
				Message[] childMessages = folder.search(getDestinationSearchTerm());

				logger.info("[parseDestination]["+folder.getFullName()+"]["+childMessages.length+" messages]");

				for (Message childMessage: childMessages) {
					try {
						MessageMeta key=new MessageMeta(childMessage);
						if (key.getMessageId()==null) continue;
						
						MessageMeta firstMatch = Utilities.getFirstMatch(sourceMessageSet, key);
					
						if (firstMatch!=null) {
							firstMatch.setAlredyPresent(true);
							matchingMessagesCount++;
						}
					}
					catch (Exception e) {
						logger.log(Level.WARN,"[parseDestination]",e);
					}
				}
				
				folder.close();
			}
			///////////////////////////////////////////////////////////////////////
		}
		
		logger.info("[parseDestination]["+matchingMessagesCount+" matching messages found]");
	}
	
	private void copyNonPresentMessages() throws Exception {
		///////////////////////////////////////////////////////////////////////
		//	COUNT PRESENT / MISSING MESSAGES
		int presentMessagesCount=0;
		for (MessageMeta m: sourceMessageSet) {
			if (m.isAlredyPresent()) {
				presentMessagesCount++;
			}
		}
		final int missingMessagesCount=sourceMessageSet.size()-presentMessagesCount;
		
		logger.info("[copyNonPresentMessages][total: "+sourceMessageSet.size()+"][present: "+presentMessagesCount+"][missing: "+missingMessagesCount+"]");
		///////////////////////////////////////////////////////////////////////
				
		for (MessageMeta m: sourceMessageSet) {
			try {
				if (m.isAlredyPresent()) continue;
				Folder destinationFolder=getOrGeneratePath(m);
				copyMessage(m,destinationFolder);
			}
			catch (Exception e) {
				logger.log(Level.WARN, "[copyNonPresentMessages][exception]",e);
			}
		}
	}
	
	private Folder getOrGeneratePath(MessageMeta messageMeta) throws Exception {
		List<Folder> fullPath = messageMeta.getFullPath();
		Folder destinationFolder=destinationConnection.getRoot();
		
		for (int i=1; i<fullPath.size(); i++) {
			Folder sourceFolder=fullPath.get(i);
			destinationFolder=destinationFolder.getFolder(sourceFolder.getName());
		}
		
		if (!destinationFolder.exists()) {
			logger.info("[generatePath][generating: "+destinationFolder.getFullName()+"]");
			boolean result=destinationFolder.create(Folder.READ_WRITE | Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
			
			if (!result) {
				throw new Exception("[generatePath][unable to generate path: "+destinationFolder.getFullName()+"]");
			}
		}
		
		return destinationFolder;
	}
	
	private void copyMessage(MessageMeta messageMeta, Folder destinationFolder) throws MessagingException, IOException {
		///////////////////////////////////////////////////////////////////////
		//	CREATE A COPY OF THE MESSAGE
		ByteArrayOutputStream baos=new ByteArrayOutputStream(1024);
		messageMeta.getMessage().getFolder().open(Folder.READ_ONLY);
		messageMeta.getMessage().writeTo(baos);
		messageMeta.getMessage().getFolder().close();
		ByteArrayInputStream bais=new ByteArrayInputStream(baos.toByteArray());
		MimeMessage msg = MimeMessageUtils.createMimeMessage(destinationConnection.getSession(),bais);
		msg.setHeader("Message-ID", messageMeta.getMessageId());
		//msg.saveChanges();		//no
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	LOGGING		
		String logMessage="";
		logMessage+="[copyMessage]";
		try {logMessage+="[id: "+messageMeta.getMessageId()+"]";} catch (Exception e) {}
		try {logMessage+="[folder: "+destinationFolder.getFullName()+"]";} catch (Exception e) {}
		try {logMessage+="[from: "+Utilities.getAllFromAddresses(msg)+"]";} catch (Exception e) {}
		try {logMessage+="[subject: "+msg.getSubject()+"]";} catch (Exception e) {}
		try {logMessage+="[received: "+Utilities.formatDate(msg.getReceivedDate())+"]";} catch (Exception e) {}
		try {logMessage+="[sent: "+Utilities.formatDate(msg.getSentDate())+"]";} catch (Exception e) {}
		logger.info(logMessage);
		///////////////////////////////////////////////////////////////////////		
		///////////////////////////////////////////////////////////////////////
		//	APPEND THE MESSAGE
		Message[] messages=new Message[1];
		messages[0]=msg; 		
		destinationFolder.open(Folder.READ_WRITE);
		destinationFolder.appendMessages(messages);
		destinationFolder.close();
		///////////////////////////////////////////////////////////////////////
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	LOW LEVEL UTILITIES
	
	private SearchTerm getSourceSearchTerm() {
//		return new ReceivedDateTerm(javax.mail.search.ComparisonTerm.GE,getThresholdDate());
		return new MyReceivedDateSearchTerm(getThresholdDate());
	}
	
	private SearchTerm getDestinationSearchTerm() {
		//SearchTerm searchTerm1 = new ReceivedDateTerm(javax.mail.search.ComparisonTerm.GE,getThresholdDate());
		SearchTerm searchTerm1 = new MyReceivedDateSearchTerm(getThresholdDate());
		SearchTerm searchTerm2 = new MessageIdSearchTerm();
		return new AndTerm(searchTerm1, searchTerm2);
	}
	
	/**created this class because javax.mail.search.ReceivedDateTerm seems buggy*/
	private class MyReceivedDateSearchTerm extends SearchTerm {
		private static final long serialVersionUID = 1L;
		private Date thresholdDate;
		
		MyReceivedDateSearchTerm(Date thresholdDate) {
			this.thresholdDate=thresholdDate;
		}

		@Override
		public boolean match(Message msg) {
			try {			
				Date date=msg.getReceivedDate();
				final int c=date.compareTo(thresholdDate);
				return c>=0;
			}
			catch (Exception e) {
				//do nothing
			}
			
			return false;
		}
		
	}

	private class MessageIdSearchTerm extends SearchTerm {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean match(Message msg) {
			MessageMeta key=new MessageMeta(msg);
			return sourceMessageSet.contains(key);
		}
	};
		
	private Date getThresholdDate() {
		final int maxMessageAgeHours=configuration.get("maxMessageAgeHours").getAsInt();

		Calendar gc=GregorianCalendar.getInstance();
		gc.add(Calendar.HOUR_OF_DAY, -maxMessageAgeHours);
		Date thresholdTime=gc.getTime();
		return thresholdTime;
	}
	
    public static void main(String[] args) {
		try {
			new ImapCopy();	
		}
		catch (Exception e) {			
			logger.log(Level.FATAL, "", e);
		}
	}
}

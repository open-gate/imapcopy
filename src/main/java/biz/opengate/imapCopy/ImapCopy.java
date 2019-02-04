package biz.opengate.imapCopy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
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
import javax.mail.search.ReceivedDateTerm;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.mail.util.MimeMessageUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();

	private JsonObject configuration;
	private boolean verbose;
	private Integer maxMessageAgeDays;
	private File configurationFile;
	private ImapConnection sourceConnection;
	private ImapConnection destinationConnection;
	private TreeSet<MessageMeta> sourceMessageSet=new TreeSet<MessageMeta>();
	
	public ImapCopy(String[] args) throws MessagingException, JsonIOException, JsonSyntaxException, FileNotFoundException {
		parseArguments(args);
		JsonParser parser = new JsonParser();
		JsonElement jsonElement = parser.parse(new FileReader(configurationFile));
		configuration = jsonElement.getAsJsonObject();
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES
	
	private void parseArguments(String[] args) {
        Options options = new Options();
        
        Option option = new Option("c", "config", true, "json configuration file");       
        option.setRequired(true);
        options.addOption(option);
        
        option = new Option("v", "verbose", false, "verbose output");
        option.setRequired(false);
        options.addOption(option);

        option = new Option("d", "maxMessageAgeDays", true, "max day age of messages (if not set all messages will be parsed)");
        option.setRequired(false);
        options.addOption(option);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            
            verbose=cmd.hasOption("verbose");
            
            if (cmd.hasOption("maxMessageAgeDays")) {
            	maxMessageAgeDays=Integer.valueOf(cmd.getOptionValue("maxMessageAgeDays"));
            }
            
            configurationFile=new File(cmd.getOptionValue("config"));
            
        	if (verbose) {
    			logger.info("[arguments][verbose: "+verbose+"][maxMessageAgeDays: "+maxMessageAgeDays+"][configurationFile: "+configurationFile.getAbsolutePath()+"]");
    		}
        } 
        catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("imapCopy", options);
            System.exit(1);
        }
	}
	
	public void doWork() throws MessagingException {
		sourceConnection=null;
		destinationConnection=null;
				
		try {
			sourceConnection=new ImapConnection(configuration,"source");
			destinationConnection=new ImapConnection(configuration,"destination");
			sourceConnection.connect();
			destinationConnection.connect();			
			parseSource();
			parseDestination();
			logMatchingStatistics();
			copyNonPresentMessages();
		}
		finally {
			if (sourceConnection!=null) sourceConnection.disconnect();
			if (destinationConnection!=null) destinationConnection.disconnect();
			sourceConnection=null;
			destinationConnection=null;
		}
	}

	private void parseSource() throws MessagingException {
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(sourceConnection.getRoot());
				
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (verbose) {
				logger.info("[parseSource]["+folder.getFullName()+"]");
			}
			
			Utilities.pushAllChildren(folder,stack);
			
			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (Utilities.canHoldMessages(folder)) {
				try {
					folder.open(Folder.READ_ONLY);
					Message[] childMessages = getChildMessages(folder);
					logger.info("[parseSource]["+folder.getFullName()+"]["+childMessages.length+" messages]");
	
					for (Message childMessage: childMessages) {
						MessageMeta mm=new MessageMeta(childMessage);
						if (mm.getMessageId()!=null) {
							sourceMessageSet.add(mm);	
						}
					}
				}
				finally {				
					folder.close();
				}
			}
			///////////////////////////////////////////////////////////////////////
		}
		
		logger.info("[parseSource]["+sourceMessageSet.size()+" messages found]");
		
		if (verbose) {
			for (MessageMeta meta: sourceMessageSet) {
				meta.getMessage().getFolder().open(Folder.READ_ONLY);
				logger.info("[parseSource][messageFound]"+meta);
				meta.getMessage().getFolder().close();
			}
		}
	}
	
	private void parseDestination() throws MessagingException {
		Stack<Folder> stack=new Stack<Folder>();
		stack.push(destinationConnection.getRoot());
		
		while (!stack.isEmpty()) {
			Folder folder=stack.pop();
			if (verbose) {
				logger.info("[parseDestination]["+folder.getFullName()+"]");
			}

			Utilities.pushAllChildren(folder,stack);

			///////////////////////////////////////////////////////////////////////
			//	READ CHILD MESSAGES
			if (Utilities.canHoldMessages(folder)) {
				try {
					folder.open(Folder.READ_ONLY);
					Message[] childMessages = getChildMessages(folder);
					logger.info("[parseDestination]["+folder.getFullName()+"]["+childMessages.length+" messages]");
	
					for (Message childMessage: childMessages) {
						try {
							MessageMeta key=new MessageMeta(childMessage);
							if (key.getMessageId()==null) continue;
							MessageMeta firstMatch = Utilities.getFirstMatch(sourceMessageSet, key);
						
							if (firstMatch!=null) {
								firstMatch.setAlredyPresent(true);
							}
						}
						catch (Exception e) {
							logger.log(Level.WARN,"[parseDestination]",e);
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
	
	private void logMatchingStatistics() {
		int presentMessagesCount=0;
		for (MessageMeta m: sourceMessageSet) {
			if (m.isAlredyPresent()) {
				presentMessagesCount++;
			}
		}
		final int missingMessagesCount=sourceMessageSet.size()-presentMessagesCount;
		logger.info("[total: "+sourceMessageSet.size()+"][present: "+presentMessagesCount+"][missing: "+missingMessagesCount+"]");
	}
	
	private void copyNonPresentMessages() {				
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
	
	private Folder getOrGeneratePath(MessageMeta messageMeta) throws MessagingException {
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
				throw new MessagingException("[generatePath][unable to generate path: "+destinationFolder.getFullName()+"]");
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
	
	private Message[] getChildMessages(Folder folder) throws MessagingException {
		if (maxMessageAgeDays==null) {
			return folder.getMessages();
		}
		
		Calendar gc=GregorianCalendar.getInstance();
		gc.add(Calendar.DAY_OF_MONTH,-maxMessageAgeDays);
		Date thresholdDate=gc.getTime();
		
		ReceivedDateTerm term=new ReceivedDateTerm(javax.mail.search.ComparisonTerm.GE,thresholdDate);
		return folder.search(term);
	}

    public static void main(String[] args) {
		try {
			logger.info("[imapCopy][start]");
			ImapCopy imapCopy = new ImapCopy(args);
			imapCopy.doWork();
			logger.info("[imapCopy][done]");
		}
		catch (Exception e) {			
			logger.log(Level.FATAL, "[imapCopy][errors]", e);
		}
	}
}

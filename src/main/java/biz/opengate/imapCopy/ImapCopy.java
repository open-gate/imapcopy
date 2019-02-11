package biz.opengate.imapCopy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

import javax.mail.MessagingException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import biz.opengate.imapCopy.connector.FolderMeta;
import biz.opengate.imapCopy.connector.MailServerConnector;
import biz.opengate.imapCopy.connector.MessageBag;
import biz.opengate.imapCopy.connector.MessageMeta;
import biz.opengate.imapCopy.connector.RawMessage;
import biz.opengate.imapCopy.connector.gmailApi.GmailApiConnector;
import biz.opengate.imapCopy.connector.javaxMail.JavaxMailConnector;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	public static final long MAX_CONNECTION_TIME_MINUTES=10;

	private static boolean verbose;
	private Integer maxMessageAgeDays;
	private File configurationFile;
	private JsonObject configuration;
	private MailServerConnector sourceConnection;
	private MailServerConnector destinationConnection;
			
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

		try {
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
        	CommandLine cmd = parser.parse(options, args);
            
            verbose=cmd.hasOption("verbose");
            
            if (cmd.hasOption("maxMessageAgeDays")) {
            	maxMessageAgeDays=Integer.valueOf(cmd.getOptionValue("maxMessageAgeDays"));
            }
            
            configurationFile=new File(cmd.getOptionValue("config"));
            
   			logger.info("[arguments][verbose: "+verbose+"][maxMessageAgeDays: "+maxMessageAgeDays+"][configurationFile: "+configurationFile.getAbsolutePath()+"]");
        } 
        catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("imapCopy", options);
            System.exit(1);
        }
	}
	
	public void doWork() throws Exception {		
		sourceConnection=getConnector("source");
		destinationConnection=getConnector("destination");
		
		///////////////////////////////////////////////////////////////////////
		//	READ THE SOURCE
		TreeSet<MessageMeta> messageSet=null;
		
		try {
			logger.info("[imapCopy][getting source messages]");
			sourceConnection.connect();
			messageSet=sourceConnection.getMessages(maxMessageAgeDays);
			logger.info("[imapCopy]["+messageSet.size()+" messages found in source account]");
		}
		finally {
			sourceConnection.disconnect();
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	READ THE DESTINATION
		try {
			logger.info("[imapCopy][removing messages present in destination]");
			destinationConnection.connect();
			destinationConnection.removePresentMessages(maxMessageAgeDays, messageSet);
			logger.info("[imapCopy]["+messageSet.size()+" messages to copy to destination account]");
		}
		finally {
			destinationConnection.disconnect();
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	GENERATE DESTINATION FOLDERS
		MessageBag messageBag=Utilities.toMessageBag(messageSet);
		try {
			logger.info("[imapCopy][generating destination folders]");
			
			destinationConnection.connect();
			for (FolderMeta folder: messageBag.keySet()) {
				destinationConnection.generatePathIfInexistent(folder.getPathList());
			}
		}
		finally {
			destinationConnection.disconnect();
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	APPEND MESSAGES
		logger.info("[imapCopy][appending messages]");
		
		for (FolderMeta folder: messageBag.keySet()) {
			appendMessages(folder, messageBag.get(folder));
		}
		///////////////////////////////////////////////////////////////////////
	}

	private void appendMessages(FolderMeta sourceFolder, List<MessageMeta> sourceMessageList) throws Exception {
		try {
			FolderMeta destinationFolderMeta=null;
			boolean firstRun=true;
			long lastReconnectionTime=0;
			final int total=sourceMessageList.size();
			int index=0;
			int failed=0;
			
			for (MessageMeta sourceMessageMeta: sourceMessageList) {
				///////////////////////////////////////////////////////////////
				//	RECONNECT EVERY n MINUTES
				if (firstRun || System.currentTimeMillis()-lastReconnectionTime>(MAX_CONNECTION_TIME_MINUTES*60*1000)) {
					reconnectBoth(!firstRun);
					destinationFolderMeta=destinationConnection.getFolder(sourceFolder.getPathList());
					
					if (firstRun) {
						logger.info("[appendMessages]["+destinationFolderMeta.getCompletePath()+"]["+total+" messages]");
					}
					
					lastReconnectionTime=System.currentTimeMillis();
					firstRun=false;
				}
				///////////////////////////////////////////////////////////////
				///////////////////////////////////////////////////////////////
				//	LOG EVERY n OPERATIONS
				index++;
				if (index%100==0) {
					logger.info("[appendMessages]["+destinationFolderMeta.getCompletePath()+"]["+index+"/"+total+"]["+failed+" failed]");
				}
				///////////////////////////////////////////////////////////////
				///////////////////////////////////////////////////////////////
				//	COPY THE MESSAGE
				try {
					RawMessage raw = sourceConnection.getRawMessage(sourceMessageMeta);
					destinationConnection.appendRawMessage(raw, destinationFolderMeta, sourceMessageMeta.getMessageId());
				}
				catch (MessagingException | IOException e) {
					logger.log(Level.WARN, "[appendMessages][exception]",e);
					failed++;
					continue;
				}
				///////////////////////////////////////////////////////////////
			}
		}
		finally {			
			sourceConnection.disconnect();
			destinationConnection.disconnect();
		}
	}
	
	private MailServerConnector getConnector(String connectionName) {
		final JsonObject connectionConfiguration=configuration.get(connectionName).getAsJsonObject();
		final String connectorClass=connectionConfiguration.get("connectorClass").getAsString();

		if ("JavaxMailConnection".equals(connectorClass)) {
			return new JavaxMailConnector(connectionName,connectionConfiguration);
		}
		
		if ("GmailApiConnection".equals(connectorClass)) {
			return new GmailApiConnector(connectionName,connectionConfiguration);
		}

		throw new RuntimeException("unknown connector class: '"+connectorClass+"'");
	}

	private void reconnectBoth(boolean doSleep) throws Exception {
		logger.info("[closingConnections]");
		
		sourceConnection.disconnect();
		destinationConnection.disconnect();
		
		if (doSleep) {
			Thread.sleep(5*1000);
		}
				
		logger.info("[reopeningConnections]");		
		sourceConnection.connect();
		destinationConnection.connect();
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	STATIC PART

    public static void main(String[] args) {
		try {
			final long startTime=System.currentTimeMillis();
			logger.info("[imapCopy][1.8][start]");
			ImapCopy imapCopy = new ImapCopy(args);
			imapCopy.doWork();
			final long endTime=System.currentTimeMillis();
			logger.info("[imapCopy][done]["+(endTime-startTime)+" ms]");
		}
		catch (Exception e) {			
			logger.log(Level.FATAL, "[imapCopy][errors]", e);
		}
	}
    
    public static boolean isVerbose() {
    	return verbose;
    }
}

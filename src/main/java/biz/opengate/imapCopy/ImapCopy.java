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
import biz.opengate.imapCopy.connector.gmailApi.GmailApiConnector;
import biz.opengate.imapCopy.connector.javaxMail.JavaxMailConnector;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	public static final int COPY_BUFFER_INITIAL_SIZE_BYTES=10*1024;

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
            
        	if (verbose) {
    			logger.info("[arguments][verbose: "+verbose+"][maxMessageAgeDays: "+maxMessageAgeDays+"][configurationFile: "+configurationFile.getAbsolutePath()+"]");
    		}
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
			logger.info("[doWork][getting source messages]");
			sourceConnection.connect();
			messageSet=sourceConnection.getMessages(maxMessageAgeDays);
			logger.info("[doWork]["+messageSet.size()+" messages found in source account]");
		}
		finally {
			sourceConnection.disconnect();
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	READ THE DESTINATION
		try {
			logger.info("[doWork][getting destination messages]");
			destinationConnection.connect();
			destinationConnection.removePresentMessages(maxMessageAgeDays, messageSet);
			logger.info("["+messageSet.size()+" messages to copy to destination account]");
		}
		finally {
			destinationConnection.disconnect();
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	GENERATE DESTINATION FOLDERS
		MessageBag messageBag=Utilities.toMessageBag(messageSet);
		try {
			logger.info("[doWork][generating destination folders]");
			
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
		logger.info("[doWork][appending messages]");
		
		for (FolderMeta folder: messageBag.keySet()) {
			appendMessages(folder, messageBag.get(folder));
		}
		///////////////////////////////////////////////////////////////////////
	}

	private void appendMessages(FolderMeta sourceFolder, List<MessageMeta> sourceMessageList) throws Exception {
		try {
			sourceConnection.connect();
			destinationConnection.connect();

			FolderMeta destinationFolderMeta = destinationConnection.getFolder(sourceFolder.getPathList());
			logger.info("[appendMessages]["+destinationFolderMeta.getCompletePath()+"]["+sourceMessageList.size()+" messages]");
			
			for (MessageMeta sourceMessageMeta: sourceMessageList) {
				try {
					byte[] raw = sourceConnection.getRawMessage(sourceMessageMeta);
					destinationConnection.appendRawMessage(raw, destinationFolderMeta, sourceMessageMeta.getMessageId());
				}
				catch (MessagingException | IOException e) {
					logger.log(Level.WARN, "[appendMessages][exception]",e);
					continue;
				}
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
	

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	STATIC PART

    public static void main(String[] args) {
		try {
			long startTime=System.currentTimeMillis();
			logger.info("[imapCopy][start]");
			ImapCopy imapCopy = new ImapCopy(args);
			imapCopy.doWork();
			long endTime=System.currentTimeMillis();
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

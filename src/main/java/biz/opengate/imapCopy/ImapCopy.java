package biz.opengate.imapCopy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

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
import biz.opengate.imapCopy.connector.MessageMeta;
import biz.opengate.imapCopy.connector.RawMessage;
import biz.opengate.imapCopy.connector.gmailApi.GmailApiConnector;
import biz.opengate.imapCopy.connector.javaxMail.JavaxMailConnector;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	private static final long COPY_RETRY_COUNT=3;
	private static final long ON_ERROR_PAUSE_TIME_MS=5*60*1000;

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

		logger.info("[doWork][reading source folders]");
		sourceConnection.connect();
		HashSet<String> allFoldersPaths = sourceConnection.getAllFoldersPaths();		
		sourceConnection.disconnect();
		
		HashSet<String> idToIgnore=new HashSet<String>();
		
		for (String sourcePath: allFoldersPaths) {
			sourceConnection.connect();			
			HashSet<MessageMeta> messageSet=sourceConnection.getMessages(sourcePath,maxMessageAgeDays,idToIgnore);
			sourceConnection.disconnect();
			
			if (messageSet.isEmpty()) {
				continue;
			}
		
			final MessageMeta messageMeta = Utilities.getFirst(messageSet);
			final FolderMeta folderMeta = messageMeta.getFolderMeta();

			destinationConnection.connect();
			destinationConnection.generatePathIfInexistent(folderMeta.getPathList());
			destinationConnection.disconnect();			

			sourceConnection.connect();
			destinationConnection.connect();
			appendMessages(folderMeta, messageSet);
			sourceConnection.disconnect();
			destinationConnection.disconnect();
		}
	}

	private void appendMessages(FolderMeta sourceFolder, HashSet<MessageMeta> sourceMessageList) throws Exception {
		final FolderMeta destinationFolderMeta=destinationConnection.getFolder(sourceFolder.getPathList());
		final int total=sourceMessageList.size();
		int index=0;
		int copied=0;
		int ignored=0;
		int failed=0;
		long lastLogTime=0;		
				
		for (MessageMeta sourceMessageMeta: sourceMessageList) {
			///////////////////////////////////////////////////////////////////
			//	COPY THE MESSAGE
			for (int retry=0; retry<COPY_RETRY_COUNT; retry++) {
				try {
					if (destinationConnection.checkMessageByMessageId(sourceMessageMeta.getMessageId())) {
						ignored++;
						break;
					}
					
					RawMessage raw = sourceConnection.getRawMessage(sourceMessageMeta);
					destinationConnection.appendRawMessage(raw, destinationFolderMeta, sourceMessageMeta.getMessageId());
					copied++;
					break;
				}
				catch (Exception e) {
					logger.log(Level.WARN, "[appendMessages][exception]["+sourceMessageMeta.getMessageId()+"][try: "+retry+"]",e);

					sourceConnection.disconnect();
					destinationConnection.disconnect();
					Thread.sleep(ON_ERROR_PAUSE_TIME_MS);
					sourceConnection.connect();
					destinationConnection.connect();
					
					if (retry==COPY_RETRY_COUNT-1) {
						failed++;
						break;
					}
				}
			}
			///////////////////////////////////////////////////////////////////
			///////////////////////////////////////////////////////////////////
			//	LOG EVERY MINUTE
			index++;
			if (System.currentTimeMillis()-lastLogTime>60*1000) {
				lastLogTime=System.currentTimeMillis();
				final int percentage=(int)(index/((double)total)*100);
				logger.info("[appendMessages]["+destinationFolderMeta.getCompletePath()+"]["+percentage+"%]["+copied+" copied]["+ignored+" ignored]["+failed+" failed]");
			}
			///////////////////////////////////////////////////////////////////
		}
		
		logger.info("[appendMessages]["+destinationFolderMeta.getCompletePath()+"]["+100+"%]["+copied+" copied]["+ignored+" ignored]["+failed+" failed]");
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
			final long startTime=System.currentTimeMillis();
			logger.info("[imapCopy][1.10][start]");
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

package biz.opengate.imapCopy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.TreeSet;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.ReceivedDateTerm;

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

import biz.opengate.imapCopy.model.FolderMeta;
import biz.opengate.imapCopy.model.MessageBag;
import biz.opengate.imapCopy.model.MessageMeta;

public class ImapCopy {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	public static final boolean DEBUG_LOG=false;
	private static final int DEFAULT_APPEND_BURST_SIZE=20;

	public static boolean verbose;		
	private Integer maxMessageAgeDays;
	private int appendBurstSize=DEFAULT_APPEND_BURST_SIZE;
	private File configurationFile;
	private JsonObject configuration;
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

            option = new Option("b", "appendBurstSize", true, "max number of messages to append to the destination IMAP at a single time (default 20)");
            option.setRequired(false);
            options.addOption(option);
           
        	CommandLineParser parser = new DefaultParser();
        	CommandLine cmd = parser.parse(options, args);
            
            verbose=cmd.hasOption("verbose");
            
            if (cmd.hasOption("maxMessageAgeDays")) {
            	maxMessageAgeDays=Integer.valueOf(cmd.getOptionValue("maxMessageAgeDays"));
            }
            
            if (cmd.hasOption("appendBurstSize")) {
            	appendBurstSize=Integer.valueOf(cmd.getOptionValue("appendBurstSize"));
            }
            
            configurationFile=new File(cmd.getOptionValue("config"));
            
        	if (verbose) {
    			logger.info("[arguments][verbose: "+verbose+"][maxMessageAgeDays: "+maxMessageAgeDays+"][appendBurstSize: "+appendBurstSize+"][configurationFile: "+configurationFile.getAbsolutePath()+"]");
    		}
        } 
        catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
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
			prepareAndAppendMessages();			
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
							if (DEBUG_LOG) {
								logger.info("[parseSource][messageFound]"+mm);
							}
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
							Utilities.remove(sourceMessageSet, key);
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
		
		logger.info("[parseDestination]["+sourceMessageSet.size()+" messages to copy]");
	}
	
	private void prepareAndAppendMessages() {
		MessageBag bag=new MessageBag();		
		for (MessageMeta m: sourceMessageSet) {
			bag.push(m);
		}

		for (FolderMeta key: bag.keySet()) {
			prepareAndAppendMessages(bag.get(key));
		}
	}
	
	private void prepareAndAppendMessages(List<MessageMeta> sourceMessageList) {
		///////////////////////////////////////////////////////////////////////
		//	CONSISTENCY CHECKS
		if (sourceMessageList==null || sourceMessageList.isEmpty()) {
			return;
		}
		///////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////
		//	PREPARE THE DESTINATION FOLDER
		final FolderMeta destinationFolderMeta;
		
		try {
			final Folder destinationFolder=destinationConnection.getOrGeneratePath(sourceMessageList.get(0).getFullPath());
			destinationFolderMeta=new FolderMeta(destinationFolder);
		}
		catch (MessagingException e) {
			logger.log(Level.WARN,"unable to generate remote path",e);
			return;
		}
		///////////////////////////////////////////////////////////////////////
				
		List<MessageMeta> destinationMessageList=new LinkedList<MessageMeta>();
		Folder sourceFolder=null;

		try {
			///////////////////////////////////////////////////////////////////
			//	OPEN THE SOURCE FOLDER. NOTE: IF THIS OPERATION FAILS THE 
			//	WHOLE LIST OF MESSAGES IS IGNORED 
			sourceFolder=sourceMessageList.get(0).getMessage().getFolder();
			sourceFolder.open(Folder.READ_ONLY);
			logger.info("[prepareMessages]["+sourceFolder.getFullName()+"]["+sourceMessageList.size()+" messages]");
			///////////////////////////////////////////////////////////////////
			
			int index=0;			
			for (MessageMeta messageMeta: sourceMessageList) {
				///////////////////////////////////////////////////////////////
				//	PREPARE A CLONED MESSAGE. NOTE: FAILURE ON THIS OPERATION
				//	ONLY AFFECT THIS MESSAGE
				index++;
				
				final MessageMeta destinationMessage;
				try {
					destinationMessage=messageMeta.clone(destinationConnection);
					if (verbose) {
						logger.info("[prepareMessages]["+sourceFolder.getFullName()+": "+index+"/"+sourceMessageList.size()+"]["+messageMeta.getMessageId()+"]");
					}
				}
				catch (MessagingException | IOException e) {
					logger.log(Level.WARN, "[prepareMessages][exception]",e);
					continue;
				}
				///////////////////////////////////////////////////////////////
				///////////////////////////////////////////////////////////////
				//	ENQUEUE AND SEND UP TO APPEND_BURST_SIZE MESSAGES
				destinationMessageList.add(destinationMessage);
				
				if (destinationMessageList.size()>=appendBurstSize) {
					appendMessagesToDestination(destinationFolderMeta, destinationMessageList);
					destinationMessageList.clear();
				}
				///////////////////////////////////////////////////////////////
			}

			///////////////////////////////////////////////////////////////////
			//	SEND THE REMAINING MESSAGES
			appendMessagesToDestination(destinationFolderMeta, destinationMessageList);
			///////////////////////////////////////////////////////////////////
		}
		catch (Exception e) {
			logger.log(Level.WARN, "[prepareMessages][exception]",e);
		}
		finally {
			if (sourceFolder!=null) {
				try {sourceFolder.close();} catch (Exception e) {}		
			}
		}
	}
	
	private void appendMessagesToDestination(FolderMeta destinationFolderMeta, List<MessageMeta> destinationMessageList) {
		try {
			if (destinationMessageList.isEmpty()) return;
			logger.info("[appendMessagesToDestination]["+destinationFolderMeta.getCompletePath()+"]["+destinationMessageList.size()+" messages]");
			destinationFolderMeta.appendMessages(destinationMessageList);
		}
		catch (Exception e) {
			logger.log(Level.WARN, "[prepareMessages][exception]",e);
		}		
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
}

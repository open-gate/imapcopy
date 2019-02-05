package biz.opengate.imapCopy.model;

import java.util.List;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;

public class FolderMeta implements Comparable<FolderMeta> {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION	

	private Folder folder;
	private String completePath;

	public FolderMeta(Folder folder) {
		this.folder=folder;
		this.completePath=folder.getFullName();
	}
	

	///////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES

	/**convert the messages list into an array and append it to the destination imap*/
	public void appendMessages(List<MessageMeta> messageList) throws MessagingException {
		try {				
			getFolder().open(Folder.READ_WRITE);
			Message[] messageArray=new Message[messageList.size()];
			int index=0;
			for (MessageMeta messageMeta: messageList) {					
				messageArray[index++]=messageMeta.getMessage();
			}
			getFolder().appendMessages(messageArray);
		}
		finally {
			getFolder().close();
		}
	}
	
	@Override
	public int compareTo(FolderMeta o) {
		return completePath.compareTo(o.completePath);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((completePath == null) ? 0 : completePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		FolderMeta other = (FolderMeta) obj;
		
		if (completePath == null) {
			if (other.completePath != null) {
				return false;
			}
		} 
		else if (!completePath.equals(other.completePath)) {
			return false;
		}
		return true;
	}


	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS
	
	public Folder getFolder() {
		return folder;
	}

	public void setFolder(Folder folder) {
		this.folder = folder;
	}
	
	public String getCompletePath() {
		return completePath;
	}
	
	public void setCompletePath(String completePath) {
		this.completePath = completePath;
	}
}

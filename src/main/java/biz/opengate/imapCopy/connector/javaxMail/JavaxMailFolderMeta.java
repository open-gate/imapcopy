package biz.opengate.imapCopy.connector.javaxMail;

import java.util.ArrayList;

import javax.mail.Folder;
import javax.mail.MessagingException;

import biz.opengate.imapCopy.connector.FolderMeta;

public class JavaxMailFolderMeta extends FolderMeta {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION	

	private Folder folder;
	
	public JavaxMailFolderMeta(Folder folder) throws MessagingException {
		this.folder=folder;
		setCompletePath(folder.getFullName());
		
		String[] components=this.getCompletePath().split(""+folder.getSeparator());
		setPathList(new ArrayList<String>(components.length));

		for (String s: components) {
			getPathList().add(s);
		}
	}

	
	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS
	
	public Folder getFolder() {
		return folder;
	}

	public void setFolder(Folder folder) {
		this.folder = folder;
	}
}

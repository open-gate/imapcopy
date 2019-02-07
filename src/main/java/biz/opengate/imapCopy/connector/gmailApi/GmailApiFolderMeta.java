package biz.opengate.imapCopy.connector.gmailApi;

import java.util.ArrayList;

import com.google.api.services.gmail.model.Label;

import biz.opengate.imapCopy.connector.FolderMeta;

public class GmailApiFolderMeta extends FolderMeta {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION	

	private Label label;
	
	public GmailApiFolderMeta(Label label) {
		setLabel(label);
		setCompletePath(label.getName());
		
		String[] components=this.getCompletePath().split("/");
		setPathList(new ArrayList<String>(components.length));

		for (String s: components) {
			getPathList().add(s);
		}
	}


	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS

	public Label getLabel() {
		return label;
	}

	public void setLabel(Label label) {
		this.label = label;
	}
}

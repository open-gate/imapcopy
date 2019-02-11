package biz.opengate.imapCopy.connector.gmailApi;

public class ReservedFolderNameException extends Exception {
	private static final long serialVersionUID = 1L;

	ReservedFolderNameException(Exception e) {
		super(e);
	}
}

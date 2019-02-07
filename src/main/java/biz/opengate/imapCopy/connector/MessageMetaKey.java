package biz.opengate.imapCopy.connector;

public class MessageMetaKey extends MessageMeta {
	public MessageMetaKey(String messageId) {
		setMessageId(messageId);
	}
	
	public FolderMeta getFolderMeta() {
		throw new UnsupportedOperationException();
	}
}

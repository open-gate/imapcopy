package biz.opengate.imapCopy.connector;

public abstract class MessageMeta implements Comparable<MessageMeta> {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION
	
	private String messageId;
	
	
	///////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((messageId == null) ? 0 : messageId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		MessageMeta other = (MessageMeta) obj;
		if (messageId == null) {
			if (other.messageId != null) {
				return false;
			}
		} 
		else if (!messageId.equals(other.messageId)) {
			return false;
		}
		return true;
	}

	@Override
	public int compareTo(MessageMeta o) {
		return messageId.compareTo(o.messageId);
	}
	
	
	///////////////////////////////////////////////////////////////////////////////////////
	//	GETTERS / SETTERS
	
	public abstract FolderMeta getFolderMeta() throws Exception;
	
	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId=messageId;;
	}
}

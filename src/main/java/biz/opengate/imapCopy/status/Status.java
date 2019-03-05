package biz.opengate.imapCopy.status;

import java.util.LinkedList;
import java.util.List;

public class Status {

	private List<StatusElement> completedElements=new LinkedList<StatusElement>();

	public List<StatusElement> getCompletedElements() {
		return completedElements;
	}

	public void setCompletedElements(List<StatusElement> completedElements) {
		this.completedElements = completedElements;
	}	
}

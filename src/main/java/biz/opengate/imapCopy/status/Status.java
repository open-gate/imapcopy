package biz.opengate.imapCopy.status;

import java.util.LinkedList;
import java.util.List;

public class Status {
	private long totalExecutionTimeMs=0;
	private List<StatusElement> completedElements=new LinkedList<StatusElement>();

	public List<StatusElement> getCompletedElements() {
		return completedElements;
	}

	public void setCompletedElements(List<StatusElement> completedElements) {
		this.completedElements = completedElements;
	}

	public long getTotalExecutionTimeMs() {
		return totalExecutionTimeMs;
	}

	public void setTotalExecutionTimeMs(long totalExecutionTimeMs) {
		this.totalExecutionTimeMs = totalExecutionTimeMs;
	}
}

package biz.opengate.imapCopy.connector;

import java.util.List;

public abstract class FolderMeta implements Comparable<FolderMeta> {
	///////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION	

	private String completePath;
	private List<String> pathList;

	
	///////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES
	
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

	public String getCompletePath() {
		return completePath;
	}
	
	public void setCompletePath(String completePath) {
		this.completePath = completePath;
	}

	public List<String> getPathList() {
		return pathList;
	}

	public void setPathList(List<String> pathList) {
		this.pathList = pathList;
	}
}

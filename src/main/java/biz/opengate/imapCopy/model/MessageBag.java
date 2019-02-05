package biz.opengate.imapCopy.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Folder;

public class MessageBag {
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION
	
	private Map<FolderMeta, List<MessageMeta>> map=new HashMap<FolderMeta, List<MessageMeta>>();
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES
	
	public List<MessageMeta> get(FolderMeta key) {
		return map.get(key);
	}
	
	public Set<FolderMeta> keySet() {
		return map.keySet();
	}
		
	public void push(MessageMeta messageMeta) {
		Folder folder = messageMeta.getMessage().getFolder();
		FolderMeta key=new FolderMeta(folder);
		push(key,messageMeta);
	}
	
	public void push(FolderMeta key, MessageMeta messageMeta) {
		List<MessageMeta> list = map.get(key);
		
		if (list==null) {
			list=new LinkedList<MessageMeta>();
			map.put(key, list);
		}

		list.add(messageMeta);		
	}
}

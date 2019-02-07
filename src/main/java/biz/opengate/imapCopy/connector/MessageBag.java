package biz.opengate.imapCopy.connector;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		
	public void push(MessageMeta messageMeta) throws Exception {
		FolderMeta key=messageMeta.getFolderMeta();
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

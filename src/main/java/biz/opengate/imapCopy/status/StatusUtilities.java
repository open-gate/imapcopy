package biz.opengate.imapCopy.status;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.cli.ParseException;

import biz.opengate.imapCopy.DateUtilities;
import biz.opengate.imapCopy.Utilities;

public class StatusUtilities {
	private static File statusFilesDir;
	private static String statusFileName;
	public static Status status;
	
	public static void setCompleted(Date date, String folder) throws FileNotFoundException {
		final int dateInt=DateUtilities.getYearMonthDay(date);
		StatusElement element=new StatusElement();
		element.setDate(dateInt);
		element.setFolder(folder);
		status.getCompletedElements().add(element);		
		save();
	}
	
	public static boolean isCompleted(Date date, String folder) throws FileNotFoundException {
		final int dateInt=DateUtilities.getYearMonthDay(date);
		for (StatusElement element: status.getCompletedElements()) {
			if (element.getDate()==dateInt && folder.equals(element.getFolder())) return true;
		}
		return false;
	}

	public static void load() throws IOException {
		if (!getStatusFile().exists()) {
			status=new Status();
			return;
		}
		status=Utilities.unmarshall(getStatusFile(), Status.class);
	}
	
	private static void save() throws FileNotFoundException {
		Utilities.marshall(getStatusFile(), status);
	}

	private static File getStatusFile() {
		return new File(statusFilesDir,statusFileName);
	}
	
	public static void setStatusFilesDir(String dir) throws Exception {
		statusFilesDir=new File(dir);
        if (!statusFilesDir.exists()) {
        	throw new ParseException("status file directory does not exists");
        }
	}
	
	public static void setStatusFileName(String name) {
		statusFileName=name;
	}
}

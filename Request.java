package JavaProject;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.JList;

public class Request implements Serializable
{
	private static final long serialVersionUID = 0xDEADBEEF;

	//User data
	ArrayList<String> UserList;
	String user;
		
	//Request flags
	boolean SendFiles;
	boolean SendFileToServer;
	boolean DeleteFileFromServer;
	boolean GetFiles;
	boolean GetUserList;
	boolean UpdateLocalDir;
	
	//For sending a single file to a different user
	File fileToSend;
	byte [] fileToSendData;
	String deletedFileName;
	
	//For synchronization and directory creation/management
	File [] fileList;
	ArrayList<byte[]> fileContentList;

}

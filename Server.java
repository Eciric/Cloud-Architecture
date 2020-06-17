package JavaProject;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import JavaProject.Request;


class ClientHandler extends Thread implements Serializable 
{
	private static final long serialVersionUID = 0xFEEDBEEF;
	
	Socket clientSocket;
	Request clientRequest;
	ObjectOutputStream out;
	ObjectInputStream in;
	boolean userListChanged;
	String userName;
	File f;
	
	
	//GUI part
	@SuppressWarnings("rawtypes")
	DefaultListModel fileModel;
	JPanel panel3;
	JPanel panel4;
	JList list;
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	ClientHandler(Socket cS)
	{
		clientSocket = cS;
		
		try {
			in = new ObjectInputStream(clientSocket.getInputStream());
			out = new ObjectOutputStream(clientSocket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			clientRequest = (Request) in.readObject();
		} catch (Exception e) {
			System.out.println("Exception occured at login with client");
		}
		
		userName = clientRequest.user;
		Server.userList.add(userName);
		userListChanged = true;
		
		
		
		//Check if that users directory already exists, otherwise create one
		f = new File("userDirectories/"+userName+"Dir");
		if (f.exists() == false)
		{
			System.out.println("Made a directory");
			f.mkdirs();	
			try {

				for (int i = 0; i < clientRequest.fileContentList.size(); i++)
				{
					String path = "userDirectories/"+userName+"Dir/"+(clientRequest.fileList[i].getName());
					System.out.println(path);
					Files.write(Paths.get(path), clientRequest.fileContentList.get(i));
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
		{
			//Check if the existing directory is synchronized with
			//Users local directory
			File [] serverFileList = new File("userDirectories/"+userName+"Dir").listFiles();
			
			//Need to update the clients local directory
			if (serverFileList.length > clientRequest.fileList.length)
			{
				Request newReq = new Request();
				newReq.UpdateLocalDir = true;
				File [] newFileList = serverFileList;
				ArrayList<byte[]> newListOfFilesContents = new ArrayList<byte[]>();
				for (int i = 0; i < serverFileList.length; i++)
				{
					String filePath = newFileList[i].getPath();
					
					Path npath = Paths.get(filePath);
				    byte[] data = null;
					try {
						data = Files.readAllBytes(npath);
					} catch (IOException e) {
						e.printStackTrace();
					}
				    newListOfFilesContents.add(data);
				}
				newReq.fileContentList = newListOfFilesContents;
				newReq.fileList = newFileList;
				
				try {
					out.writeObject(newReq);
					out.flush();	
				} catch (Exception e1) {
					System.out.println("Failed to send new file list to client");
				}
				
			}
			
			//Need to update the clients directory on server side
			if (serverFileList.length < clientRequest.fileList.length)
			{
				for (int i = 0; i < clientRequest.fileContentList.size(); i++)
				{
					String path = "userDirectories/"+userName+"Dir/"+(clientRequest.fileList[i].getName());
					System.out.println(path);
					try {
						Files.write(Paths.get(path), clientRequest.fileContentList.get(i));
					} catch (IOException e) {
						System.out.println("Failed to update clients directory on server");
					}
				}
			}
		}
		
		//Create clients file list window
		File [] fileListForGUI = new File("userDirectories/"+userName+"Dir").listFiles();
		fileModel = new DefaultListModel();
		for (int i = 0; i < fileListForGUI.length; i++)
		{
			fileModel.addElement(fileListForGUI[i]);
		}
		list = new JList(fileModel);
		
		list.setLayoutOrientation(JList.VERTICAL);
		
		JScrollPane scrollPane = new JScrollPane();
	    scrollPane.setViewportView(list);
	    
	    //Create the user files list part
	    panel3 = new JPanel(new FlowLayout());
		panel4 = new JPanel(new FlowLayout());
		
		JLabel label2 = new JLabel(userName + "'s files");
		label2.setFont(new Font("Arial", Font.BOLD, 16));

		scrollPane.setPreferredSize(new Dimension (175, 250));
		panel3.setPreferredSize(new Dimension(200, 200));
		panel4.setPreferredSize(new Dimension(150, 20));

		panel3.add(scrollPane, BorderLayout.CENTER);
		panel4.add(label2, BorderLayout.NORTH);
		Server.mainCenterPanel.add(panel3, BorderLayout.CENTER);
		Server.mainCenterPanel.add(panel4, BorderLayout.NORTH);
				
	}
	
	public void run()
	{
		try
		{
			new Thread(() -> {
				while (true)
				{
					if (userListChanged)
					{
						System.out.println("UserList change detected in " + userName + "'s thread");
						Request newResponse = new Request();
						newResponse.GetUserList = true;
						ArrayList<String> aL = new ArrayList<String>();
						for (int i = 0; i < Server.userList.size(); i++)
						{
							aL.add(Server.userList.get(i));
						}
						newResponse.UserList = aL;
						System.out.println("Sending updated list:" + newResponse.UserList);
						
						try {
							out.writeObject(newResponse);
							out.flush();
						} catch (IOException e) {
							System.out.println("Failed to send updated userList to client");
							break;
						}
						userListChanged = false;
					}	
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}
				}
			}).start();

			while (true)
			{
				//Parse client objects
				try {
					clientRequest = (Request) in.readObject();
				} catch(Exception e) {
					Server.addMessage(userName + " disconnected");
					System.out.println("Removing from list" + userName);
					for (int i = 0; i < Server.userList.size(); i++)
					{
						String name = Server.userList.get(i);
						System.out.println("Name: " + name);
						if (name == userName)
						{
							System.out.println("Attempting to remove"+name);
							Server.userList.remove(i);
							Server.globalUserListChanged = true;
							break;
						}
					}
					Server.globalUserListChanged = true;
					Server.mainCenterPanel.remove(panel3);
					Server.mainCenterPanel.remove(panel4);
					System.out.println("Connection reset or reading client object failed, closing thread");
					break;
				}
				
				//Client requested to send a file to a different user
				if (clientRequest.SendFiles)
				{
					//Validate user
					if (userName != clientRequest.user)
					{
						System.out.println("Client requested to send file");
						
						for (int i = 0; i < Server.threads.size(); i++)
						{
							ClientHandler cH = Server.threads.get(i);
							if (clientRequest.user.equals(cH.userName))
							{
								System.out.println("Found user");
								if (cH.sendFileToClient(clientRequest.fileToSendData, clientRequest.fileToSend))
								{
									Server.addMessage(userName + " sent file to " + clientRequest.user);
								}
								else Server.addMessage("Failed to send file requested by " + userName);
								break;
							}
						}
					}
				}
				
				if (clientRequest.DeleteFileFromServer)
				{
					String path = "userDirectories/"+userName+"Dir/"+(clientRequest.deletedFileName);
					try {
						Files.delete(Paths.get(path));
					} catch (IOException e) {
						System.out.println("Failed to delete a file as requested by client");
					}
					updateFileList(fileModel, "userDirectories/"+userName+"Dir", list);
					Server.addMessage("Updated file list for "+userName);
				}
					
				if (clientRequest.SendFileToServer)
				{
					System.out.println("SendFileToServer triggered");
					String path = "userDirectories/"+userName+"Dir/"+(clientRequest.fileToSend.getName());
					try {
						Files.write(Paths.get(path), clientRequest.fileToSendData);
					} catch (IOException e) {
						System.out.println("Failed to create or modify file as requested by client");
					}
					updateFileList(fileModel, "userDirectories/"+userName+"Dir", list);
					Server.addMessage("Updated file list for "+userName);
				}
			
			}
		}
		catch (Exception e)
		{
			System.out.println("Connection reset, closing thread");
		}

	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void updateFileList(DefaultListModel fileList, String path, JList list)
	{
		fileList.clear();
		File [] listOfFiles2 = new File(path).listFiles();
		System.out.println(listOfFiles2.length);
		for (int i = 0; i < listOfFiles2.length;i++)
		{
			fileList.addElement(listOfFiles2[i]);
		}
		list.repaint();
	}
	
	public boolean sendFileToClient(byte[] data, File f)
	{
		Request newResponse = new Request();
		newResponse.fileToSendData = data;
		newResponse.fileToSend = f;
		newResponse.SendFiles = true;
		
		try {
			out.writeObject(newResponse);
			out.flush();
		} catch (IOException e) {
			System.out.println("Failed to send file to user");
			return false;
		}
		Server.addMessage("Sent file to client: " + userName);
		return true;
	}
}

public class Server 
{
	public static ArrayList<String> userList = new ArrayList<String>();
	public static boolean globalUserListChanged = false;
	public static ArrayList<ClientHandler> threads = new ArrayList<ClientHandler>();
	
	
	@SuppressWarnings("rawtypes")
	static DefaultListModel model = new DefaultListModel();
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static JList serverList = new JList(model);
	//Create the main panels of layout
	static JPanel mainLeftPanel = new JPanel(new BorderLayout());
	static JPanel mainCenterPanel = new JPanel(new BorderLayout());
	static JPanel mainRightPanel = new JPanel(new BorderLayout());
	static JPanel mainDownPanel = new JPanel(new BorderLayout());
	static JPanel mainUpPanel = new JPanel(new FlowLayout());
	
	@SuppressWarnings("unchecked")
	public static void main(String [] args)
	{
		try
		{
			@SuppressWarnings("resource")
			ServerSocket serverSocket = new ServerSocket(4999);
			
			//GUI
			//Create the JFrame window
			JFrame serverWindow = createWindow(800, 500, "Server window");
					
			//Thread for refreshing the window
			Refresher rThread = new Refresher(serverWindow);
			rThread.start();
			
			//Set preferred sizes
			mainUpPanel.setPreferredSize(new Dimension(500, 70));
			mainRightPanel.setPreferredSize(new Dimension(250, 350));
			mainLeftPanel.setPreferredSize(new Dimension(300, 350));
			mainCenterPanel.setPreferredSize(new Dimension(500, 500));

			serverWindow.add(mainUpPanel, BorderLayout.NORTH);
			serverWindow.add(mainLeftPanel, BorderLayout.WEST);
			serverWindow.add(mainCenterPanel, BorderLayout.CENTER);
			serverWindow.add(mainRightPanel, BorderLayout.EAST);
			serverWindow.add(mainDownPanel, BorderLayout.SOUTH);

			
			//Create server responses list
			createClientServerList(serverList, mainLeftPanel);
			addMessage("Server window initialized");
			
			//Main server loop
			while (true)
			{
				Socket socket = serverSocket.accept();
				ClientHandler cH = new ClientHandler(socket);
				threads.add(cH);
				cH.start();
				
				globalUserListChanged = true;
				
				new Thread (() -> {
					Thread.currentThread().setName("serverUserListWatcherThread");
					while (true)
					{
						if (globalUserListChanged)
						{
							for (int i = 0; i < threads.size(); i++)
							{
								threads.get(i).userListChanged = true;
							}
							
							while (true)
							{
								int threadsUpdated = 0;
								for (int i = 0; i < threads.size(); i++)
								{
									boolean test = threads.get(i).userListChanged;
									if (test == false) threadsUpdated++; 
								}
								if (threadsUpdated == threads.size())
								{
									globalUserListChanged = false;
									break;
								}
							}

						}
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

					}			
				}).start();
				
				
				String newClientName = threads.get(threads.size() - 1).userName;
				addMessage(newClientName + " connected");			
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	
	public static JFrame createWindow(int width, int height, String name)
	{
		JFrame window = new JFrame(name);
		
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);		
		window.setLayout(new BorderLayout());
		window.setVisible(true);
		window.setSize(width, height);
		
		return window;
	}
	
	public static void createClientFilesList(JList<File> list, JPanel Panel, String labelMessage)
	{
		list.setLayoutOrientation(JList.VERTICAL);
		
		JScrollPane scrollPane = new JScrollPane();
	    scrollPane.setViewportView(list);
	    
	    //Create the user files list part
	    JPanel panel3 = new JPanel(new FlowLayout());
		JPanel panel4 = new JPanel(new FlowLayout());
		
		JLabel label2 = new JLabel(labelMessage);
		label2.setFont(new Font("Arial", Font.BOLD, 16));

		scrollPane.setPreferredSize(new Dimension (175, 250));
		panel3.setPreferredSize(new Dimension(200, 200));
		panel4.setPreferredSize(new Dimension(150, 20));

		panel3.add(scrollPane, BorderLayout.CENTER);
		panel4.add(label2, BorderLayout.NORTH);
		Panel.add(panel3, BorderLayout.CENTER);
		Panel.add(panel4, BorderLayout.NORTH);
	}
	
	public static void createClientServerList(JList<ArrayList<String>> list, JPanel mainLeftPanel)
	{
		list.setLayoutOrientation(JList.VERTICAL);
		
		JScrollPane scrollPane = new JScrollPane();
	    scrollPane.setViewportView(list);
	    
	    //Create the user files list part
	    JPanel panel3 = new JPanel(new FlowLayout());
		JPanel panel4 = new JPanel(new FlowLayout());
		
		JLabel label2 = new JLabel("Server messages");
		label2.setFont(new Font("Arial", Font.BOLD, 16));

		scrollPane.setPreferredSize(new Dimension (255, 250));
		panel3.setPreferredSize(new Dimension(285, 200));
		panel4.setPreferredSize(new Dimension(150, 25));

		panel3.add(scrollPane, BorderLayout.CENTER);
		panel4.add(label2, BorderLayout.NORTH);
		mainLeftPanel.add(panel3, BorderLayout.CENTER);
		mainLeftPanel.add(panel4, BorderLayout.NORTH);
	}
	
	public static String getCurTime()
	{
		Date dNow = new Date();
	    SimpleDateFormat ft = new SimpleDateFormat ("HH:mm:ss");
	    return ft.format(dNow);
	}
	
	@SuppressWarnings("unchecked") 
	public static void addMessage(String message)
	{
	    String curTime = getCurTime();
		
		model.addElement("[" + curTime + "] " + message);
		serverList.setModel(model);
	}
	
	
}

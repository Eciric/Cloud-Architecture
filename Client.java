package JavaProject;

import JavaProject.Request;

import java.io.*;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class DirectoryWatcher extends Thread
{
	String dirName;
	
	DirectoryWatcher(String dN)
	{
		dirName = dN;
	}
	
	public void run()
	{
		try (WatchService service = FileSystems.getDefault().newWatchService())
		{
			Map<WatchKey, Path> keyMap = new HashMap<>();
			Path path = Paths.get(dirName);
			keyMap.put(path.register(service,
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_DELETE,
					StandardWatchEventKinds.ENTRY_MODIFY), path);
			
			WatchKey watchKey;
			
			do
			{
				watchKey = service.take();
				
				for (WatchEvent<?> event : watchKey.pollEvents())
				{
					WatchEvent.Kind<?> kind = event.kind();
					Path eventPath = (Path)event.context();
					
					File tempFile = eventPath.toFile();
					if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
						Client.deleteFileFromServer(tempFile.getName());
					}
					
					if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
						Client.uploadFileToServer(tempFile.getName());
					}
				
					if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
						Client.uploadFileToServer(tempFile.getName());
					}
				}

			} while (watchKey.reset());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}

public class Client 
{
	private static ObjectOutputStream out;
	private static String path;
	private static JList list;
	private static DefaultListModel fileList;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String [] args)
	{
		try 
		{
			if (args.length < 2)
			{
				System.out.println("Failed to provide 2 arguments");
				System.exit(1);
			}

			String login = args[0];
			path = args[1];
			
			//Create local client thread for watching over the local directory
			DirectoryWatcher dirWatcher = new DirectoryWatcher(path);	
			dirWatcher.start();
			
			//Create the JFrame window
			JFrame clientWindow = createWindow(800, 500, login + "'s window");
					
			//Thread for refreshing the window
			Refresher rThread = new Refresher(clientWindow);
			rThread.start();
			
			//Get list of files
			fileList = new DefaultListModel();
			File[] listOfFiles = new File(path).listFiles();
			ArrayList<byte[]> listOfFilesContents = new ArrayList<byte[]>();
			for (int i = 0; i < listOfFiles.length; i++)
			{
				fileList.addElement(listOfFiles[i]);
				String filePath = listOfFiles[i].getPath();
				
				
				Path npath = Paths.get(filePath);
			    byte[] data = Files.readAllBytes(npath);
			    listOfFilesContents.add(data);
			}
			
			
			list = new JList(fileList);

		    //Create the main panels of layout
		    JPanel mainLeftPanel = new JPanel(new BorderLayout());
			JPanel mainCenterPanel = new JPanel(new BorderLayout());
			JPanel mainRightPanel = new JPanel(new BorderLayout());
			JPanel mainDownPanel = new JPanel(new BorderLayout());
			JPanel mainUpPanel = new JPanel(new FlowLayout());
			
			mainUpPanel.setPreferredSize(new Dimension(800, 70));
			mainRightPanel.setPreferredSize(new Dimension(250, 350));
			mainLeftPanel.setPreferredSize(new Dimension(300, 350));
			mainCenterPanel.setPreferredSize(new Dimension(250, 350));

			clientWindow.add(mainUpPanel, BorderLayout.NORTH);
			clientWindow.add(mainLeftPanel, BorderLayout.WEST);
			clientWindow.add(mainCenterPanel, BorderLayout.CENTER);
			clientWindow.add(mainRightPanel, BorderLayout.EAST);
			clientWindow.add(mainDownPanel, BorderLayout.SOUTH);

			
			//Create the welcome message and client's files list
			JLabel label = new JLabel("Welcome " + login);
			label.setFont(new Font("Arial", Font.PLAIN, 20));
			
			mainUpPanel.add(label);
			createClientFilesList(list, mainRightPanel, "Your files");

					
			//Create server responses list
			DefaultListModel model = new DefaultListModel();
			JList serverList = new JList(model);
			createClientServerList(serverList, mainLeftPanel);

			addMessage(serverList, "Client window initialized", model);
			
			//Create the panel of other users
			DefaultListModel model2 = new DefaultListModel();
			JList usersList = new JList(model2);
			createClientFilesList(usersList, mainCenterPanel, "Other users");
			
			//JPanel modifying to position the send file JButton correctly
			JPanel downPanelWest = new JPanel(new FlowLayout());
			JPanel downPanelCenter = new JPanel(new FlowLayout());
			JPanel downPanelEast = new JPanel(new FlowLayout());
			downPanelEast.setPreferredSize(new Dimension(180, 65));
			mainDownPanel.add(downPanelWest, BorderLayout.WEST);
			mainDownPanel.add(downPanelCenter, BorderLayout.CENTER);
			mainDownPanel.add(downPanelEast, BorderLayout.EAST);
			
			//Create send file button
			JButton sendFileButton = new JButton("Send File");
			sendFileButton.setPreferredSize(new Dimension(100, 50));
			downPanelEast.add(sendFileButton, BorderLayout.CENTER);
			
			//Initialize server connection
			Socket socket = new Socket("localhost", 4999);
			addMessage(serverList, "Server connection established", model);
			
			out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
						
			//Handshake and login to server
			Request loginMessage = setRequestUserList(login);
			loginMessage.fileList = listOfFiles;
			loginMessage.fileContentList = listOfFilesContents;
			
			try {
				out.writeObject(loginMessage);
				out.flush();
			}
			catch (Exception e)
			{
				System.out.println("Couldnt login with server");
			}
			
			
			//Create a thread responsible for listening to server responses
			new Thread (() -> {
				Thread.currentThread().setName("clientListeningThread");
				
				while (true)
				{
					Request serverMessage = null;
					try {
						serverMessage = (Request) in.readObject();
					} catch (ClassNotFoundException | IOException e) {
						System.out.println("Server disconnected");
						addMessage(serverList, "Server disconnected", model);
					}
					
					if (serverMessage == null)
					{
						int line = Thread.currentThread().getStackTrace()[0].getLineNumber();
						System.out.println("Server sent null object, error detected in line: " + line);
						break;
					}
					
					//Server sent updated userList
					if (serverMessage.GetUserList)
					{
						System.out.println("Server sent updated userList!");
						System.out.println("UserList is:" + serverMessage.UserList);
						model2.clear();
						for (int i = 0; i < serverMessage.UserList.size(); i++)
						{
							model2.addElement(serverMessage.UserList.get(i));
						}
						usersList.repaint();
						addMessage(serverList, "User list changed", model);
					}
					
					//Server sent a file to add to local directory
					if (serverMessage.SendFiles)
					{
						System.out.println("New file arrived");
						byte[] newFileData = serverMessage.fileToSendData;
						File newFile = serverMessage.fileToSend;
						
						String nPath = new File(path).getPath();
						Path npath = Paths.get(nPath+"/"+newFile.getName());
						try {
							Files.write(npath, newFileData);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						updateFileList(fileList, path, list);
						addMessage(serverList, "Downloaded new file", model);
					}
					
					if (serverMessage.UpdateLocalDir)
					{
						System.out.println("Updating local directory");
						
						for (int i = 0; i < serverMessage.fileContentList.size(); i++)
						{
							try {
								Files.write(Paths.get(path+"/"+serverMessage.fileList[i].getName()), serverMessage.fileContentList.get(i));
							} catch (IOException e1) {
								System.out.println("Failed to update the local directory");
							}
						}
						updateFileList(fileList, path, list);
						addMessage(serverList, "Updated file list", model);
					}
					
				}
	
			}).start();

			//Implement ActionListener for button to send files
			sendFileButton.addActionListener(new ActionListener() 
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					//If the button is clicked user wants to send a file to
					//a different user, we need to get the user name and filename
					String userSelected = (String) usersList.getSelectedValue();
					System.out.println(userSelected);
					File fileSelected = (File) list.getSelectedValue();
					System.out.println(fileSelected.getName());
					
					//Get the contents of that file
					Path npath = Paths.get(fileSelected.getPath());
				    byte[] data = null;
					try {
						data = Files.readAllBytes(npath);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
			
				    
				    //Now once the name of requested user and file is known
				    //and the contents of that file are read and stored
				    //we can initialize a Request object with the data
				    //and send it to the server, where it handles the rest.
					Request sendFileRequest = setRequestSendFile(data, userSelected, fileSelected);
					try {
						out.writeObject(sendFileRequest);
						out.flush();
					}
					catch (Exception e1)
					{
						System.out.println("Couldnt send request file to server");
					}
				}
				
			});

			
			Scanner s = new Scanner(System.in);
			s.nextLine();
			s.close();
			socket.close();
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		
	}
	
	public static void deleteFileFromServer(String fileName)
	{
		Request newReq = new Request();
		newReq.deletedFileName = fileName;
		newReq.DeleteFileFromServer = true;
	
		try {
			out.writeObject(newReq);
			out.flush();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		updateFileList(fileList, path, list);
	}
	
	public static void uploadFileToServer(String fileName)
	{
		System.out.println("created triggered");
		//New file has been created
		String nPath = new File(path).getPath();
		Path npath = Paths.get(nPath+"/"+fileName);
		byte[] data = null;
		try {
			data = Files.readAllBytes(npath);
		} catch (IOException e1) {
			System.out.println("Failed to read new File");
		}
		
		Request newReq = new Request();
		newReq.SendFileToServer = true;
		newReq.fileToSendData = data;
		newReq.fileToSend = npath.toFile();
		try {
			System.out.println("Writing object");
			out.writeObject(newReq);
			out.flush();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		updateFileList(fileList, path, list);
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
	
	public static void createClientFilesList(JList list, JPanel Panel, String labelMessage)
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
	
	@SuppressWarnings({ "rawtypes", "unchecked" }) 
	public static void addMessage(JList serverList, String message, DefaultListModel model)
	{
	    String curTime = getCurTime();
		
		model.addElement("[" + curTime + "] " + message);
		serverList.setModel(model);
	}
	
	public static Request setRequestUserList(String login)
	{
		Request newRequest = new Request();
		newRequest.GetUserList = true;
		newRequest.user = login;
		return newRequest;
	}
	
	public static Request setRequestSendFile(byte[] data, String userName, File file)
	{
		Request newRequest = new Request();
		newRequest.fileToSend = file;
		newRequest.SendFiles = true;
		newRequest.user = userName;
		newRequest.fileToSendData = data;
		return newRequest;
	}
	
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
}

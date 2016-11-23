import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;

import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.RowSpec;
import com.sun.jna.NativeLibrary;

import Client.TCPClient;
import Server.TCPServer;
import Shared.TCPPacketBeginPlay;
import Shared.TCPPacketChat;
import Shared.TCPPacketTransfer;
import Shared.TCPPacketTransferStatus;
import Shared.TCPPayload;
import Shared.TCPPayload.PayloadType;
import StateMachine.StateMachine;
import StateMachine.StateMachineBuilder;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

import com.google.common.base.Charsets;
import com.jgoodies.forms.factories.FormFactory;
import org.slf4j.*;

import javax.swing.JButton;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class WindowFrame {
	//Top Level Components
	private JFrame frame;
	private JPanel panelVLC, panelWeb;
	private JTabbedPane tabbedPane;
	
	//Web tab components
	private JTextField txtPort;
	private JButton btnStartServ = null;
	private JButton btnStopServ = null;
	private JLabel lblPort = null;
	private JTextField txtServerAddress;
	private JButton btnSendFile;
	private JLabel lbltxtIp;
	private JLabel lblLocalIP;
	private JButton btnCheckIp;
	private JButton btnMessageServer;
	private JButton btnMessageClient;
	//end web tab
	
	//Media player tab components
	private EmbeddedMediaPlayer vlc;
	//end media tab
	
	enum ServerState{
		DISCONNECTED,STARTING,IDLE,SENDINGCHAT, SENDINGMEDIA, AWAITING_MEDIA_RECV, PLAYINGMEDIA,STOPPING
	}
	
	enum ServerEvent{
		START,STOP,SENDMSG, SENDMEDIA, SUCCESS, FAIL
	}
	
	enum ClientState{
		CONNECTING, IDLE, SENDINGCHAT, SAVINGMEDIA, AWAITING_PLAY, PLAYINGMEDIA, DISCONNECTING, DISCONNECTED
	}
	
	enum ClientEvent{
		CONNECT, DISCONNECT, SENDMSG, RECEIVE_MEDIA, PLAY, SUCCESS,FAIL
	}
	
	private StateMachine<ServerState,ServerEvent,TCPPayload> serverFSM = null;
	private StateMachine<ClientState,ClientEvent,TCPPayload> clientFSM = null;
	
	private TCPServer server = new TCPServer();
	private TCPClient client = new TCPClient();
	
	private AtomicInteger awaitingSuccessCount = new AtomicInteger(0);
	
	private File servermediaFile = null;
	private File clientmediaFile = null;

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					WindowFrame window = new WindowFrame();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public WindowFrame() {
		serverFSM = new StateMachineBuilder<ServerState,ServerEvent,TCPPayload>(ServerState.DISCONNECTED)
				//Define the FSM transitions
				.addTransition(ServerState.DISCONNECTED, ServerEvent.START, ServerState.STARTING) //If told to start then start
				.addTransition(ServerState.STARTING, ServerEvent.SUCCESS, ServerState.IDLE) //if start succeeded go to idle
				.addTransition(ServerState.STARTING, ServerEvent.FAIL, ServerState.DISCONNECTED)
				
				.addTransition(ServerState.IDLE, ServerEvent.SENDMSG, ServerState.SENDINGCHAT) //Send a message if requested
				.addTransition(ServerState.SENDINGCHAT, ServerEvent.SUCCESS, ServerState.IDLE) 
				.addTransition(ServerState.SENDINGCHAT, ServerEvent.FAIL, ServerState.IDLE) //Go back to idle regardless if it sent successfully
				.addTransition(ServerState.IDLE,ServerEvent.SENDMEDIA,ServerState.SENDINGMEDIA)
				.addTransition(ServerState.SENDINGMEDIA, ServerEvent.SUCCESS, ServerState.AWAITING_MEDIA_RECV) //If file sent successfully wait for clients to verify they received
				.addTransition(ServerState.SENDINGMEDIA, ServerEvent.FAIL, ServerState.IDLE) //otherwise idle
				.addTransition(ServerState.AWAITING_MEDIA_RECV, ServerEvent.SUCCESS, ServerState.PLAYINGMEDIA) //If everyone responded play it
				.addTransition(ServerState.AWAITING_MEDIA_RECV, ServerEvent.FAIL, ServerState.IDLE) //otherwise idle
				
				.addTransition(ServerState.IDLE, ServerEvent.STOP, ServerState.STOPPING) //if idle and told to stop, stop
				.addTransition(ServerState.STOPPING, ServerEvent.SUCCESS, ServerState.DISCONNECTED)
				//.addTransition(ServerState.STOPPING, ServerEvent.FAIL, ServerState.IDLE) purposely throw exception in this case
				
				//Our Callbacks
				.onEnter(ServerState.STARTING, ()-> sEventStarting())
				.onEnter(ServerState.IDLE, ()-> sEventIdle())
				.onEnter(ServerState.SENDINGCHAT, ()-> sEventSendChat())
				.onEnter(ServerState.SENDINGMEDIA, () -> sEventSendMedia())
				.onEnter(ServerState.PLAYINGMEDIA,() -> sEventPlayMedia())
				.build();
		
		clientFSM = new StateMachineBuilder<ClientState,ClientEvent,TCPPayload>(ClientState.DISCONNECTED)
				.addTransition(ClientState.DISCONNECTED,ClientEvent.CONNECT,ClientState.CONNECTING)
				.addTransition(ClientState.CONNECTING, ClientEvent.SUCCESS, ClientState.IDLE)
				.addTransition(ClientState.CONNECTING, ClientEvent.FAIL, ClientState.DISCONNECTED)
				
				.addTransition(ClientState.IDLE, ClientEvent.SENDMSG, ClientState.SENDINGCHAT)
				.addTransition(ClientState.SENDINGCHAT, ClientEvent.SUCCESS, ClientState.IDLE)
				.addTransition(ClientState.SENDINGCHAT, ClientEvent.FAIL, ClientState.IDLE)
				.addTransition(ClientState.IDLE, ClientEvent.RECEIVE_MEDIA, ClientState.SAVINGMEDIA)
				.addTransition(ClientState.SAVINGMEDIA, ClientEvent.SUCCESS, ClientState.AWAITING_PLAY)
				.addTransition(ClientState.SAVINGMEDIA, ClientEvent.FAIL, ClientState.IDLE)
				.addTransition(ClientState.AWAITING_PLAY, ClientEvent.PLAY, ClientState.PLAYINGMEDIA)
				.addTransition(ClientState.AWAITING_PLAY, ClientEvent.FAIL, ClientState.IDLE)
				
				.addTransition(ClientState.IDLE, ClientEvent.DISCONNECT, ClientState.DISCONNECTING)
				.addTransition(ClientState.DISCONNECTING, ClientEvent.SUCCESS, ClientState.DISCONNECTED)
				//.addTransition(ClientState.DISCONNECTING, ClientEvent.FAIL, ClientState.IDLE) throw exception on purpose
				
				.onEnter(ClientState.CONNECTING, ()->cEventConnecting())
				.onEnter(ClientState.IDLE, ()-> cEventIdle())
				.onEnter(ClientState.SENDINGCHAT, ()-> cEventSendChat())
				.onEnter(ClientState.SAVINGMEDIA, ()-> cEventSavingMedia())
				.onEnter(ClientState.PLAYINGMEDIA, ()-> cEventPlayMedia())
				
				.build();
		
		initialize();
	}
	
	//UI FUNCTIONS
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());
		frame.setVisible(true);
		
		tabbedPane = new JTabbedPane();
		tabbedPane.setVisible(true);
		
		PopulateWebPanel();
		PopulateVLCPanel();
		
		tabbedPane.addTab("Connection", panelWeb);
		tabbedPane.add("Media Player", panelVLC);
		frame.add(tabbedPane);
	}

	private void PopulateWebPanel() {
		panelWeb = new JPanel();
		
		panelWeb.setLayout(new FormLayout(new ColumnSpec[] {
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				FormFactory.DEFAULT_COLSPEC,
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),},
			new RowSpec[] {
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,}));
		
		btnStartServ = new JButton("Start Server");
		btnStartServ.addActionListener(e->StartServerBtn(e));
		
		btnStopServ = new JButton("Stop Server");
		btnStopServ.addActionListener(e->StopServerBtn(e));
		btnStopServ.setEnabled(false);
		
		lbltxtIp = new JLabel("Local IP");
		panelWeb.add(lbltxtIp, "4, 2");
		
		lblLocalIP = new JLabel(server.GetLocalIP());
		panelWeb.add(lblLocalIP, "4, 4");
		
		btnMessageServer = new JButton("Message Server");
		btnMessageServer.addActionListener(e->MessageServerBtn(e));
		panelWeb.add(btnMessageServer, "2, 8");
		
		btnMessageClient = new JButton("Message Client");
		btnMessageClient.addActionListener(e->MessageClientBtn(e));
		panelWeb.add(btnMessageClient, "6, 8");
		
		btnSendFile = new JButton("Play Media File");
		btnSendFile.addActionListener(e->PlayMediaBtn(e));
		
		panelWeb.add(btnSendFile, "6, 10");
		
		JLabel lblServerAddress = new JLabel("Server Address");
		panelWeb.add(lblServerAddress, "2, 14, center, default");
		
		lblPort = new JLabel("Server Port");
		panelWeb.add(lblPort, "6, 14, center, default");
		
		txtServerAddress = new JTextField();
		txtServerAddress.setText("127.0.0.1");
		panelWeb.add(txtServerAddress, "2, 16, center, default");
		txtServerAddress.setColumns(10);
		
		txtPort = new JTextField();
		txtPort.setText("123");
		
		panelWeb.add(txtPort, "6, 16, center, default");
		txtPort.setColumns(10);
		
		JButton btnStartClient = new JButton("Start Client");
		btnStartClient.addActionListener(e->StartClientBtn(e));
		
		panelWeb.add(btnStartClient, "2, 18, center, default");
		panelWeb.add(btnStartServ, "6, 18, center, default");
		panelWeb.add(btnStopServ, "6, 20, center, default");
	}

	private void PopulateVLCPanel()
	{
		NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "C:\\Program Files\\VideoLAN\\VLC");
		Canvas canvas = new Canvas();
		canvas.setBackground(Color.black);
		
		panelVLC = new JPanel();
		panelVLC.setLayout(new BorderLayout());
		panelVLC.add(canvas);
		frame.add(panelVLC);
		
		MediaPlayerFactory factory = new MediaPlayerFactory();
		vlc = factory.newEmbeddedMediaPlayer();
		vlc.setVideoSurface(factory.newVideoSurface(canvas));
	}
	
	public void StartServerBtn(ActionEvent arg0) {
		serverFSM.apply(ServerEvent.START);
		btnStartServ.setEnabled(false);
		btnStopServ.setEnabled(true);
	}
	
	public void StopServerBtn(ActionEvent arg0) {
		serverFSM.apply(ServerEvent.STOP);
		btnStartServ.setEnabled(true);
		btnStopServ.setEnabled(false);
	}
	
	public void StartClientBtn(ActionEvent arg0) {
		clientFSM.apply(ClientEvent.CONNECT);
	}
	
	public void MessageServerBtn(ActionEvent arg0)
	{
		clientFSM.apply(ClientEvent.SENDMSG);
	}
	
	public void MessageClientBtn(ActionEvent arg0)
	{
		serverFSM.apply(ServerEvent.SENDMSG);
	}
	
 	public void PlayMediaBtn(ActionEvent arg0)
	{
 		serverFSM.apply(ServerEvent.SENDMEDIA);
	}
 	//END UI
 	
 	
 	
 	//SERVER FSM EVENTS
 	public void sEventStarting()
 	{
 		System.out.println("[+] Starting Server");
		
		server.setReceiveListener(p->OnServerReceivePacket(p));
		//Create a thread that runs the server 
		Thread one = new Thread(server.newRunnable(Integer.parseInt(txtPort.getText())));
		one.start();
		
		serverFSM.apply(ServerEvent.SUCCESS);
 	}
 	
 	public void sEventIdle()
 	{
 		//Reset machine data while idle
 		servermediaFile = null;
 	}
 	
 	
 	public void sEventSendChat()
 	{
 		String Message = JOptionPane.showInputDialog(null,"Please enter a message");
		server.SendPacket(new TCPPacketChat(Message));
		
		//pretend it always succeeds
		serverFSM.apply(ServerEvent.SUCCESS);
 	}
 	
 	
 	public void sEventSendMedia()
 	{
 		//Send file and then store how many clients we sent it to
 		servermediaFile = ServerSendFileToClient(true);
		awaitingSuccessCount.set(server.GetClientCount());
		
 		if(servermediaFile != null)
 			serverFSM.apply(ServerEvent.SUCCESS);
 		else
 			serverFSM.apply(ServerEvent.FAIL);
 	}
 	
 	public void sEventStopping()
 	{
 		server.SoftKill();
 		serverFSM.apply(ServerEvent.SUCCESS);
 	}

 	
 	public void sEventPlayMedia()
 	{
 		//Play file on server
 		vlc.playMedia(servermediaFile.getAbsolutePath());
 		
 		//Notify clients to also begin play
 		server.SendPacket(new TCPPacketBeginPlay());
 	} 	//END SERVER FSM EVENTS
 	
 	
 	//Networking event callbacks
	
 	//End networking event callbacks
 	
	private File ServerSendFileToClient(boolean IsMusic) {
		JFileChooser chooser=new JFileChooser();
		chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
		int Result = chooser.showOpenDialog(frame);
		if(Result == JFileChooser.APPROVE_OPTION)
		{
			File selectedFile = chooser.getSelectedFile();
			if(IsMusic)
				servermediaFile = selectedFile;
			
			try {
				server.SendPacket(new TCPPacketTransfer(Files.readAllBytes(selectedFile.toPath()), selectedFile.getName(),IsMusic));
				return selectedFile;
			} catch (IOException e) {
				System.out.println("[!] Exception sending file");
			}
		}
		return null;
	}
	//END SERVER FSM EVENTS
	
	
	
	//CLIENT FSM EVENTS
	public void cEventConnecting()
	{
		String IP = txtServerAddress.getText();
		int Port = Integer.parseInt(txtPort.getText());
		
		client.setReceiveListener(p->OnClientReceivePacket(p));
		Thread one = new Thread(client.newRunnable(IP, Port));
		one.start();
		
		clientFSM.apply(ClientEvent.SUCCESS);
	}
	
	public void cEventIdle()
	{
		clientmediaFile = null;
	}
	
	public void cEventSendChat()
	{
		String Message = JOptionPane.showInputDialog(null,"Please enter a message");
		client.SendPacket(new TCPPacketChat(Message));
		
		clientFSM.apply(ClientEvent.SUCCESS);
	}

	public void cEventSavingMedia()
	{
		TCPPacketTransfer tpacket = (TCPPacketTransfer)clientFSM.getContext();
		boolean diskWriteSucceeded = WriteTransferPacketToDisk(tpacket);
			
		client.SendPacket(new TCPPacketTransferStatus(diskWriteSucceeded));
		if(diskWriteSucceeded)
			clientFSM.apply(ClientEvent.SUCCESS);
		else
			clientFSM.apply(ClientEvent.FAIL);
	}
	
	public void cEventPlayMedia()
	{
		vlc.playMedia(clientmediaFile.getAbsolutePath());
	}
	//END CLIENT FSM EVENTS
	
	
	
	//Networking Callbacks
	public void OnClientReceivePacket(TCPPayload packet)
	{
		//TO-DO: Make all of this async
		System.out.println("[+] Client received a packet from server");
		if(packet.m_Type == PayloadType.CmdChat)
		{
			onClientReceiveChat((TCPPacketChat)packet);
		}else if(packet.m_Type == PayloadType.CmdTransfer){
			clientFSM.apply(ClientEvent.RECEIVE_MEDIA,packet);
		}else if(packet.m_Type == PayloadType.CmdBeginPlay){
			clientFSM.apply(ClientEvent.PLAY);
		}
	}
 	
 	public void onClientReceiveChat(TCPPacketChat packet)
 	{
		JOptionPane.showMessageDialog(null,packet.m_Message);
 	}
 	
 	public boolean WriteTransferPacketToDisk(TCPPacketTransfer packet)
 	{
 		//Packet Subtype holds filename
		JFileChooser chooser=new JFileChooser();
		chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
		chooser.setSelectedFile(new File(packet.m_FileName));
		
		//Ask the user where to store the file
		int Result = chooser.showOpenDialog(frame);
		if(Result == JFileChooser.APPROVE_OPTION)
		{
			File selectedFile = chooser.getSelectedFile();
			if(packet.m_IsMusic)
				clientmediaFile = selectedFile;
			
			try{
				//Write the file to disk
				FileOutputStream fos = new FileOutputStream(selectedFile);
				fos.write(packet.m_FileData);
				fos.close();
				return true;
			}catch(Exception ex){
			}
		}
		return false;
 	}
 	
 	public void OnServerReceivePacket(TCPPayload packet)
	{ 
		//Enter the crude Finite State Machine
		//TO-DO: Make all of these async
		System.out.println("[+] Server received a packet from client");
		if(packet.m_Type == PayloadType.CmdChat)
		{
			OnServerReceiveChat((TCPPacketChat)packet);
		}else if(packet.m_Type == PayloadType.CmdTransferStatus){
			if(serverFSM.getState() == ServerState.AWAITING_MEDIA_RECV)
			{
				TCPPacketTransferStatus spacket = (TCPPacketTransferStatus)packet;
				OnServerReceiveTransferStatus(spacket);		
			}
		}
	}
 	
 	public void OnServerReceiveChat(TCPPacketChat packet)
 	{
		JOptionPane.showMessageDialog(null,packet.m_Message);
 	}
 	
 	public void OnServerReceiveTransferStatus(TCPPacketTransferStatus packet)
 	{
		if(packet.m_TransferSucceeded)
		{
			//Once everyone has said they got the file
			if(awaitingSuccessCount.decrementAndGet() <= 0)
				serverFSM.apply(ServerEvent.SUCCESS);
		}else{
			System.out.println("[!] [Server] Client failed to receive file");
			serverFSM.apply(ServerEvent.FAIL);
		}
 	}
 	//End Networking Callbacks
}

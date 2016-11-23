package Client;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

import com.google.common.base.Charsets;

import Server.onReceiveListener;
import Shared.PacketInterface;
import Shared.PacketManager;
import Shared.TCPPayload;
import Shared.TCPPayload.PayloadType;

public class TCPClient implements PacketInterface {
	public TCPClient()
	{
		m_ShouldDie = false;
	}
	
	public void Connect(String IP, int Port) throws IOException
	{
		m_Socket = new Socket(IP,Port);
		try {
			m_InputStream = new BufferedInputStream(m_Socket.getInputStream());
			m_OutputStream = new BufferedOutputStream(m_Socket.getOutputStream());
		} catch (IOException e) {
			System.out.println("[!] Client Exception Getting Streams");
		}
		m_PacketMngr = new PacketManager(m_InputStream,m_OutputStream);
		System.out.println("[+] Client Opened Connection");
		
		while(!m_ShouldDie)
		{
			TCPPayload packet = null;
			try {
				packet = m_PacketMngr.GetPacket();
			} catch (IOException e) {
				System.out.println("[!] Client Exception Receiving Packet");
				Close();
			}
			onReceive(packet);
		}
		System.out.println("[+] Client Closed Connection");
		Close();
	}
	
	private void onReceive(TCPPayload packet)
	{
		if(m_ReceiveCallback != null)
			m_ReceiveCallback.onReceive(packet);
		
		System.out.println("[+] Client Received Packet");
	}
	
	public void setReceiveListener(onReceiveListener event)
	{
		m_ReceiveCallback = event;
	}
	
	public void SendPacket(TCPPayload packet)
	{
		try {
			m_PacketMngr.SendPacket(packet);
		} catch (IOException e) {
			System.out.println("[!] Client Exception Sending Packet");
		}
		System.out.println("[+] Client Sent a Packet");
	}
	
	public void Close()
	{
		try {
			m_Socket.close();
		} catch (IOException e) {
		}
		m_ShouldDie = true;
		System.out.println("[+]Client Closed Connection");
	}
	
	public Runnable newRunnable(String IP, int Port)
	{
		//Constructs a runnable with a parameter
		TCPClient us = this;
		Runnable inst = new Runnable(){
			public void run(){
				try {
					us.Connect(IP,Port);
				} catch (IOException e) {
				}
			}
		};
		return inst;
	}

	private Socket m_Socket;
	private BufferedInputStream m_InputStream;
	private BufferedOutputStream m_OutputStream;
	private PacketManager m_PacketMngr;
	private onReceiveListener m_ReceiveCallback;
	boolean m_ShouldDie;
}

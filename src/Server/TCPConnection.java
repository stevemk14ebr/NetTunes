package Server;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import java.io.IOException;
import java.net.Socket;

import Shared.PacketManager;
import Shared.TCPPayload;

public class TCPConnection implements Runnable
{
	public TCPConnection(Socket SockIn,onReceiveListener callback)
	{
		m_ShouldDie = false;
		m_ServerReceiveCallback = callback;
		m_Socket = SockIn;
		try {
			m_InputStream = new BufferedInputStream(m_Socket.getInputStream());
			m_OutputStream = new BufferedOutputStream(m_Socket.getOutputStream());
			m_PacketMngr = new PacketManager(m_InputStream,m_OutputStream);
		} catch (IOException e) {
			
		}
	}
	
	public void run()
	{
		System.out.println("[+] Server Handling a Client");
		while(!m_ShouldDie)
		{
			TCPPayload packet = null;
			try {
				packet = m_PacketMngr.GetPacket();
			} catch (IOException e) {
				Close();
				return;
			}
			if(packet == null)
				continue;
			
			//Forward packet back to the main server thread
			System.out.println("[+] Server Thread Received a packet");
			m_ServerReceiveCallback.onReceive(packet);
		}
		Close();
	}
	
	public void SendPacket(TCPPayload packet)
	{
		try {
			m_PacketMngr.SendPacket(packet);
		} catch (IOException e) {
			System.out.println("[!] Server Exception Sending Packet");
		}
	}
	
	public void Close()
	{
		m_ShouldDie = true;
		try {
			m_Socket.close();
		} catch (IOException e) {
		}
		System.out.println("[+] Server Closed Connection");
	}
	
	public boolean IsOpen()
	{
		return !m_ShouldDie;
	}
	
	private Socket m_Socket;
	private boolean m_ShouldDie;
	private BufferedInputStream m_InputStream;
	private BufferedOutputStream m_OutputStream;
	private PacketManager m_PacketMngr;
	private onReceiveListener m_ServerReceiveCallback;
}
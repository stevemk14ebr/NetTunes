package Server;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import Shared.PacketInterface;
import Shared.TCPPayload;
import Shared.TCPPayload.PayloadType;
import Shared.Tuple;

public class TCPServer implements PacketInterface{
	public TCPServer()
	{
		m_ShouldDie = false;
		m_Connections = new ArrayList<Tuple<TCPConnection,onReceiveListener>>();
		
		//Spawns infinite threads as needed, runs off one core thread
		m_ThreadPool= MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
	}
	
	//Not a runnable
	public void run(int Port) throws IOException
	{
		System.out.println("[+] Server Started");
		m_ServerSocket = new ServerSocket(Port);
		
		//Accept all new clients
		while(!m_ShouldDie)
		{
			Socket sock = null;
			try{
				sock = m_ServerSocket.accept();
			}catch(IOException e){
				System.out.println("[+] Socket Timeout During Accept");
			}
			System.out.println("[+] Server has a new client: " + (++m_ClientsConnected));
			
			onReceiveListener callback = p->onReceive(p);
			TCPConnection connection = new TCPConnection(sock,callback);
			m_Connections.add(new Tuple<TCPConnection,onReceiveListener>(connection,callback));
			
			//Tell thread pool to open a thread for the new client
			ListenableFuture future = m_ThreadPool.submit(connection);
			
			//Executed once the TCPConnection functions work is done
			future.addListener(new Runnable(){
				@Override
				public void run() {
					System.out.println("[+] Server Thread Killed: "+ (--m_ClientsConnected));
				}
			}, m_ThreadPool);
		}
	}
	
	public Runnable newRunnable(final int Port)
	{
		//Constructs a runnable with a parameter
		TCPServer us = this;
		Runnable inst = new Runnable(){
			public void run(){
				try {
					us.run(Port);
				} catch (IOException e) {
				}
			}
		};
		return inst;
	}
	
	public void SoftKill()
	{
		m_ShouldDie = true;
		try {
			m_ServerSocket.close();
		} catch (IOException e) {
			
		}
		m_ThreadPool.shutdown();
		System.out.println("[+] Server Killed");
	}
	
	public void ImmediateKill()
	{
		m_ShouldDie = true;
		try {
			m_ServerSocket.close();
		} catch (IOException e) {
			
		}
		m_ThreadPool.shutdownNow();
		System.out.println("[+] Server Killed");
	}
	
	public String GetLocalIP()
	{
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
		}
		return "Error";
	}
	
	private void UpdateConnections()
	{
		//Remove closed connections
		for(Iterator<Tuple<TCPConnection,onReceiveListener>> it = m_Connections.iterator(); it.hasNext();)
		{
			Tuple<TCPConnection,onReceiveListener> i = it.next();
			if(!i.x.IsOpen())
				m_Connections.remove(i);
		}
	}
	
	//Sends packet to all clients
	public void SendPacket(TCPPayload packet)
	{
		UpdateConnections();
		for(Iterator<Tuple<TCPConnection,onReceiveListener>> it = m_Connections.iterator(); it.hasNext();)
		{
			Tuple<TCPConnection,onReceiveListener> i = it.next();
			i.x.SendPacket(packet);
		}
	}
	
	public void setReceiveListener(onReceiveListener event)
	{
		m_ReceiveCallback = event;
	}
	
	private void onReceive(TCPPayload packet)
	{
		//Tell everyone one of our threads got a packet
		m_ReceiveCallback.onReceive(packet);
	}
	
	public int GetClientCount()
	{
		return m_Connections.size();
	}
	
	ListeningExecutorService m_ThreadPool;
	ServerSocket m_ServerSocket;
	int m_ClientsConnected;
	boolean m_ShouldDie;
	
	private List<Tuple<TCPConnection,onReceiveListener>> m_Connections;
	onReceiveListener m_ReceiveCallback;
}

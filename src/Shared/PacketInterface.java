package Shared;

import Server.onReceiveListener;

public interface PacketInterface {
	public void SendPacket(TCPPayload packet);
	
	public void setReceiveListener(onReceiveListener event);
}

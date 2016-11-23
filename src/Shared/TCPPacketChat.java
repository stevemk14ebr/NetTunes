package Shared;

import java.io.Serializable;

public class TCPPacketChat extends TCPPayload implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1643869040835672098L;
	public TCPPacketChat(String Message)
	{
		super.m_Type = PayloadType.CmdChat;
		m_Message = Message;
	}
	public String m_Message;
}

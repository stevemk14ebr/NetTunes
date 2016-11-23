package Shared;

import java.io.Serializable;

public class TCPPacketBeginPlay extends TCPPayload implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 745232791680477373L;

	public TCPPacketBeginPlay()
	{
		super.m_Type = PayloadType.CmdBeginPlay;
	}
}

package Shared;

import java.io.Serializable;

public class TCPPacketTransferStatus extends TCPPayload implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5142202660318759149L;
	public TCPPacketTransferStatus(boolean Status)
	{
		super.m_Type = PayloadType.CmdTransferStatus;
		m_TransferSucceeded = Status;
	}
	public boolean m_TransferSucceeded;
}

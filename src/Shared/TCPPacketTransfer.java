package Shared;

import java.io.Serializable;

public class TCPPacketTransfer extends TCPPayload implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3311841750223018353L;
	public TCPPacketTransfer(byte[] data,String filename, boolean IsMusic)
	{
		super.m_Type = PayloadType.CmdTransfer;
		m_FileData = data;
		m_FileName = filename;
		m_IsMusic = IsMusic;
	}
	public byte[] m_FileData;
	public String m_FileName;
	public boolean m_IsMusic;
}

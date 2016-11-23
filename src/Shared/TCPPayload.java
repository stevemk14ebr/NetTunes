package Shared;
import java.io.Serializable;

public class TCPPayload implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1614528533762976760L;
	public enum PayloadType {
		CmdTransfer,
		CmdTransferStatus,
		CmdBeginPlay,
		CmdChat
	}
	
	public PayloadType m_Type;
}

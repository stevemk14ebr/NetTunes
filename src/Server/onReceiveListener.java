package Server;

import Shared.TCPPayload;
public interface onReceiveListener {
	void onReceive(TCPPayload packet);
}

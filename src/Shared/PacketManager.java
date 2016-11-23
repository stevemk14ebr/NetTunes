package Shared;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.nio.charset.StandardCharsets;


import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

public class PacketManager {
	public PacketManager(BufferedInputStream input,BufferedOutputStream output)
	{
		m_InputStream = input;
		m_OutputStream = output;
	}
	
	//Returns as soon as data is sent
	public void SendPacket(TCPPayload payload) throws IOException
	{
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		ObjectOutputStream o = new ObjectOutputStream(b);
		o.writeObject(payload);
		
		byte[] SerializedObject = b.toByteArray();
		String Base16Object = BaseEncoding.base16().encode(SerializedObject) + "\r\n";
	
		m_OutputStream.write(Base16Object.getBytes(Charsets.UTF_8));
		m_OutputStream.flush();
	}

	//Blocks until data is read or returns immediately if there is none
	public TCPPayload GetPacket() throws IOException
	{
		BufferedReader r = new BufferedReader(
				new InputStreamReader(m_InputStream,StandardCharsets.UTF_8));
		
		String SerializedObjectString = r.readLine();
		byte[] SerializedObject = BaseEncoding.base16().decode(SerializedObjectString);
		
		ByteArrayInputStream bis = new ByteArrayInputStream(SerializedObject);
		ObjectInput in = new ObjectInputStream(bis);
		try {
			Object o = in.readObject();
			return (TCPPayload)o;
		} catch (ClassNotFoundException e) {
			System.out.println("Failed");
		} 
		return null;
	}

	private BufferedInputStream m_InputStream;
	private BufferedOutputStream m_OutputStream;
}

package sd;


import java.net.*;
import java.io.*;
public class MulticastPeer{
	
	/**
	 * Classe de demonstracao 
	 * 
	 * @param argss
	 */
    public static void main(String argss[]){
		// args give message contents and destination multicast group (e.g. "228.5.6.7")


		String args[] = new String[2];
		args[0] = "Mensagem...";
		args[1] = "228.5.6.8";

		MulticastSocket s =null;
		try {
			InetAddress group = InetAddress.getByName(args[1]);
			s = new MulticastSocket(1025);
			s.joinGroup(group);
 			byte [] m = args[0].getBytes();
			DatagramPacket messageOut = new DatagramPacket(m, m.length, group, 1025);
			s.send(messageOut);	
			byte[] buffer = new byte[1000];
 			for(int i=0; i< 3;i++) {		// get messages from others in group
 				DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
 				s.receive(messageIn);
 				System.out.println("Received:" + new String(messageIn.getData()));
  			}
			s.leaveGroup(group);		
		}catch (SocketException e){System.out.println("Socket: " + e.getMessage());
		}catch (IOException e){System.out.println("IO: " + e.getMessage());
		}finally {if(s != null) s.close();}
	}		      	
	
}
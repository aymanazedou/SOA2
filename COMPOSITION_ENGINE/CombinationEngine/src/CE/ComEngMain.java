package CE;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class ComEngMain {

	public static void main(String[] args) throws IOException {

		ServerSocket socketCE;
		Socket SocketCombEngine;
		int ListeningPort;
		int RegistryPort;
		String RegistryHostname;
		
		
		//LOAD PROPERTIES
    	Properties prop = new Properties();
    	InputStream input = ComEngMain.class.getClassLoader().getResourceAsStream("Properties/conf.properties");
    	prop.load(input);

    	
    	//GET VALUES FROM THE PROPERTY FILE
    	ListeningPort = Integer.parseInt(prop.getProperty("ListeningPort"));
    	RegistryPort = Integer.parseInt(prop.getProperty("RegistryPort"));
    	RegistryHostname = prop.getProperty("RegistryHostname");
		
		
			socketCE = new ServerSocket(ListeningPort);
			while (true) {
				SocketCombEngine = socketCE.accept();
				(new SendThreadEXIST(SocketCombEngine,new Socket(RegistryHostname, RegistryPort))).start();
			}
	}

}
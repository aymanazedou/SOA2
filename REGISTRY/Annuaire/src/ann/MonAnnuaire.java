/**this program represent the registry that will be connected to a database and search for services that have the given input and return outputs
 * that will be communicated to the composition engine. The program verify in the data base if the parameter exist (if not, return NoParameter),
 * or if it exists and there is no service that take it as input (Returns NoService), or return the services and the corresponding output.
 */

package ann;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


//================================================================================================================
public class MonAnnuaire {
	static Connection connection=null;
	static String DbUserName;
	static String DbUserPassword;
	static String DbName;
	static String DbTableName;
	
	
	public static void main(String[] args) throws SQLException, IOException {
		ServerSocket socketAnnuaire;
		Socket Socketdeannuaire;	
		
		//LOAD PROPERTIES
    	Properties prop = new Properties();
    	InputStream input = MonAnnuaire.class.getClassLoader().getResourceAsStream("Properties/conf.properties");
    	prop.load(input);

    	
    	//GET VALUES FROM THE PROPERTY FILE
    	int ListeningPort = Integer.parseInt(prop.getProperty("ListeningPort"));
    	DbUserName = prop.getProperty("DbUserName");
    	DbUserPassword = prop.getProperty("DbUserPassword");
    	DbName = prop.getProperty("DbName");
    	DbTableName = prop.getProperty("DbTableName");
        
    	
		
		
		
		
		//CONNECT TO THE DATABASE==============================================================================
		System.out.println("------------Mysql JDBC Connection-------------");
		try {
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException e) {
			System.out.println("Driver not found!!");
			e.printStackTrace();
			return;
		}
		System.out.println("JDBC Driver OK!");
		try {
			MonAnnuaire.connection = DriverManager.getConnection("jdbc:mysql://localhost/"+DbName+"?"+ "user="+DbUserName+"&password="+DbUserPassword);
		}catch (Exception e) {
			return;
		}
		System.out.println("OK! Connected to DataBase ..");
		System.out.println("============================");
		
		//Each time a Son-thread will treat the request of a client of the Composition Engine===============
		socketAnnuaire = new ServerSocket(ListeningPort);
		while (true) {
			Socketdeannuaire = socketAnnuaire.accept();
			(new MonFils(Socketdeannuaire)).start();
		}
	}
}
//===========================================================================================================








//================================================================================================================
class MonFils extends Thread{
	String msg;
	Socket soc;
	BufferedReader in;
	PrintWriter out;
	List<String> links = new ArrayList<>();
	FileInputStream fis;
	BufferedInputStream bis;
	OutputStream os;

	public MonFils(Socket soc) {
		this.soc=soc;
		try {
			out = new PrintWriter(soc.getOutputStream());
			in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
			fis = null;
		    bis = null;
		    os = null;
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	@Override
	public void run() {
		try {
		while(true) {
			//to verify that a parameter exist in the data base, it should exist either as an input or output of a service.
			boolean ParamExist = false;
			//verify if at least one service exists with the requested input parameter.
			boolean ServiceExist = false;
			
			msg = "";
			msg = in.readLine();
			
			//get the information needed from the message==========================================================
			String[] splitMsg = msg.split(" ");
			System.out.println("Command : " + splitMsg[0] + " || Parameter : "+ splitMsg[1]);
			System.out.println("============================");

			java.sql.Statement statement = MonAnnuaire.connection.createStatement();
			//send reply=====================command : EXIST=======================================================
			if (splitMsg[0].equals("exist")){
				links.clear();
				ResultSet resultSetIN = null;
				ResultSet resultSetVerifyParameter = null;
				String ServiceName = null;
				String InPara = "";
				String ResponseExist = "";
				
				resultSetIN = statement.executeQuery("SELECT OUTP,servicename,linkWSDL FROM "+MonAnnuaire.DbName+"."+MonAnnuaire.DbTableName+" WHERE INP ='"+splitMsg[1]+"'");
				int i=0;
				
				//Construct the message
				while (resultSetIN.next()) {
					ParamExist=true;
					ServiceExist=true;
					InPara = resultSetIN.getString("OUTP");
					ServiceName = resultSetIN.getString("servicename");
					if (i==0){
						ResponseExist =ResponseExist+InPara+","+ServiceName;
					}
					else {
						ResponseExist =ResponseExist+" "+InPara+","+ServiceName;
					}
					i++;
					
				}
				
				resultSetVerifyParameter = statement.executeQuery("SELECT INP FROM "+MonAnnuaire.DbName+"."+MonAnnuaire.DbTableName+" WHERE OUTP ='"+splitMsg[1]+"'");
				
				if (resultSetVerifyParameter.next()) {
					ParamExist=true;
				}
				if (!ParamExist) {
					ResponseExist="NoParameter";
				}
				if (!ServiceExist && ParamExist) {
					ResponseExist="NoService";
				}
				
				//send the Response of EXIST message
				out.println(ResponseExist);
				out.flush();
			}
			
			// send file
			else if (splitMsg[0].equals("SendFile")){
				ResultSet resultSetWSDL = null;
				resultSetWSDL = statement.executeQuery("SELECT linkWSDL FROM "+MonAnnuaire.DbName+"."+MonAnnuaire.DbTableName+" WHERE servicename ='"+splitMsg[1]+"'");
					try {
						os =soc.getOutputStream();
						resultSetWSDL.next();
						File myFile = new File (resultSetWSDL.getString("linkWSDL"));
						System.out.println("File : "+ resultSetWSDL.getString("linkWSDL"));
						byte [] mybytearray  = new byte [(int)myFile.length()];
						fis = new FileInputStream(myFile);
						bis = new BufferedInputStream(fis);
						bis.read(mybytearray,0,mybytearray.length);
						System.out.println("Sending File (" + mybytearray.length + " bytes) ...");
						os.write(mybytearray,0,mybytearray.length);
						os.flush();
						System.out.println("Done.");
					}catch(IOException e) {
						e.printStackTrace();
					}

				}
		}
		}catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
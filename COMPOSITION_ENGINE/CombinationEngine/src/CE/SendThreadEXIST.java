/**This program deals with one client (application) and demands to the registry if the parameter exists in the database.
the registry then answer with a message, that can be NoParameter if it doesn't exist in the database, or NoService if the parameter exists
but there is no services that take it as entry, or an explicit response with all services that take the parameter as input and the corresponding
output.
with those parameters and service names, the tree algorithm is created to find a certain composition of services that take one parameter as input
and give another.
 **/

package CE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


public class SendThreadEXIST extends Thread {
	String msg;
	Socket soc;
	Socket SocClient;
	BufferedReader in;
	PrintWriter out;
	BufferedReader inClient;
	PrintWriter outClient;
	OutputStream osClient;
	FileInputStream fis;
	BufferedInputStream bis;
	String message;
	String Response;
	String destination;
	String RootName;
	String WsdlDownloadDirectry;
	ArrayList<Node<String>> table1;
	ArrayList<Node<String>> table2;
	List<String> OldRequest;
	List<String> services;
	List<String> PreOldRequest1;
	List<String> PreOldRequest2;
	List<String> combination;
	Node<String> root;
	String MessageClient;
	boolean NoComposition;
	boolean NoParameter;
	boolean found;
	boolean NoService;
	int bytesRead;
	int current = 0;
	FileOutputStream fos;
	BufferedOutputStream bos;


	private static <T> void printTree(Node<T> node, String appender) {
		System.out.println(appender + node.getData() + " "+ node.getService());
		node.getChildren().forEach(each ->  printTree(each, appender + appender));
	}

	public SendThreadEXIST (Socket SocClient,Socket soc) throws IOException {
		this.soc=soc;
		this.SocClient=SocClient;

		//LOAD PROPERTIES
		Properties prop = new Properties();
		InputStream input = ComEngMain.class.getClassLoader().getResourceAsStream("Properties/conf.properties");
		prop.load(input);


		//GET VALUES FROM THE PROPERTY FILE
		WsdlDownloadDirectry = prop.getProperty("WsdlDownloadDirectry");

		try {
			combination = new ArrayList<>();
			PreOldRequest1 = new ArrayList<>();
			PreOldRequest2 = new ArrayList<>();
			OldRequest = new ArrayList<>();
			table2 = new ArrayList<Node<String>>();
			table1 = new ArrayList<Node<String>>();
			outClient = new PrintWriter(SocClient.getOutputStream());
			inClient = new BufferedReader(new InputStreamReader(SocClient.getInputStream()));
			out = new PrintWriter(soc.getOutputStream());
			in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
			bos = null;
			fos = null;
			fis=null;
			bis=null;
			osClient=null;
			services = new ArrayList<>();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void run() {

		while(true) {
			String ComOfService= "";
			String servicename = "";
			MessageClient="";
			NoComposition = false;
			NoParameter = false;
			NoService = false;
			found=false;
			table1.clear();
			table2.clear();
			OldRequest.clear();
			PreOldRequest1.clear();
			PreOldRequest2.clear();
			combination.clear();


			//GET THE MESSAGE FROM THE CLIENT (APPLICATION)
			try {
				MessageClient = inClient.readLine();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			//SPLIT THE MESSAGE AND GET THE INPUT (ROOT NODE OF THE TREE) AND THE OUTPUT (DESTINATION NODE) OF THE SERVICE SEARCHED.
			String[] splitMessage = MessageClient.split(" ");

			if (splitMessage[0].equals("exist")) {

				destination = splitMessage[2];
				RootName = splitMessage[1];

				//SEARCH FOR CHILDREN OF THE ROOT PARAMETER BY SENDING MESSAGE TO THE REGISTRY
				msg = "exist " + splitMessage[1];
				out.println(msg);
				out.flush();

				//GET THE RESPONSE FROM THE REGISTRY
				try {
					Response = in.readLine();
					//IF THE RESPONSE IS NoParameter, MEANS THE PARAMETER DOESN'T EXIST IN THE DATABASE OF REGISTRY, NEITHER AS INPUT NOR OUTPUT.
					if (Response.equals("NoParameter")) {
						NoParameter=true;
						found=true;
						ComOfService="NoParameter" + RootName;
					}
					//IF THE RESPONSE IS NoService, MEANS THE PARAMETER EXIST IN THE DATABASE OF THE REGISTRY, BUT NOT AS INPUT OF ANY SERVICE.
					else if (Response.equals("NoService")) {
						NoService=true;
						found=true;
						ComOfService="NoService";
					}
					//IF NO ONE OF THE PREVIOUS CONDITIONS, WE ARE SURE THAT THE PARAMETER REQUESTED EXIST AS INPUT OF SOME SERVICES.
					else {

						/**add to OldRequest, this list prevent re-sending the same request (to get children) of the same parameter,
						 * because we know that we will have the same response of children. By doing that, we prevent the infinite loop.
						 */
						OldRequest.add(splitMessage[1]);

						//CREATE THE ROOT
						root = new Node<>(splitMessage[1]);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				//WE ARE SURE THE ROOT HAVE CHILDREN, SE WE ADD THEM TO TABLE1.
				if (!NoParameter && !NoService) {
					String[] splitResponse = Response.split(" ");
					int i;
					for (i = 0; i < splitResponse.length; i++) {
						String[] splitPara = splitResponse[i].split(",");
						Node<String> child = root.addChild(new Node<String>(splitPara[0]),splitPara[1]);
						PreOldRequest1.add(splitPara[0]);
						if (splitPara[0].equals(destination)) {
							found = true;
							servicename= child.getService();
							combination.add(servicename);
						}
						table1.add(child);
						if (found) {break;}
					}

					//DOWNLOAD ALL WSDL FILES OF THE SERVICES RETURNED.
					//SERVICES TABLE ALOW US TO REQUEST THE DOWNLOAD OF THE SERVICES RETURNED IN THE RESPONSE.
					//CLEAR IS EVERYTIME WE WANT TO USE IT.
					services.clear();
					String FilesDownLoad = "";
					for (int j = 0; j < splitResponse.length; j++) {
						String[] splitPara = splitResponse[j].split(",");
						FilesDownLoad += " " + splitPara[1];
						services.add(splitPara[1]);
					}
					System.out.println("*************************************");
					System.out.println("--> files to download : " + FilesDownLoad);

					//SEND MESSAGE TO GET WSDL FILE CORRESPONDING TO A SERVICE
					InputStream is;
					System.out.println("--> START DOWNLOADING FILES");
					for (int j = 0; j < services.size(); j++) {
						String MessageSendFile;
						MessageSendFile = "SendFile "+ services.get(j);
						out.println(MessageSendFile);
						out.flush();

						//READ THE STREAM AND WRITE ON THE FILE
						try {	
							is = soc.getInputStream();
							byte [] mybytearray  = new byte [12386];
							fos = new FileOutputStream(WsdlDownloadDirectry+services.get(j)+".wsdl");
							bos = new BufferedOutputStream(fos);
							bytesRead = is.read(mybytearray,0,mybytearray.length);
							bos.write(mybytearray, 0 , bytesRead);
							bos.flush();
							System.out.println("File of " + services.get(j) + " downloaded (" + bytesRead + " bytes read)");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}

				//WE INITIATE THE INTEGER i, THAT WILL HELP US TO SWITCH BETWEEN TABLE1 AND TABLE2.
				//AT EACH ROUND ONE TABLE WILL BE INITIALIZED AND TAKE CHILDREN OF THE OTHER TABLE.

				int i = 1;
				while(!found) {
					if (i%2!=0) {
						table2.clear();

						//LOOP TO SEARCH FOR CHILDREN OF NODES IN TABLE1
						for (int j = 0; j < table1.size(); j++) {
							String Parameter = table1.get(j).getData();
							if (!OldRequest.contains(Parameter)) {
								String message = "exist "+Parameter;

								//SEND THE MESSAGE
								out.println(message);
								out.flush();

								//GET THE REPLY
								try {
									Response = in.readLine();
								} catch (IOException e) {
									e.printStackTrace();
								}
								if (Response.equals("NoService")) {
									continue;
								}

								//SPLIT THE MESSAGE AND AddCHILDREN TO TABLE2
								String[] splitResp = Response.split(" ");
								for (int t = 0; t < splitResp.length;t++) {
									String[] splitPara = splitResp[t].split(",");
									PreOldRequest2.add(splitPara[0]);
									Node<String> child = table1.get(j).addChild(new Node<String>(splitPara[0]),splitPara[1]);
									if (splitPara[0].equals(destination)) {
										found = true;
										while(child!=root) {
											servicename= child.getService();
											child = child.getParent();
											combination.add(servicename);
										}
									}
									table2.add(child);
									if (found) {break;}
								}


								//***********************************************
								//DOWNLOAD ALL WSDL FILES OF THE SERVICES RETURNED.
								//SERVICES TABLE ALOW US TO REQUEST THE DOWNLOAD OF THE SERVICES RETURNED IN THE RESPONSE.
								services.clear();
								String FilesDownLoad = "";
								for (int t = 0; t < splitResp.length; t++) {
									String[] splitPara = splitResp[t].split(",");
									FilesDownLoad += " " + splitPara[1];
									services.add(splitPara[1]);
								}
								System.out.println("*************************************");
								System.out.println("--> files to download : " + FilesDownLoad);

								//SEND MESSAGE TO GET WSDL FILE CORRESPONDING TO A SERVICE
								InputStream is;
								System.out.println("--> START DOWNLOADING FILES");
								for (int t = 0; t < services.size(); t++) {
									String MessageSendFile;
									MessageSendFile = "SendFile "+ services.get(t);
									out.println(MessageSendFile);
									out.flush();

									//READ THE STREAM AND WRITE ON THE FILE
									try {	
										is = soc.getInputStream();
										byte [] mybytearray  = new byte [12386];
										fos = new FileOutputStream(WsdlDownloadDirectry+services.get(t)+".wsdl");
										bos = new BufferedOutputStream(fos);
										bytesRead = is.read(mybytearray,0,mybytearray.length);
										bos.write(mybytearray, 0 , bytesRead);
										bos.flush();
										System.out.println("File of " + services.get(t) + " downloaded (" + bytesRead + " bytes read)");
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
								//***********************************************


								if (found) {break;}
							}
						}
						if (table2.isEmpty()) {
							NoComposition=true;
							break;
						}
						OldRequest.addAll(PreOldRequest1);
						PreOldRequest1.clear();
					}
					else {
						table1.clear();

						////LOOP TO SEARCH FOR CHILDREN OF NODES IN TABLE2
						for (int j = 0; j < table2.size(); j++) {
							String Parameter = table2.get(j).getData();
							if (!OldRequest.contains(Parameter)) {
								String message = "exist "+Parameter;

								//SEND THE MESSAGE
								out.println(message);
								out.flush();
								//GET THE REPLY
								try {
									Response = in.readLine();
								} catch (IOException e) {
									e.printStackTrace();
								}
								if (Response.equals("NoService")) {
									continue;
								}

								////SPLIT THE MESSAGE AND AddCHILDREN TO TABLE1
								String[] splitResp = Response.split(" ");


								for (int t = 0; t < splitResp.length; t++) {
									String[] splitPara = splitResp[t].split(",");
									PreOldRequest1.add(splitPara[0]);
									Node<String> child = table2.get(j).addChild(new Node<String>(splitPara[0]),splitPara[1]);
									if (splitPara[0].equals(destination)) {
										found = true;
										while(child!=root) {
											servicename= child.getService();
											child = child.getParent();
											combination.add(servicename);
										}
									}
									table1.add(child);
									if (found) {break;}

								}
								//***********************************************
								//DOWNLOAD ALL WSDL FILES OF THE SERVICES RETURNED.
								//SERVICES TABLE ALOW US TO REQUEST THE DOWNLOAD OF THE SERVICES RETURNED IN THE RESPONSE.
								services.clear();
								String FilesDownLoad = "";
								for (int t = 0; t < splitResp.length; t++) {
									String[] splitPara = splitResp[t].split(",");
									FilesDownLoad += " " + splitPara[1];
									services.add(splitPara[1]);
								}
								System.out.println("*************************************");
								System.out.println("--> files to download : " + FilesDownLoad);

								//SEND MESSAGE TO GET WSDL FILE CORRESPONDING TO A SERVICE
								InputStream is;
								System.out.println("--> START DOWNLOADING FILES");
								for (int t = 0; t < services.size(); t++) {
									String MessageSendFile;
									MessageSendFile = "SendFile "+ services.get(t);
									out.println(MessageSendFile);
									out.flush();

									//READ THE STREAM AND WRITE ON THE FILE
									try {	
										is = soc.getInputStream();
										byte [] mybytearray  = new byte [12386];
										fos = new FileOutputStream(WsdlDownloadDirectry+services.get(t)+".wsdl");
										bos = new BufferedOutputStream(fos);
										bytesRead = is.read(mybytearray,0,mybytearray.length);
										bos.write(mybytearray, 0 , bytesRead);
										bos.flush();
										System.out.println("File of " + services.get(t) + " downloaded (" + bytesRead + " bytes read)");
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
								//***********************************************
								if (found) {break;}
							}
						}
						if (table1.isEmpty()) {
							NoComposition=true;
							break;
						}
						OldRequest.addAll(PreOldRequest2);
						PreOldRequest2.clear();
					}
					System.out.println("================RESULT===============");
					i++;
				}

				if (!NoService && !NoParameter) {
					System.out.println("--> Client's Command : " + MessageClient);
					System.out.println("--> TREEEEEEEEEEEEEEEEEEEEEEE");
					printTree(root, " ");
					if(!NoComposition) {
						for (String s : combination) {
							ComOfService += s + "<";
						}
						System.out.println("================================");
						System.out.println("combination is : " + ComOfService);
						System.out.println("================================");

					}
					else {
						ComOfService = "NoComposition";
						System.out.println("================================");
						System.out.println("there is no composition of services !");
						System.out.println("================================");

					}
				}

				System.out.println("****************************************");
				outClient.println(ComOfService);
				outClient.flush();	

			}
			else if (splitMessage[0].equals("SendFile")) {
				//SENDING FILES OF THE COMPOSITION TO THE CLIENT

				System.out.println("--> Sending files to client");
				try {
					osClient =SocClient.getOutputStream();
					File File = new File (WsdlDownloadDirectry+splitMessage[1]+".wsdl");
					byte [] mybytearray  = new byte [(int)File.length()];
					fis = new FileInputStream(File);
					bis = new BufferedInputStream(fis);
					bis.read(mybytearray,0,mybytearray.length);
					System.out.println("Sending File "+ splitMessage[1] +" (" + mybytearray.length + " bytes) ...");
					osClient.write(mybytearray,0,mybytearray.length);
					osClient.flush();
					System.out.println("Done.");
				}catch(IOException e) {
					e.printStackTrace();
				}


			}
		}
	}

}
//=================================================================================
class Node<T> {

	private T data = null;

	private String service ="";

	private List<Node<T>> children = new ArrayList<>();
	private Node<T> parent = null;

	public Node(T data) {
		this.data = data;
	}

	public Node<T> addChild(Node<T> child, String service) {
		child.setParent(this);
		child.setService(service);
		this.children.add(child);
		return child;
	}

	public void addChildren(List<Node<T>> children) {
		children.forEach(each -> each.setParent(this));
		this.children.addAll(children);
	}

	public List<Node<T>> getChildren() {
		return children;
	}

	public T getData() {
		return data;
	}
	public String getService() {
		return service;
	}
	public void setService(String service) {
		this.service = service;
	}

	public void setData(T data) {
		this.data = data;
	}

	private void setParent(Node<T> parent) {
		this.parent = parent;
	}

	public Node<T> getParent() {
		return parent;
	}

}





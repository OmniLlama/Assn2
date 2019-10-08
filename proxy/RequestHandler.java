package proxy;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.omg.PortableInterceptor.ServerRequestInterceptorOperations;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

// RequestHandler is thread that process requests of one client connection
public class RequestHandler extends Thread {

	Socket clientSocket;

	InputStream inFromClient;
	OutputStream outToClient;

	byte[] request = new byte[1024];
	String requestStr;

	BufferedReader inFromClientBufRdr;
	BufferedWriter outToClientBufRdr;

	String fileName = "";
	File cacheFile;
	private ProxyServer server;
	String method = "", url = "", ver = "", host = "", reqStr;

	public RequestHandler(Socket clientSocket, ProxyServer proxyServer) {

		this.clientSocket = clientSocket;

		this.server = proxyServer;

		try {
			clientSocket.setSoTimeout(200000);
			//setup streams
			inFromClient = clientSocket.getInputStream();
			outToClient = clientSocket.getOutputStream();
			//set up buffered I/Os
			inFromClientBufRdr = new BufferedReader(new InputStreamReader(inFromClient));
			outToClientBufRdr = new BufferedWriter(new OutputStreamWriter(outToClient));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override

	public void run() {
		// To do Process the requests from a client. In particular,
		// (1) Check the request type, only process GET request and ignore others
		// (2) If the url of GET request has been cached, respond with cached content
		// (3) Otherwise, call method proxyServertoClient to process the GET request

		String[] bufferedTokes;
		String header = "";
		char[] chars = new char[1024];
		while (clientSocket != null && clientSocket.isConnected()) {
			try {
				//Read request from browser
				inFromClientBufRdr.read(chars);
				//String processing / assignment
				reqStr = new String(chars);
				request = reqStr.getBytes();
				header = reqStr.split("\r\n")[0];
				bufferedTokes = header.split("\n|\r| ");
				
				//get request components and parse
				if (bufferedTokes.length > 1) {
					method = bufferedTokes[0];
					url = bufferedTokes[1];
					
					//3 - If get method, use functions
					if (method.equals("GET")) {

						host = header.split("/")[2];
						
						System.out.println("request: " + requestStr + "\nhost: " + host);
						if (ProxyServer.INST.getCache(url) != null) {
							//Send cache if exists
							sendCachedInfoToClient(ProxyServer.INST.getCache(url));							
						} else {
							//send request out, and create cache from response
							proxyServertoClient(request, host);
							
						}
						
					}

				}
				// 1

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.err.println(method + "  " + url + " Connection Issue");
				try {
					//join thread if error
					join();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
//				break;
			}
		}
		try {
			//join thread if socket disconnected / null
			join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private boolean proxyServertoClient(byte[] clientRequest, String host) {

		FileOutputStream fileOutStream = null;
		Socket toServerSocket = null;
		/*
		 * 
		 * FLUSH YO STREAMS AFTER WRITE
		 */
		InputStream inFromServer;
		OutputStream outToServer;

		// Create Buffered output stream to write to cached copy of file
//		String fileName = "cached/" + generateFileName() + ".dat";
		String fileName = "cached/" + generateRandomFileName() + ".dat";
		String dbgRepStr;
		// to handle binary content, byte is used
		byte[] serverReply = new byte[4096];
//		byte serverReplyByte;
		int size = 0;
		// To do
		// (1) Create a socket to connect to the web server (default port 80)
		// (2) Send client's request (clientRequest) to the web server, you may want to
		// use flush() after writing.
		// (3) Use a while loop to read all responses from web server and send back to
		// client
		// (4) Write the web server's response to a cache file, put the request URL and
		// cache file name to the cache Map
		// (5) close file, and sockets.
		
		try {
			// 1 Create Socket
			toServerSocket = new Socket(host, 80);
			
			// 2 Init streams, write out to server
			inFromServer = toServerSocket.getInputStream();
			outToServer = toServerSocket.getOutputStream();
			outToServer.write(clientRequest);
			outToServer.flush();
			size = inFromServer.available();
			// 3 -- reply Reader Loop/
			while (inFromServer.read(serverReply) != -1) {
				outToClient.write(serverReply);
				outToClient.flush();
				dbgRepStr = new String(serverReply);
				size += inFromServer.available();
			}
			//Close socket
			toServerSocket.close();
			
		} catch (UnknownHostException e) {// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//write cache file, write log
		try {
			cacheFile = new File(fileName);
			fileOutStream = new FileOutputStream(cacheFile);
			fileOutStream.write(serverReply);
			fileOutStream.close();
			ProxyServer.INST.putCache(sanitizeURL(url), fileName);
			ProxyServer.INST.writeLog(InetAddress.getByName(host) + " :: " + reqStr.split(" ")[1] + " " + size);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//close socket
		if (clientSocket != null) {
			try {
				clientSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//join thread
		try {
			join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;

	}

	// Sends the cached content stored in the cache file to the client
	private void sendCachedInfoToClient(String fileName) {

		try {

			byte[] bytes = Files.readAllBytes(Paths.get(fileName));

			outToClient.write(bytes);
			outToClient.flush();
			ProxyServer.INST.writeLog(reqStr.split(" ")[1] + "\n" + "Cache Pulled from file: " + fileName);

		} catch (Exception e) {
			e.printStackTrace();
		}

		try {

			if (clientSocket != null) {
				clientSocket.close();
			}

		} catch (Exception e) {
			e.printStackTrace();

		}
		try {
			join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String generateFileName() {
		if (fileName.isEmpty())
			fileName = "Placeholder_Cache_File_Name";
		return fileName;
	}

	// Generates a random file name
	public String generateRandomFileName() {

		String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
		SecureRandom RANDOM = new SecureRandom();
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < 10; ++i) {
			sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
	/**
	 * Removes extraneous chars and otherwise unnecessary parts of a URL
	 * @param s
	 * @return
	 */
	private String sanitizeURL(String s) {
		if (s.substring(0, 4).equals("http:".toLowerCase()))
			return s.substring(7);
		else if (s.substring(0, 4).equals("https".toLowerCase()))
			return s.substring(8);
		else
			return s;
	}
}
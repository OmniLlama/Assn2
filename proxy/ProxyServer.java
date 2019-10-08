package proxy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.org.objectweb.asm.util.CheckFieldAdapter;

public class ProxyServer {

	// cache is a Map: the key is the URL and the value is the file name of the
	// file that stores the cached content
	Map<String, String> cache;
	ServerSocket proxySocket;
	String logFileName = "proxy.log";
	File logFile = new File(logFileName);
	FileWriter fw;
	static boolean SERV_ACTIVE = true;
	static ProxyServer INST;

	public static void main(String[] args) {
		
		System.out.println("_You hit the start of the main method!");
		INST = new ProxyServer();
		INST.startServer(Integer.parseInt(args[0]));
		
//		System.out.println("__at main loop!");
		while (SERV_ACTIVE) {
			INST.CheckForConnection();
//			break;
		}
		System.out.println("___You hit the end of the main method!");
	}

	void startServer(int proxyPort) {

		cache = new ConcurrentHashMap<>();

		// create the directory to store cached files.
		File cacheDir = new File("cached");
		if (!cacheDir.exists() || (cacheDir.exists() && !cacheDir.isDirectory())) {
			cacheDir.mkdirs();
		}
		Socket clientSocket = null;
		/**
		 * To do: create a serverSocket to listen on the port (proxyPort) Create a
		 * thread (RequestHandler) for each new client connection remember to catch
		 * Exceptions!
		 *
		 */
		try {
			proxySocket = new ServerSocket(proxyPort);
//			proxySocket.setSoTimeout(5000);

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		INST.CheckForConnection();
	}

	public String getCache(String hashcode) {
		return cache.get(hashcode);
	}

	public void putCache(String hashcode, String fileName) {
		cache.put(hashcode, fileName);
	}

	public synchronized void writeLog(String info) {

		/**
		 * To do write string (info) to the log file, and add the current time stamp
		 * e.g. String timeStamp = new
		 * SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
		 *
		 */

		String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
			//writes info to log file
			try {
				fw = new FileWriter(logFile, true);
				fw.write((timeStamp + " " + info));
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public void CheckForConnection() {
		Socket clientSocket = null;
		RequestHandler reqHnd = null;
		//wait for and accept any incoming connections
		try {
			clientSocket = proxySocket.accept();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			try {
				//join if error
				reqHnd.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		//create and start new RequestHandler
		reqHnd = new RequestHandler(clientSocket, this);
		reqHnd.start();
	}
}

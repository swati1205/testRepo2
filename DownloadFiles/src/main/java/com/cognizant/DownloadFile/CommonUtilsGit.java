package com.cognizant.DownloadFile;

import hudson.model.BuildListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.X509Certificate;

import java.io.BufferedInputStream;
import java.net.MalformedURLException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;


public class CommonUtilsGit {

	private static org.apache.log4j.Logger logger = Logger.getLogger(CommonUtilsGit.class);


	// Downloads a file from the github repository to the local machine's folder
	public static void fileDownload(String localUrl, String localFileName, String repoName, String remoteFileName,
			String authFileLocation,BuildListener listener)
			throws NoSuchAlgorithmException, KeyManagementException, FileNotFoundException, IOException {

		sslCertificate();

		Properties prop = new Properties();
		InputStream input = null;
		input = new FileInputStream(authFileLocation);

		// load a properties file
		prop.load(input);

		// get the property value
		String userName = prop.getProperty("git.username");

		proxy(authFileLocation);

		String remote = "https://raw.githubusercontent.com/" + userName + "/" + repoName + "/master/" + remoteFileName;

		logger.info("Downloading file from github");
		listener.getLogger().println("Downloading file from github");
		downloadFileFromUrl(localUrl + "/" + localFileName, remote);
		logger.info("Downloaded file from github");
		listener.getLogger().println("Downloaded file from github");

	}

	public static void downloadFolder(String localUrl, String repoPath, String folderName, String authFileLocation)
			throws KeyManagementException, NoSuchAlgorithmException, IOException {
		proxy(authFileLocation);
		sslCertificate();

		String drive = localUrl;
		localUrl=localUrl+folderName;
		File file = new File(localUrl);
		file.mkdir();
		HashMap<String, String> map = new HashMap<String, String>();
		// String drive = "D:/";
		map = getFileUrl(repoPath, folderName, localUrl, drive, authFileLocation);
		ArrayList<String> namesList = new ArrayList<String>();

		Set<String> keys = map.keySet();
		for (String key : keys) {
			namesList.add(key);
		}
		int i;
		for (i = 0; i < map.size(); i++) {
			String remote = map.get(namesList.get(i));
			
			logger.info("Downloading file "+namesList.get(i)+" from github");
			downloadFileFromUrl(localUrl + "/" + namesList.get(i), remote);
			logger.info("Downloaded file "+namesList.get(i)+" from github");
		}
	}

	// Encrypts user name and password
	private static String getAuthentication(String authFileLocation) throws IOException {

		String userName, password;

		Properties prop = new Properties();
		InputStream input = null;
		String authStringEnc = null;
		try {
			input = new FileInputStream(authFileLocation);

			// load a properties file
			prop.load(input);

			// get the property value
			userName = prop.getProperty("git.username");
			password = prop.getProperty("git.password");

			String authString = userName + ":" + password;
			// generates base64 code for user name and password
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			logger.info("Successfully authenticated");

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.error(e);
				}
			}
		}
		return authStringEnc;
	}

	// sets proxy
	public static void proxy(String authFileLocation) throws FileNotFoundException, IOException {
		String host = "";
		String port = "";
		String user = "";
		String pwd = "";

		Properties prop = new Properties();
		// loads the property file
		prop.load(new FileInputStream(authFileLocation));

		// get the property value
		host = prop.getProperty("proxy.host");
		port = prop.getProperty("proxy.port");
		user = prop.getProperty("proxy.user");
		pwd = prop.getProperty("proxy.pwd");

		// all the above values should be present for the proxy to be set, else
		// the proxy will not be set
		if ((host != "") && (port != "") && (user != "") && (pwd != "")) {
			Properties systemSettings = System.getProperties();

			systemSettings.put("https.proxyHost", host);
			systemSettings.put("https.proxyPort", port);
			systemSettings.put("https.proxyUser", user);
			systemSettings.put("https.proxyPassword", pwd);

			systemSettings.put("http.proxyHost", host);
			systemSettings.put("http.proxyPort", port);
			systemSettings.put("http.proxyUser", user);
			systemSettings.put("http.proxyPassword", pwd);
		}
	}

	// posts a request
	public static void post(String repoPath, String sha, String branchName, String authFileLocation)
			throws ClientProtocolException, IOException {
		// Proxy settings
		proxy(authFileLocation);
		// OkHttp
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("application/json");
		// Constructs the body for the POST request
		RequestBody body = RequestBody.create(mediaType,
				"{\n\"ref\": \"refs/heads/" + branchName + "\",\n\"sha\":\"" + sha + "\"\n}");

		String authCode = getAuthentication(authFileLocation);

		// repo path =
		// https://api.github.com/repos/anamika0311/jpetstore-master/

		String postpath = "git/refs";
		String repo = repoPath + postpath;

		// Creates a POST request for the above url and body (constructed)
		Request request = new Request.Builder().url(repo).post(body).addHeader("authorization", "Basic " + authCode)
				.addHeader("content-type", "application/json").addHeader("cache-control", "no-cache").build();

		// Executes the request
		Response response = client.newCall(request).execute();
	}

	// returns the sha of the master in a repository
	public static String masterSha(String repoPath, String authFileLocation) throws IOException {
		// Proxy settings
		proxy(authFileLocation);
		int responsecode = 200;

		String shamaster = "git/trees/master";

		String repo = repoPath + shamaster;
		URL httpsUrl = new URL(repo);
		HttpsURLConnection conn = (HttpsURLConnection) httpsUrl.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);

		conn.setRequestMethod("GET");

		conn.setRequestProperty("Accept", "application/json");
		responsecode = conn.getResponseCode();

		if (responsecode != 200) {
			throw new RuntimeException("Failed : HTTP error code : " + responsecode);
		}
		// Reads the response(JSON response) from that connection object
		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		String output;
		StringBuffer outputJson = new StringBuffer();
		while ((output = br.readLine()) != null) {
			outputJson.append(output);
		}
		int i;
		String json = "[" + outputJson.toString() + "]";
		// Converts the JSON Response string to JSON array
		JSONArray jsonArray = new JSONArray(json);
		String sha = null;
		for (i = 0; i < jsonArray.length(); i++) {
			JSONObject objects = jsonArray.getJSONObject(i);
			// Gets the JSON object "sha" from the JSON response
			sha = objects.getString("sha");
		}
		conn.disconnect();
		return sha;
	}

	
	// Gives the list of branches in a repository
	public static ArrayList<String> BranchesInRepo(String repoPath, String authFileLocation) throws IOException {
		int responsecode = 200;

		// repo path
		// ="https://api.github.com/repos/anamika0311/jpetstore-master/"

		String repo = repoPath + "branches";
		URL httpsUrl = new URL(repo);
		// Establishes connection for the above url
		HttpsURLConnection conn = (HttpsURLConnection) httpsUrl.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);

		// sets the request method as GET to the connection object
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		responsecode = conn.getResponseCode();

		if (responsecode != 200) {
			throw new RuntimeException("Failed : HTTP error code : " + responsecode);
		}
		// Reads the response(JSON response) from that connection object
		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
		String output;
		StringBuffer outputJson = new StringBuffer();
		while ((output = br.readLine()) != null) {
			outputJson.append(output);
		}

		String json = outputJson.toString();

		// converts the JSON response to JSON Array
		JSONArray jsonArray = new JSONArray(json);
		ArrayList<String> listOfBranches = new ArrayList<String>();
		// Iterates the JSON Array
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject objects = jsonArray.getJSONObject(i);
			// Gets the object named "name" and stores it in a string
			String name = objects.getString("name");
			listOfBranches.add(name);
		}
		return listOfBranches;
	}

	// Function for Downloading a file
	public static void downloadFileFromUrl(String fileName, String fileUrl) throws MalformedURLException, IOException {
		BufferedInputStream inStream = null;
		FileOutputStream outStream = null;
		try {
			URL fileUrlObj = new URL(fileUrl);
			inStream = new BufferedInputStream(fileUrlObj.openStream());
			outStream = new FileOutputStream(fileName);

			byte data[] = new byte[1024];
			int count;
			while ((count = inStream.read(data, 0, 1024)) != -1) {
				outStream.write(data, 0, count);
			}
		} finally {
			if (inStream != null)
				inStream.close();
			if (outStream != null)
				outStream.close();
		}
	}

	public static HashMap<String, String> getFileUrl(String repoPath, String folderName, String localUrl, String drive, String authFileLocation) throws IOException {
	

		proxy(authFileLocation);
		int responsecode = 200;
			
		//repoPath= "https://api.github.com/repos/anamika0311/jpetstore-master/contents/"
		String repo = repoPath  + folderName;
			
		URL httpsUrl = new URL(repo);
		HttpsURLConnection conn = (HttpsURLConnection) httpsUrl.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);

		conn.setRequestMethod("GET");

		conn.setRequestProperty("Accept", "application/json");
		responsecode = conn.getResponseCode();

		if (responsecode != 200) {
			throw new RuntimeException("Failed : HTTP error code : " + responsecode);
		}
	
		// Reads the response(JSON response) from that connection object
		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		String output;
		StringBuffer outputJson = new StringBuffer();
		while ((output = br.readLine()) != null) {
			outputJson.append(output);
		}
		int i;
		
		String json = outputJson.toString();
		
		HashMap<String, String> map = new HashMap<String, String>();
		
		// Converts the JSON Response string to JSON array
		JSONArray jsonArray = new JSONArray(json);
		String name = null;
		String pathObject = null;
		String downloadUrl = null;
		for (i = 0; i < jsonArray.length(); i++) {
			JSONObject objects = jsonArray.getJSONObject(i);
			// Gets the JSON object "sha" from the JSON response
			name = objects.getString("name");
			pathObject = objects.getString("path");
			
			String regex = "^[A-Za-z0-9-_,\\s]+[.]{1}[A-Za-z]{3}$";
			if(name.matches(regex))
			{
				downloadUrl = objects.getString("download_url");
				map.put(name, downloadUrl);
			}
			else
			{
				String path = repo+"/";
				HashMap<String, String> map1 = getFileUrl(path, name,localUrl, drive, authFileLocation);
				
				//File file = new File("D:/"+pathObject);
				File file = new File(drive+pathObject);
				if(pathObject.contains("/")){
					String []files = pathObject.split("/");
					String filePath = "";
					for(String temp : files){
						filePath = filePath+temp+"/";
						File dirs = new File(drive+filePath);
						dirs.mkdir();
					}
				}
				file.mkdir();
				System.out.println("Folder names:   "+file.getAbsolutePath());
				
				Set<String> keys = map1.keySet();
				ArrayList<String> list = new ArrayList<String>();
				for (String key : keys) {
					list.add(key);
				}
				for (int j =0 ;j<list.size();j++)
				{
					downloadUrl = map1.get(list.get(j));
					map.put(name+"/"+list.get(j), downloadUrl);	
				}
			}
		}
		conn.disconnect();
		return map;
	}

	public static void sslCertificate() throws NoSuchAlgorithmException, KeyManagementException {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers1() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
					throws CertificateException {

			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
					throws CertificateException {

			}

			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				// TODO Auto-generated method stub
				return null;
			}
		} };

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify1(String hostname, SSLSession session) {
				return true;
			}

			public boolean verify(String arg0, SSLSession arg1) {
				return false;
			}
		};
		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}

}

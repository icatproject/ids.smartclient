package org.icatproject.ids.smartclient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

import org.icatproject.icat.client.ICAT;
import org.icatproject.icat.client.Session;
import org.icatproject.ids.client.BadRequestException;
import org.icatproject.ids.client.DataSelection;
import org.icatproject.ids.client.IdsClient;
import org.icatproject.ids.client.IdsClient.Flag;
import org.icatproject.ids.client.IdsClient.Status;
import org.icatproject.ids.client.InsufficientPrivilegesException;
import org.icatproject.ids.client.InternalException;
import org.icatproject.ids.client.NotFoundException;
import org.icatproject.ids.client.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
// Keeps eclipse happy
public class Server {
	private static Path dot;
	private static Path top;
	private final static Logger logger = LoggerFactory.getLogger(Server.class);

	public static void main(String[] args) throws Exception {

		logger.info("Personal server starting");

		int port = 8888;
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		httpServer.setExecutor(Executors.newCachedThreadPool());

		httpServer.createContext("/getData", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("POST")) {
					report(httpExchange, 404, "BadRequestException", "POST expected");
				} else {
					String line = null;
					try (BufferedReader in = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody()));) {
						line = in.readLine();
					}
					if (line != null) {
						IdsClient idsClient;
						String sessionId;
						DataSelection data;
						try {
							String[] pairs = line.split("&");
							Map<String, String> map = new HashMap<>();
							for (String pair : pairs) {
								int idx = pair.indexOf("=");
								map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
										URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
							}
							logger.debug("POST parms " + map);
							if (!map.containsKey("idsUrl")) {
								report(httpExchange, 400, "BadRequestException", "idsUrl not specified");
								return;
							}
							String idsUrl = map.get("idsUrl");
							idsClient = new IdsClient(new URL(idsUrl));

							try {
								sessionId = getSessionId(idsUrl);
							} catch (Exception e) {
								report(httpExchange, 404, "BadRequestException", "Please login to " + idsUrl + " first");
								return;
							}
							logger.debug("SessionId " + sessionId);

							data = new DataSelection();
							String list = map.get("investigationIds");
							if (list != null) {
								for (String one : list.split(",")) {
									long num = Long.parseLong(one);
									addRequest(idsUrl + " GET Investigation " + num);
									data.addInvestigation(num);
								}
							}
							list = map.get("datasetIds");
							if (list != null) {
								for (String one : list.split(",")) {
									long num = Long.parseLong(one);
									addRequest(idsUrl + " GET Dataset " + num);
									data.addDataset(num);
								}
							}
							list = map.get("datafileIds");
							if (list != null) {
								for (String one : list.split(",")) {
									long num = Long.parseLong(one);
									addRequest(idsUrl + " GET Datafile " + num);
									data.addDatafile(num);
								}
							}
						} catch (Exception e) {
							report(httpExchange, 500, "UnexpectedException", e.getMessage());
							return;
						}

						try {
							idsClient.restore(sessionId, data);
							httpExchange.sendResponseHeaders(200, 0);
							httpExchange.getResponseBody().close();
							System.out.println("Success!");
						} catch (NotImplementedException | BadRequestException | InsufficientPrivilegesException
								| InternalException | NotFoundException e) {
							report(httpExchange, 400, e.getClass().getSimpleName(), e.getMessage());
						}

					}

				}
			}

		});

		httpServer.createContext("/login", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("POST")) {
					report(httpExchange, 404, "BadRequestException", "POST expected");
				} else {
					BufferedReader in = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody()));
					String line = in.readLine();
					if (line != null) {
						String[] pairs = line.split("&");
						Map<String, String> map = new HashMap<>();
						for (String pair : pairs) {
							int idx = pair.indexOf("=");
							map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
									URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
						}

						String filename = getServerFile(map.get("idsUrl"));
						try (PrintWriter os = new PrintWriter(dot.resolve("servers").resolve(filename).toFile())) {
							os.println(map.get("sessionId"));
						}
					}
					httpExchange.sendResponseHeaders(200, 0);
					httpExchange.getResponseBody().close();
				}
			}
		});

		Path home = Paths.get(System.getProperty("user.home"));
		dot = home.resolve(".smartclient");
		// TODO Fix for windows
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions
				.fromString("rwx------"));
		Files.createDirectories(dot, attr);
		Files.createDirectories(dot.resolve("servers"));
		Files.createDirectories(dot.resolve("requests"));

		top = home.resolve("smartclient");
		httpServer.start();

		processRequests();

		logger.info("Personal server started");
	}

	private static void processRequests() throws InterruptedException {

		while (true) {
			File[] files = dot.resolve("requests").toFile().listFiles();
			if (files.length == 0) {
				Thread.sleep(1000);
			} else {
				for (File file : files) {
					try (BufferedReader br = new BufferedReader(new FileReader(file))) {
						String[] cmd = br.readLine().split("\\s");
						logger.debug("Process request: " + cmd[0] + " " + cmd[1] + " " + cmd[2] + " " + cmd[3]);
						if (cmd[1].equals("GET")) {
							if (cmd[2].equals("Investigation")) {
								processGetInvestigation(cmd);
							} else if (cmd[2].equals("Datafile")) {
								processGetDatafile(cmd);
							}
						}
					} catch (Exception e) {
						logger.debug(e.getClass() + " " + e.getMessage());
					}
					try {
						Files.delete(file.toPath());
					} catch (IOException e) {
						logger.debug(e.getClass() + " " + e.getMessage());
					}
				}

			}
		}

	}

	private static void processGetDatafile(String[] cmd) {

		try {
			IdsClient idsClient = new IdsClient(new URL(cmd[0]));
			String sessionId = getSessionId(cmd[0]);

			String icatUrl = idsClient.getIcatUrl().toString();
			ICAT icatClient = new ICAT(icatUrl);
			Session session = icatClient.getSession(sessionId);
			long dfId = Long.parseLong(cmd[3]);
			String jsonString = session.get("Datafile", dfId);
			try (JsonReader parser = Json.createReader(new ByteArrayInputStream(jsonString.getBytes()))) {
				JsonObject datafile = (JsonObject) parser.readObject().get("Datafile");
				String location = ((JsonString) datafile.get("location")).getString();
				long fileSize = ((JsonNumber) datafile.get("fileSize")).longValueExact();
				logger.debug("with: " + location + " " + fileSize);
				if (location.startsWith("/")) {
					location = location.substring(1);
				}
				Path target = top.resolve(location);
				Files.createDirectories(target.getParent());
				if (!Files.exists(top.resolve(location))) {
					DataSelection data = new DataSelection().addDatafile(dfId);
					Status status = idsClient.getStatus(sessionId, data);
					if (status == Status.ONLINE) {
						try (InputStream in = idsClient.getData(sessionId, data, Flag.NONE, 0)) {
							Files.copy(in, target);
						}
					} else if (status == Status.ARCHIVED) {
						idsClient.restore(sessionId, data);
					}
				}
			}

			Status status = idsClient.getStatus(sessionId, new DataSelection().addDatafile(dfId));
			logger.debug("Status " + status);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void processGetInvestigation(String[] cmd) {
		int offset = 0;

		IdsClient idsClient = null;
		try {
			idsClient = new IdsClient(new URL(cmd[0]));
		} catch (MalformedURLException e1) {
			// Can't happen
		}
		String sessionId;
		try {
			sessionId = getSessionId(cmd[0]);
			String icatUrl = idsClient.getIcatUrl().toString();
			ICAT icatClient = new ICAT(icatUrl);
			Session session = icatClient.getSession(sessionId);
			while (true) {
				String query = "SELECT df.id FROM Datafile df WHERE df.dataset.investigation.id = " + cmd[3]
						+ " ORDER BY df.id LIMIT " + offset + ", 1000";
				String jsonString = session.search(query);
				logger.debug(jsonString);
				try (JsonReader parser = Json.createReader(new ByteArrayInputStream(jsonString.getBytes()))) {
					JsonArray dfids = parser.readArray();
					for (JsonValue v : dfids) {
						addRequest(cmd[0] + " GET Datafile " + v);
					}
					if (dfids.size() < 1000) {
						break;
					}

					offset += 1000;
				}
			}
		} catch (Exception e) {
			// TODO
			logger.debug(e.getClass().getSimpleName() + " " + e.getMessage());
			return;
		}

	}

	protected static void addRequest(String request) {
		try (PrintWriter os = new PrintWriter(dot.resolve("requests").resolve(UUID.randomUUID().toString()).toFile())) {
			os.println(request);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
		}
	}

	protected static String getSessionId(String idsUrl) throws IOException {
		String filename = getServerFile(idsUrl);
		try (BufferedReader br = new BufferedReader(new FileReader(dot.resolve("servers").resolve(filename).toFile()));) {
			return br.readLine();
		}
	}

	protected static String getServerFile(String urlString) throws IOException {
		try {

			URL url = new URL(urlString);
			url = new URL(url.getProtocol(), url.getHost().toLowerCase(), url.getPort(), url.getFile());

			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hashedBytes = digest.digest(url.toString().getBytes("UTF-8"));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < hashedBytes.length; i++) {
				sb.append(Integer.toString((hashedBytes[i] & 0xff) + 0x100, 16).substring(1));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IOException("Failed to decode " + urlString + ":" + e.getMessage());
		}
	}

	private static void report(HttpExchange httpExchange, int rc, String code, String msg) {
		logger.debug("Reporting error " + code + " " + msg);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		try {
			gen.writeStartObject().write("code", code).write("message", msg).writeEnd().close();
			byte[] out = baos.toString().getBytes("UTF-8");
			httpExchange.sendResponseHeaders(400, out.length);
			OutputStream os = httpExchange.getResponseBody();
			os.write(out);
			os.close();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}
}
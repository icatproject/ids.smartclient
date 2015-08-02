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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

import org.icatproject.icat.client.ICAT;
import org.icatproject.icat.client.IcatException;
import org.icatproject.icat.client.IcatException.IcatExceptionType;
import org.icatproject.icat.client.Session;
import org.icatproject.ids.client.BadRequestException;
import org.icatproject.ids.client.DataNotOnlineException;
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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
// Keeps eclipse happy
public class Server {
	public class RestoreTask implements Callable<Void> {

		private String sessionId;
		private DataSelection data;
		private IdsClient idsClient;

		public RestoreTask(IdsClient idsClient, String sessionId, DataSelection data) {
			this.idsClient = idsClient;
			this.sessionId = sessionId;
			this.data = data;
		}

		@Override
		public Void call() {
			try {
				idsClient.restore(sessionId, data);
				logger.debug("Restore request was succesful");
			} catch (NotImplementedException | BadRequestException | InsufficientPrivilegesException
					| InternalException | NotFoundException e) {
				logger.warn("Restore request failed " + e.getClass().getSimpleName() + " " + e.getMessage());
			}
			return null;
		}

	}

	private static Path dot;
	private static Path top;
	private final static Logger logger = LoggerFactory.getLogger(Server.class);

	public static void main(String[] args) throws Exception {
		new Server();
	}

	private ExecutorService singleThreadPool;

	public Server() throws IOException {
		logger.info("Personal server starting");

		int port = 8888;
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		httpServer.setExecutor(Executors.newCachedThreadPool());

		httpServer.createContext("/getData", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("POST")) {
					report(httpExchange, 404, "BadRequestException", "POST expected");
				} else {
					byte[] jsonBytes;
					try (BufferedReader in = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody()));) {
						String line = in.readLine();
						int idx = line.indexOf("=");
						jsonBytes = URLDecoder.decode(line.substring(idx + 1), "UTF-8").getBytes("UTF-8");
					}

					IdsClient idsClient;
					String sessionId;
					DataSelection data = new DataSelection();

					try (JsonReader reader = Json.createReader(new ByteArrayInputStream(jsonBytes))) {
						JsonObject json = reader.readObject();
						String idsUrl = json.getString("idsUrl");

						idsClient = new IdsClient(new URL(idsUrl));

						try {
							sessionId = getSessionId(idsUrl);
						} catch (Exception e) {
							report(httpExchange, 404, "BadRequestException", "Please login to " + idsUrl + " first");
							return;
						}
						logger.debug("SessionId " + sessionId);

						JsonArray list = json.getJsonArray("investigationIds");
						if (list != null) {
							for (JsonValue jv : list) {
								long num = ((JsonNumber) jv).longValueExact();
								addRequest(idsUrl + " GET Investigation " + num);
								data.addInvestigation(num);
							}
						}

						list = json.getJsonArray("datasetIds");
						if (list != null) {
							for (JsonValue jv : list) {
								long num = ((JsonNumber) jv).longValueExact();
								addRequest(idsUrl + " GET Dataset " + num);
								data.addDataset(num);
							}
						}

						list = json.getJsonArray("datafileIds");
						if (list != null) {
							for (JsonValue jv : list) {
								long num = ((JsonNumber) jv).longValueExact();
								addRequest(idsUrl + " GET Datafile " + num);
								data.addDatafile(num);
							}
						}
					} catch (Exception e) {
						report(httpExchange, 500, "UnexpectedException", e.getMessage());
						return;
					}

					singleThreadPool.submit(new RestoreTask(idsClient, sessionId, data));
					corsify(httpExchange, 200, 0);
					httpExchange.close();
				}

			}

		});

		httpServer.createContext("/status", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("GET")) {
					report(httpExchange, 404, "BadRequestException", "GET expected");
				} else {

					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					JsonGenerator gen = Json.createGenerator(baos);
					try {
						gen.writeStartObject().write("requests", dot.resolve("requests").toFile().listFiles().length)
								.write("dfids", dot.resolve("dfids").toFile().listFiles().length)
								.writeStartArray("servers");
						for (File file : dot.resolve("servers").toFile().listFiles()) {
							try (BufferedReader br = new BufferedReader(new FileReader(file));) {
								String sessionId = br.readLine();
								String idsUrl = br.readLine();
								IdsClient idsClient = new IdsClient(new URL(idsUrl));
								String icatUrl;
								try {
									icatUrl = idsClient.getIcatUrl().toString();
									ICAT icatClient = new ICAT(icatUrl);
									Session session = icatClient.getSession(sessionId);
									String user = session.getUserName();
									gen.writeStartObject().write("idsUrl", idsUrl).write("user", user).writeEnd();
								} catch (InternalException | NotImplementedException | BadRequestException
										| URISyntaxException e) {
									report(httpExchange, 500, "Possible internal error",
											e.getClass() + " " + e.getMessage());
									return;
								} catch (IcatException e) {
									gen.writeStartObject().write("idsUrl", idsUrl).writeEnd();
								}
							}
						}
						gen.writeEnd().writeEnd().close();
						byte[] out = baos.toString().getBytes("UTF-8");
						corsify(httpExchange, 200, out.length);
						OutputStream os = httpExchange.getResponseBody();
						os.write(out);
						os.close();
					} catch (Exception e) {
						logger.error(e.getMessage());
					}
				}
			}
		});

		httpServer.createContext("/ping", new HttpHandler() {

			public void handle(HttpExchange httpExchange) {
				if (!httpExchange.getRequestMethod().equals("GET")) {
					report(httpExchange, 404, "BadRequestException", "GET expected");
				} else {
					try {
						corsify(httpExchange, 200, 0);
						httpExchange.close();
					} catch (Exception e) {
						logger.error(e.getMessage());
					}
				}
			}
		});

		httpServer.createContext("/login", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				logger.debug("Login request received");
				if (!httpExchange.getRequestMethod().equals("POST")) {
					report(httpExchange, 404, "BadRequestException", "POST expected");
				} else {
					byte[] jsonBytes;
					try (BufferedReader in = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody()));) {
						String line = in.readLine();
						int idx = line.indexOf("=");
						jsonBytes = URLDecoder.decode(line.substring(idx + 1), "UTF-8").getBytes("UTF-8");
					}
					try (JsonReader reader = Json.createReader(new ByteArrayInputStream(jsonBytes))) {
						JsonObject json = reader.readObject();
						String idsUrl = json.getString("idsUrl");
						String sessionId = json.containsKey("sessionId") ? json.getString("sessionId") : null;
						String plugin = json.containsKey("plugin") ? json.getString("plugin") : null;
						IdsClient idsClient = new IdsClient(new URL(idsUrl));
						String filename = getServerFileName(idsUrl);

						try {
							String icatUrl = idsClient.getIcatUrl().toString();
							ICAT icatClient = new ICAT(icatUrl);
							if (sessionId == null) {
								JsonObject credentials = json.getJsonObject("credentials");
								Map<String, String> credentialMap = new HashMap<>();
								for (Entry<String, JsonValue> entry : credentials.entrySet()) {
									credentialMap.put(entry.getKey(), ((JsonString) entry.getValue()).getString());
								}
								sessionId = icatClient.login(plugin, credentialMap).getId();
							} else {
								Session session = icatClient.getSession(sessionId);
								session.refresh();
							}
						} catch (InternalException | NotImplementedException | BadRequestException | URISyntaxException e) {
							report(httpExchange, 500, "Possible internal error", e.getClass() + " " + e.getMessage());
							return;
						} catch (IcatException e) {
							report(httpExchange, 403, "ICAT reports", e.getClass() + " " + e.getMessage());
							return;
						}

						try (PrintWriter os = new PrintWriter(dot.resolve("servers").resolve(filename).toFile())) {
							os.println(sessionId);
							os.println(idsUrl);
						}

					} catch (Exception e) {
						e.printStackTrace();
						report(httpExchange, 403, "Unexpected error", e.getClass() + " " + e.getMessage());
						return;
					}
					corsify(httpExchange, 200, 0);
					httpExchange.getResponseBody().close();
				}
			}
		});

		httpServer.createContext("/logout", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("POST")) {
					report(httpExchange, 404, "BadRequestException", "POST expected");
				} else {
					byte[] jsonBytes;
					try (BufferedReader in = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody()));) {
						String line = in.readLine();
						int idx = line.indexOf("=");
						jsonBytes = URLDecoder.decode(line.substring(idx + 1), "UTF-8").getBytes("UTF-8");
					}
					try (JsonReader reader = Json.createReader(new ByteArrayInputStream(jsonBytes))) {
						JsonObject json = reader.readObject();
						String idsUrl = json.getString("idsUrl");
						IdsClient idsClient = new IdsClient(new URL(idsUrl));
						String filename = getServerFileName(idsUrl);

						try {
							String sessionId = getSessionId(idsUrl);
							String icatUrl = idsClient.getIcatUrl().toString();
							ICAT icatClient = new ICAT(icatUrl);
							Session session = icatClient.getSession(sessionId);
							session.logout();
						} catch (Exception e) {
							// Ignore
						}
						try {
							Files.delete(dot.resolve("servers").resolve(filename));
						} catch (Exception e) {
							// Ignore
						}

					} catch (Exception e) {
						e.printStackTrace();
						report(httpExchange, 403, "Unexpected error", e.getClass() + " " + e.getMessage());
						return;
					}
					corsify(httpExchange, 200, 0);
					httpExchange.getResponseBody().close();
				}
			}
		});

		singleThreadPool = Executors.newSingleThreadExecutor();

		Path home = Paths.get(System.getProperty("user.home"));
		dot = home.resolve(".smartclient");

		try {
			FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions
					.fromString("rwx------"));
			Files.createDirectories(dot, attr);
		} catch (UnsupportedOperationException e) {
			Files.createDirectories(dot);
		}
		Files.createDirectories(dot.resolve("servers"));
		Files.createDirectories(dot.resolve("requests"));
		Files.createDirectories(dot.resolve("dfids"));

		top = home.resolve("smartclient");
		httpServer.start();

		ProcessRequests pr = new ProcessRequests();
		new Thread(pr).start();

		ProcessGetDatafiles pdf = new ProcessGetDatafiles();
		new Thread(pdf).start();

		RefreshTask rt = new RefreshTask();
		new Thread(rt).start();

		logger.info("Personal server started");
	}

	class RefreshTask implements Runnable {

		@Override
		public void run() {
			while (true) {
				for (File file : dot.resolve("servers").toFile().listFiles()) {
					try {
						String sessionId;
						String idsUrl;
						try (BufferedReader br = new BufferedReader(new FileReader(file));) {
							sessionId = br.readLine();
							idsUrl = br.readLine();
						}

						IdsClient idsClient = new IdsClient(new URL(idsUrl));

						String icatUrl = null;
						try {
							icatUrl = idsClient.getIcatUrl().toString();
							ICAT icatClient = new ICAT(icatUrl);
							Session session = icatClient.getSession(sessionId);
							session.refresh();
						} catch (InternalException | NotImplementedException | BadRequestException | URISyntaxException e) {
							logger.warn("RefreshTask possible internal error for " + file + " " + " for Icat "
									+ icatUrl + " " + e.getMessage());
						} catch (IcatException e) {
							logger.debug("ICAT reports " + e.getClass() + " " + e.getMessage());
							Files.delete(file.toPath());
						}
					} catch (IOException e) {
						logger.warn("RefreshTask possible internal error for " + file + " " + e.getClass() + " "
								+ e.getMessage());
					}
				}
				try {
					Thread.sleep(300 * 1000); // Five minutes
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	class ProcessRequests implements Runnable {
		public void run() {
			while (true) {
				File[] files = dot.resolve("requests").toFile().listFiles();
				if (files.length == 0) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						return;
					}
				} else {
					for (File file : files) {
						try (BufferedReader br = new BufferedReader(new FileReader(file))) {
							String[] cmd = br.readLine().split("\\s");
							logger.debug("Process request: " + cmd[0] + " " + cmd[1] + " " + cmd[2] + " " + cmd[3]);
							if (cmd[1].equals("GET")) {
								if (cmd[2].equals("Investigation")) {
									processGetInvestigation(cmd);
								} else if (cmd[2].equals("Dataset")) {
									processGetDataset(cmd);
								} else if (cmd[2].equals("Datafile")) {
									processGetDatafile(cmd);
								}
							}
						} catch (Exception e) {
							logger.warn(e.getClass() + " " + e.getMessage());
						}
						try {
							Files.delete(file.toPath());
						} catch (IOException e) {
							logger.warn(e.getClass() + " " + e.getMessage());
						}
					}
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						return;
					}
				}
			}

		}
	}

	private static void processGetDatafile(String[] cmd) throws Exception {
		Long.parseLong(cmd[3]);
		File target = dot.resolve("dfids").resolve(cmd[3]).toFile();
		try (PrintWriter os = new PrintWriter(target)) {
			os.println(cmd[0]);
		}
	}

	public class ProcessOneDf implements Callable<Void> {

		private File file;

		public ProcessOneDf(File file) {
			this.file = file;
		}

		@Override
		public Void call() throws Exception {
			String idsUrl;
			try (BufferedReader br = new BufferedReader(new FileReader(file));) {
				idsUrl = br.readLine();
			}

			IdsClient idsClient = new IdsClient(new URL(idsUrl));
			String sessionId = getSessionId(idsUrl);

			String icatUrl = idsClient.getIcatUrl().toString();
			ICAT icatClient = new ICAT(icatUrl);
			Session session = icatClient.getSession(sessionId);
			long dfId = Long.parseLong(file.getName());
			String jsonString;
			try {
				jsonString = session.get("Datafile", dfId);
			} catch (IcatException e) {
				IcatExceptionType type = e.getType();
				if (type == IcatExceptionType.NO_SUCH_OBJECT_FOUND) {
					logger.debug("File " + dfId + " does not exist so stop looking for it");
					Files.delete(file.toPath());
				} else {
					logger.debug("Get of file " + dfId + " reports " + type + " " + e.getMessage());
				}
				return null;
			}

			String location;
			long fileSize;
			try (JsonReader parser = Json.createReader(new ByteArrayInputStream(jsonString.getBytes()))) {
				JsonObject datafile = (JsonObject) parser.readObject().get("Datafile");
				location = ((JsonString) datafile.get("location")).getString();
				fileSize = ((JsonNumber) datafile.get("fileSize")).longValueExact();
			}
			if (location.startsWith("/")) {
				location = location.substring(1);
			}
			Path target = top.resolve(getServerFileName(idsUrl)).resolve(location);
			Files.createDirectories(target.getParent());
			if (Files.exists(target)) {
				if (Files.size(target) == fileSize) {
					Files.delete(file.toPath());
					logger.debug("File " + target + " already present");
					return null;
				}
			}

			DataSelection data = new DataSelection().addDatafile(dfId);
			Status status = idsClient.getStatus(sessionId, data);

			if (status == Status.ONLINE) {
				logger.debug("File " + dfId + " location " + location + " size " + fileSize + " bytes will be obtained");
				Path temp = Files.createTempFile(target.getParent(), null, null);

				try (InputStream in = idsClient.getData(sessionId, data, Flag.NONE, 0)) {
					Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException | NotImplementedException | BadRequestException | InsufficientPrivilegesException
						| NotFoundException | InternalException | DataNotOnlineException e) {
					Files.delete(temp);
					return null;
				}

				Files.move(temp, target);
				Files.delete(file.toPath());
				logger.debug("File " + target + " retrieved");
			} else if (status == Status.ARCHIVED) {
				idsClient.restore(sessionId, data);
				logger.debug("File " + dfId + " location " + location + " will be restored");
			} else if (status == Status.RESTORING) {
				logger.debug("File " + dfId + " location " + location + " is being restored");
			}
			return null;
		}
	}

	public class ProcessGetDatafiles implements Runnable {

		@Override
		public void run() {
			int ncores = Runtime.getRuntime().availableProcessors();
			ExecutorService threadPool = Executors.newFixedThreadPool(ncores * 2);
			Map<File, Future<Void>> queued = new HashMap<>();

			while (true) {
				File[] files = dot.resolve("dfids").toFile().listFiles();
				if (files.length != 0) {
					for (File file : files) {
						if (!queued.containsKey(file)) {
							Future<Void> f = threadPool.submit(new ProcessOneDf(file));
							queued.put(file, f);
							logger.debug("Queued request to get " + file.getName());
						}
					}
					Iterator<File> iter = queued.keySet().iterator();
					while (iter.hasNext()) {
						File file = iter.next();
						if (queued.get(file).isDone()) {
							try {
								queued.get(file).get();
							} catch (InterruptedException e) {
								// Do nothing
							} catch (ExecutionException e) {
								Throwable c = e.getCause();
								logger.debug("Get " + file + " failed with " + c.getClass() + " " + c.getMessage());
							}
							iter.remove();
						}
					}
				}
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void corsify(HttpExchange httpExchange, int sc, int length) throws IOException {
		Headers headers = httpExchange.getResponseHeaders();
		headers.add("Access-Control-Allow-Origin", "*");
		headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
		httpExchange.sendResponseHeaders(sc, length);
	}

	private static void processGetInvestigation(String[] cmd) throws Exception {
		IdsClient idsClient = null;
		try {
			idsClient = new IdsClient(new URL(cmd[0]));
		} catch (MalformedURLException e1) {
			// Can't happen
		}
		String sessionId = getSessionId(cmd[0]);

		for (Long invid : idsClient.getDatafileIds(sessionId,
				new DataSelection().addInvestigation(Long.parseLong(cmd[3])))) {
			addRequest(cmd[0] + " GET Datafile " + Long.toString(invid));
		}
	}

	private static void processGetDataset(String[] cmd) throws Exception {

		IdsClient idsClient = null;
		try {
			idsClient = new IdsClient(new URL(cmd[0]));
		} catch (MalformedURLException e1) {
			// Can't happen
		}
		String sessionId;

		sessionId = getSessionId(cmd[0]);

		for (Long invid : idsClient.getDatafileIds(sessionId, new DataSelection().addDataset(Long.parseLong(cmd[3])))) {
			addRequest(cmd[0] + " GET Datafile " + Long.toString(invid));
		}
	}

	private static void addRequest(String request) throws FileNotFoundException {
		try (PrintWriter os = new PrintWriter(dot.resolve("requests").resolve(UUID.randomUUID().toString()).toFile())) {
			os.println(request);
		}
	}

	private static String getSessionId(String idsUrl) throws IOException {
		String filename = getServerFileName(idsUrl);
		try (BufferedReader br = new BufferedReader(new FileReader(dot.resolve("servers").resolve(filename).toFile()));) {
			return br.readLine();
		}
	}

	private static String getServerFileName(String urlString) throws IOException {
		URL url = new URL(urlString);
		return url.getHost().toLowerCase();
	}

	private static void report(HttpExchange httpExchange, int rc, String code, String msg) {
		logger.debug("Reporting error " + code + " " + msg);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		try {
			gen.writeStartObject().write("code", code).write("message", msg).writeEnd().close();
			byte[] out = baos.toString().getBytes("UTF-8");
			corsify(httpExchange, 400, out.length);
			OutputStream os = httpExchange.getResponseBody();
			os.write(out);
			os.close();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}
}
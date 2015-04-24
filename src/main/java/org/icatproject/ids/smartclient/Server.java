package org.icatproject.ids.smartclient;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.icatproject.ids.client.BadRequestException;
import org.icatproject.ids.client.DataSelection;
import org.icatproject.ids.client.IdsClient;
import org.icatproject.ids.client.InsufficientPrivilegesException;
import org.icatproject.ids.client.InternalException;
import org.icatproject.ids.client.NotFoundException;
import org.icatproject.ids.client.NotImplementedException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
// Keeps eclipse happy
public class Server {
	private static Path dot;

	public static void main(String[] args) throws Exception {
		int port = 8888;
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		httpServer.setExecutor(Executors.newCachedThreadPool());

		httpServer.createContext("/getData", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				byte[] out = null;
				int resp = -1;
				if (!httpExchange.getRequestMethod().equals("POST")) {
					out = "POST expected".getBytes("UTF-8");
					httpExchange.sendResponseHeaders(404, out.length);
					OutputStream os = httpExchange.getResponseBody();
					os.write(out);
					os.close();
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
						System.out.println(map);
						if (!map.containsKey("idsUrl")) {
							 report(httpExchange, "BadRequestException",
							 "idsUrl not specified");
						}
						IdsClient idsClient = new IdsClient(new URL(map.get("idsUrl")));
						DataSelection data = new DataSelection();
						String list = map.get("investigationIds");
						if (list != null) {
							for (String one : list.split(",")) {
								data.addInvestigation(Long.parseLong(one));
							}
						}
						list = map.get("datasetIds");
						if (list != null) {
							for (String one : list.split(",")) {
								data.addDataset(Long.parseLong(one));
							}
						}
						list = map.get("datafileIds");
						if (list != null) {
							for (String one : list.split(",")) {
								data.addDatafile(Long.parseLong(one));
							}
						}

						try {
							idsClient.restore(map.get("sessionId"), data);
						} catch (NotImplementedException | BadRequestException | InsufficientPrivilegesException
								| InternalException | NotFoundException e) {
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							JsonGenerator gen = Json.createGenerator(baos);
							gen.writeStartObject().write("code", e.getClass().getSimpleName())
									.write("message", e.getMessage()).close();
							 out = baos.toString().getBytes("UTF-8");
							httpExchange.sendResponseHeaders(400, out.length);
							OutputStream os = httpExchange.getResponseBody();
							os.write(out);
							os.close();
						}
					}

					httpExchange.sendResponseHeaders(200, 0);
					httpExchange.getResponseBody().close();
				}
			}

		});

		httpServer.createContext("/login", new HttpHandler() {

			public void handle(HttpExchange httpExchange) throws IOException {
				if (!httpExchange.getRequestMethod().equals("POST")) {
					final byte[] out = "POST expected".getBytes("UTF-8");
					httpExchange.sendResponseHeaders(404, out.length);
					OutputStream os = httpExchange.getResponseBody();
					os.write(out);
					os.close();
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
						System.out.println(map);
						try (PrintWriter os = new PrintWriter(dot.resolve("servers").resolve(map.get("idsUrl"))
								.toFile())) {
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
		Files.createDirectories(dot.resolve("servers"));
		httpServer.start();

		System.out.println("HttpServer Test Start!");
	}
}
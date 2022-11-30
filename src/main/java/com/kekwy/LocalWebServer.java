package com.kekwy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class LocalWebServer {


	private String responseMessage = null;
	private final Object mutexResponse = new Object();

	private boolean responseReady = false;

	private int feedback;

	private final Object mutexFeedback = new Object();

	private boolean feedbackReady = false;

	public int send(String response) {
		synchronized (mutexResponse) {
			this.responseMessage = response;
			responseReady = true;
			mutexResponse.notify();
		}
		synchronized (mutexFeedback) {
			if (!feedbackReady) {
				try {
					mutexFeedback.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			feedbackReady = false;
			return feedback;
		}
	}

	public LocalWebServer(int port) {
		try {
			HttpServer httpserver = HttpServerProvider.provider().
					createHttpServer(new InetSocketAddress(port), 100);
			httpserver.createContext("/", this::handle);
			httpserver.createContext("/equal", this::handleEqual);
			httpserver.createContext("/inequal", this::handleInequal);
			httpserver.createContext("/favicon.ico", this::handleIcon);
			httpserver.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void handleIcon(HttpExchange exchange) {
		try {
			exchange.sendResponseHeaders(200, 0);
			OutputStream os = exchange.getResponseBody();
			os.write(Objects.requireNonNull(this.getClass().getResourceAsStream("/icon.jpg")).readAllBytes());
			os.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void handleEqual(HttpExchange exchange) throws IOException {
		helper(exchange, 1);
	}

	public void handleInequal(HttpExchange exchange) throws IOException {
		helper(exchange, 0);
	}

	private void helper(HttpExchange exchange, int feedback) throws IOException {
		boolean flag = true;
		synchronized (mutexResponse) {
			if (responseMessage == null) {
				flag = false;
			}
			// responseReady = false;
		}
		if (flag) {
			synchronized (mutexFeedback) {
				this.feedback = feedback;
				feedbackReady = true;
				mutexFeedback.notify();
			}
		}
		handle(exchange);
	}

	public void handlePost(HttpExchange exchange) throws IOException {
		System.out.println(new String(exchange.getRequestBody().readAllBytes()));
	}

	public void handle(HttpExchange exchange) throws IOException {
		String response;
		if (Objects.equals(exchange.getRequestMethod(), "POST")) {
			handlePost(exchange);
			return;
		}
		synchronized (mutexResponse) {
			if (!responseReady) {
				try {
					mutexResponse.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			response = TEMPLATE.formatted(GITHUB_CSS, DIFF2HTML_CSS, DIFF2HTML_JS, responseMessage);
		}
		exchange.sendResponseHeaders(200, 0);
		OutputStream os = exchange.getResponseBody();
		os.write(response.getBytes(StandardCharsets.UTF_8));
		os.close();
	}

	private static final String GITHUB_CSS =
			"https://cdn.jsdelivr.net/gh/1506085843/fillDiff@master/src/main/resources/css/github.min.css";
	private static final String DIFF2HTML_CSS =
			"https://cdn.jsdelivr.net/gh/1506085843/fillDiff@master/src/main/resources/css/diff2html.min.css";
	private static final String DIFF2HTML_JS =
			"https://cdn.jsdelivr.net/gh/1506085843/fillDiff@master/src/main/resources/js/diff2html-ui.min.js";

	public static final String TEMPLATE =
			"""
					<!DOCTYPE html>
					<html lang="en-us">
						<head>
							<meta charset="utf-8" />
							<link rel="stylesheet" href=%s />
							<link rel="stylesheet" type="text/css" href=%s />
							<script type="text/javascript" src=%s></script>
						</head>
						<script>
						
							const diffString = `%s`;
							
							document.addEventListener('DOMContentLoaded', function () {
								var targetElement = document.getElementById('myDiffElement');
								var configuration = {
									drawFileList: false,
							        fileListToggle: false,
									fileListStartVisible: false,
									fileContentToggle: false,
									matching: 'lines',
									outputFormat: 'side-by-side',
									synchronisedScroll: true,
									highlight: true,
									renderNothingWhenEmpty: true,
								};
								var diff2htmlUi = new Diff2HtmlUI(targetElement, diffString, configuration);
								diff2htmlUi.draw();
								diff2htmlUi.highlightCode();
							});
						</script>
						<body>
							<div id="myDiffElement"></div>
							<div class="buttons">
								请选择：
								<input value="确认等价" type="button" id="equalButton">
								<input value="确认不等价" type="button" id="inequalButton">
							</div>
						</body>
						
						<script>
							document.getElementById("equalButton").onclick = function() {
								document.location.href='/equal';
							}
							document.getElementById("inequalButton").onclick = function() {
								document.location.href='/inequal';
							}
						</script>
						
					</html>
					""";


}

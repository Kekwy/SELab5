package com.kekwy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalWebServer {

	private String responseMessage = null;
	private final Object mutexResponse = new Object();

	private boolean responseReady = false;

	private int feedback;

	private final Object mutexFeedback = new Object();

	private boolean feedbackReady = false;

	private final HttpServer httpserver;

	public LocalWebServer(int port) {
		try {
			httpserver = HttpServerProvider.provider().
					createHttpServer(new InetSocketAddress(port), 100);
			httpserver.createContext("/", this::handle);
			httpserver.createContext("/favicon.ico", this::handleIcon);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void start() {
		httpserver.start();
	}

	public void stop() {
		httpserver.stop(0);
	}

	public int send(String response) {
		synchronized (mutexResponse) {
			this.responseMessage = response;
			responseReady = true;
			mutexResponse.notifyAll();
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

	public void send(String response, boolean block) {
		if (block) {
			send(response);
		} else {
			synchronized (mutexResponse) {
				this.responseMessage = response;
				responseReady = true;
				mutexResponse.notify();
			}
		}
	}

	public void clearFeedback() {
		synchronized (mutexFeedback) {
			feedbackReady = false;
		}
	}

	/**
	 * 设置网页标签页的图标
	 */
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

	private void helper(int feedback) {
		boolean flag;
		synchronized (mutexResponse) {
			flag = responseReady;
			responseReady = false;
		}
		if (flag) {
			synchronized (mutexFeedback) {
				this.feedback = feedback;
				feedbackReady = true;
				mutexFeedback.notify();
			}
		}
	}

	private static final Pattern PATTERN = Pattern.compile("(\\w+)=(\\w+)");

	public boolean handlePost(HttpExchange exchange) throws IOException {
		Matcher matcher = PATTERN.matcher(new String(exchange.getRequestBody().readAllBytes()));
		if (!matcher.find()) {
			return false;
		}
		if (Objects.equals(matcher.group(2), "equal")) {
			helper(1);
		} else if (Objects.equals(matcher.group(2), "inequal")) {
			helper(0);
		} else {
			return false;
		}
		return true;
	}

	public void handle(HttpExchange exchange) throws IOException {
		boolean flag = true;
		if (Objects.equals(exchange.getRequestMethod(), "POST")) {
			flag = handlePost(exchange);
		}
		if (!flag) {
			exchange.sendResponseHeaders(400, 0);
			exchange.getResponseBody().write("Bad Request.".getBytes());
			exchange.getResponseBody().close();
			return;
		}
		String response;
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
							<div class="buttons">
								请选择：
								<input value="确认等价" type="button" id="equalButton">
								<input value="确认不等价" type="button" id="inequalButton">
							</div>
							<div id="myDiffElement"></div>
						</body>
						
						<script>
							document.getElementById("equalButton").onclick = function() {
					            helper("equal");
					        }
					        document.getElementById("inequalButton").onclick = function() {
					            helper("inequal");
					        }
					        function helper(v) {
					            var temp = document.createElement("form");
					            temp.action = "/";
					            temp.method = "post";
					            temp.style.display = "none";
					            var opt = document.createElement("textarea");
					            opt.name = "result";
					            opt.value = v;
					            temp.appendChild(opt);
					            document.body.appendChild(temp);
					            temp.submit();
					            return temp;
					        }
						</script>
						
					</html>
					""";
}

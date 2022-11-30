package com.kekwy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Main {
	/**
	 * -csv 指定待确认的判断结果文件所在的目录
	 * -dir 指定程序源码输入文件夹的根目录
	 */
	public static void main(String[] args) {
		boolean csv = false, dir = false;
		String csvDir = null, dirPath = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-csv" -> {
					if (csv) {
						throw new RuntimeException("重复指定文件路径");
					} else if (i + 1 >= args.length) {
						throw new RuntimeException("未指定待确认的CSV文件目录");
					} else {
						csvDir = args[i + 1];
						i++;
						csv = true;
					}
				}
				case "-dir" -> {
					if (dir) {
						throw new RuntimeException("重复指定文件路径");
					} else if (i + 1 >= args.length) {
						throw new RuntimeException("未指定程序源码根目录");
					} else {
						dirPath = args[i + 1];
						i++;
						dir = true;
					}
				}
				default -> throw new RuntimeException("未知参数：" + args[i]);
			}
		}
		if (!dir) {
			throw new RuntimeException("未指定源代码文件根目录");
		}
		if (!csv) {
			throw new RuntimeException("未指定CSV输出文件路径");
		}
		new CheckToolController(csvDir, dirPath).start();

		try {
			HttpServer httpserver = HttpServerProvider.provider().
					createHttpServer(new InetSocketAddress(8080), 100);
			httpserver.createContext("/", new HttpHandler(){

				@Override
				public void handle(HttpExchange exchange) throws IOException {
					// System.out.println(exchange.getProtocol());
					// exchange.sendResponseHeaders(200, 100);
					String response = "test message";
					exchange.sendResponseHeaders(200, 0);
					OutputStream os = exchange.getResponseBody();
					os.write(response.getBytes(StandardCharsets.UTF_8));
					os.close();

				}
			});
			httpserver.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

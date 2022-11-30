package com.kekwy;

import com.csvreader.CsvReader;
import com.kekwy.util.DiffTextGenerator;
import com.kekwy.util.DisjointSetUnion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class CheckToolController extends Thread {

	private static final int MODE_CHECK_EQUAL = 0;
	private static final int MODE_CHECK_INEQUAL = 1;

	private int mode = MODE_CHECK_EQUAL;

	private final String csvFileDir, pathPrefix;

	public CheckToolController(String csvFileDir, String pathPrefix) {

		this.csvFileDir = csvFileDir;
		this.pathPrefix = pathPrefix;

	}

	@Override
	public void run() {
		LocalWebServer server = new LocalWebServer(8080);
		DisjointSetUnion<String> dsUnion = new DisjointSetUnion<>();
		List<String> equalPairs = new ArrayList<>();
		List<String> inequalPairs = new ArrayList<>();
		server.start();
		while(true) {
			String fileName = "equal.csv";
			if (mode == MODE_CHECK_INEQUAL) {
				fileName = "inequal.csv";
			}
			String csvFilePath = csvFileDir + fileName;
			CsvReader reader;
			try {
				reader = new CsvReader(csvFilePath, ',', StandardCharsets.UTF_8);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e); // 未找到目标文件
			}
			try {
				reader.readRecord();
				while (reader.readRecord()) {
					// reader.
					String[] pair = reader.getValues();
					List<String> original = Files.readAllLines(new File(pathPrefix + pair[0]).toPath());
					List<String> revised = Files.readAllLines(new File(pathPrefix + pair[1]).toPath());
					// 由于等价性的传递性，一组经人工确认后建立了间接等价关系的两个程序，可以跳过人工确认
					if (Objects.equals(dsUnion.find(pair[0]), dsUnion.find(pair[1]))) {
						continue;
					}
					String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
					boolean isEqual = true;
					if (diffString != null) {
						isEqual = server.send(diffString) == 1;
					}
					String pairStr = pair[0] + "," + pair[1];
					if (isEqual) {
						dsUnion.union(pair[0], pair[1]);
						if (inequalPairs.contains(pairStr)) {
							throw new RuntimeException("人工确认结果中存在矛盾项: \n" + pairStr);
						}
						equalPairs.add(pairStr);
					} else {
						if (equalPairs.contains(pairStr)) {
							throw new RuntimeException("人工确认结果中存在矛盾项: \n" + pairStr);
						}
						inequalPairs.add(pairStr);
					}
					// checkMap.put(pair[0]+","+pair[1], isEqual);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			reader.close();
			if (mode == MODE_CHECK_INEQUAL) {
				break;
			} else {
				// TODO 确认是否需要人工对比不等价对
				Scanner scanner = new Scanner(System.in);
				if (Objects.equals(scanner.next(), "yes")) {
					mode = MODE_CHECK_INEQUAL;
				} else {
					break;
				}
			}
		}
		dsUnion.shutdown();
		server.stop();
		Scanner scanner = new Scanner(System.in);
		// toCSVFile(checkMap, Objects.equals(scanner.next(), "yes"));
	}

	private void toCSVFile(List<String> equalPairs, List<String> inequalPairs, boolean cover) {

	}

}

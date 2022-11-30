package com.kekwy;

import com.csvreader.CsvReader;
import com.kekwy.util.DatePrefix;
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
		if (!csvFileDir.endsWith("/")) {
			csvFileDir += '/';
		}
		this.csvFileDir = csvFileDir;
		if (!pathPrefix.endsWith("/")) {
			pathPrefix += '/';
		}
		this.pathPrefix = pathPrefix;
	}

	@Override
	public void run() {
		LocalWebServer server = new LocalWebServer(8080);
		DisjointSetUnion<String> dsUnion = new DisjointSetUnion<>();
		List<String> equalPairs = new ArrayList<>();
		List<String> inequalPairs = new ArrayList<>();
		server.start();
		while (true) {
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
					boolean isEqual = true;
					String pairRecord = pair[0] + "," + pair[1];
					// 由于等价性的传递性，一组经人工确认后建立了间接等价关系的两个程序，可以跳过人工确认
					if (!Objects.equals(dsUnion.find(pair[0]), dsUnion.find(pair[1]))) {
						String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
						if (diffString != null) {
							isEqual = server.send(diffString) == 1;
						}
					}
					if (isEqual) {
						dsUnion.union(pair[0], pair[1]);
						if (inequalPairs.contains(pairRecord)) {
							throw new RuntimeException("人工确认结果中存在矛盾项: \n" + pairRecord);
						}
						equalPairs.add(pairRecord);
					} else {
						if (equalPairs.contains(pairRecord)) {
							throw new RuntimeException("人工确认结果中存在矛盾项: \n" + pairRecord);
						}
						inequalPairs.add(pairRecord);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			reader.close();
			if (mode == MODE_CHECK_INEQUAL) {
				break;
			} else {
				// TODO 确认是否需要人工对比不等价对
				System.out.println("所有自动判断的等价对结果已经完成确认，是否要对不等价结果进行确认？[yes/no]");
				Scanner scanner = new Scanner(System.in);
				if (Objects.equals(scanner.next(), "yes")) {
					mode = MODE_CHECK_INEQUAL;
				} else {
					break;
				}
			}
		}
		dsUnion.shutdown();
		Thread thread = new Thread(server::stop);
		thread.start();
		while (thread.isAlive()) {
			server.send("Finish!", false);
		}
		System.out.println("确认已完成，是否在原有CSV文件上进行修改？[yes/no]");
		Scanner scanner = new Scanner(System.in);
		toCSVFile(equalPairs, inequalPairs, Objects.equals(scanner.next(), "yes"));
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void toCSVFile(List<String> equalPairs, List<String> inequalPairs, boolean cover) {
		File equalFile, inequalFile;
		if (cover) {
			equalFile = new File(csvFileDir + "equal.csv");
			inequalFile = new File(csvFileDir + "inequal.csv");
		} else {
			File folder = new File(csvFileDir + "after_check/");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			String prefix = DatePrefix.getDateString();
			equalFile = new File(csvFileDir + "after_check/" + "equal_" + prefix + ".csv");
			inequalFile = new File(csvFileDir + "after_check/" + "inequal_" + prefix + ".csv");
			try {
				equalFile.createNewFile();
				inequalFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		try {
			FileWriter fops = new FileWriter(equalFile);
			fops.write("file1,file2\n");
			for (String equalPair : equalPairs) {
				fops.write(equalPair + "\n");
			}
			fops.close();
			fops = new FileWriter(inequalFile, true);
			if (cover) {
				fops.write("file1,file2\n");
			}
			for (String inequalPair : inequalPairs) {
				fops.write(inequalPair + "\n");
			}
			fops.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

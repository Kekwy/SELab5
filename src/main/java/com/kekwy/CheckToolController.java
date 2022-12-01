package com.kekwy;

import com.csvreader.CsvReader;
import com.kekwy.util.DatePrefix;
import com.kekwy.util.DiffTextGenerator;
import com.kekwy.util.DisjointSetUnion;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

public class CheckToolController extends Thread {

	private static final int MODE_CHECK_EQUAL = 0;
	private static final int MODE_CHECK_INEQUAL = 1;

	private int mode = MODE_CHECK_EQUAL;

	private final String csvFileDir, pathPrefix;

	private final int SERVER_PORT;

	public CheckToolController(String csvFileDir, String pathPrefix) {
		if (!csvFileDir.endsWith("/")) {
			csvFileDir += '/';
		}
		this.csvFileDir = csvFileDir;
		if (!pathPrefix.endsWith("/")) {
			pathPrefix += '/';
		}
		this.pathPrefix = pathPrefix;

		Properties props = new Properties();
		try {
			props.load(new FileInputStream("./config.properties"));
			SERVER_PORT = Integer.parseInt((String) props.get("port"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void run() {
		LocalWebServer server = new LocalWebServer(SERVER_PORT);
		DisjointSetUnion<String> dsUnion = new DisjointSetUnion<>();
		List<String> equalPairs = new ArrayList<>();
		List<String> inequalPairs = new ArrayList<>();
		Map<String, List<String>> inequalMap = new HashMap<>();
		server.start();
		showPage();
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
					if (!inequalMap.containsKey(pair[0])) {
						inequalMap.put(pair[0], new ArrayList<>());
					}
					if (!inequalMap.containsKey(pair[1])) {
						inequalMap.put(pair[1], new ArrayList<>());
					}
					// 由于等价性的传递性，一组经人工确认后建立了间接等价关系的两个程序，可以跳过人工确认
					if (!Objects.equals(dsUnion.find(pair[0]), dsUnion.find(pair[1]))) {
						String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
						if (diffString != null) {
							isEqual = server.send(diffString) == 1;
						}
					}
					if (isEqual) {
						BiConsumer<String, String> helper = (String p1, String p2) -> {
							for (String s : inequalMap.get(p1)) {
								String root1 = dsUnion.find(s);
								String root2 = dsUnion.find(p2);
								if (Objects.equals(root1, root2)) {
									toCSVFile(equalPairs, inequalPairs, false);
									throw new RuntimeException("人工确认结果中存在矛盾项:\n"
											+ p2 + "与" + p1 + "等价;\n"
											+ p2 + "与" + s + "等价;\n"
											+ "而" + p1 + "与" + s + "不等价");
								}
							}
						};
						helper.accept(pair[0], pair[1]);
						helper.accept(pair[1], pair[0]);
						dsUnion.union(pair[0], pair[1]);
						equalPairs.add(pairRecord);
					} else {
						if (Objects.equals(dsUnion.find(pair[0]), dsUnion.find(pair[1]))) {
							String root = dsUnion.find(pair[0]);
							toCSVFile(equalPairs, inequalPairs, false);
							throw new RuntimeException("人工确认结果中存在矛盾项:\n"
									+ pair[0] + "与" + root + "等价;\n"
									+ pair[1] + "与" + root + "等价;\n"
									+ "而" + pair[0] + "与" + pair[1] + "不等价");
						}
						inequalMap.get(pair[0]).add(pair[1]);
						inequalMap.get(pair[1]).add(pair[0]);
						inequalPairs.add(pairRecord);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			reader.close();
			server.send("Finish!", false);
			if (mode == MODE_CHECK_INEQUAL) {
				break;
			} else {
				System.out.println("所有自动判断的等价对结果已经完成确认，是否要对不等价结果进行确认？[yes/no]");
				Scanner scanner = new Scanner(System.in);
				if (Objects.equals(scanner.next(), "yes")) {
					mode = MODE_CHECK_INEQUAL;
					server.clearFeedback();
					showPage();
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
			if (!cover) {
				fops = new FileWriter(inequalFile);
				fops.write("file1,file2\n");
			} else {
				if (mode == MODE_CHECK_INEQUAL) {
					fops = new FileWriter(inequalFile);
				} else {
					fops = new FileWriter(inequalFile, true);
				}
			}
			for (String inequalPair : inequalPairs) {
				fops.write(inequalPair + "\n");
			}
			fops.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("确认结果文件生成于: \n" + equalFile.getAbsolutePath() + "\n"
				+ inequalFile.getAbsolutePath());
	}

	private void showPage() {
		try {
			Desktop.getDesktop().browse(new URI("http://localhost:" + SERVER_PORT));
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}

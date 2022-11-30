package com.kekwy;

import com.csvreader.CsvReader;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.kekwy.util.DiffTextGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
			// List<String[]> pairList = new ArrayList<>();
			try {
				reader.readRecord();
				while (reader.readRecord()) {
					String[] pair = reader.getValues();

					List<String> original = Files.readAllLines(new File(pathPrefix + pair[0]).toPath());
					List<String> revised = Files.readAllLines(new File(pathPrefix + pair[1]).toPath());

					String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
					if (diffString != null) {
						server.send(diffString);
					}

				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			// pairList.remove(0);


			if (mode == MODE_CHECK_INEQUAL) {
				break;
			} else {
				// TODO 确认是否需要人工对比不等价对

			}
		}
	}
}

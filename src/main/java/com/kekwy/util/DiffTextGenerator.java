package com.kekwy.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffTextGenerator {

	private static List<String> generateUnifiedDiff(String originalFileName, String revisedFileName,
	                                                List<String> original, List<String> revised) {
		Patch<String> patch = DiffUtils.diff(original, revised);
		return UnifiedDiffUtils.generateUnifiedDiff(
				originalFileName, revisedFileName, original, patch, 0);
	}

	private static List<String> insertIntoOriginal(List<String> unifiedDiff, List<String> original) {
		Pattern pattern = Pattern.compile("@@ -(\\d+),(\\d+) [+](\\d+),(\\d+) @@");

		List<String> diffText = new ArrayList<>();
		diffText.add("@@diffStart");
		diffText.add(unifiedDiff.get(0));
		diffText.add(unifiedDiff.get(1));

		int ptr = 0;

		for (int i = 2; i < unifiedDiff.size(); i++) {
			Matcher matcher = pattern.matcher(unifiedDiff.get(i));
			if (matcher.find()) {
				int oriLineNumber = Integer.parseInt(matcher.group(1));
				int oriLineRange = Integer.parseInt(matcher.group(2));
				// int revLineNumber = Integer.parseInt(matcher.group(3));
				int revLineRange = Integer.parseInt(matcher.group(4));
				if (oriLineNumber == 1 && diffText.size() != 3) {
					throw new RuntimeException("unifiedDiff格式有误" + Arrays.toString(unifiedDiff.toArray()));
				}
				while (ptr + 1 < oriLineNumber) {
					diffText.add(" " + original.get(ptr));
					ptr++;
				}
				ptr += oriLineRange;
				for (int j = 0, t = i; j < oriLineRange + revLineRange; j++, t++) {
					diffText.add(unifiedDiff.get(t + 1));
				}
				i += oriLineRange + revLineRange;
			}
		}
		while (ptr < original.size()) {
			diffText.add(" " + original.get(ptr));
			ptr++;
		}
		return diffText;
	}

	public static String generate(String originalFileName, String revisedFileName,
	                              List<String> original, List<String> revised) {
		List<String> unifiedDiff = generateUnifiedDiff(
				originalFileName, revisedFileName, original, revised);
		if (unifiedDiff.isEmpty()) {
			return null;
		}
		List<String> diffText = insertIntoOriginal(unifiedDiff, original);
		return String.join("\n", diffText);
		// return TEMPLATE.formatted(GITHUB_CSS, DIFF2HTML_CSS, DIFF2HTML_JS, diffString);
	}


}

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
				if (oriLineNumber == 1) {
					if (diffText.size() != 2) {
						throw new RuntimeException("unifiedDiff格式有误" + Arrays.toString(unifiedDiff.toArray()));
					}
					diffText.add(0, "@@");
				}
				while(ptr + 1 < oriLineNumber) {
					diffText.add(" " + original.get(ptr));
					ptr++;
				}
				ptr += oriLineRange;
				for (int j = 0; j < oriLineRange + revLineRange; j++, i++) {
					diffText.add(unifiedDiff.get(i + 1));
				}
			}
		}
		return diffText;
	}

	public static String generate(String originalFileName, String revisedFileName,
	                              List<String> original, List<String> revised) {
		List<String> unifiedDiff = generateUnifiedDiff(
				originalFileName, revisedFileName, original, revised);
		List<String> diffText = insertIntoOriginal(unifiedDiff, original);
		return String.join("\n", diffText);
		// return TEMPLATE.formatted(GITHUB_CSS, DIFF2HTML_CSS, DIFF2HTML_JS, diffString);
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
						</body>
					</html>
					""";


}

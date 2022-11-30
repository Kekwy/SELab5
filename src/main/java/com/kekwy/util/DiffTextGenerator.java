package com.kekwy.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.List;

public class DiffTextGenerator {

	private static List<String> generateUnifiedDiff(String originalFileName, String revisedFileName,
	                                                List<String> original, List<String> revised) {
		Patch<String> patch = DiffUtils.diff(original, revised);
		return UnifiedDiffUtils.generateUnifiedDiff(
				originalFileName, revisedFileName, original, patch, 0);
	}

	private static void insertOriginal(List<String> unifiedDiff, List<String> original) {



		for (String s : unifiedDiff) {
			
		}
	}

	public static String generate(String originalFileName, String revisedFileName,
	                              List<String> original, List<String> revised) {
		List<String> unifiedDiff = generateUnifiedDiff(
				originalFileName, revisedFileName, original, revised);
		insertOriginal(unifiedDiff, original);
		String diffString = String.join("\n", unifiedDiff);
		return TEMPLATE.formatted(GITHUB_CSS, DIFF2HTML_CSS, DIFF2HTML_JS, diffString);
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

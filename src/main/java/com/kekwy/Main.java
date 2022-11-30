package com.kekwy;

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
	}
}

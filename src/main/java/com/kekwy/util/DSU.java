package com.kekwy.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DSU<T> {

	// private int ;

	private Map<T, T> parent = new HashMap<>();
	private Map<T, Integer> size = new HashMap<>();

	/**
	 * 压缩路径的查找
	 *
	 * @param p 需要查找的节点
	 * @return 节点 p 的根节点
	 */
	public T find(T p) {
		if (p != parent.get(p))
			parent.put(p, find(parent.get(p)));
		return parent.get(p);
	}

	public void union(T p, T q) {
		T rootP = find(p);
		T rootQ = find(q);
		if (rootP == rootQ) return;

		if (size.get(rootP) < size.get(rootQ)) {
			parent.put(rootP, rootQ);
			size.put(rootQ, size.get(rootQ) + size.get(rootP));
		} else {
			parent.put(rootQ, rootP);
			size.put(rootP, size.get(rootP) + size.get(rootQ));
		}
	}



}

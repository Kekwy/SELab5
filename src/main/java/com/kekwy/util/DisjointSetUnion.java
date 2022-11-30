package com.kekwy.util;

import java.util.HashMap;
import java.util.Map;

public class DisjointSetUnion<T> {

	boolean isShutdown = false;
	private final Map<T, T> parent = new HashMap<>();
	private final Map<T, Integer> size = new HashMap<>();

	/**
	 * 压缩路径的查找
	 *
	 * @param p 需要查找的节点
	 * @return 节点 p 的根节点
	 */
	public T find(T p) {
		// 如果元素p第一次出现，则将其在并查集中初始化为孤立节点
		if (!parent.containsKey(p)) {
			parent.put(p, p);
			size.put(p, 1);
		}
		if (p != parent.get(p))
			parent.put(p, find(parent.get(p)));
		return parent.get(p);
	}

	public void union(T p, T q) {
		if (isShutdown) {
			throw new RuntimeException("The dsu has been shutdown.");
		}
		T rootP = find(p);
		T rootQ = find(q);
		if (rootP == rootQ)
			return;
		if (size.get(rootP) < size.get(rootQ)) {
			parent.put(rootP, rootQ);
			size.put(rootQ, size.get(rootQ) + size.get(rootP));
		} else {
			parent.put(rootQ, rootP);
			size.put(rootP, size.get(rootP) + size.get(rootQ));
		}
	}

	public void shutdown() {
		isShutdown = true;
	}
}

package org.rapidoid.util;

/*
 * #%L
 * rapidoid-u
 * %%
 * Copyright (C) 2014 - 2015 Nikolche Mihajlovski
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rapidoid.lambda.Mapper;
import org.rapidoid.lambda.Predicate;

public class U {

	protected static final Random RND = new Random();
	private static ScheduledThreadPoolExecutor EXECUTOR;
	private static long measureStart;

	private static Pattern JRE_CLASS_PATTERN = Pattern
			.compile("^(java|javax|javafx|com\\.sun|sun|com\\.oracle|oracle|jdk|org\\.omg|org\\.w3c).*");

	// regex taken from
	// http://stackoverflow.com/questions/2559759/how-do-i-convert-camelcase-into-human-readable-names-in-java
	private static Pattern CAMEL_SPLITTER_PATTERN = Pattern
			.compile("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])");

	private static Pattern PLURAL1 = Pattern.compile(".*(s|x|z|ch|sh)$");
	private static Pattern PLURAL1U = Pattern.compile(".*(S|X|Z|CH|SH)$");
	private static Pattern PLURAL2 = Pattern.compile(".*[bcdfghjklmnpqrstvwxz]o$");
	private static Pattern PLURAL2U = Pattern.compile(".*[BCDFGHJKLMNPQRSTVWXZ]O$");
	private static Pattern PLURAL3 = Pattern.compile(".*[bcdfghjklmnpqrstvwxz]y$");
	private static Pattern PLURAL3U = Pattern.compile(".*[BCDFGHJKLMNPQRSTVWXZ]Y$");

	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = U.map(boolean.class, Boolean.class, byte.class,
			Byte.class, char.class, Character.class, double.class, Double.class, float.class, Float.class, int.class,
			Integer.class, long.class, Long.class, short.class, Short.class, void.class, Void.class);

	private U() {
	}

	public static String text(Object obj) {
		if (obj == null) {
			return "null";
		} else if (obj instanceof byte[]) {
			return Arrays.toString((byte[]) obj);
		} else if (obj instanceof short[]) {
			return Arrays.toString((short[]) obj);
		} else if (obj instanceof int[]) {
			return Arrays.toString((int[]) obj);
		} else if (obj instanceof long[]) {
			return Arrays.toString((long[]) obj);
		} else if (obj instanceof float[]) {
			return Arrays.toString((float[]) obj);
		} else if (obj instanceof double[]) {
			return Arrays.toString((double[]) obj);
		} else if (obj instanceof boolean[]) {
			return Arrays.toString((boolean[]) obj);
		} else if (obj instanceof char[]) {
			return Arrays.toString((char[]) obj);
		} else if (obj instanceof Object[]) {
			return text((Object[]) obj);
		} else {
			return String.valueOf(obj);
		}
	}

	public static String text(Object[] objs) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		for (int i = 0; i < objs.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(text(objs[i]));
		}

		sb.append("]");

		return sb.toString();
	}

	public static RuntimeException rte(String message, Object... args) {
		return new RuntimeException(readable(message, args));
	}

	public static RuntimeException rte(Throwable cause) {
		return new RuntimeException(cause);
	}

	public static RuntimeException rte(String message) {
		return new RuntimeException(message);
	}

	public static RuntimeException notExpected() {
		return rte("This operation is not expected to be called!");
	}

	public static IllegalArgumentException illegalArg(String message) {
		return new IllegalArgumentException(message);
	}

	public static <T> T newInstance(Class<T> clazz) {
		try {
			Constructor<T> constr = clazz.getDeclaredConstructor();
			boolean accessible = constr.isAccessible();
			constr.setAccessible(true);

			T obj = constr.newInstance();

			constr.setAccessible(accessible);
			return obj;
		} catch (Exception e) {
			throw rte(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> clazz, Object... args) {
		for (Constructor<?> constr : clazz.getConstructors()) {
			Class<?>[] paramTypes = constr.getParameterTypes();
			if (areAssignable(paramTypes, args)) {
				try {
					boolean accessible = constr.isAccessible();
					constr.setAccessible(true);

					T obj = (T) constr.newInstance(args);

					constr.setAccessible(accessible);
					return obj;
				} catch (Exception e) {
					throw rte(e);
				}
			}
		}

		throw rte("Cannot find appropriate constructor for %s with args %s!", clazz, text(args));
	}

	public static <T> T customizable(Class<T> clazz, Object... args) {
		String customClassName = "Customized" + clazz.getSimpleName();

		Class<T> customClass = U.getClassIfExists(customClassName);

		if (customClass == null) {
			customClass = U.getClassIfExists("custom." + customClassName);
		}

		if (customClass != null && !clazz.isAssignableFrom(customClass)) {
			customClass = null;
		}

		return newInstance(U.or(customClass, clazz), args);
	}

	public static boolean areAssignable(Class<?>[] types, Object[] values) {
		if (types.length != values.length) {
			return false;
		}

		for (int i = 0; i < values.length; i++) {
			Object val = values[i];
			if (val != null && !instanceOf(val, types[i])) {
				return false;
			}
		}

		return true;
	}

	public static <T> T or(T value, T fallback) {
		return value != null ? value : fallback;
	}

	public static String format(String s, Object... args) {
		return String.format(s, args);
	}

	public static String readable(String format, Object... args) {

		for (int i = 0; i < args.length; i++) {
			args[i] = text(args[i]);
		}

		return String.format(format, args);
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new ThreadDeath();
		}
	}

	public static boolean waitInterruption(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.interrupted();
			return false;
		}
	}

	public static void waitFor(Object obj) {
		try {
			synchronized (obj) {
				obj.wait();
			}
		} catch (InterruptedException e) {
			// do nothing
		}
	}

	public static void joinThread(Thread thread) {
		try {
			thread.join();
		} catch (InterruptedException e) {
			// do nothing
		}
	}

	public static String text(Collection<Object> coll) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		boolean first = true;

		for (Object obj : coll) {
			if (!first) {
				sb.append(", ");
			}

			sb.append(text(obj));
			first = false;
		}

		sb.append("]");
		return sb.toString();
	}

	public static String text(Iterator<?> it) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		boolean first = true;

		while (it.hasNext()) {
			if (first) {
				sb.append(", ");
				first = false;
			}

			sb.append(text(it.next()));
		}

		sb.append("]");

		return sb.toString();
	}

	public static String textln(Object[] objs) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		for (int i = 0; i < objs.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("\n  ");
			sb.append(text(objs[i]));
		}

		sb.append("\n]");

		return sb.toString();
	}

	public static String replaceText(String s, String[][] repls) {
		for (String[] repl : repls) {
			s = s.replaceAll(Pattern.quote(repl[0]), repl[1]);
		}
		return s;
	}

	public static <T> String join(String sep, T... items) {
		return render(items, "%s", sep);
	}

	public static String join(String sep, Iterable<?> items) {
		return render(items, "%s", sep);
	}

	public static String join(String sep, char[][] items) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < items.length; i++) {
			if (i > 0) {
				sb.append(sep);
			}
			sb.append(items[i]);
		}

		return sb.toString();
	}

	public static String render(Object[] items, String itemFormat, String sep) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < items.length; i++) {
			if (i > 0) {
				sb.append(sep);
			}
			sb.append(readable(itemFormat, items[i]));
		}

		return sb.toString();
	}

	public static String render(Iterable<?> items, String itemFormat, String sep) {
		StringBuilder sb = new StringBuilder();

		int i = 0;
		Iterator<?> it = items.iterator();
		while (it.hasNext()) {
			Object item = it.next();
			if (i > 0) {
				sb.append(sep);
			}

			sb.append(readable(itemFormat, item));
			i++;
		}

		return sb.toString();
	}

	public static <T> T[] array(T... items) {
		return items;
	}

	public static <T> Iterator<T> arrayIterator(T[] arr) {
		return Arrays.asList(arr).iterator();
	}

	public static <T> Set<T> set(Collection<? extends T> coll) {
		Set<T> set = new LinkedHashSet<T>();
		set.addAll(coll);
		return set;
	}

	public static <T> Set<T> set(T... values) {
		Set<T> set = new LinkedHashSet<T>();

		for (T val : values) {
			set.add(val);
		}

		return set;
	}

	public static <T> List<T> list(Collection<? extends T> coll) {
		List<T> list = new ArrayList<T>();
		list.addAll(coll);
		return list;
	}

	public static <T> List<T> list(T... values) {
		List<T> list = new ArrayList<T>();

		for (T item : values) {
			list.add(item);
		}

		return list;
	}

	public static <K, V> Map<K, V> map(Map<? extends K, ? extends V> src) {
		Map<K, V> map = map();
		map.putAll(src);
		return map;
	}

	public static <K, V> Map<K, V> map() {
		return new HashMap<K, V>();
	}

	public static <K, V> Map<K, V> map(K key, V value) {
		Map<K, V> map = map();
		map.put(key, value);
		return map;
	}

	public static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2) {
		Map<K, V> map = map(key1, value1);
		map.put(key2, value2);
		return map;
	}

	public static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2, K key3, V value3) {
		Map<K, V> map = map(key1, value1, key2, value2);
		map.put(key3, value3);
		return map;
	}

	public static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4) {
		Map<K, V> map = map(key1, value1, key2, value2, key3, value3);
		map.put(key4, value4);
		return map;
	}

	public static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4, K key5,
			V value5) {
		Map<K, V> map = map(key1, value1, key2, value2, key3, value3, key4, value4);
		map.put(key5, value5);
		return map;
	}

	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> map(Object... keysAndValues) {
		must(keysAndValues.length % 2 == 0, "Incorrect number of arguments (expected key-value pairs)!");

		Map<K, V> map = map();

		for (int i = 0; i < keysAndValues.length / 2; i++) {
			map.put((K) keysAndValues[i * 2], (V) keysAndValues[i * 2 + 1]);
		}

		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(Map<? extends K, ? extends V> src) {
		ConcurrentMap<K, V> map = concurrentMap();
		map.putAll(src);
		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap() {
		return new ConcurrentHashMap<K, V>();
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(K key, V value) {
		ConcurrentMap<K, V> map = concurrentMap();
		map.put(key, value);
		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(K key1, V value1, K key2, V value2) {
		ConcurrentMap<K, V> map = concurrentMap(key1, value1);
		map.put(key2, value2);
		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(K key1, V value1, K key2, V value2, K key3, V value3) {
		ConcurrentMap<K, V> map = concurrentMap(key1, value1, key2, value2);
		map.put(key3, value3);
		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(K key1, V value1, K key2, V value2, K key3, V value3,
			K key4, V value4) {
		ConcurrentMap<K, V> map = concurrentMap(key1, value1, key2, value2, key3, value3);
		map.put(key4, value4);
		return map;
	}

	public static <K, V> ConcurrentMap<K, V> concurrentMap(K key1, V value1, K key2, V value2, K key3, V value3,
			K key4, V value4, K key5, V value5) {
		ConcurrentMap<K, V> map = concurrentMap(key1, value1, key2, value2, key3, value3, key4, value4);
		map.put(key5, value5);
		return map;
	}

	@SuppressWarnings("unchecked")
	public static <K, V> ConcurrentMap<K, V> concurrentMap(Object... keysAndValues) {
		must(keysAndValues.length % 2 == 0, "Incorrect number of arguments (expected key-value pairs)!");

		ConcurrentMap<K, V> map = concurrentMap();

		for (int i = 0; i < keysAndValues.length / 2; i++) {
			map.put((K) keysAndValues[i * 2], (V) keysAndValues[i * 2 + 1]);
		}

		return map;
	}

	public static <K, V> Map<K, V> autoExpandingMap(final Class<V> clazz) {
		return autoExpandingMap(new Mapper<K, V>() {
			@Override
			public V map(K src) throws Exception {
				return newInstance(clazz);
			}
		});
	}

	@SuppressWarnings("serial")
	public static <K, V> Map<K, V> autoExpandingMap(final Mapper<K, V> valueFactory) {
		return new ConcurrentHashMap<K, V>() {
			@SuppressWarnings("unchecked")
			@Override
			public synchronized V get(Object key) {
				V val = super.get(key);

				if (val == null) {
					try {
						val = valueFactory.map((K) key);
					} catch (Exception e) {
						throw rte(e);
					}

					put((K) key, val);
				}

				return val;
			}
		};
	}

	public static <T> Queue<T> queue(int maxSize) {
		return maxSize > 0 ? new ArrayBlockingQueue<T>(maxSize) : new ConcurrentLinkedQueue<T>();
	}

	public static <FROM, TO> Mapper<FROM, TO> mapper(final Map<FROM, TO> map) {
		return new Mapper<FROM, TO>() {
			@Override
			public TO map(FROM key) throws Exception {
				return map.get(key);
			}
		};
	}

	public static URL resource(String filename) {
		return classLoader().getResource(filename);
	}

	public static ClassLoader classLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	public static File file(String filename) {
		File file = new File(filename);

		if (!file.exists()) {
			URL res = resource(filename);
			if (res != null) {
				return new File(res.getFile());
			}
		}

		return file;
	}

	public static long time() {
		return System.currentTimeMillis();
	}

	public static boolean xor(boolean a, boolean b) {
		return a && !b || b && !a;
	}

	public static boolean eq(Object a, Object b) {
		if (a == b) {
			return true;
		}

		if (a == null || b == null) {
			return false;
		}

		return a.equals(b);
	}

	public static void failIf(boolean failureCondition, String msg) {
		if (failureCondition) {
			throw rte(msg);
		}
	}

	public static void failIf(boolean failureCondition, String msg, Object... args) {
		if (failureCondition) {
			throw rte(msg, args);
		}
	}

	public static byte[] loadBytes(InputStream input) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		byte[] buffer = new byte[4 * 1024];

		try {
			int readN = 0;
			while ((readN = input.read(buffer)) != -1) {
				output.write(buffer, 0, readN);
			}
		} catch (IOException e) {
			throw rte(e);
		}

		return output.toByteArray();
	}

	public static byte[] loadBytes(String filename) {
		InputStream input = classLoader().getResourceAsStream(filename);

		if (input == null) {
			File file = new File(filename);

			if (file.exists()) {
				try {
					input = new FileInputStream(filename);
				} catch (FileNotFoundException e) {
					throw rte(e);
				}
			}
		}

		return input != null ? loadBytes(input) : null;
	}

	public static byte[] classBytes(String fullClassName) {
		return loadBytes(fullClassName.replace('.', '/') + ".class");
	}

	public static String load(String filename) {
		byte[] bytes = loadBytes(filename);
		return bytes != null ? new String(bytes) : null;
	}

	public static List<String> loadLines(String filename) {
		InputStream input = classLoader().getResourceAsStream(filename);
		BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		List<String> lines = list();

		try {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {
			throw rte(e);
		}

		return lines;
	}

	public static List<String> loadLines(String filename, final boolean filterEmpty, final String commentPrefix) {

		List<String> lines = loadLines(filename);

		List<String> lines2 = list();

		for (String line : lines) {
			String s = line.trim();
			if ((!filterEmpty || !s.isEmpty()) && (commentPrefix == null || !s.startsWith(commentPrefix))) {
				lines2.add(s);
			}
		}

		return lines2;
	}

	public static void save(String filename, String content) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(filename);
			out.write(content.getBytes());
			close(out, false);
		} catch (Exception e) {
			close(out, true);
			throw rte(e);
		}
	}

	public static void close(OutputStream out, boolean quiet) {
		try {
			if (out != null) {
				out.close();
			}
		} catch (IOException e) {
			if (!quiet) {
				throw rte(e);
			}
		}
	}

	public static void close(InputStream in, boolean quiet) {
		try {
			in.close();
		} catch (IOException e) {
			if (!quiet) {
				throw rte(e);
			}
		}
	}

	public static void delete(String filename) {
		new File(filename).delete();
	}

	public static <T> T[] expand(T[] arr, int factor) {
		int len = arr.length;

		arr = Arrays.copyOf(arr, len * factor);

		return arr;
	}

	public static <T> T[] expand(T[] arr, T item) {
		int len = arr.length;

		arr = Arrays.copyOf(arr, len + 1);
		arr[len] = item;

		return arr;
	}

	public static <T> T[] subarray(T[] arr, int from, int to) {
		int start = from >= 0 ? from : arr.length + from;
		int end = to >= 0 ? to : arr.length + to;

		if (start < 0) {
			start = 0;
		}

		if (end > arr.length - 1) {
			end = arr.length - 1;
		}

		must(start <= end, "Invalid range: expected form <= to!");

		int size = end - start + 1;

		T[] part = Arrays.copyOf(arr, size);

		System.arraycopy(arr, start, part, 0, size);

		return part;
	}

	public static Object[] flat(Object... arr) {
		List<Object> flat = list();
		flatInsertInto(flat, 0, arr);
		return flat.toArray();
	}

	@SuppressWarnings("unchecked")
	public static <T> int flatInsertInto(List<T> dest, int index, Object item) {
		if (index > dest.size()) {
			index = dest.size();
		}
		int inserted = 0;

		if (item instanceof Object[]) {
			Object[] arr = (Object[]) item;
			for (Object obj : arr) {
				inserted += flatInsertInto(dest, index + inserted, obj);
			}
		} else if (item instanceof Collection<?>) {
			Collection<?> coll = (Collection<?>) item;
			for (Object obj : coll) {
				inserted += flatInsertInto(dest, index + inserted, obj);
			}
		} else if (item != null) {
			if (index >= dest.size()) {
				dest.add((T) item);
			} else {
				dest.add(index + inserted, (T) item);
			}
			inserted++;
		}

		return inserted;
	}

	public static boolean must(boolean expectedCondition, String message) {
		if (!expectedCondition) {
			throw rte(message);
		}
		return true;
	}

	public static String copyNtimes(String s, int n) {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < n; i++) {
			sb.append(s);
		}

		return sb.toString();
	}

	public static RuntimeException rte(String message, Throwable cause, Object... args) {
		return new RuntimeException(readable(message, args), cause);
	}

	public static RuntimeException rte(String message, Throwable cause) {
		return new RuntimeException(message, cause);
	}

	public static boolean must(boolean expectedCondition) {
		if (!expectedCondition) {
			throw rte("Expectation failed!");
		}
		return true;
	}

	public static boolean must(boolean expectedCondition, String message, long arg) {
		if (!expectedCondition) {
			throw rte(message, arg);
		}
		return true;
	}

	public static boolean must(boolean expectedCondition, String message, Object arg) {
		if (!expectedCondition) {
			throw rte(message, text(arg));
		}
		return true;
	}

	public static boolean must(boolean expectedCondition, String message, Object arg1, Object arg2) {
		if (!expectedCondition) {
			throw rte(message, text(arg1), text(arg2));
		}
		return true;
	}

	public static boolean must(boolean expectedCondition, String message, Object arg1, Object arg2, Object arg3) {
		if (!expectedCondition) {
			throw rte(message, text(arg1), text(arg2), text(arg3));
		}
		return true;
	}

	public static void secure(boolean condition, String msg, Object arg) {
		if (!condition) {
			throw new SecurityException(readable(msg, arg));
		}
	}

	public static void secure(boolean condition, String msg, Object arg1, Object arg2) {
		if (!condition) {
			throw new SecurityException(readable(msg, arg1, arg2));
		}
	}

	public static void bounds(int value, int min, int max) {
		must(value >= min && value <= max, "%s is not in the range [%s, %s]!", value, min, max);
	}

	public static void notNullAll(Object... items) {
		for (int i = 0; i < items.length; i++) {
			if (items[i] == null) {
				throw rte("The item[%s] must NOT be null!", i);
			}
		}
	}

	public static <T> T notNull(T value, String desc, Object... descArgs) {
		if (value == null) {
			throw rte("%s must NOT be null!", readable(desc, descArgs));
		}

		return value;
	}

	public static RuntimeException notReady() {
		return rte("Not yet implemented!");
	}

	public static RuntimeException notSupported() {
		return rte("This operation is not supported by this implementation!");
	}

	public static void show(Object... values) {
		String text = values.length == 1 ? text(values[0]) : text(values);
		print(">" + text + "<");
	}

	public static synchronized void schedule(Runnable task, long delay) {
		if (EXECUTOR == null) {
			EXECUTOR = new ScheduledThreadPoolExecutor(3);
		}

		EXECUTOR.schedule(task, delay, TimeUnit.MILLISECONDS);
	}

	public static void startMeasure() {
		measureStart = time();
	}

	public static void endMeasure() {
		long delta = time() - measureStart;
		show(delta + " ms");
	}

	public static void endMeasure(String info) {
		long delta = time() - measureStart;
		show(info + ": " + delta + " ms");
	}

	public static void print(Object value) {
		System.out.println(value);
	}

	public static void printAll(Collection<?> collection) {
		for (Object item : collection) {
			print(item);
		}
	}

	public static boolean isEmpty(String value) {
		return value == null || value.isEmpty();
	}

	public static boolean isEmpty(Object[] arr) {
		return arr == null || arr.length == 0;
	}

	public static boolean isEmpty(Collection<?> coll) {
		return coll == null || coll.isEmpty();
	}

	public static boolean isEmpty(Map<?, ?> map) {
		return map == null || map.isEmpty();
	}

	public static boolean isEmpty(Object value) {
		if (value == null) {
			return true;
		} else if (value instanceof String) {
			return isEmpty((String) value);
		} else if (value instanceof Object[]) {
			return isEmpty((Object[]) value);
		} else if (value instanceof Collection<?>) {
			return isEmpty((Collection<?>) value);
		} else if (value instanceof Map<?, ?>) {
			return isEmpty((Map<?, ?>) value);
		}
		return false;
	}

	public static String capitalized(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
	}

	public static String uncapitalized(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toLowerCase() + s.substring(1);
	}

	public static String mid(String s, int beginIndex, int endIndex) {
		if (endIndex < 0) {
			endIndex = s.length() + endIndex;
		}
		return s.substring(beginIndex, endIndex);
	}

	public static String urlDecode(String value) {
		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw rte(e);
		}
	}

	public static String mul(String s, int n) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < n; i++) {
			sb.append(s);
		}

		return sb.toString();
	}

	public static int num(String s) {
		return Integer.parseInt(s);
	}

	public static String bytesToString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < bytes.length; i++) {
			sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
		}

		return sb.toString();
	}

	private static MessageDigest digest(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw rte("Cannot find algorithm: " + algorithm);
		}
	}

	public static String md5(byte[] bytes) {
		MessageDigest md5 = digest("MD5");
		md5.update(bytes);
		return bytesToString(md5.digest());
	}

	public static String md5(String data) {
		return md5(data.getBytes());
	}

	public static char rndChar() {
		return (char) (65 + rnd(26));
	}

	public static String rndStr(int length) {
		return rndStr(length, length);
	}

	public static String rndStr(int minLength, int maxLength) {
		int len = minLength + rnd(maxLength - minLength + 1);
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < len; i++) {
			sb.append(rndChar());
		}

		return sb.toString();
	}

	public static int rnd(int n) {
		return RND.nextInt(n);
	}

	public static int rndExcept(int n, int except) {
		if (n > 1 || except != 0) {
			while (true) {
				int num = RND.nextInt(n);
				if (num != except) {
					return num;
				}
			}
		} else {
			throw new RuntimeException("Cannot produce such number!");
		}
	}

	public static <T> T rnd(T[] arr) {
		return arr[rnd(arr.length)];
	}

	public static int rnd() {
		return RND.nextInt();
	}

	public static long rndL() {
		return RND.nextLong();
	}

	@SuppressWarnings("resource")
	public static MappedByteBuffer mmap(String filename, MapMode mode, long position, long size) {
		try {
			File file = new File(filename);
			FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
			return fc.map(mode, position, size);
		} catch (Exception e) {
			throw rte(e);
		}
	}

	public static MappedByteBuffer mmap(String filename, MapMode mode) {
		File file = new File(filename);
		must(file.exists());
		return mmap(filename, mode, 0, file.length());
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getClassIfExists(String className) {
		try {
			return (Class<T>) Class.forName(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public static String fillIn(String template, String placeholder, String value) {
		return template.replace("{{" + placeholder + "}}", value);
	}

	public static ByteBuffer expand(ByteBuffer buf, int newSize) {
		ByteBuffer buf2 = ByteBuffer.allocate(newSize);

		ByteBuffer buff = buf.duplicate();
		buff.rewind();
		buff.limit(buff.capacity());

		buf2.put(buff);

		return buf2;
	}

	public static ByteBuffer expand(ByteBuffer buf) {
		int cap = buf.capacity();

		if (cap <= 1000) {
			cap *= 10;
		} else if (cap <= 10000) {
			cap *= 5;
		} else {
			cap *= 2;
		}

		return expand(buf, cap);
	}

	public static String buf2str(ByteBuffer buf) {
		ByteBuffer buf2 = buf.duplicate();

		buf2.rewind();
		buf2.limit(buf2.capacity());

		byte[] bytes = new byte[buf2.capacity()];
		buf2.get(bytes);

		return new String(bytes);
	}

	public static ByteBuffer buf(String s) {
		byte[] bytes = s.getBytes();

		ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
		buf.put(bytes);
		buf.rewind();

		return buf;
	}

	public static void benchmark(String name, int count, Runnable runnable) {
		long start = time();

		for (int i = 0; i < count; i++) {
			runnable.run();
		}

		benchmarkComplete(name, count, start);
	}

	public static void benchmarkComplete(String name, int count, long startTime) {
		long end = time();
		long ms = end - startTime;

		if (ms == 0) {
			ms = 1;
		}

		double avg = ((double) count / (double) ms);

		String avgs = avg > 1 ? Math.round(avg) + "K" : Math.round(avg * 1000) + "";

		String data = format("%s: %s in %s ms (%s/sec)", name, count, ms, avgs);

		print(data + " | " + getCpuMemStats());
	}

	public static void benchmarkMT(int threadsN, final String name, final int count, final CountDownLatch outsideLatch,
			final Runnable runnable) {

		eq(count % threadsN, 0);
		final int countPerThread = count / threadsN;

		final CountDownLatch latch = outsideLatch != null ? outsideLatch : new CountDownLatch(threadsN);

		long time = time();

		for (int i = 1; i <= threadsN; i++) {
			new Thread() {
				public void run() {
					benchmark(name, countPerThread, runnable);
					if (outsideLatch == null) {
						latch.countDown();
					}
				};
			}.start();
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw rte(e);
		}

		benchmarkComplete("avg(" + name + ")", threadsN * countPerThread, time);
	}

	public static void benchmarkMT(int threadsN, final String name, final int count, final Runnable runnable) {
		benchmarkMT(threadsN, name, count, null, runnable);
	}

	public static String getCpuMemStats() {
		Runtime rt = Runtime.getRuntime();
		long totalMem = rt.totalMemory();
		long maxMem = rt.maxMemory();
		long freeMem = rt.freeMemory();
		long usedMem = totalMem - freeMem;
		int megs = 1024 * 1024;

		String msg = "MEM [total=%s MB, used=%s MB, max=%s MB]";
		return format(msg, totalMem / megs, usedMem / megs, maxMem / megs);
	}

	public static String replace(String s, String regex, Mapper<String[], String> replacer) {
		StringBuffer output = new StringBuffer();
		Pattern p = Pattern.compile(regex);
		Matcher matcher = p.matcher(s);

		while (matcher.find()) {
			int len = matcher.groupCount() + 1;
			String[] gr = new String[len];

			for (int i = 0; i < gr.length; i++) {
				gr[i] = matcher.group(i);
			}

			matcher.appendReplacement(output, eval(replacer, gr));
		}

		matcher.appendTail(output);
		return output.toString();
	}

	public static <T> boolean eval(Predicate<T> predicate, T target) {
		try {
			return predicate.eval(target);
		} catch (Exception e) {
			throw rte("Cannot evaluate predicate %s on target: %s", e, predicate, target);
		}
	}

	public static <FROM, TO> TO eval(Mapper<FROM, TO> mapper, FROM src) {
		try {
			return mapper.map(src);
		} catch (Exception e) {
			throw rte("Cannot evaluate mapper %s on target: %s", e, mapper, src);
		}
	}

	public static boolean isJREClass(String canonicalClassName) {
		return JRE_CLASS_PATTERN.matcher(canonicalClassName).matches();
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getWrapperClass(Class<T> c) {
		must(c.isPrimitive());
		return c.isPrimitive() ? (Class<T>) PRIMITIVE_WRAPPERS.get(c) : c;
	}

	public static boolean instanceOf(Object obj, Class<?>... classes) {
		return obj != null ? isAssignableTo(obj.getClass(), classes) : false;
	}

	public static boolean isAssignableTo(Class<?> clazz, Class<?>... targetClasses) {
		for (Class<?> cls : targetClasses) {
			if (cls.isPrimitive()) {
				if (cls.isAssignableFrom(clazz)) {
					return true;
				}
				cls = getWrapperClass(cls);
			}
			if (cls.isAssignableFrom(clazz)) {
				return true;
			}
		}

		return false;
	}

	public static boolean contains(Object arrOrColl, Object value) {
		if (arrOrColl instanceof Object[]) {
			Object[] arr = (Object[]) arrOrColl;
			return Arr.indexOf(arr, value) >= 0;
		} else if (arrOrColl instanceof Collection<?>) {
			Collection<?> coll = (Collection<?>) arrOrColl;
			return coll.contains(value);
		} else {
			throw illegalArg("Expected array or collection!");
		}
	}

	@SuppressWarnings("unchecked")
	public static Object include(Object arrOrColl, Object item) {
		if (arrOrColl instanceof Object[]) {
			Object[] arr = (Object[]) arrOrColl;
			return Arr.indexOf(arr, item) < 0 ? expand(arr, item) : arr;
		} else if (arrOrColl instanceof Collection<?>) {
			Collection<Object> coll = (Collection<Object>) arrOrColl;
			if (!coll.contains(item)) {
				coll.add(item);
			}
			return coll;
		} else {
			throw illegalArg("Expected array or collection!");
		}
	}

	@SuppressWarnings("unchecked")
	public static Object exclude(Object arrOrColl, Object item) {
		if (arrOrColl instanceof Object[]) {
			Object[] arr = (Object[]) arrOrColl;
			int ind = Arr.indexOf(arr, item);
			return ind >= 0 ? Arr.deleteAt(arr, ind) : arr;
		} else if (arrOrColl instanceof Collection<?>) {
			Collection<Object> coll = (Collection<Object>) arrOrColl;
			if (coll.contains(item)) {
				coll.remove(item);
			}
			return coll;
		} else {
			throw illegalArg("Expected array or collection!");
		}
	}

	public static String camelSplit(String s) {
		return CAMEL_SPLITTER_PATTERN.matcher(s).replaceAll(" ");
	}

	public static String camelPhrase(String s) {
		return capitalized(camelSplit(s).toLowerCase());
	}

	public static int limit(int min, int value, int max) {
		return Math.min(Math.max(min, value), max);
	}

	public static String plural(String s) {
		if (isEmpty(s)) {
			return s;
		}

		if (PLURAL1.matcher(s).matches()) {
			return s + "es";
		} else if (PLURAL2.matcher(s).matches()) {
			return s + "es";
		} else if (PLURAL3.matcher(s).matches()) {
			return mid(s, 0, -1) + "ies";
		} else if (PLURAL1U.matcher(s).matches()) {
			return s + "ES";
		} else if (PLURAL2U.matcher(s).matches()) {
			return s + "ES";
		} else if (PLURAL3U.matcher(s).matches()) {
			return mid(s, 0, -1) + "IES";
		} else {
			boolean upper = Character.isUpperCase(s.charAt(s.length() - 1));
			return s + (upper ? "S" : "s");
		}
	}

	public static Throwable rootCause(Throwable e) {
		while (e.getCause() != null) {
			e = e.getCause();
		}
		return e;
	}

	public static <T> T single(Collection<T> coll) {
		must(coll.size() == 1, "Expected exactly 1 items, but found: %s!", coll.size());
		return coll.iterator().next();
	}

	public static <T> T singleOrNone(Collection<T> coll) {
		must(coll.size() <= 1, "Expected 0 or 1 items, but found: %s!", coll.size());
		return !coll.isEmpty() ? coll.iterator().next() : null;
	}

}

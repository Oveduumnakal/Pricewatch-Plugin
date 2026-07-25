/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Freezes the shape of every persisted class as a checked-in snapshot so no build
 * can silently change or break the JSON written to the RS profile config.
 *
 * <p>The test reflects over the persisted roots ({@link #ROOTS}), walking every
 * non-static, non-transient field the way Gson serializes it, and compares the
 * result to {@code persisted-schema.snapshot}. Any added, removed, renamed, or
 * retyped field — or any changed enum constant — fails the build. When a schema
 * change is intentional the snapshot is regenerated (delete the resource and
 * re-run, or copy {@code build/persisted-schema.actual}); that regenerated diff
 * is what the PR-checks workflow requires a {@code Schema-Change:} note to explain.
 */
public class PersistedSchemaSnapshotTest
{
	private static final String SNAPSHOT_NAME = "persisted-schema.snapshot";

	/** Package every persisted model lives in — the reachability walk stays inside it. */
	private static final String MODEL_PACKAGE = "com.oveduumnakal.pricewatch";

	/** The types actually serialized to config; everything else is reached from these. */
	private static final Class<?>[] ROOTS = {
			PricewatchPlugin.PersistedItem.class,
			PricewatchPlugin.CachedPrice.class,
	};

	@Test
	public void persistedSchemaMatchesSnapshot() throws IOException
	{
		String actual = buildSnapshot();

		String expected = readSnapshot();
		if (expected == null)
		{
			Path bootstrap = Paths.get("src", "test", "resources", SNAPSHOT_NAME);
			Files.createDirectories(bootstrap.getParent());
			Files.write(bootstrap, actual.getBytes(StandardCharsets.UTF_8));
			fail(SNAPSHOT_NAME + " did not exist — wrote " + bootstrap + ". Review it and re-run.");
		}

		expected = normalizeEol(expected);

		if (!expected.equals(actual))
		{
			Path dump = Paths.get("build", "persisted-schema.actual");
			Files.createDirectories(dump.getParent());
			Files.write(dump, actual.getBytes(StandardCharsets.UTF_8));
		}

		assertEquals("Persisted schema changed. If intentional, regenerate "
				+ SNAPSHOT_NAME + " (see " + Paths.get("build", "persisted-schema.actual")
				+ ") and add a 'Schema-Change:' note to the PR.", expected, actual);
	}

	/** Reflects the reachable persisted types into a stable, name-sorted text form. */
	private static String buildSnapshot()
	{
		TreeMap<String, String> blocks = new TreeMap<>();
		Set<Class<?>> seen = new LinkedHashSet<>();
		Deque<Class<?>> queue = new ArrayDeque<>(Arrays.asList(ROOTS));

		while (!queue.isEmpty())
		{
			Class<?> type = queue.poll();
			if (!seen.add(type))
				continue;

			if (type.isEnum())
			{
				blocks.put(type.getSimpleName(), renderEnum(type));
				continue;
			}

			blocks.put(type.getSimpleName(), renderClass(type, queue));
		}

		return String.join("\n", blocks.values()) + "\n";
	}

	/** One class block: fields sorted by name, since JSON is keyed by name not order. */
	private static String renderClass(Class<?> type, Deque<Class<?>> queue)
	{
		List<String> fields = new ArrayList<>();

		for (Field field : type.getDeclaredFields())
		{
			int mods = field.getModifiers();
			if (Modifier.isStatic(mods) || Modifier.isTransient(mods))
				continue;

			fields.add("  " + field.getName() + ": " + normalize(field.getGenericType()));
			enqueueModelTypes(field.getGenericType(), queue);
		}

		fields.sort(String::compareTo);

		return "class " + type.getSimpleName() + " {\n"
				+ String.join("\n", fields) + "\n}";
	}

	/**
	 * One enum block: {@code name()} constants in declaration order. Gson persists
	 * enums by their declared name (not the overridden {@code toString()} label), so
	 * renaming or removing a constant is what breaks stored values.
	 */
	private static String renderEnum(Class<?> type)
	{
		String constants = Arrays.stream(type.getEnumConstants())
				.map(c -> ((Enum<?>) c).name())
				.collect(Collectors.joining(", "));

		return "enum " + type.getSimpleName() + " { " + constants + " }";
	}

	/** Enqueues any project-owned classes referenced by a field type (raw and generic args). */
	private static void enqueueModelTypes(Type type, Deque<Class<?>> queue)
	{
		if (type instanceof Class)
		{
			Class<?> clazz = (Class<?>) type;
			if (clazz.getName().startsWith(MODEL_PACKAGE))
				queue.add(clazz);

			return;
		}

		if (type instanceof ParameterizedType)
		{
			ParameterizedType parameterized = (ParameterizedType) type;
			enqueueModelTypes(parameterized.getRawType(), queue);
			for (Type arg : parameterized.getActualTypeArguments())
				enqueueModelTypes(arg, queue);
		}
	}

	/** Strips package and enclosing-class qualifiers so the snapshot reads in simple names. */
	private static String normalize(Type type)
	{
		return type.getTypeName()
				.replaceAll("(?:[a-zA-Z_$][a-zA-Z0-9_$]*\\.)+", "")
				.replaceAll("[A-Za-z0-9_]+\\$", "");
	}

	/**
	 * Collapses CRLF to LF so a snapshot checked out with Windows line endings
	 * (git {@code core.autocrlf=true}) still matches the LF-joined {@link #buildSnapshot()}
	 * output. The schema comparison is about field shape, not line terminators.
	 */
	private static String normalizeEol(String text)
	{
		return text.replace("\r\n", "\n");
	}

	private static String readSnapshot() throws IOException
	{
		try (InputStream in = PersistedSchemaSnapshotTest.class.getClassLoader().getResourceAsStream(SNAPSHOT_NAME))
		{
			if (in == null)
				return null;

			return new String(readAll(in), StandardCharsets.UTF_8);
		}
	}

	private static byte[] readAll(InputStream in) throws IOException
	{
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = in.read(buffer)) != -1)
			out.write(buffer, 0, read);

		return out.toByteArray();
	}
}

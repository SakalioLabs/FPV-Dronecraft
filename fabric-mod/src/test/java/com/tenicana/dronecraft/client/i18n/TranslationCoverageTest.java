package com.tenicana.dronecraft.client.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class TranslationCoverageTest {
	private static final Path LANG_DIR = Path.of("src/main/resources/assets/fpvdrone/lang");
	private static final Pattern DIRECT_TRANSLATION_KEY = Pattern.compile(
			"\"((?:item|entity|creativeTab|key|key\\.categories|message|hud|button|screen)\\.fpvdrone(?:\\.[A-Za-z0-9_]+)*)\""
	);

	@Test
	void englishAndChineseLangFilesExposeTheSameKeys() throws IOException {
		Set<String> english = langKeys("en_us.json");
		Set<String> chinese = langKeys("zh_cn.json");

		Set<String> missingInChinese = difference(english, chinese);
		Set<String> missingInEnglish = difference(chinese, english);

		assertTrue(missingInChinese.isEmpty(), "zh_cn.json missing keys: " + missingInChinese);
		assertTrue(missingInEnglish.isEmpty(), "en_us.json missing keys: " + missingInEnglish);
	}

	@Test
	void directFpvDroneTranslationKeysUsedBySourceAreDefined() throws IOException {
		Set<String> knownKeys = langKeys("en_us.json");
		Set<String> referencedKeys = referencedSourceKeys();
		Set<String> missing = difference(referencedKeys, knownKeys);

		assertTrue(missing.isEmpty(), "Language files missing referenced keys: " + missing);
	}

	private static Set<String> langKeys(String fileName) throws IOException {
		String json = Files.readString(LANG_DIR.resolve(fileName), StandardCharsets.UTF_8);
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		return new TreeSet<>(object.keySet());
	}

	private static Set<String> referencedSourceKeys() throws IOException {
		Set<String> keys = new TreeSet<>();
		for (Path root : javaSourceRoots()) {
			if (!Files.exists(root)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
					Matcher matcher = DIRECT_TRANSLATION_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
					while (matcher.find()) {
						keys.add(matcher.group(1));
					}
				}
			}
		}
		return keys;
	}

	private static Set<Path> javaSourceRoots() {
		return Set.of(
				Path.of("src/main/java"),
				Path.of("src/client/java"),
				Path.of("src/gametest/java")
		);
	}

	private static Set<String> difference(Set<String> left, Set<String> right) {
		Set<String> result = new TreeSet<>(left);
		result.removeAll(right);
		return result;
	}
}

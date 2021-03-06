package com.espirit.moddev.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArchiveUtilTest {

	@Rule
	public TemporaryFolder _temp = new TemporaryFolder();

	@Test
	public void decompressJarEntry() throws IOException {
		// decompress
		final Path archivePath = new File(getClass().getResource("/test_archive.jar").getFile()).toPath();
		final Path targetDir = _temp.getRoot().toPath();
		final Path jarFile = targetDir.resolve("jarFile.jar");
		Files.copy(archivePath, jarFile);
		final Path targetFile = targetDir.resolve("jarEntry.txt");
		ArchiveUtil.decompressJarEntry(jarFile, "sub/dir/file.txt", targetFile);
		// verify
		assertTrue("file should have been decompressed", targetFile.toFile().exists());
		final List<String> lines = Files.readAllLines(targetFile);
		assertEquals("lines mismatch", 1, lines.size());
		assertEquals("content mismatch", "sub/dir/file.txt", lines.get(0));
		// verify that the jar stream is closed (by deleting the file)
		assertTrue("jar file should have been deleted", jarFile.toFile().delete());
	}

	@Test
	public void decompressTarGz() throws IOException {
		// decompress
		final Path archivePath = new File(getClass().getResource("/test_archive.tar.gz").getFile()).toPath();
		final Path targetDir = _temp.getRoot().toPath();
		ArchiveUtil.decompressTarGz(archivePath, targetDir);
		// verify
		try (final ArchiveInputStream inputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archivePath.toFile())))) {
			ArchiveEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				final File file = targetDir.resolve(entry.getName()).toFile();
				assertTrue("file should have been decompressed", file.exists());
			}
		}
	}

}
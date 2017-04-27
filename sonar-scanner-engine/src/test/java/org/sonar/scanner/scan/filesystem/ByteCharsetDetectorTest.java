/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.scan.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.ByteOrderMark;
import org.junit.Before;
import org.junit.Test;

public class ByteCharsetDetectorTest {
  private CharsetValidation validation;
  private ByteCharsetDetector charsets;

  @Before
  public void setUp() {
    validation = mock(CharsetValidation.class);
    charsets = new ByteCharsetDetector(validation, null);
  }

  @Test
  public void detectBOM() throws URISyntaxException, IOException {
    byte[] b = ByteOrderMark.UTF_16BE.getBytes();
    assertThat(charsets.detectBOM(b)).isEqualTo(ByteOrderMark.UTF_16BE);

    assertThat(charsets.detectBOM(readFile("UTF-8"))).isEqualTo(ByteOrderMark.UTF_8);
    assertThat(charsets.detectBOM(readFile("UTF-16BE"))).isEqualTo(ByteOrderMark.UTF_16LE);
    assertThat(charsets.detectBOM(readFile("UTF-16LE"))).isEqualTo(ByteOrderMark.UTF_16BE);
    assertThat(charsets.detectBOM(readFile("UTF-32BE"))).isEqualTo(ByteOrderMark.UTF_32LE);
    assertThat(charsets.detectBOM(readFile("UTF-32LE"))).isEqualTo(ByteOrderMark.UTF_32BE);
  }

  private byte[] readFile(String fileName) throws URISyntaxException, IOException {
    Path path = Paths.get(this.getClass().getClassLoader().getResource("org/sonar/scanner/scan/filesystem/" + fileName + ".txt").toURI());
    return Files.readAllBytes(path);
  }

  @Test
  public void invalidBOM() {
    byte[] b1 = {(byte) 0xFF, (byte) 0xFF};
    assertThat(charsets.detectBOM(b1)).isNull();

    // not enough bytes
    byte[] b2 = {(byte) 0xFE};
    assertThat(charsets.detectBOM(b2)).isNull();
  }
}

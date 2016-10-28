/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.transpiler.integration;

import java.io.IOException;

/**
 * Tests that the help flag dumps help information.
 */
public class HelpFlagTest extends IntegrationTestCase {
  public void testHelpFlag() throws IOException, InterruptedException {
    String[] args = new String[] {"-help"};
    TranspileResult transpileResult = transpile(args);
    assertEquals(0, transpileResult.getExitCode());
    assertOutputContainsSnippet(transpileResult.getProblems(), "<source files .java|.srcjar>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-bootclasspath <path>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-classpath (-cp) <path>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-d <file>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-encoding <encoding>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-h (-help)");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-source <release>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-sourcepath <file>");
    assertOutputContainsSnippet(transpileResult.getProblems(), "-nativesourcezip <file>");
  }
}

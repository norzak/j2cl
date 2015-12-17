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
package com.google.j2cl.transpiler.integration.jsinteroptests;

import jsinterop.annotations.JsConstructor;
import jsinterop.annotations.JsType;

/**
 * A test class that exports a constructor.
 */
@JsType(namespace = "woo")  // Remove when package-info files are supported.
public class MyClassExportsConstructor {
  private int a;

  @JsConstructor
  public MyClassExportsConstructor(int a) {
    this.a = a;
  }

  public MyClassExportsConstructor() {
    this(1);
  }

  public int foo() {
    return a * 2;
  }
}

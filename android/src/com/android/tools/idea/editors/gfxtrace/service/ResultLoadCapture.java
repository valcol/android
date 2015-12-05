/*
 * Copyright (C) 2015 The Android Open Source Project
 *
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.rpclib.schema.*;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;

import java.io.IOException;

final class ResultLoadCapture implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private CapturePath myValue;

  // Constructs a default-initialized {@link ResultLoadCapture}.
  public ResultLoadCapture() {}


  public CapturePath getValue() {
    return myValue;
  }

  public ResultLoadCapture setValue(CapturePath v) {
    myValue = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service","resultLoadCapture","","");

  static {
    ENTITY.setFields(new Field[]{
      new Field("value", new Pointer(new Struct(CapturePath.Klass.INSTANCE.entity()))),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new ResultLoadCapture(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResultLoadCapture o = (ResultLoadCapture)obj;
      e.object(o.myValue);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResultLoadCapture o = (ResultLoadCapture)obj;
      o.myValue = (CapturePath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}

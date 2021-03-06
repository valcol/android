/*
 * Copyright (C) 2016 The Android Open Source Project
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
 */
package com.android.tools.profilers;

import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import io.grpc.stub.StreamObserver;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

final public class StudioProfilersTest {
  private static final String FAKE_VERSION = "3141592";

  @Rule public TestGrpcChannel myGrpcChannel =
    new TestGrpcChannel<>("StudioProfilerTestChannel", new ProfilerServiceGrpc.ProfilerServiceImplBase() {
      @Override
      public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
        responseObserver.onNext(Profiler.VersionResponse.newBuilder().setVersion(FAKE_VERSION).build());
        responseObserver.onCompleted();
      }
    });

  @Test
  public void testVersion() throws Exception {
    Profiler.VersionResponse response =
      myGrpcChannel.getClient().getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(FAKE_VERSION, response.getVersion());
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = myGrpcChannel.getProfilers();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }
}

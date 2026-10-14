/*
 * Copyright (c) Vinkius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vinkius.connect.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.error.ConnectionException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class FuturesTest {

  @Test
  void joinReturnsCompletedValue() {
    assertThat(Futures.join(CompletableFuture.completedFuture("v"))).isEqualTo("v");
  }

  @Test
  void joinRethrowsRuntimeFailuresAsIs() {
    assertThatThrownBy(
            () ->
                Futures.join(
                    CompletableFuture.<String>failedFuture(new IllegalStateException("boom"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void joinWrapsCheckedFailuresInConnectionException() {
    assertThatThrownBy(
            () -> Futures.join(CompletableFuture.<String>failedFuture(new IOException("io"))))
        .isInstanceOf(ConnectionException.class)
        .hasRootCauseInstanceOf(IOException.class);
  }

  @Test
  void joinRethrowsErrorsAsIs() {
    assertThatThrownBy(
            () -> Futures.join(CompletableFuture.<String>failedFuture(new StackOverflowError())))
        .isInstanceOf(StackOverflowError.class);
  }

  @Test
  void joinRestoresInterruptFlagAndThrowsConnectionException() {
    CompletableFuture<String> pending = new CompletableFuture<>();
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> Futures.join(pending))
          .isInstanceOf(ConnectionException.class)
          .hasMessage("Operation interrupted");
      assertThat(Thread.interrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void unwrapStripsNestedCompletionAndExecutionWrappers() {
    CompletionException wrapped =
        new CompletionException(new ExecutionException(new IllegalStateException("root")));
    assertThat(Futures.unwrap(wrapped))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("root");

    IllegalStateException plain = new IllegalStateException("plain");
    assertThat(Futures.unwrap(plain)).isSameAs(plain);
  }
}

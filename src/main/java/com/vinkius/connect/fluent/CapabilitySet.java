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
package com.vinkius.connect.fluent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A set of capabilities aggregated for one user (or one connector).
 *
 * <p>Kept intentionally small to avoid surprising {@code List} overrides: find with {@link
 * #findCapability}, filter with {@link #forConnector}. Iteration order matches the underlying
 * listing.
 */
public final class CapabilitySet implements Iterable<Capability> {

  private final List<Capability> items;

  /** An empty set. */
  public CapabilitySet() {
    this.items = new ArrayList<>();
  }

  private CapabilitySet(List<Capability> items) {
    this.items = items;
  }

  /** Build a set from a collection (snapshot copy). */
  public static CapabilitySet fromCapabilities(Collection<Capability> capabilities) {
    return new CapabilitySet(new ArrayList<>(capabilities));
  }

  /** Capabilities belonging to a specific connector slug. */
  public CapabilitySet forConnector(String slug) {
    List<Capability> matched = new ArrayList<>();
    for (Capability capability : items) {
      if (capability.connector().equals(slug)) {
        matched.add(capability);
      }
    }
    return new CapabilitySet(matched);
  }

  /** Find a capability by display name or raw name. */
  public @Nullable Capability findCapability(String name) {
    for (Capability capability : items) {
      if (capability.name().equals(name) || capability.rawName().equals(name)) {
        return capability;
      }
    }
    return null;
  }

  /** Number of capabilities in the set. */
  public int size() {
    return items.size();
  }

  /** Whether the set has no capabilities. */
  public boolean isEmpty() {
    return items.isEmpty();
  }

  /** The capability at {@code index}. */
  public Capability get(int index) {
    return items.get(index);
  }

  /** Immutable list view of the set. */
  public List<Capability> asList() {
    return List.copyOf(items);
  }

  /** Sequential stream over the set. */
  public Stream<Capability> stream() {
    return items.stream();
  }

  @Override
  public Iterator<Capability> iterator() {
    return asList().iterator();
  }

  @Override
  public String toString() {
    return "CapabilitySet" + items;
  }
}

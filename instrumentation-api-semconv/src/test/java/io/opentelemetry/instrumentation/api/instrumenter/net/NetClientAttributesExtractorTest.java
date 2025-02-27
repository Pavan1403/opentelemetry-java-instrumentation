/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetClientAttributesExtractorTest {

  static class TestNetClientAttributesGetter
      implements NetClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String transport(Map<String, String> request, Map<String, String> response) {
      return response.get("transport");
    }

    @Override
    public String peerName(Map<String, String> request) {
      return request.get("peerName");
    }

    @Override
    public Integer peerPort(Map<String, String> request) {
      String peerPort = request.get("peerPort");
      return peerPort == null ? null : Integer.valueOf(peerPort);
    }

    @Override
    public String sockFamily(Map<String, String> request, Map<String, String> response) {
      return response.get("sockFamily");
    }

    @Override
    public String sockPeerAddr(Map<String, String> request, Map<String, String> response) {
      return response.get("sockPeerAddr");
    }

    @Override
    public String sockPeerName(Map<String, String> request, Map<String, String> response) {
      return response.get("sockPeerName");
    }

    @Override
    public Integer sockPeerPort(Map<String, String> request, Map<String, String> response) {
      String sockPeerPort = response.get("sockPeerPort");
      return sockPeerPort == null ? null : Integer.valueOf(sockPeerPort);
    }
  }

  private final AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
      NetClientAttributesExtractor.create(new TestNetClientAttributesGetter());

  @Test
  void normal() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("transport", IP_TCP);
    map.put("peerName", "opentelemetry.io");
    map.put("peerPort", "42");
    map.put("sockFamily", "inet6");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerName", "proxy.opentelemetry.io");
    map.put("sockPeerPort", "123");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"),
            entry(SemanticAttributes.NET_PEER_PORT, 42L));

    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, IP_TCP),
            entry(NetAttributes.NET_SOCK_FAMILY, "inet6"),
            entry(NetAttributes.NET_SOCK_PEER_ADDR, "1:2:3:4::"),
            entry(NetAttributes.NET_SOCK_PEER_NAME, "proxy.opentelemetry.io"),
            entry(NetAttributes.NET_SOCK_PEER_PORT, 123L));
  }

  @Test
  void empty() {
    // given
    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, emptyMap());

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, emptyMap(), emptyMap(), null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  @DisplayName("does not set any net.sock.* attributes when net.peer.name = net.sock.peer.addr")
  void doesNotSetDuplicates1() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("transport", IP_TCP);
    map.put("peerName", "1:2:3:4::");
    map.put("peerPort", "42");
    map.put("sockFamily", "inet6");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerName", "proxy.opentelemetry.io");
    map.put("sockPeerPort", "123");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "1:2:3:4::"),
            entry(SemanticAttributes.NET_PEER_PORT, 42L));

    assertThat(endAttributes.build()).containsOnly(entry(SemanticAttributes.NET_TRANSPORT, IP_TCP));
  }

  @Test
  @DisplayName(
      "does not set net.sock.* attributes when they duplicate related net.peer.* attributes")
  void doesNotSetDuplicates2() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("transport", IP_TCP);
    map.put("peerName", "opentelemetry.io");
    map.put("peerPort", "42");
    map.put("sockFamily", "inet6");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerName", "opentelemetry.io");
    map.put("sockPeerPort", "42");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"),
            entry(SemanticAttributes.NET_PEER_PORT, 42L));

    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, IP_TCP),
            entry(NetAttributes.NET_SOCK_FAMILY, "inet6"),
            entry(NetAttributes.NET_SOCK_PEER_ADDR, "1:2:3:4::"));
  }

  @Test
  void doesNotSetNegativePortValues() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("peerName", "opentelemetry.io");
    map.put("peerPort", "-12");
    map.put("sockPeerAddr", "1:2:3:4::");
    map.put("sockPeerPort", "-42");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"));

    assertThat(endAttributes.build())
        .containsOnly(entry(NetAttributes.NET_SOCK_PEER_ADDR, "1:2:3:4::"));
  }

  @Test
  void doesNotSetSockFamilyInet() {
    // given
    Map<String, String> map = new HashMap<>();
    map.put("peerName", "opentelemetry.io");
    map.put("sockPeerAddr", "1.2.3.4");
    map.put("sockFamily", NetAttributes.SOCK_FAMILY_INET);

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, map);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, map, map, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.NET_PEER_NAME, "opentelemetry.io"));

    assertThat(endAttributes.build())
        .containsOnly(entry(NetAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4"));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/** Factory for {@link Resource} retrieving Container ID information. */
public final class ContainerResource {

  private static final Logger logger = Logger.getLogger(ContainerResource.class.getName());
  private static final String UNIQUE_HOST_NAME_FILE_NAME = "/proc/self/cgroup";
  private static final Resource INSTANCE = buildSingleton(UNIQUE_HOST_NAME_FILE_NAME);

  @IgnoreJRERequirement
  private static Resource buildSingleton(String uniqueHostNameFileName) {
    // can't initialize this statically without running afoul of animalSniffer on paths
    return buildResource(Paths.get(uniqueHostNameFileName));
  }

  // package private for testing
  static Resource buildResource(Path path) {
    return extractContainerId(path)
        .map(
            containerId ->
                Resource.create(Attributes.of(ResourceAttributes.CONTAINER_ID, containerId)))
        .orElseGet(Resource::empty);
  }

  /** Returns resource with container information. */
  public static Resource get() {
    return INSTANCE;
  }

  /**
   * Each line of cgroup file looks like "14:name=systemd:/docker/.../... A hex string is expected
   * inside the last section separated by '/' Each segment of the '/' can contain metadata separated
   * by either '.' (at beginning) or '-' (at end)
   *
   * @return containerId
   */
  @IgnoreJRERequirement
  private static Optional<String> extractContainerId(Path cgroupFilePath) {
    if (!Files.exists(cgroupFilePath) || !Files.isReadable(cgroupFilePath)) {
      return Optional.empty();
    }
    try (Stream<String> lines = Files.lines(cgroupFilePath)) {
      return lines
          .filter(line -> !line.isEmpty())
          .map(ContainerResource::getIdFromLine)
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to read file", e);
    }
    return Optional.empty();
  }

  private static Optional<String> getIdFromLine(String line) {
    // This cgroup output line should have the container id in it
    int lastSlashIdx = line.lastIndexOf('/');
    if (lastSlashIdx < 0) {
      return Optional.empty();
    }

    String containerId;

    String lastSection = line.substring(lastSlashIdx + 1);
    int colonIdx = lastSection.lastIndexOf(':');

    if (colonIdx != -1) {
      // since containerd v1.5.0+, containerId is divided by the last colon when the cgroupDriver is
      // systemd:
      // https://github.com/containerd/containerd/blob/release/1.5/pkg/cri/server/helpers_linux.go#L64
      containerId = lastSection.substring(colonIdx + 1);
    } else {
      int startIdx = lastSection.lastIndexOf('-');
      int endIdx = lastSection.lastIndexOf('.');

      startIdx = startIdx == -1 ? 0 : startIdx + 1;
      if (endIdx == -1) {
        endIdx = lastSection.length();
      }
      if (startIdx > endIdx) {
        return Optional.empty();
      }

      containerId = lastSection.substring(startIdx, endIdx);
    }

    if (OtelEncodingUtils.isValidBase16String(containerId) && !containerId.isEmpty()) {
      return Optional.of(containerId);
    } else {
      return Optional.empty();
    }
  }

  private ContainerResource() {}
}

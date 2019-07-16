package datadog.opentracing;

import lombok.Data;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class ContainerInfo {
  private static final Path CGROUP_DEFAULT_PROCFILE = Paths.get("/proc/self/cgroup");
  private static final String UUID_REGEX =
      "[0-9a-f]{8}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{12}";
  private static final String CONTAINER_REGEX = "[0-9a-f]{64}";
  private static final Pattern LINE_PATTERN = Pattern.compile("(\\d+):([^:]*):(.+)$");
  private static final Pattern POD_PATTERN = Pattern.compile("pod(" + UUID_REGEX + ")(?:.slice)?$");
  private static final Pattern CONTAINER_PATTERN =
      Pattern.compile("(" + UUID_REGEX + "|" + CONTAINER_REGEX + ")(?:.scope)?$");

  public String containerId;
  public String podId;
  public List<CGroupInfo> cGroups;

  @Data
  public static class CGroupInfo {
    public int id;
    public String path;
    public List<String> controllers;
    public String containerId;
    public String podId;
  }

  public static boolean isRunningInContainer() {
    return Files.isReadable(CGROUP_DEFAULT_PROCFILE);
  }

  public static ContainerInfo fromDefaultProcFile() throws IOException, ParseException {
    final String content = new String(Files.readAllBytes(CGROUP_DEFAULT_PROCFILE));
    return parse(content);
  }

  public static ContainerInfo parse(final String cgroupsContent) throws ParseException {
    final ContainerInfo containerInfo = new ContainerInfo();

    final String[] lines = cgroupsContent.split("\n");
    final List<CGroupInfo> parsedCGroups = new ArrayList<>();
    for (final String line : lines) {
      final CGroupInfo cGroupInfo = parseLine(line);

      parsedCGroups.add(cGroupInfo);

      if (cGroupInfo.getPodId() != null) {
        containerInfo.setPodId(cGroupInfo.getPodId());
      }

      if (cGroupInfo.getContainerId() != null) {
        containerInfo.setContainerId(cGroupInfo.getContainerId());
      }
    }

    containerInfo.setCGroups(parsedCGroups);

    return containerInfo;
  }

  static CGroupInfo parseLine(final String line) throws ParseException {
    final Matcher matcher = LINE_PATTERN.matcher(line);

    if (!matcher.matches()) {
      throw new ParseException("Unable to match cgroup", 0);
    }

    final CGroupInfo cGroupInfo = new CGroupInfo();
    cGroupInfo.setId(Integer.parseInt(matcher.group(1)));
    cGroupInfo.setControllers(Arrays.asList(matcher.group(2).split(",")));

    final String path = matcher.group(3);
    final String[] pathParts = path.split("/");

    cGroupInfo.setPath(path);

    if (pathParts.length >= 1) {
      final Matcher containerIdMatcher = CONTAINER_PATTERN.matcher(pathParts[0]);
      final String containerId = containerIdMatcher.matches() ? containerIdMatcher.group(1) : null;
      cGroupInfo.setContainerId(containerId);
    }

    if (pathParts.length >= 2) {
      final Matcher podIdMatcher = POD_PATTERN.matcher(pathParts[1]);
      final String podId = podIdMatcher.matches() ? podIdMatcher.group(1) : null;
      cGroupInfo.setPodId(podId);
    }

    return cGroupInfo;
  }
}

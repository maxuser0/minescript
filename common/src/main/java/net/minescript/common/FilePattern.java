// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FilePattern {
  private static final Logger LOGGER = LogManager.getLogger();

  // Filename or a glob pattern.
  private final String pattern;

  private FilePattern(String pattern) {
    this.pattern = pattern;
  }

  public static FilePattern of(String pattern) {
    return new FilePattern(pattern);
  }

  public static FilePattern of(Path path) {
    return new FilePattern(path.toString());
  }

  public FilePattern and(String pattern) {
    if (isAbsolutePath(pattern)) {
      return FilePattern.of(pattern);
    } else {
      return FilePattern.of(combinePaths(this.pattern, pattern));
    }
  }

  public FilePattern and(FilePattern path) {
    if (path.isAbsolute()) {
      return path;
    } else {
      return FilePattern.of(combinePaths(this.pattern, path.toString()));
    }
  }

  public FilePattern and(Path path) {
    if (path.isAbsolute()) {
      return FilePattern.of(path);
    } else {
      return FilePattern.of(combinePaths(this.pattern, path.toString()));
    }
  }

  @Override
  public String toString() {
    return pattern;
  }

  /**
   * Joins path segments using forward slashes, creating a valid glob string.
   *
   * <p>Safe for Windows, Linux, and Mac.
   *
   * <p>Note that {@code Paths.of("path/with/glob/*")} throws {@code InvalidPathException} on
   * Windows because "*" is an invalid path character in the Windows file system. Use {@code
   * combinePaths()} in those cases instead.
   */
  private static String combinePaths(String path1, String path2) {
    // Handle null or empty inputs gracefully.
    boolean empty1 = (path1 == null || path1.isEmpty());
    boolean empty2 = (path2 == null || path2.isEmpty());
    if (empty1 && empty2) return "";
    if (empty1) return path2 == null ? "" : path2.replace("\\", "/");
    if (empty2) return path1 == null ? "" : path1.replace("\\", "/");

    // Normalize separators to forward slashes for glob compatibility.
    String a = path1.replace("\\", "/");
    String b = path2.replace("\\", "/");

    // Remove trailing slash from a, if present.
    if (a.endsWith("/")) {
      a = a.substring(0, a.length() - 1);
    }

    // Remove leading slash from b, if present.
    if (b.startsWith("/")) {
      b = b.substring(1);
    }

    return a + "/" + b;
  }

  /**
   * Determines if a path string represents an absolute path.
   *
   * <p>Similar to Path::isAbsolute, but supports paths that contain glob characters that are
   * illegal on some file systems like "*" on Windows.
   */
  public boolean isAbsolute() {
    return isAbsolutePath(pattern);
  }

  private static boolean isAbsolutePath(String path) {
    if (path == null || path.isEmpty()) {
      return false;
    }

    // 1. Unix-style absolute path (starts with /)
    if (path.startsWith("/")) {
      return true;
    }

    // 2. Windows UNC paths (starts with \\)
    if (path.startsWith("\\\\")) {
      return true;
    }

    // 3. Windows Drive Letter (e.g., C:\ or C:/)
    // Requirements: Letter + Colon + Separator
    if (path.length() >= 3) {
      char letter = path.charAt(0);
      char colon = path.charAt(1);
      char sep = path.charAt(2);

      boolean isLetter = (letter >= 'a' && letter <= 'z') || (letter >= 'A' && letter <= 'Z');

      // Must match "X:/" or "X:\"
      if (isLetter && colon == ':' && (sep == '/' || sep == '\\')) {
        return true;
      }
    }

    return false;
  }

  public Optional<Path> resolvePath() {
    var matchingFiles = resolvePathsWithLimit(1);
    return matchingFiles.isEmpty() ? Optional.empty() : Optional.of(matchingFiles.get(0));
  }

  public List<Path> resolvePaths() {
    return resolvePathsWithLimit(Integer.MAX_VALUE);
  }

  private List<Path> resolvePathsWithLimit(int limit) {
    // Avoid calling Paths.get(pattern) when pattern contains glob chars
    // (e.g. '*' on Windows) because that can throw InvalidPathException.
    String patternString = pattern;
    boolean globSymbolsInPattern = isGlobPattern(patternString);

    Path fullPath = null;
    Path staticRoot = Paths.get(".");

    if (!globSymbolsInPattern) {
      // Safe to build a Path for literal paths.
      fullPath = Paths.get(patternString);

      // 1. Calculate Static Root (Same optimization as before)
      Path remainingPattern = fullPath;
      if (fullPath.isAbsolute()) {
        staticRoot = fullPath.getRoot();
        remainingPattern = staticRoot.relativize(fullPath);
      }

      for (Path part : remainingPattern) {
        String partStr = part.toString();
        if (staticRoot.toString().equals(".")) {
          staticRoot = Paths.get(partStr);
        } else {
          staticRoot = staticRoot.resolve(partStr);
        }
      }

      // 2. Fast path: pattern had no glob symbols, treat as a literal path.
      return Files.exists(fullPath) ? List.of(fullPath) : List.of();
    } else {
      // Pattern contains glob chars. Don't call Paths.get(patternString) directly.
      // Build staticRoot by iterating string segments safely.
      String normalized = patternString.replace('\\', '/');
      String[] parts = normalized.split("/");

      // Handle absolute-ish prefixes: Unix '/', Windows 'C:' and UNC '\\'
      if (patternString.startsWith("/")) {
        staticRoot = Paths.get("/");
      } else if (patternString.startsWith("\\\\")) {
        // UNC: leave staticRoot as '.' and let Files.walkFileTree handle it,
        // best-effort; we avoid constructing an invalid Path from the UNC prefix.
        staticRoot = Paths.get(".");
      } else if (parts.length > 0 && parts[0].matches("[A-Za-z]:")) {
        // Drive letter like C: or C:\
        staticRoot = Paths.get(parts[0] + "/");
      }

      for (int i = 0; i < parts.length; i++) {
        String part = parts[i];
        if (part.isEmpty()) continue; // skip repeated slashes
        if (isGlobPattern(part)) {
          break;
        }
        if (staticRoot.toString().equals(".")) {
          staticRoot = Paths.get(part);
        } else {
          staticRoot = staticRoot.resolve(part);
        }
      }
    }

    // 3. Use walkFileTree to handle errors gracefully
    if (!Files.exists(staticRoot)) return List.of();

    String globSyntax = "glob:" + patternString.replace("\\", "\\\\");
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globSyntax);

    var matchingFiles = new ArrayList<Path>();
    try {
      Files.walkFileTree(
          staticRoot,
          new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (matcher.matches(file)) {
                matchingFiles.add(file);
                LOGGER.info("Found matching file for {}: {}", globSyntax, file);
                if (matchingFiles.size() >= limit) {
                  return FileVisitResult.TERMINATE; // Stop searching immediately
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              // Log the IOException then swallow it.
              LOGGER.warn("Error reading file from {}: {}", globSyntax, exc.toString());
              return FileVisitResult.CONTINUE;
            }

            // Skip dirs that start with a dot.
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (dir.getFileName().toString().startsWith(".")) {
                LOGGER.warn("Skipping dot dir while resolving {}: {}", globSyntax, dir);
                return FileVisitResult.SKIP_SUBTREE;
              }
              if (matcher.matches(dir)) {
                matchingFiles.add(dir);
                LOGGER.info("Found matching dir for {}: {}", globSyntax, dir);
                if (matchingFiles.size() >= limit) {
                  return FileVisitResult.TERMINATE; // Stop searching immediately
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      LOGGER.error("IOException while resolving %s".formatted(globSyntax), e);
    }

    return matchingFiles;
  }

  private static boolean isGlobPattern(String s) {
    return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("[");
  }
}

/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent;

import static io.opentracing.contrib.specialagent.Constants.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class AssembleUtil {
  private static final Logger logger = Logger.getLogger(AssembleUtil.class);
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;

  public static boolean hasFileInJar(final File jarFile, final String name) throws IOException {
    try (final ZipFile zipFile = new ZipFile(jarFile)) {
      return getEntryFromJar(zipFile, name) != null;
    }
  }

  public static String readFileFromJar(final File jarFile, final String name) throws IOException {
    try (final ZipFile zipFile = new ZipFile(jarFile)) {
      final ZipEntry entry = getEntryFromJar(zipFile, name);
      if (entry == null)
        return null;

      try (final InputStream in = zipFile.getInputStream(entry)) {
        return new String(readBytes(in));
      }
    }
  }

  private static ZipEntry getEntryFromJar(final ZipFile zipFile, final String name) {
    final Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    while (enumeration.hasMoreElements()) {
      final ZipEntry entry = enumeration.nextElement();
      if (entry.getName().equals(name))
        return entry;
    }

    return null;
  }

  /**
   * Returns a {@code Set} of string paths representing the classpath locations
   * of the specified classes.
   *
   * @param classes The classes for which to return a {@code Set} of classpath
   *          paths.
   * @return A {@code Set} of string paths representing the classpath locations
   *         of the specified classes.
   * @throws IOException If an I/O error has occurred.
   */
  public static Set<String> getLocations(final Class<?> ... classes) throws IOException {
    final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    final Set<String> locations = new LinkedHashSet<>();
    for (final Class<?> cls : classes) {
      final String resourceName = classNameToResource(cls);
      final Enumeration<URL> resources = classLoader.getResources(resourceName);
      while (resources.hasMoreElements()) {
        final String resource = resources.nextElement().getFile();
        locations.add(resource.startsWith("file:") ? resource.substring(5, resource.indexOf('!')) : resource.substring(0, resource.length() - resourceName.length() - 1));
      }
    }

    return locations;
  }

  /**
   * Returns the array of bytes read from the specified {@code URL}.
   *
   * @param url The {@code URL} from which to read bytes.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws NullPointerException If the specified {@code URL} is null.
   */
  public static byte[] readBytes(final URL url) {
    try {
      try (final InputStream in = url.openStream()) {
        return readBytes(in);
      }
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the array of bytes read from the specified {@code InputStream}.
   *
   * @param in The {@code InputStream} from which to read bytes.
   * @return The array of bytes read from an {@code InputStream}.
   * @throws IOException If an I/O error has occurred.
   * @throws NullPointerException If the specified {@code InputStream} is null.
   */
  public static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] bytes = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(bytes)) != -1;)
      if (len != 0)
        buffer.write(bytes, 0, len);

    return buffer.toByteArray();
  }

  /**
   * Returns string representation of the specified array.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Arrays#toString(Object[])} by formatting the output to separate
   * entries onto new lines, indented with 2 spaces. If the specified array is
   * null, this method returns the string {@code "null"}. If the length of the
   * specified array is 0, this method returns {@code ""}.
   *
   * @param a The array.
   * @return An indented string representation of the specified array, using the
   *         algorithm in {@link Arrays#toString(Object[])}.
   */
  public static String toIndentedString(final Object[] a) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(a[i]);
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified collection.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Collection#toString()} by formatting the output to separate entries
   * onto new lines, indented with 2 spaces. If the specified collection is
   * null, this method returns the string {@code "null"}. If the size of the
   * specified collection is 0, this method returns {@code ""}.
   *
   * @param l The collection.
   * @return An indented string representation of the specified {@code List},
   *         using the algorithm in {@link Collection#toString()}.
   */
  public static String toIndentedString(final Collection<?> l) {
    if (l == null)
      return "null";

    if (l.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = l.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Returns string representation of the specified map.
   * <p>
   * This method differentiates itself from the algorithm in
   * {@link Map#toString()} by formatting the output to separate entries
   * onto new lines, indented with 2 spaces. If the specified map is
   * null, this method returns the string {@code "null"}. If the size of the
   * specified map is 0, this method returns {@code ""}.
   *
   * @param m The map.
   * @return An indented string representation of the specified {@code List},
   *         using the algorithm in {@link Map#toString()}.
   */
  public static String toIndentedString(final Map<?,?> m) {
    if (m == null)
      return "null";

    if (m.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = m.entrySet().iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(",\n");

      builder.append(iterator.next());
    }

    return builder.toString();
  }

  /**
   * Recursively process each sub-path of the specified directory.
   *
   * @param dir The directory to process.
   * @param predicate The predicate defining the test process.
   * @return {@code true} if the specified predicate returned {@code true} for
   *         each sub-path to which it was applied, otherwise {@code false}.
   */
  public static boolean recurseDir(final File dir, final Predicate<File> predicate) {
    final File[] files = dir.listFiles();
    if (files != null)
      for (final File file : files)
        if (!recurseDir(file, predicate))
          return false;

    return predicate.test(dir);
  }

  /**
   * Recursively process each sub-path of the specified directory.
   *
   * @param dir The directory to process.
   * @param function The function defining the test process, which returns a
   *          {@link FileVisitResult} to direct the recursion process.
   * @return A {@link FileVisitResult} to direct the recursion process.
   */
  public static FileVisitResult recurseDir(final File dir, final Function<File,FileVisitResult> function) {
    final File[] files = dir.listFiles();
    if (files != null) {
      for (final File file : files) {
        final FileVisitResult result = recurseDir(file, function);
        if (result == FileVisitResult.SKIP_SIBLINGS)
          break;

        if (result == FileVisitResult.TERMINATE)
          return result;

        if (result == FileVisitResult.SKIP_SUBTREE)
          return FileVisitResult.SKIP_SIBLINGS;
      }
    }

    return function.apply(dir);
  }

  /**
   * Compares two {@code Object} arrays, within comparable elements,
   * lexicographically.
   * <p>
   * A {@code null} array reference is considered lexicographically less than a
   * non-{@code null} array reference. Two {@code null} array references are
   * considered equal. A {@code null} array element is considered
   * lexicographically than a non-{@code null} array element. Two {@code null}
   * array elements are considered equal.
   * <p>
   * The comparison is consistent with {@link Arrays#equals(Object[], Object[])
   * equals}, more specifically the following holds for arrays {@code a} and
   * {@code b}:
   *
   * <pre>
   * {@code Arrays.equals(a, b) == (AssembleUtil.compare(a, b) == 0)}
   * </pre>
   *
   * @param a The first array to compare.
   * @param b The second array to compare.
   * @param <T> The type of comparable array elements.
   * @return The value {@code 0} if the first and second array are equal and
   *         contain the same elements in the same order; a value less than
   *         {@code 0} if the first array is lexicographically less than the
   *         second array; and a value greater than {@code 0} if the first array
   *         is lexicographically greater than the second array.
   */
  public static <T extends Comparable<? super T>>int compare(final T[] a, final T[] b) {
    if (a == b)
      return 0;

    // A null array is less than a non-null array
    if (a == null || b == null)
      return a == null ? -1 : 1;

    int length = Math.min(a.length, b.length);
    for (int i = 0; i < length; ++i) {
      final T oa = a[i];
      final T ob = b[i];
      if (oa != ob) {
        // A null element is less than a non-null element
        if (oa == null || ob == null)
          return oa == null ? -1 : 1;

        final int v = oa.compareTo(ob);
        if (v != 0)
          return v;
      }
    }

    return a.length - b.length;
  }

  /**
   * Compares two {@code Object} lists, within comparable elements,
   * lexicographically.
   * <p>
   * A {@code null} list reference is considered lexicographically less than a
   * non-{@code null} list reference. Two {@code null} list references are
   * considered equal. A {@code null} list element is considered
   * lexicographically than a non-{@code null} list element. Two {@code null}
   * list elements are considered equal.
   * <p>
   * The comparison is consistent with {@link Objects#equals(Object,Object)
   * equals}, more specifically the following holds for arrays {@code a} and
   * {@code b}:
   *
   * <pre>
   * {@code Objects.equals(a, b) == (AssembleUtil.compare(a, b) == 0)}
   * </pre>
   *
   * @param a The first list to compare.
   * @param b The second list to compare.
   * @param <T> The type of comparable list elements.
   * @return The value {@code 0} if the first and second list are equal and
   *         contain the same elements in the same order; a value less than
   *         {@code 0} if the first list is lexicographically less than the
   *         second list; and a value greater than {@code 0} if the first list
   *         is lexicographically greater than the second list.
   */
  public static <T extends Comparable<? super T>>int compare(final List<T> a, final List<T> b) {
    if (a == b)
      return 0;

    // A null array is less than a non-null array
    if (a == null || b == null)
      return a == null ? -1 : 1;

    int length = Math.min(a.size(), b.size());
    for (int i = 0; i < length; ++i) {
      final T oa = a.get(i);
      final T ob = b.get(i);
      if (oa != ob) {
        // A null element is less than a non-null element
        if (oa == null || ob == null)
          return oa == null ? -1 : 1;

        final int v = oa.compareTo(ob);
        if (v != 0)
          return v;
      }
    }

    return a.size() - b.size();
  }

  /**
   * Tests whether the first specified array contains all {@link Comparable}
   * elements in the second specified array.
   *
   * @param <T> Type parameter of array, which must extend {@link Comparable}.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @return {@code true} if the first specifies array contains all elements in
   *         the second specified array.
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  public static <T extends Comparable<? super T>>boolean containsAll(final T[] a, final T[] b) {
    for (int i = 0, j = 0;;) {
      if (j == b.length)
        return true;

      if (i == a.length)
        return false;

      final int comparison = a[i].compareTo(b[j]);
      if (comparison > 0)
        return false;

      ++i;
      if (comparison == 0)
        ++j;
    }
  }

  /**
   * Tests whether the first specifies array contains all elements in the second
   * specified array, with comparison determined by the specified
   * {@link Comparator}.
   *
   * @param <T> Type parameter of array.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @param c The {@link Comparator}.
   * @return {@code true} if the first specifies array contains all elements in
   *         the second specified array.
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  public static <T>boolean containsAll(final T[] a, final T[] b, final Comparator<T> c) {
    for (int i = 0, j = 0;;) {
      if (j == b.length)
        return true;

      if (i == a.length)
        return false;

      final int comparison = c.compare(a[i], b[j]);
      if (comparison > 0)
        return false;

      ++i;
      if (comparison == 0)
        ++j;
    }
  }

  /**
   * Returns an array of type {@code <T>} that includes only the elements that
   * belong to the specified arrays (the specified arrays must be sorted).
   * <p>
   * <i><b>Note:</b> This is a recursive algorithm, implemented to take
   * advantage of the high performance of callstack registers, but will fail due
   * to a {@link StackOverflowError} if the number of differences between the
   * first and second specified arrays approaches ~8000.</i>
   *
   * @param <T> Type parameter of array.
   * @param a The first specified array (sorted).
   * @param b The second specified array (sorted).
   * @param i The starting index of the first specified array (should be set to
   *          0).
   * @param j The starting index of the second specified array (should be set to
   *          0).
   * @param r The starting index of the resulting array (should be set to 0).
   * @return An array of type {@code <T>} that includes only the elements that
   *         belong to the first and second specified array (the specified
   *         arrays must be sorted).
   * @throws NullPointerException If {@code a} or {@code b} are null.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Comparable<? super T>>T[] retain(final T[] a, final T[] b, final int i, final int j, final int r) {
    for (int d = 0;; ++d) {
      int comparison = 0;
      if (i + d == a.length || j + d == b.length || (comparison = a[i + d].compareTo(b[j + d])) != 0) {
        final T[] retained;
        if (i + d == a.length || j + d == b.length)
          retained = r + d == 0 ? null : (T[])Array.newInstance(a.getClass().getComponentType(), r + d);
        else if (comparison < 0)
          retained = retain(a, b, i + d + 1, j + d, r + d);
        else
          retained = retain(a, b, i + d, j + d + 1, r + d);

        if (d > 0)
          System.arraycopy(a, i, retained, r, d);

        return retained;
      }
    }
  }

  /**
   * Sorts the specified array of objects into ascending order, according to the
   * natural ordering of its elements. All elements in the array must implement
   * the {@link Comparable} interface. Furthermore, all elements in the array
   * must be mutually comparable (that is, {@code e1.compareTo(e2)} must not
   * throw a {@link ClassCastException} for any elements {@code e1} and
   * {@code e2} in the array).
   *
   * @param <T> The component type of the specified array.
   * @param array The array to be sorted.
   * @return The specified array, which is sorted in-place (unless it is null).
   * @see Arrays#sort(Object[])
   */
  public static <T>T[] sort(final T[] array) {
    if (array == null)
      return null;

    Arrays.sort(array);
    return array;
  }

  /**
   * Sorts the specified list of objects into ascending order, according to the
   * natural ordering of its elements. All elements in the list must implement
   * the {@link Comparable} interface. Furthermore, all elements in the list
   * must be mutually comparable (that is, {@code e1.compareTo(e2)} must not
   * throw a {@link ClassCastException} for any elements {@code e1} and
   * {@code e2} in the list).
   *
   * @param <T> The component type of the specified list.
   * @param list The list to be sorted.
   * @return The specified list, which is sorted in-place (unless it is null).
   * @see Collections#sort(List)
   */
  public static <T extends Comparable<? super T>>List<T> sort(final List<T> list) {
    if (list == null)
      return null;

    Collections.sort(list);
    return list;
  }

  /**
   * Returns the name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getSimpleNameId(Object)
   */
  public static String getNameId(final Object obj) {
    return obj != null ? obj.getClass().getName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
  }

  /**
   * Returns the simple name of the class of the specified object suffixed with
   * {@code '@'} followed by the hexadecimal representation of the object's
   * identity hash code, or {@code "null"} if the specified object is null.
   *
   * @param obj The object.
   * @return The simple name of the class of the specified object suffixed with
   *         {@code '@'} followed by the hexadecimal representation of the
   *         object's identity hash code, or {@code "null"} if the specified
   *         object is null.
   * @see #getNameId(Object)
   */
  public static String getSimpleNameId(final Object obj) {
    return obj != null ? obj.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(obj), 16) : "null";
  }

  /**
   * Returns a string representation of the specified array, using the specified
   * delimiter between the string representation of each element. If the
   * specified array is null, this method returns the string {@code "null"}. If
   * the length of the specified array is 0, this method returns {@code ""}.
   *
   * @param a The array.
   * @param del The delimiter.
   * @return A string representation of the specified array, using the specified
   *         delimiter between the string representation of each element.
   */
  public static String toString(final Object[] a, final String del) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < a.length; ++i) {
      if (i > 0)
        builder.append(del);

      builder.append(String.valueOf(a[i]));
    }

    return builder.toString();
  }

  /**
   * Returns a string representation of the specified collection, using the
   * specified delimiter between the string representation of each element. If
   * the specified collection is null, this method returns the string
   * {@code "null"}. If the size of the specified collection is 0, this method
   * returns {@code ""}.
   *
   * @param c The array.
   * @param del The delimiter.
   * @return A string representation of the specified array, using the specified
   *         delimiter between the string representation of each element.
   */
  public static String toString(final Collection<?> c, final String del) {
    if (c == null)
      return "null";

    if (c.size() == 0)
      return "";

    final StringBuilder builder = new StringBuilder();
    final Iterator<?> iterator = c.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      if (i > 0)
        builder.append(del);

      builder.append(String.valueOf(iterator.next()));
    }

    return builder.toString();
  }

  public static void absorbProperties(final String command) {
    final String[] parts = command.split("\\s+-");
    for (int i = 0; i < parts.length; ++i) {
      final String part = parts[i];
      if (part.charAt(0) != 'D')
        continue;

      final int index = part.indexOf('=');
      if (index == -1)
        System.setProperty(part.substring(1), "");
      else
        System.setProperty(part.substring(1, index), part.substring(index + 1));
    }
  }

  public static <T>void forEachClass(final URL[] urls, final T arg, final BiConsumer<String,T> consumer) throws IOException {
    for (final URL url : urls) {
      if (url.getPath().endsWith(".jar")) {
        try (final ZipInputStream in = new ZipInputStream(url.openStream())) {
          for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
            final String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(name, arg);
            }
          }
        }
      }
      else {
        final File file = new File(url.getPath());
        final Path path = file.toPath();
        AssembleUtil.recurseDir(file, new Predicate<File>() {
          @Override
          public boolean test(final File t) {
            if (t.isDirectory())
              return true;

            final String name = path.relativize(t.toPath()).toString();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("module-info")) {
              consumer.accept(name, arg);
            }

            return true;
          }
        });
      }
    }
  }

  private static URL _toURL(final File file) throws MalformedURLException {
    final String path = file.getAbsolutePath();
    return new URL("file", "", file.isDirectory() ? path + "/" : path);
  }

  public static URL toURL(final File file) {
    try {
      return _toURL(file);
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final File ... files) {
    try {
      final URL[] urls = new URL[files.length];
      for (int i = 0; i < files.length; ++i)
        urls[i] = _toURL(files[i]);

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final List<File> files) {
    try {
      final URL[] urls = new URL[files.size()];
      for (int i = 0; i < files.size(); ++i)
        urls[i] = _toURL(files.get(i));

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static URL[] toURLs(final Collection<File> files) {
    try {
      final URL[] urls = new URL[files.size()];
      final Iterator<File> iterator = files.iterator();
      for (int i = 0; iterator.hasNext(); ++i)
        urls[i] = AssembleUtil._toURL(iterator.next());

      return urls;
    }
    catch (final MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String convertToRegex(String pattern) {
    return pattern
      .replace("\\", "\\\\")
      .replace(".", "\\.")
      .replace("^", "\\^")
      .replace("$", "\\$")
      .replace("*", ".*")
      .replace("/", "\\/")
      .replace('?', '.');
  }

  public static String classNameToResource(final String className) {
    return className.replace('.', '/').concat(".class");
  }

  public static String classNameToResource(final Class<?> cls) {
    return classNameToResource(cls.getName());
  }

  public static String resourceToClassName(final String resource) {
    return resource.substring(0, resource.length() - 6).replace('/', '.');
  }

  /**
   * Returns {@code true} if and only if the system property named by the
   * argument exists and is not equal to the string {@code "false"}.
   *
   * @param key The name of the system property.
   * @return {@code true} if and only if the system property named by the
   *         argument exists and is not equal to the string {@code "false"}.
   */
  public static boolean isSystemProperty(final String key, final String deprecatedKey) {
    String value = System.getProperty(key);
    if (value != null)
      return !"false".equals(value);

    if (deprecatedKey == null)
      return false;

    value = System.getProperty(deprecatedKey);
    if (value == null)
      return false;

    logger.warning("Deprecated key (as of v1.7.0): \"" + deprecatedKey + "\" should be changed to \"" + key + "\"");
    return !"false".equals(value);
  }

  public static Pattern convertToNameRegex(String pattern) {
    if (pattern.length() == 0)
      throw new IllegalArgumentException("Empty pattern");

    final char lastCh = pattern.charAt(pattern.length() - 1);
    if (lastCh == '*')
      pattern = pattern.substring(0, pattern.length() - 1);

    final String regex = "^" + AssembleUtil.convertToRegex(pattern).replace(".*", "[^:]*");
    boolean hasDigit = false;
    for (int i = regex.length() - 2; i >= 0; --i) {
      if (regex.charAt(i) == ':') {
        hasDigit = Character.isDigit(regex.charAt(i + 1));
        break;
      }
    }

    if (lastCh == '?')
      return Pattern.compile(regex);

    if (hasDigit || regex.length() == 1 || regex.endsWith(":"))
      return Pattern.compile(regex + ".*");

    return Pattern.compile("(" + regex + "$|" + regex + ":.*)");
  }

  /**
   * Returns the source location of the specified resource in the provided URL.
   *
   * @param url The {@code URL} from which to find the source location.
   * @param resourcePath The resource path that is the suffix of the specified
   *          URL.
   * @return The source location of the specified resource in the provided URL.
   * @throws MalformedURLException If no protocol is specified, or an unknown
   *           protocol is found, or spec is null.
   * @throws IllegalArgumentException If the specified resource path is not the
   *           suffix of the specified URL.
   */
  public static File getSourceLocation(final URL url, final String resourcePath) throws MalformedURLException {
    final String string = url.toString();
    if (!string.endsWith(resourcePath))
      throw new IllegalArgumentException(url + " does not end with \"" + resourcePath + "\"");

    if (string.startsWith("jar:file:"))
      return new File(string.substring(9, string.lastIndexOf('!')));

    if (string.startsWith("file:"))
      return new File(string.substring(5, string.length() - resourcePath.length()));

    throw new UnsupportedOperationException("Unsupported protocol: " + url.getProtocol());
  }

  /**
   * Returns the name of the file or directory denoted by the specified
   * pathname. This is just the last name in the name sequence of {@code path}.
   * If the name sequence of {@code path} is empty, then the empty string is
   * returned.
   *
   * @param path The path string.
   * @return The name of the file or directory denoted by the specified
   *         pathname, or the empty string if the name sequence of {@code path}
   *         is empty.
   * @throws NullPointerException If {@code path} is null.
   * @throws IllegalArgumentException If {@code path} is an empty string.
   */
  public static String getName(final String path) {
    if (path.length() == 0)
      throw new IllegalArgumentException("Empty path");

    if (path.length() == 0)
      return path;

    final boolean end = path.charAt(path.length() - 1) == File.separatorChar;
    final int start = end ? path.lastIndexOf(File.separatorChar, path.length() - 2) : path.lastIndexOf(File.separatorChar);
    return start == -1 ? (end ? path.substring(0, path.length() - 1) : path) : end ? path.substring(start + 1, path.length() - 1) : path.substring(start + 1);
  }

  private static boolean propertiesLoaded = false;

  private static void loadProperties(final Map<String,String> properties, final BufferedReader reader) throws IOException {
    for (String line; (line = reader.readLine()) != null;) {
      line = line.trim();
      char ch;
      if (line.length() == 0 || (ch = line.charAt(0)) == '#' || ch == '!')
        continue;

      final int eq = line.indexOf('=');
      if (eq == -1) {
        properties.put(line, "");
      }
      else if (eq > 0) {
        final String key = line.substring(0, eq).trim();
        final String value = line.substring(eq + 1).trim();
        if (key.length() > 0)
          properties.put(key, value);
      }
    }
  }

  static void loadProperties() {
    if (propertiesLoaded)
      return;

    propertiesLoaded = true;
    final String configProperty = System.getProperty(CONFIG_ARG);
    try (
      final InputStream defaultConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream("default.properties");
      final FileReader userConfig = configProperty == null ? null : new FileReader(configProperty);
    ) {
      final Map<String,String> properties = new HashMap<>();

      // Load default config properties
      loadProperties(properties, new BufferedReader(new InputStreamReader(defaultConfig)));

      // Load user config properties
      if (userConfig != null)
        loadProperties(properties, new BufferedReader(userConfig));

      // Set config properties as system properties
      for (final Map.Entry<String,String> entry : properties.entrySet())
        if (System.getProperty(entry.getKey()) == null)
          System.setProperty(entry.getKey(), entry.getValue());

      Logger.refreshLoggers();
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns an array of {@link File} objects representing each path entry in
   * the specified {@code classpath}.
   *
   * @param classpath The classpath which to convert to an array of {@link File}
   *          objects.
   * @return An array of {@link File} objects representing each path entry in
   *         the specified {@code classpath}.
   */
  public static File[] classPathToFiles(final String classpath) {
    if (classpath == null)
      return null;

    final String[] paths = classpath.split(File.pathSeparator);
    final File[] files = new File[paths.length];
    for (int i = 0; i < paths.length; ++i)
      files[i] = new File(paths[i]).getAbsoluteFile();

    return files;
  }

  public static URL toURL(final String path) {
    try {
      return new URL("file", "", path);
    }
    catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private AssembleUtil() {
  }
}
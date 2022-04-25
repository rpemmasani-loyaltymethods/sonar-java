/*
 * SonarQube Java
 * Copyright (C) 2012-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.AnalysisException;
import org.sonar.java.EndOfAnalysisCheck;
import org.sonar.java.model.DefaultJavaFileScannerContext;
import org.sonar.java.model.ExpressionUtils;
import org.sonar.java.reporting.AnalyzerMessage;
import org.sonar.plugins.java.api.InputFileScannerContext;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.caching.CacheContext;
import org.sonar.plugins.java.api.caching.JavaWriteCache;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.Tree;

import static org.sonar.plugins.java.api.semantic.MethodMatchers.ANY;

@Rule(key = "S5693")
public class ExcessiveContentRequestCheck extends IssuableSubscriptionVisitor implements EndOfAnalysisCheck {

  @RuleProperty(
    key = "fileUploadSizeLimit",
    description = "The maximum size of HTTP requests handling file uploads (in bytes).",
    defaultValue = "" + DEFAULT_MAX)
  public long fileUploadSizeLimit = DEFAULT_MAX;

  private static final long BYTES_PER_KB = 1_024L;
  private static final long BYTES_PER_MB = 1_048_576L;
  private static final long BYTES_PER_GB = 1_073_741_824L;
  private static final long BYTES_PER_TB = 1_099_511_627_776L;

  private static final long DEFAULT_MAX = 8 * BYTES_PER_MB;

  private static final String MESSAGE_EXCEED_SIZE = "The content length limit of %d bytes is greater than the defined limit of %d; make sure it is safe here.";
  private static final String MESSAGE_SIZE_NOT_SET = "Make sure not setting any maximum content length limit is safe here.";

  private static final Pattern DATA_SIZE_PATTERN = Pattern.compile("^([+\\-]?\\d+)([a-zA-Z]{0,2})$");

  private static final String MULTIPART_RESOLVER = "org.springframework.web.multipart.commons.CommonsMultipartResolver";
  private static final String MULTIPART_CONFIG = "org.springframework.boot.web.servlet.MultipartConfigFactory";
  private static final MethodMatchers METHODS_SETTING_MAX_SIZE = MethodMatchers.or(
    MethodMatchers.create()
      .ofSubTypes(MULTIPART_RESOLVER)
      .names("setMaxUploadSize")
      .addParametersMatcher("long")
      .build(),
    MethodMatchers.create()
      .ofSubTypes(MULTIPART_CONFIG)
      .names("setMaxFileSize", "setMaxRequestSize")
      .addParametersMatcher("long")
      .addParametersMatcher("java.lang.String")
      .build()
  );

  private static final MethodMatchers MULTIPART_CONSTRUCTOR = MethodMatchers.create()
    .ofSubTypes(MULTIPART_RESOLVER, MULTIPART_CONFIG)
    .constructor()
    .withAnyParameters()
    .build();

  private static final String DATA_SIZE = "org.springframework.util.unit.DataSize";

  private static final MethodMatchers DATA_SIZE_OF_SOMETHING = MethodMatchers.create()
    .ofSubTypes(DATA_SIZE)
    .name(name -> name.startsWith("of"))
    .addParametersMatcher("long")
    .build();

  private static final MethodMatchers DATA_SIZE_WITH_UNIT = MethodMatchers.create()
    .ofSubTypes(DATA_SIZE)
    .names("parse", "of")
    .addParametersMatcher(ANY, "org.springframework.util.unit.DataUnit")
    .build();

  private static final MethodMatchers DATA_SIZE_PARSE = MethodMatchers.create()
    .ofSubTypes(DATA_SIZE)
    .names("parse")
    .addParametersMatcher("java.lang.CharSequence")
    .build();

  public static final String CACHE_KEY_CACHED = "java:S5693:cached";
  public static final String CACHE_KEY_INSTANTIATE = "java:S5693:instantiate";
  public static final String CACHE_KEY_SET_MAXIMUM_SIZE = "java:S5693:maximumSize";

  private static final Logger LOGGER = Loggers.get(ExcessiveContentRequestCheck.class);

  private final List<AnalyzerMessage> multipartConstructorIssues = new ArrayList<>();
  private boolean sizeSetSomewhere = false;

  private Set<String> filesCached = new HashSet<>();

  private final List<String> filesThatSetMaximumSize = new ArrayList<>();
  private final List<String> filesThatInstantiate = new ArrayList<>();

  @Override
  public boolean scanWithoutParsing(InputFileScannerContext context) {
    InputFile unchangedFile = context.getInputFile();
    CacheContext cacheContext = context.getCacheContext();
    // Check if results have been cached previously for this unchanged file
    if (!isCached(cacheContext, unchangedFile)) {
      LOGGER.debug(() -> String.format("No cached data for %s", unchangedFile));
      return false;
    }

    filesCached.add(unchangedFile.key());

    boolean isInstantiating = isInstantiating(cacheContext, unchangedFile);
    if (isInstantiating) {
      filesThatInstantiate.add(unchangedFile.key());
    }

    boolean isSettingMaximumSize = isSettingMaximumSize(cacheContext, unchangedFile);
    if (isSettingMaximumSize) {
      sizeSetSomewhere = true;
      filesThatSetMaximumSize.add(unchangedFile.key());
    }

    keepForNextAnalysis(cacheContext, context.getInputFile(), isInstantiating, isSettingMaximumSize);

    return true;
  }

  @Override
  public void endOfAnalysis(CacheContext cacheContext) {
    if (!sizeSetSomewhere && context != null) {
      DefaultJavaFileScannerContext defaultContext = (DefaultJavaFileScannerContext) context;
      multipartConstructorIssues.forEach(defaultContext::reportIssue);
    }
    filesThatSetMaximumSize.clear();
    filesThatInstantiate.clear();
    filesCached.clear();
    multipartConstructorIssues.clear();
    sizeSetSomewhere = false;
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Arrays.asList(Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    DefaultJavaFileScannerContext defaultContext = (DefaultJavaFileScannerContext) context;
    if (tree.is(Tree.Kind.NEW_CLASS)) {
      NewClassTree newClassTree = (NewClassTree) tree;
      if (MULTIPART_CONSTRUCTOR.matches(newClassTree)) {
        // Create an issue that we will report only at the end of the analysis if the maximum size was never set.
        AnalyzerMessage analyzerMessage = defaultContext.createAnalyzerMessage(this, newClassTree, MESSAGE_SIZE_NOT_SET);
        multipartConstructorIssues.add(analyzerMessage);
        filesThatInstantiate.add(context.getInputFile().key());
      }
    } else {
      MethodInvocationTree mit = (MethodInvocationTree) tree;
      if (METHODS_SETTING_MAX_SIZE.matches(mit)) {
        filesThatSetMaximumSize.add(context.getInputFile().key());
        sizeSetSomewhere = true;
        getIfExceedSize(mit.arguments().get(0))
          .map(bytesExceeding -> {
            return defaultContext.createAnalyzerMessage(this, mit, String.format(MESSAGE_EXCEED_SIZE, bytesExceeding, fileUploadSizeLimit));
          })
          .ifPresent(defaultContext::reportIssue);
      }
    }
  }

  @Override
  public void leaveFile(JavaFileScannerContext context) {
    super.leaveFile(context);
    boolean instantiates =  filesThatInstantiate.contains(context.getInputFile().key());
    boolean setsMaximumSize = filesThatSetMaximumSize.contains(context.getInputFile().key());
    CacheContext cacheContext = context.getCacheContext();
    if (cacheContext.isCacheEnabled()) {
      writeForNextAnalysis(cacheContext, context.getInputFile(), instantiates, setsMaximumSize);
    }
  }

  private Optional<Long> getIfExceedSize(ExpressionTree expressionTree) {
    if (expressionTree.is(Tree.Kind.METHOD_INVOCATION)) {
      return getSizeFromDataSize((MethodInvocationTree) expressionTree)
        .filter(b -> b > fileUploadSizeLimit);
    }
    return getNumberOfBytes(expressionTree).filter(b -> b > fileUploadSizeLimit);
  }

  private static Optional<Long> getSizeFromDataSize(MethodInvocationTree mit) {
    if (DATA_SIZE_PARSE.matches(mit)) {
      return getNumberOfBytes(mit.arguments().get(0));
    } else if (DATA_SIZE_OF_SOMETHING.matches(mit)) {
      return getNumberOfBytes(mit.arguments().get(0))
        .map(b -> b * getMultiplierFromName(ExpressionUtils.methodName(mit).name()));
    } else if (DATA_SIZE_WITH_UNIT.matches(mit)) {
      Optional<Long> multiplier = getIdentifierName(mit.arguments().get(1))
        .map(ExcessiveContentRequestCheck::getMultiplierFromName);
      if (multiplier.isPresent()) {
        return getNumberOfBytes(mit.arguments().get(0))
          .map(l -> l * multiplier.get());
      }
    }
    return Optional.empty();
  }

  private static Optional<Long> getNumberOfBytes(ExpressionTree expression) {
    Optional<Integer> integerOptional = expression.asConstant(Integer.class);
    if (integerOptional.isPresent()) {
      return Optional.of(integerOptional.get().longValue());
    }

    Optional<String> stringOptional = expression.asConstant(String.class);
    if (stringOptional.isPresent()) {
      return getLongValueFromString(stringOptional.get());
    }

    return expression.asConstant(Long.class);
  }

  private static Optional<Long> getLongValueFromString(String s) {
    Matcher matcher = DATA_SIZE_PATTERN.matcher(s);
    if (matcher.matches()) {
      return Optional.of(Long.parseLong(matcher.group(1)) * getMultiplierFromName(matcher.group(2)));
    }
    return Optional.empty();
  }

  private static Long getMultiplierFromName(String name) {
    switch (name.toUpperCase(Locale.ENGLISH)) {
      case "OFKILOBYTES":
      case "KILOBYTES":
      case "KB":
        return BYTES_PER_KB;
      case "OFMEGABYTES":
      case "MEGABYTES":
      case "MB":
        return BYTES_PER_MB;
      case "OFGIGABYTES":
      case "GIGABYTES":
      case "GB":
        return BYTES_PER_GB;
      case "OFTERABYTES":
      case "TERABYTES":
      case "TB":
        return BYTES_PER_TB;
      default:
        return 1L;
    }
  }

  private static Optional<String> getIdentifierName(ExpressionTree expression) {
    if (expression.is(Tree.Kind.IDENTIFIER)) {
      return Optional.of(((IdentifierTree) expression).name());
    } else if (expression.is(Tree.Kind.MEMBER_SELECT)) {
      return Optional.of(((MemberSelectExpressionTree) expression).identifier().name());
    }
    return Optional.empty();
  }

  private static String computeCacheKey(String base, InputFile inputFile) {
    return base + ":" + inputFile.key();
  }

  private static boolean isCached(CacheContext cacheContext, InputFile inputFile) {
    String cacheKey = computeCacheKey(CACHE_KEY_CACHED, inputFile);
    return cacheContext.getReadCache().contains(cacheKey);
  }

  public static boolean isInstantiating(CacheContext cacheContext, InputFile inputFile) {
    String cacheKey = computeCacheKey(CACHE_KEY_INSTANTIATE, inputFile);
    return cacheContext.getReadCache().contains(cacheKey);
  }

  public static boolean isSettingMaximumSize(CacheContext cacheContext, InputFile inputFile) {
    String cacheKey = computeCacheKey(CACHE_KEY_SET_MAXIMUM_SIZE, inputFile);
    return cacheContext.getReadCache().contains(cacheKey);
  }

  public static void keepForNextAnalysis(CacheContext cacheContext, InputFile inputFile, boolean instantiates, boolean setsMaximumSize) {
    JavaWriteCache writeCache = cacheContext.getWriteCache();
    try {
      if (instantiates) {
        writeCache.copyFromPrevious(CACHE_KEY_INSTANTIATE + ":" + inputFile.key());
      }
      if (setsMaximumSize) {
        writeCache.copyFromPrevious(CACHE_KEY_SET_MAXIMUM_SIZE + ":" + inputFile.key());
      }
      writeCache.copyFromPrevious(CACHE_KEY_CACHED + ":" + inputFile.key());
    } catch (IllegalArgumentException e) {
      String message = String.format("Failed to copy from previous cache for file %s", inputFile);
      LOGGER.trace(() -> message);
      throw new AnalysisException(message, e);
    }
  }

  public static void writeForNextAnalysis(CacheContext cacheContext, InputFile inputFile, boolean instantiates, boolean setsMaximumSize) {
    JavaWriteCache writeCache = cacheContext.getWriteCache();
    try {
      if (instantiates) {
        writeCache.write(computeCacheKey(CACHE_KEY_INSTANTIATE, inputFile), new byte[]{1});
      }
      if (setsMaximumSize) {
        writeCache.write(computeCacheKey(CACHE_KEY_SET_MAXIMUM_SIZE, inputFile), new byte[]{1});
      }
      writeCache.write(computeCacheKey(CACHE_KEY_CACHED, inputFile), new byte[]{1});
    } catch (IllegalArgumentException e) {
      String message = String.format("Failed to write to cache for file %s", inputFile);
      LOGGER.trace(() -> message);
      throw new AnalysisException(message, e);
    }
  }
}
